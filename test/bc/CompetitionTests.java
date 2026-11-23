package bc;

import bc.core.BlockError;
import bc.core.Blockchain;
import bc.core.Candidate;
import bc.core.Competition;
import bc.core.Merkle;
import bc.core.State;
import bc.core.TxError;
import bc.core.TxPool;
import bc.model.BlockHeader;
import bc.model.Transaction;
import bc.model.User;
import bc.sim.Generator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class CompetitionTests {
    private CompetitionTests() {}

    static void all() {
        T.test("5 kandidatai: tas pats prev_hash ir busena, <=100 tx, be pasikartojimu viduje", CompetitionTests::buildFive);
        T.test("kandidatai skiriasi, bet gali persidengti; tikroji busena nepakinta", CompetitionTests::overlapNoEffect);
        T.test("padidinus biudzeta paieska tesiama nuo netikrintu nonce, sudetingumas nemazinamas", CompetitionTests::budgetContinues);
        T.test("laimetojas = maziausiai bandymu is radusiu; perpatikrinamas ir prijungiamas", CompetitionTests::winnerRule);
        T.test("nelaimejusieji neturi poveikio; ju blokas po laimetojo atmetamas", CompetitionTests::losersNoEffect);
        T.test("bendra riba: per maxRounds nerasta -> aiskus sustojimas, niekas nepakeista", CompetitionTests::stopsAtLimit);
    }

    record Setup(Blockchain chain, TxPool pool, Random rng) {}

    static Setup setup(int difficulty, int users, int txs) {
        Random rng = new Random(7);
        List<User> us = Generator.users(rng, users);
        Blockchain chain = new Blockchain(State.fromUsers(us), difficulty, 100);
        TxPool pool = new TxPool();
        for (Transaction t : Generator.transactions(rng, us, txs, 300_000, 0)) pool.submit(t, chain);
        return new Setup(chain, pool, rng);
    }

    static List<Candidate> build(Setup s, int n) {
        Map<Transaction, TxError> perm = new LinkedHashMap<>();
        return Competition.build(s.chain(), s.pool(), n, 100, s.rng(), 0, perm);
    }

    static void buildFive() {
        Setup s = setup(2, 50, 600);
        List<Candidate> cands = build(s, 5);
        T.eq(5, cands.size(), "5 kandidatai");
        for (Candidate c : cands) {
            BlockHeader h = c.template();
            T.eq(s.chain().tipHash(), h.prevHash(), "tas pats prev_hash");
            T.eq(s.chain().difficulty(), h.difficulty(), "grandines sudetingumas");
            T.eq(Blockchain.VERSION, h.version(), "versija");
            T.check(c.transactions().size() <= 100 && !c.transactions().isEmpty(), "1..100 tx");
            Set<String> ids = new HashSet<>();
            for (Transaction t : c.transactions()) T.check(ids.add(t.id()), "ID kartojasi kandidate K" + c.index());
            T.eq(Merkle.root(c.transactions()), h.txRoot(), "tx_root");
            // kiekvienas kandidatas atskirai įvykdomas nuo tos pačios patvirtintos būsenos
            T.check(isExecutable(s.chain(), c.transactions()), "K" + c.index() + " ivykdomas");
        }
    }

    static boolean isExecutable(Blockchain chain, List<Transaction> txs) {
        try {
            chain.applyTransactions(txs);
            return true;
        } catch (bc.core.InvalidBlockException e) {
            return false;
        }
    }

    static void overlapNoEffect() {
        Setup s = setup(2, 50, 600);
        Map<String, Long> before = s.chain().state().snapshot();
        int poolBefore = s.pool().size();
        List<Candidate> cands = build(s, 5);
        Set<String> roots = new HashSet<>();
        for (Candidate c : cands) roots.add(c.template().txRoot());
        T.check(roots.size() > 1, "kandidatai skiriasi");
        T.eq(before, s.chain().state().snapshot(), "tikrieji balansai nepakito");
        T.eq(poolBefore, s.pool().size(), "fondas nepakito");
    }

    static void budgetContinues() {
        Setup s = setup(3, 20, 100);
        List<Candidate> cands = build(s, 5);
        Competition.Result r = Competition.run(cands, new Competition.Budget(1, Long.MAX_VALUE, 30));
        T.check(r.winner() != null, "rasta per 30 raundu");
        T.check(r.rounds() > 1, "reikejo padidinti biudzeta (raundai=" + r.rounds() + ")");
        for (Candidate c : cands) {
            // nonce prasideda nuo 0 ir tikrinami iš eilės: jei nė vienas nonce netikrintas du kartus,
            // tai sekantis netikrintas nonce == bandymų skaičius
            T.eq(c.attempts(), c.nextNonce(), "K" + c.index() + " nonce nesikartoja");
            if (c.solved()) {
                T.eq(c.attempts() - 1, c.solution().nonce(), "sprendimas paskutiniame bandyme");
                T.eq(3, c.solution().difficulty(), "sudetingumas nesumazintas");
                T.check(c.solution().hash().startsWith("000"), "maisa tenkina salyga");
            }
        }
        // biudžetai 1,2,4,... -> po r raundų bendras biudžetas 2^r - 1
        long cumulative = (1L << r.rounds()) - 1;
        for (Candidate c : cands) if (!c.solved()) T.eq(cumulative, c.attempts(), "nelaimejes isnaudojo visa biudzeta");
    }

    static void winnerRule() throws Exception {
        Setup s = setup(3, 50, 600);
        List<Candidate> cands = build(s, 5);
        Competition.Result r = Competition.run(cands, new Competition.Budget(2000, Long.MAX_VALUE, 10));
        Candidate w = r.winner();
        for (Candidate c : cands) {
            if (c.solved()) T.check(w.attempts() <= c.attempts(), "laimetojas su maziausiai bandymu");
            if (c.solved() && c.attempts() == w.attempts()) T.check(w.index() <= c.index(), "lygiosios -> mazesnis numeris");
        }
        s.chain().validate(w.block()); // pakartotinė pilna patikra be pakeitimų
        s.chain().addBlock(w.block(), s.pool());
        T.eq(1, s.chain().height(), "prijungtas");
        for (Transaction t : w.transactions()) {
            T.check(s.chain().isConfirmed(t.id()), "laimetojo tx patvirtinta");
            T.check(!s.pool().contains(t.id()), "laimetojo tx pasalinta is fondo");
        }
    }

    static void losersNoEffect() throws Exception {
        Setup s = setup(2, 50, 600);
        List<Candidate> cands = build(s, 5);
        Competition.Result r = Competition.run(cands, new Competition.Budget(100_000, Long.MAX_VALUE, 5));
        Candidate w = r.winner();
        s.chain().addBlock(w.block(), s.pool());
        Set<String> winnerIds = new HashSet<>();
        for (Transaction t : w.transactions()) winnerIds.add(t.id());
        for (Candidate c : cands) {
            if (c == w) continue;
            for (Transaction t : c.transactions()) {
                boolean inWinner = winnerIds.contains(t.id());
                T.eq(inWinner, s.chain().isConfirmed(t.id()), "patvirtinta tik jei yra laimetojo bloke");
                T.eq(!inWinner, s.pool().contains(t.id()), "neitraukta lieka fonde");
            }
            if (c.solved()) T.expectReject(s.chain(), c.block(), BlockError.BAD_PREV_HASH, null);
        }
        // kitam aukščiui kandidatai sudaromi iš naujo nuo naujos būsenos
        for (Candidate c : build(s, 5)) T.eq(s.chain().tipHash(), c.template().prevHash(), "naujas prev_hash");
    }

    static void stopsAtLimit() {
        Setup s = setup(8, 20, 50); // difficulty 8: per 3 bandymus praktiškai neįmanoma
        Map<String, Long> before = s.chain().state().snapshot();
        Competition.Result r = Competition.buildAndRun(s.chain(), s.pool(), 5, 100, s.rng(), 0,
                new Competition.Budget(1, Long.MAX_VALUE, 2));
        T.eq(null, r.winner(), "laimetojo nera");
        T.eq(2, r.rounds(), "sustota po maxRounds");
        List<Long> attempts = new ArrayList<>();
        for (Candidate c : r.candidates()) attempts.add(c.attempts());
        T.check(attempts.stream().allMatch(a -> a == 3), "kiekvienas isnaudojo 1 + 2 bandymus: " + attempts);
        T.eq(0, s.chain().height(), "grandine nepakito");
        T.eq(before, s.chain().state().snapshot(), "balansai nepakito");
    }
}
