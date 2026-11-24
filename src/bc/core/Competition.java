package bc.core;

import bc.model.BlockHeader;
import bc.model.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Penkių (N) kandidatų konkurencija dėl to paties aukščio – nuosekli simuliacija viename procese.
 *
 * <p>Sudarymas: visi kandidatai remiasi tuo pačiu prev_hash ir ta pačia patvirtinta būsena; kiekvienas gauna
 * savo atsitiktinę (nuo seed priklausomą) fondo tvarką ir savo laikiną būsenos kopiją. Viename kandidate ID
 * nesikartoja, tarp kandidatų gali persidengti.
 *
 * <p>Kasimas: raundais. Raunde r kandidatai tikrinami eilės tvarka K1, K2, ..., KN; kiekvienas tęsia paiešką nuo
 * savo pirmo dar netikrinto nonce, kol randa sprendimą arba išnaudoja raundo biudžetą (bandymų skaičius ARBA
 * laikas – kas pirmiau). Jei po raundo niekas nerado – biudžetas dvigubinamas, sudėtingumas nekeičiamas.
 * Po maxRounds raundų paieška sustabdoma (winner == null).
 *
 * <p>Laimėtojo taisyklė: iš raunde sprendimą radusių – mažiausiai bandymų (bendrai per visus raundus) prireikęs
 * kandidatas; lygiųjų atveju – mažesnis numeris. Tai atitinka „kas pirmas rastų, jei visi kastų lygiagrečiai
 * vienodu greičiu“. Laimėtojas prieš prijungimą iš naujo pilnai patikrinamas ({@link Blockchain#validate}).
 */
public final class Competition {
    private Competition() {}

    public record Budget(long attempts, long nanos, int maxRounds) {}

    public record Result(List<Candidate> candidates, Candidate winner, int rounds, long finalBudgetAttempts,
                         Map<Transaction, TxError> permanentErrors) {}

    public static List<Candidate> build(Blockchain chain, TxPool pool, int count, int maxTx, Random rng, long timestamp,
                                        Map<Transaction, TxError> permanentOut) {
        List<Candidate> out = new ArrayList<>();
        List<Transaction> base = pool.pending();
        for (int k = 1; k <= count; k++) {
            List<Transaction> order = new ArrayList<>(base);
            Collections.shuffle(order, new Random(rng.nextLong()));
            CandidateBuilder.Selection sel = CandidateBuilder.select(chain, order, maxTx);
            permanentOut.putAll(sel.permanent());
            if (sel.txs().isEmpty()) continue;
            BlockHeader template = new BlockHeader(chain.tipHash(), timestamp, Blockchain.VERSION,
                    Merkle.root(sel.txs()), 0, chain.difficulty());
            out.add(new Candidate(k, sel.txs(), template));
        }
        return out;
    }

    public static Result run(List<Candidate> candidates, Budget budget) {
        long attemptsBudget = budget.attempts();
        for (int round = 1; round <= budget.maxRounds(); round++) {
            for (Candidate c : candidates) c.search(attemptsBudget, budget.nanos());
            Candidate winner = null;
            for (Candidate c : candidates) {
                if (c.solved() && (winner == null || c.attempts() < winner.attempts())) winner = c;
            }
            if (winner != null) return new Result(candidates, winner, round, attemptsBudget, Map.of());
            attemptsBudget = Math.multiplyExact(attemptsBudget, 2);
        }
        return new Result(candidates, null, budget.maxRounds(), attemptsBudget / 2, Map.of());
    }

    // Patogus apjungimas: sudaro kandidatus, juos kasa, grąžina rezultatą (permanentErrors – atmestinos tx).
    public static Result buildAndRun(Blockchain chain, TxPool pool, int count, int maxTx, Random rng, long timestamp,
                                     Budget budget) {
        Map<Transaction, TxError> perm = new LinkedHashMap<>();
        List<Candidate> cands = build(chain, pool, count, maxTx, rng, timestamp, perm);
        if (cands.isEmpty()) return new Result(cands, null, 0, budget.attempts(), perm);
        Result r = run(cands, budget);
        return new Result(r.candidates(), r.winner(), r.rounds(), r.finalBudgetAttempts(), perm);
    }
}
