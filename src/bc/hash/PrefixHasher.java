package bc.hash;

/**
 * v0.3: maiša su iš anksto apskaičiuota pastovaus prefikso būsena (midstate).
 *
 * <p>{@link Hash} apdoroja įvestį 32 baitų blokais, perduodamas būseną h iš bloko į bloką. Jei įvesties
 * pradžia (prefix) nekinta, pilnų jos blokų rezultatas visada tas pats – jį skaičiuojame vieną kartą.
 * Kiekvienam priesagos (suffix) variantui suspaudžiami tik likę blokai: prefikso likutis + priesaga + užpildymas.
 * Rezultatas tapatus {@code Hash.hashWords(prefix || suffix)} – užpildymas priklauso tik nuo bendro ilgio,
 * kuris skaičiuojamas iš prefikso ir priesagos ilgių.
 *
 * <p>Kasime prefix = {@code prev_hash|timestamp|version|tx_root|} (~143 B, t. y. 4 pilni blokai), o priesaga =
 * {@code nonce|difficulty}. Vietoj 5 blokų kiekvienam bandymui suspaudžiamas 1 (kartais 2).
 * Objektas nėra gijoms saugus (naudoja darbinius masyvus).
 */
public final class PrefixHasher {
    private final int[] midstate;
    private final byte[] tail;          // prefikso baitai po paskutinio pilno bloko
    private final long prefixLength;
    private final int[] h = new int[8];
    private final int[] w = new int[64];
    private final int[] saved = new int[8];
    private byte[] buf = new byte[0];

    public PrefixHasher(byte[] prefix) {
        int full = prefix.length / 32 * 32;
        midstate = Hash.initialState();
        for (int off = 0; off < full; off += 32) Hash.compress(midstate, prefix, off, w, saved);
        tail = java.util.Arrays.copyOfRange(prefix, full, prefix.length);
        prefixLength = prefix.length;
    }

    /** Grąžina maišos žodžius įvesčiai prefix || suffix[0..suffixLen). Grąžinamas masyvas perrašomas kito kvietimo. */
    public int[] hashWords(byte[] suffix, int suffixLen) {
        int restLen = tail.length + suffixLen;
        int padded = ((restLen + 1 + 8 + 31) / 32) * 32;  // ta pati formulė kaip Hash.pad
        if (buf.length < padded) buf = new byte[padded];
        java.util.Arrays.fill(buf, 0, padded, (byte) 0);
        System.arraycopy(tail, 0, buf, 0, tail.length);
        System.arraycopy(suffix, 0, buf, tail.length, suffixLen);
        buf[restLen] = (byte) 0x80;
        long bitLength = (prefixLength + suffixLen) << 3;   // ilgis – visos žinutės, ne tik likučio
        for (int i = 0; i < 8; i++) buf[padded - 1 - i] = (byte) (bitLength >>> (8 * i));

        System.arraycopy(midstate, 0, h, 0, 8);
        for (int off = 0; off < padded; off += 32) Hash.compress(h, buf, off, w, saved);
        return h;
    }
}
