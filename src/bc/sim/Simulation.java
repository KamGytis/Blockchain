package bc.sim;

import bc.core.Blockchain;
import bc.core.Candidate;
import bc.core.Competition;
import bc.core.InvalidBlockException;
import bc.core.State;
import bc.core.TxError;
import bc.core.TxPool;
import bc.hash.Hash;
import bc.hash.HashUtil;
import bc.model.Block;
import bc.model.BlockHeader;
import bc.model.Transaction;
import bc.model.User;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Visa eiga viename procese: vartotojai -> transakcijos -> fondas -> kiekvienam aukščiui N kandidatų konkurencija
 * -> laimėtojo patikra ir prijungimas -> suvestinė.
 */
public final class Simulation {
    private final Config cfg;

    public Simulation(Config cfg) {
        this.cfg = cfg;
    }

    /** @return paskutinio bloko hash */
    public String run() throws IOException {
        long t0 = System.nanoTime();
        Random rng = new Random(cfg.seed());

        // 1. Vartotojai ir pradinė būsena (išsaugoma prieš taikant transakcijas)
        List<User> users = Generator.users(rng, cfg.users());
        State genesis = State.fromUsers(users);
        Blockchain chain = new Blockchain(genesis, cfg.difficulty(), cfg.blockSize());
        Files.createDirectories(cfg.outDir());
        writeInitialState(users, cfg.outDir().resolve("initial_state.csv"));

        // 2. Transakcijos ir fondas
        List<Transaction> generated = Generator.transactions(rng, users, cfg.transactions(), cfg.maxAmount(), cfg.invalidRate());
        TxPool pool = new TxPool();
        for (Transaction tx : generated) pool.submit(tx, chain);

        System.out.println("=== Blockchain v0.2 ===");
        System.out.println("hash:        " + Hash.VERSION);
        System.out.println("seed:        " + cfg.seed());
        System.out.printf("parametrai:  users=%d txs=%d block_size=%d difficulty=%d max_amount=%d invalid_rate=%s%n",
                cfg.users(), cfg.transactions(), cfg.blockSize(), cfg.difficulty(), cfg.maxAmount(), cfg.invalidRate());
        System.out.printf("kandidatai:  %d aukstiui, biudzetas %d bandymu arba %d ms kandidatui per raunda, "
                + "nepavykus x2, daugiausia %d raundu%n", cfg.candidates(), cfg.budgetAttempts(), cfg.budgetMs(), cfg.maxRounds());
        System.out.println("tx_set_fp:   " + fingerprint(generated) + "  (visu sugeneruotu tx ID maisa)");
        System.out.printf("fondas:      priimta %d, atmesta priimant %d%n%n", pool.size(), pool.rejected().size());

        // 3. Kiekvienam aukščiui – N kandidatų konkurencija; prijungiamas tik laimėtojas
        Competition.Budget budget = new Competition.Budget(cfg.budgetAttempts(),
                Math.multiplyExact(cfg.budgetMs(), 1_000_000L), cfg.maxRounds());
        Stats stats = new Stats(cfg.candidates());
        String stopReason = "fonde nebeliko ivykdomu transakciju";
        while (true) {
            long ts = cfg.fixedTime() ? chain.height() : System.currentTimeMillis() / 1000;
            Competition.Result r = Competition.buildAndRun(chain, pool, cfg.candidates(), cfg.blockSize(), rng, ts, budget);
            r.permanentErrors().forEach(pool::reject);
            if (r.candidates().isEmpty()) {
                if (!r.permanentErrors().isEmpty()) continue; // atmetimas – irgi pažanga, bandome iš naujo
                break;                                      // nėra pažangos – sustojame
            }
            stats.add(r);
            if (r.winner() == null) {
                stopReason = "per " + cfg.maxRounds() + " raundu (paskutinis biudzetas " + r.finalBudgetAttempts()
                        + ") nei vienas kandidatas nerado sprendimo - paieska sustabdyta";
                printCompetition(chain.height(), r, true);
                break;
            }
            Candidate w = r.winner();
            try {
                chain.addBlock(w.block(), pool); // pilna pakartotinė patikra + atominis prijungimas ir fondo išvalymas
            } catch (InvalidBlockException e) {
                throw new IllegalStateException("laimetojas K" + w.index() + " nepraejo patikros: " + e.getMessage(), e);
            }
            printBlock(chain.height() - 1, w);
            printCompetition(chain.height() - 1, r, cfg.verbose());
        }

        // 4. Suvestinė
        State fin = chain.state();
        List<Transaction> remaining = pool.pending();
        List<TxPool.Rejection> rejected = pool.rejected();
        long totalNanos = System.nanoTime() - t0;

        System.out.println("=== Suvestine ===");
        System.out.println("sustojimo priezastis:  " + stopReason);
        System.out.printf("vartotojai:            %d%n", users.size());
        System.out.printf("transakcijos (sugen.): %d%n", generated.size());
        System.out.printf("priimti blokai:        %d%n", chain.height());
        System.out.printf("itrauktos tx:          %d%n", chain.confirmedCount());
        System.out.printf("atmestos tx:           %d%n", rejected.size());
        for (Map.Entry<TxError, Integer> e : countRejections(rejected).entrySet())
            System.out.printf("    %-22s %d (%s)%n", e.getKey(), e.getValue(), e.getKey().description());
        System.out.printf("likusios fonde tx:     %d%n", remaining.size());
        Map<String, Integer> remainingWhy = new java.util.TreeMap<>();
        for (Transaction t : remaining) {
            TxError e = fin.check(t);
            remainingWhy.merge(e == null ? "IVYKDOMA (paieska sustabdyta)" : e + " (" + e.description() + ", galutineje busenoje)", 1, Integer::sum);
        }
        remainingWhy.forEach((why, n) -> System.out.printf("    %d - %s%n", n, why));
        System.out.printf("pradine lesu suma:     %d%n", chain.initialTotal());
        System.out.printf("galutine lesu suma:    %d  (%s)%n", fin.total(),
                fin.total() == chain.initialTotal() ? "sutampa" : "NESUTAMPA");
        System.out.printf("maziausias balansas:   %d%n", fin.minBalance());
        System.out.printf("paskutinio bloko hash: %s%n", chain.tipHash());
        stats.print();
        System.out.printf(Locale.ROOT, "bendras laikas:        %.3f s%n", totalNanos / 1e9);

        int accounted = chain.confirmedCount() + rejected.size() + remaining.size();
        System.out.printf("patikra: itrauktos+atmestos+likusios = %d = sugeneruota %d (%s)%n", accounted, generated.size(),
                accounted == generated.size() ? "OK" : "KLAIDA");

        Path report = cfg.outDir().resolve("report.txt");
        writeReport(report, chain, rejected, remaining, fin);
        System.out.println("pilnas atmestu/likusiu tx paaiskinimas: " + report);
        System.out.println("pradine busena: " + cfg.outDir().resolve("initial_state.csv"));

        if (fin.total() != chain.initialTotal() || fin.minBalance() < 0 || accounted != generated.size())
            throw new IllegalStateException("pazeisti invariantai");
        return chain.tipHash();
    }

