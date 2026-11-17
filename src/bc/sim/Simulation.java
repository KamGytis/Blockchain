package bc.sim;

import bc.core.Blockchain;
import bc.core.InvalidBlockException;
import bc.core.Miner;
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

/** Visa 2 skyriaus eiga viename procese: vartotojai -> transakcijos -> fondas -> blokai -> suvestinė. */
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
        Blockchain chain = new Blockchain(genesis, cfg.difficulty());
        Files.createDirectories(cfg.outDir());
        writeInitialState(users, cfg.outDir().resolve("initial_state.csv"));

        // 2. Transakcijos ir fondas
        List<Transaction> generated = Generator.transactions(rng, users, cfg.transactions(), cfg.maxAmount(), cfg.invalidRate());
        TxPool pool = new TxPool();
        for (Transaction tx : generated) pool.submit(tx, chain);

        System.out.println("=== Blockchain v0.1 ===");
        System.out.println("hash:        " + Hash.VERSION);
        System.out.println("seed:        " + cfg.seed());
        System.out.printf("parametrai:  users=%d txs=%d block_size=%d difficulty=%d max_amount=%d invalid_rate=%s%n",
                cfg.users(), cfg.transactions(), cfg.blockSize(), cfg.difficulty(), cfg.maxAmount(), cfg.invalidRate());
        System.out.println("tx_set_fp:   " + fingerprint(generated) + "  (visu sugeneruotu tx ID maisa)");
        System.out.printf("fondas:      priimta %d, atmesta priimant %d%n%n", pool.size(), pool.rejected().size());

        // 3. Blokų kasimas, kol fonde yra įvykdomų transakcijų
        long miningNanos = 0;
        while (true) {
            List<Transaction> selected = Miner.select(chain, pool, cfg.blockSize(), rng);
            if (selected == null) break;
            long ts = cfg.fixedTime() ? chain.height() : System.currentTimeMillis() / 1000;
            Miner.Mined m = Miner.mine(chain, selected, ts);
            try {
                chain.addBlock(m.block());
            } catch (InvalidBlockException e) {
                throw new IllegalStateException("savo iskastas blokas atmestas: " + e.getMessage(), e);
            }
            pool.removeConfirmed(selected);
            miningNanos += m.pow().nanos();
            printBlock(chain.height() - 1, m);
        }

        // 4. Suvestinė
        State fin = chain.state();
        List<Transaction> remaining = pool.pending();
        List<TxPool.Rejection> rejected = pool.rejected();
        long totalNanos = System.nanoTime() - t0;

        System.out.println("=== Suvestine ===");
        System.out.printf("vartotojai:            %d%n", users.size());
        System.out.printf("transakcijos (sugen.): %d%n", generated.size());
        System.out.printf("priimti blokai:        %d%n", chain.height());
        System.out.printf("itrauktos tx:          %d%n", chain.confirmedCount());
        System.out.printf("atmestos tx:           %d%n", rejected.size());
        for (Map.Entry<TxError, Integer> e : countRejections(rejected).entrySet())
            System.out.printf("    %-22s %d (%s)%n", e.getKey(), e.getValue(), e.getKey().description());
        System.out.printf("likusios fonde tx:     %d%n", remaining.size());
        if (!remaining.isEmpty())
            System.out.printf("    %-22s %d (%s, galutineje busenoje)%n", TxError.INSUFFICIENT_BALANCE, remaining.size(),
                    TxError.INSUFFICIENT_BALANCE.description());
        System.out.printf("pradine lesu suma:     %d%n", chain.initialTotal());
        System.out.printf("galutine lesu suma:    %d  (%s)%n", fin.total(),
                fin.total() == chain.initialTotal() ? "sutampa" : "NESUTAMPA");
        System.out.printf("maziausias balansas:   %d%n", fin.minBalance());
        System.out.printf("paskutinio bloko hash: %s%n", chain.tipHash());
        System.out.printf(Locale.ROOT, "kasimo laikas:         %.3f s%n", miningNanos / 1e9);
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

    private void printBlock(int height, Miner.Mined m) {
        Block b = m.block();
        BlockHeader h = b.header();
        System.out.printf("Blokas #%d | tx=%d%n", height, b.transactions().size());
        System.out.println("  prev_hash:  " + h.prevHash());
        System.out.println("  tx_root:    " + h.txRoot());
        System.out.println("  hash:       " + b.hash());
        System.out.printf(Locale.ROOT, "  difficulty=%d nonce=%d bandymai=%d laikas=%.1f ms timestamp=%d%n%n",
                h.difficulty(), h.nonce(), m.pow().attempts(), m.pow().nanos() / 1e6, h.timestamp());
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
                String why = e == null ? "ivykdoma (neturetu likti!)"
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
