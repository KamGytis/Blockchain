package bc;

import bc.core.BlockError;
import bc.core.Blockchain;
import bc.core.InvalidBlockException;
import bc.core.TxError;
import bc.hash.HashUtil;
import bc.model.Block;
import bc.model.User;

import java.util.ArrayList;
import java.util.List;

/** Minimalios testų priemonės (be išorinių bibliotekų). */
final class T {
    static int passed, failed;
    static final List<String> failures = new ArrayList<>();

    static final String A = HashUtil.hashUtf8("A"), B = HashUtil.hashUtf8("B"), C = HashUtil.hashUtf8("C"),
            D = HashUtil.hashUtf8("D");

    private T() {}

    interface Body { void run() throws Exception; }

    static void section(String name) {
        System.out.println();
        System.out.println("--- " + name + " ---");
    }

    static void test(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("[OK]   " + name);
        } catch (Throwable t) {
            failed++;
            failures.add(name);
            System.out.println("[FAIL] " + name + ": " + t);
        }
    }

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    static void eq(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(msg + ": tiketasi <" + expected + ">, gauta <" + actual + ">");
    }

    static User user(String name, String key, long balance) {
        return new User(name, key, balance);
    }

    /** v0.1 stiliaus patikra pagal žinutės fragmentą. */
    static void expectReject(Blockchain chain, Block b, String reasonPart) {
        try {
            chain.addBlock(b);
        } catch (InvalidBlockException e) {
            check(e.getMessage().contains(reasonPart), "atmesta del kitos priezasties: " + e.getMessage());
            return;
        }
        throw new AssertionError("blokas turejo buti atmestas (" + reasonPart + ")");
    }

    /** Tiksli patikra: blokas atmetamas būtent dėl {@code expected} (ir, jei duota, dėl tx klaidos). */
    static InvalidBlockException expectReject(Blockchain chain, Block b, BlockError expected, TxError expectedTx) {
        try {
            chain.addBlock(b);
        } catch (InvalidBlockException e) {
            eq(expected, e.error(), "bloko klaida (" + e.getMessage() + ")");
            if (expectedTx != null) eq(expectedTx, e.txError(), "transakcijos klaida");
            return e;
        }
        throw new AssertionError("blokas turejo buti atmestas: " + expected);
    }
}