    private void printBlock(int height, Candidate w) {
        BlockHeader h = w.solution();
        System.out.printf("Blokas #%d | tx=%d | laimetojas K%d%n", height, w.transactions().size(), w.index());
        System.out.println("  prev_hash:  " + h.prevHash());
        System.out.println("  tx_root:    " + h.txRoot() + "  (Merkle)");
        System.out.println("  hash:       " + h.hash());
        System.out.printf(Locale.ROOT, "  difficulty=%d nonce=%d bandymai=%d laikas=%.1f ms timestamp=%d%n",
                h.difficulty(), h.nonce(), w.attempts(), w.nanos() / 1e6, h.timestamp());
    }

    private void printCompetition(int height, Competition.Result r, boolean perCandidate) {
        long total = 0, nanos = 0;
        for (Candidate c : r.candidates()) { total += c.attempts(); nanos += c.nanos(); }
        System.out.printf(Locale.ROOT, "  konkurencija: %d kandidatai, raundai=%d, biudzetas=%d, visi bandymai=%d, laikas=%.1f ms%n",
                r.candidates().size(), r.rounds(), r.finalBudgetAttempts(), total, nanos / 1e6);
        if (perCandidate) {
            for (Candidate c : r.candidates()) {
                System.out.printf(Locale.ROOT, "    K%d tx=%-3d tx_root=%s bandymai=%-6d laikas=%6.1f ms %s%n",
                        c.index(), c.transactions().size(), HashUtil.shortHex(c.template().txRoot()), c.attempts(),
                        c.nanos() / 1e6, c.solved() ? (c == r.winner() ? "LAIMEJO nonce=" : "rado nonce=")
                                + c.solution().nonce() : "nerado");
            }
        }
        System.out.println();
    }

