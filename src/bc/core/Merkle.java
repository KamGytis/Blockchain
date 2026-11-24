package bc.core;

import bc.hash.Hash;
import bc.hash.HashUtil;
import bc.model.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Merkle medis (tx_root nuo v0.2).
 * <ul>
 *   <li>Lapai – transakcijų ID jų tvarka bloke, hex dekoduoti į 32 baitus.</li>
 *   <li>Vidinis mazgas = H(kairys 32 B || dešinys 32 B), H – savo maiša {@link Hash}.</li>
 *   <li>Neporinis lygio mazgas poruojamas su savo kopija.</li>
 *   <li>Vienas lapas – jis pats yra šaknis.</li>
 *   <li>Tuščias sąrašas – {@link #EMPTY_ROOT} (64 nuliai). Tik genesis atvejui; tuščių blokų grandinė nepriima.</li>
 * </ul>
 */
public final class Merkle {
    public static final String EMPTY_ROOT = HashUtil.ZERO_HASH;

    private Merkle() {}

    public static String root(List<Transaction> txs) {
        List<String> ids = new ArrayList<>(txs.size());
        for (Transaction tx : txs) ids.add(tx.id());
        return rootOfIds(ids);
    }

    public static String rootOfIds(List<String> ids) {
        List<List<String>> levels = levels(ids);
        return levels.isEmpty() ? EMPTY_ROOT : levels.get(levels.size() - 1).get(0);
    }

    // Visi medžio lygiai nuo lapų iki šaknies (testams ir aiškinimui). Tuščiam sąrašui – tuščias.
    public static List<List<String>> levels(List<String> ids) {
        List<List<String>> levels = new ArrayList<>();
        if (ids.isEmpty()) return levels;
        for (String id : ids) {
            if (!HashUtil.isHash(id)) throw new IllegalArgumentException("lapas ne 64 hex: " + id);
        }
        List<String> level = List.copyOf(ids);
        levels.add(level);
        while (level.size() > 1) {
            List<String> next = new ArrayList<>((level.size() + 1) / 2);
            for (int i = 0; i < level.size(); i += 2) {
                String left = level.get(i);
                String right = i + 1 < level.size() ? level.get(i + 1) : left; // neporinis -> su kopija
                next.add(parent(left, right));
            }
            level = List.copyOf(next);
            levels.add(level);
        }
        return levels;
    }

    public static String parent(String leftHex, String rightHex) {
        byte[] l = HashUtil.hexToBytes(leftHex), r = HashUtil.hexToBytes(rightHex);
        byte[] both = new byte[l.length + r.length];
        System.arraycopy(l, 0, both, 0, l.length);
        System.arraycopy(r, 0, both, l.length, r.length);
        return Hash.hash(both);
    }
}
