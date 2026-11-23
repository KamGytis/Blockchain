package bc.core;

import bc.hash.HashUtil;
import bc.model.Transaction;

import java.util.Set;

/**
 * Transakcijos taisyklės, suskirstytos į atskiras procedūras. Tos pačios procedūros naudojamos
 * visuose trijuose etapuose: priimant į fondą ({@link Validator}), sudarant kandidatą ({@link CandidateBuilder})
 * ir priimant bloką ({@link Blockchain#addBlock}).
 */
public final class Validation {
    private Validation() {}

    /** Laukų formatas: 64 hex raktai ir ID, sequence >= 0. */
    public static TxError checkFormat(Transaction tx) {
        if (tx == null || !HashUtil.isHash(tx.sender()) || !HashUtil.isHash(tx.receiver()) || !HashUtil.isHash(tx.id()))
            return TxError.BAD_FORMAT;
        if (tx.sequence() < 0) return TxError.BAD_FORMAT;
        return null;
    }

    /** Teigiama sveikoji suma (long visada sveikasis). */
    public static TxError checkAmount(Transaction tx) {
        return tx.amount() > 0 ? null : TxError.NON_POSITIVE_AMOUNT;
    }

    /** Abu dalyviai egzistuoja ir nesutampa. */
    public static TxError checkParticipants(Transaction tx, State state) {
        if (!state.hasAccount(tx.sender())) return TxError.UNKNOWN_SENDER;
        if (!state.hasAccount(tx.receiver())) return TxError.UNKNOWN_RECEIVER;
        if (tx.sender().equals(tx.receiver())) return TxError.SELF_TRANSFER;
        return null;
    }

    /**
     * ID perskaičiuojamas iš turinio. Tai tikrina vientisumą (ar turinys nepakeistas po ID sukūrimo),
     * bet NEĮRODO, kad mokėjimą leido siuntėjas – tam reikėtų skaitmeninio parašo.
     */
    public static TxError checkId(Transaction tx) {
        return tx.hasValidId() ? null : TxError.BAD_ID;
    }

    /** Nuo būsenos balansų nepriklausančios taisyklės (formatas, suma, dalyviai, ID). */
    public static TxError checkStatic(Transaction tx, State state) {
        TxError e = checkFormat(tx);
        if (e == null) e = checkAmount(tx);
        if (e == null) e = checkParticipants(tx, state);
        if (e == null) e = checkId(tx);
        return e;
    }

    /** v0.1 suderinamumui. */
    public static TxError checkStructure(Transaction tx, State state) {
        return checkStatic(tx, state);
    }

    /**
     * Tikrina transakciją bloko kontekste: statinės taisyklės, ID nesikartoja bloke, nėra jau patvirtintas,
     * siuntėjas turi pakankamai lėšų LAIKINOJE būsenoje {@code work} (į ją jau įskaitytos ankstesnės to paties
     * bloko transakcijos), gavėjo balansas neperpildomas. Būsenos nekeičia.
     */
    public static TxError checkInBlock(Transaction tx, State work, Set<String> seenInBlock, Blockchain chain) {
        TxError e = checkStatic(tx, work);
        if (e != null) return e;
        if (seenInBlock.contains(tx.id())) return TxError.DUPLICATE_IN_BLOCK;
        if (chain.isConfirmed(tx.id())) return TxError.ALREADY_CONFIRMED;
        return work.check(tx); // INSUFFICIENT_BALANCE arba OVERFLOW
    }
}