    /** Kandidatų bandymų / laiko suvestinė per visą simuliaciją. */
    private static final class Stats {
        final long[] attempts, nanos;
        final int[] wins, built;
        int heights, extraRounds;
        long winnerAttempts, allAttempts, allNanos;

        Stats(int n) {
            attempts = new long[n + 1]; nanos = new long[n + 1]; wins = new int[n + 1]; built = new int[n + 1];
        }

        void add(Competition.Result r) {
            heights++;
            if (r.rounds() > 1) extraRounds++;
            for (Candidate c : r.candidates()) {
                attempts[c.index()] += c.attempts();
                nanos[c.index()] += c.nanos();
                built[c.index()]++;
                allAttempts += c.attempts();
                allNanos += c.nanos();
            }
            if (r.winner() != null) {
                wins[r.winner().index()]++;
                winnerAttempts += r.winner().attempts();
            }
        }

        void print() {
            System.out.printf("konkursu (aukstiu):    %d, is ju su padidintu biudzetu: %d%n", heights, extraRounds);
            System.out.printf("bandymai is viso:      %d (laimetoju %d, nelaimejusiu %d)%n", allAttempts, winnerAttempts,
                    allAttempts - winnerAttempts);
            System.out.printf(Locale.ROOT, "kasimo laikas:         %.3f s%n", allNanos / 1e9);
            System.out.println("kandidatas  sudaryta  laimejo  bandymai   laikas");
            for (int k = 1; k < wins.length; k++) {
                System.out.printf(Locale.ROOT, "  K%-8d %8d %8d %9d %7.3f s%n", k, built[k], wins[k], attempts[k], nanos[k] / 1e9);
            }
        }
    }

    static String fingerprint(List<Transaction> txs) {
        StringBuilder sb = new StringBuilder();
        for (Transaction tx : txs) sb.append(tx.id());
        return HashUtil.hashUtf8(sb.toString());
    }

    private static Map<TxError, Integer> countRejections(List<TxPool.Rejection> rejected) {
        Map<TxError, Integer> m = new EnumMap<>(TxError.class);
        for (TxPool.Rejection r : rejected) m.merge(r.reason(), 1, Integer::sum);
        return m;
    }

    private static void writeInitialState(List<User> users, Path file) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            w.println("name,public_key,initial_balance");
            for (User u : users) w.println(u.name() + "," + u.publicKey() + "," + u.initialBalance());
        }
    }

    private static void writeReport(Path file, Blockchain chain, List<TxPool.Rejection> rejected,
                                    List<Transaction> remaining, State fin) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            w.println("# Atmestos transakcijos (" + rejected.size() + ")");
            w.println("id,sender,receiver,amount,sequence,priezastis");
            for (TxPool.Rejection r : rejected) {
                Transaction t = r.tx();
                w.println(t.id() + "," + t.sender() + "," + t.receiver() + "," + t.amount() + "," + t.sequence()
                        + "," + r.reason() + " (" + r.reason().description() + ")");
            }
            w.println();
            w.println("# Likusios fonde transakcijos (" + remaining.size() + ") - tikrinta su galutine busena");
            w.println("id,sender,receiver,amount,sequence,priezastis");
            for (Transaction t : remaining) {
                TxError e = fin.check(t);
                String why = e == null ? "ivykdoma (paieska sustabdyta pries ja itraukiant)"
                        : e + " (turi " + fin.balance(t.sender()) + ", reikia " + t.amount() + ")";
                w.println(t.id() + "," + t.sender() + "," + t.receiver() + "," + t.amount() + "," + t.sequence() + "," + why);
            }
            w.println();
            w.println("# Blokai (" + chain.height() + ")");
            w.println("height,tx_count,prev_hash,tx_root,hash,nonce,timestamp");
            List<Block> blocks = chain.blocks();
            for (int i = 0; i < blocks.size(); i++) {
                Block b = blocks.get(i);
                BlockHeader h = b.header();
                w.println(i + "," + b.transactions().size() + "," + h.prevHash() + "," + h.txRoot() + "," + b.hash()
                        + "," + h.nonce() + "," + h.timestamp());
            }
        }
    }
}
