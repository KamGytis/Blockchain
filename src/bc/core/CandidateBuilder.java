package bc.core;

import bc.model.Transaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2 etapas – transakcijų atranka į kandidatą. Patvirtinti balansai nukopijuojami į laikiną būseną; transakcijos
 * tikrinamos ir taikomos šiai kopijai numatoma tvarka bloke. Atrenkama iki maxTx nuosekliai įvykdomų transakcijų.
 * Tikrieji balansai nekeičiami. Kiekvienas kandidatas turi savo kopiją.
 *
 * Pvz. A turi 100, fonde T1: A->B 70, T2: A->C 60. T1: 70 <= 100 -> įtraukiama, laikinas A = 30.
 * T2: 60 > 30 -> šiam kandidatui netinka (lieka fonde). Jei tvarka atvirkščia – įtraukiama T2, netinka T1.
 */
public final class CandidateBuilder {
    private CandidateBuilder() {}

    /**
     * @param txs            parinktos transakcijos (tvarka bloke)
     * @param deferred       laikinai netinkančios (nepakankamas balansas šiame kandidate) – lieka fonde
     * @param permanent      nuolatinės klaidos – jas kviečiantysis turi atmesti iš fondo
     */
    public record Selection(List<Transaction> txs, List<Transaction> deferred, Map<Transaction, TxError> permanent) {}

    public static Selection select(Blockchain chain, List<Transaction> ordered, int maxTx) {
        State work = chain.state();                    // atskira patvirtintos būsenos kopija
        Set<String> seen = new HashSet<>();
        List<Transaction> selected = new ArrayList<>();
        List<Transaction> deferred = new ArrayList<>();
        Map<Transaction, TxError> permanent = new LinkedHashMap<>();
        for (Transaction tx : ordered) {
            if (selected.size() == maxTx) break;
            TxError e = Validation.checkInBlock(tx, work, seen, chain);
            if (e == null) {
                work.apply(tx);
                seen.add(tx.id());
                selected.add(tx);
            } else if (e == TxError.DUPLICATE_IN_BLOCK) {
                // tas pats ID antrą kartą tame pačiame sąraše – tiesiog praleidžiame
            } else if (e.permanent()) {
                permanent.put(tx, e);
            } else {
                deferred.add(tx);
            }
        }
        return new Selection(List.copyOf(selected), deferred, permanent);
    }
}
