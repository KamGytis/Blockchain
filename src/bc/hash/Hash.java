package bc.hash;

public class Hash {
    public static final String VERSION = "KGHash v2 (8 raundai, message schedule, K konstantos, StrictMath)";

    private static final int ROUNDS = 8;               // NAUJA: buvo 4
    private static final int STEPS = ROUNDS * 8;       // 64 žingsniai

    private static final int[] INIT = {
            0x17859D00,
            0x2468ACE0,
            0x1A2B3C4D,
            0x77777777,
            0x89ABCDEF,
            0x11203040,
            0xBEEFCA80,
            0x12578950
    };

    // NAUJA: raundų konstantos (pirmų 64 pirminių skaičių kubinių šaknų trupmeninės dalys)
    private static final int[] K = new int[STEPS];
    static {
        int n = 0;
        for (int c = 2; n < STEPS; c++) {
            boolean prime = true;
            for (int d = 2; d * d <= c; d++) {
                if (c % d == 0) { prime = false; break; }
            }
            if (!prime) continue;
            K[n++] = (int) (long) ((StrictMath.cbrt(c) % 1.0) * 4294967296.0);
        }
    }

    public static String hash(byte[] data) {
        return toHex(hashWords(data));
    }

    /** Maiša kaip 8 žodžiai (be hex konvertavimo). hash(data) == toHex(hashWords(data)). */
    public static int[] hashWords(byte[] data) {
        byte[] padded = pad(data);
        int[] h = INIT.clone();
        int[] w = new int[STEPS];
        int[] saved = new int[8];
        for (int block = 0; block < padded.length; block += 32) {
            compress(h, padded, block, w, saved);
        }
        return h;
    }

    static int[] initialState() {
        return INIT.clone();
    }

    /** Kiek pradinių hex nulių turi toHex(words) – be eilutės kūrimo. */
    public static int leadingHexZeros(int[] words) {
        int zeros = 0;
        for (int word : words) {
            if (word == 0) {
                zeros += 8;
                continue;
            }
            return zeros + Integer.numberOfLeadingZeros(word) / 4;
        }
        return zeros;
    }

    public static String toHexString(int[] words) {
        return toHex(words);
    }

    /**
     * Vieno 32 baitų bloko suspaudimas (v0.3: išskirta iš hash() be algoritmo pakeitimų, kad būtų galima
     * išsaugoti tarpinę būseną – midstate). w ir saved – darbiniai masyvai, kad nereikėtų jų kurti kas kartą.
     */
    static void compress(int[] h, byte[] p, int off, int[] w, int[] saved) {
        System.arraycopy(h, 0, saved, 0, 8);

        // NAUJA: message schedule (8 žodžiai -> 64)
        for (int i = 0; i < 8; i++) {
            w[i] = readWord(p, off + i * 4);
        }
        for (int t = 8; t < STEPS; t++) {
            w[t] = w[t - 8] + smallSigma0(w[t - 7]) + w[t - 3] + smallSigma1(w[t - 2]);
        }

        // pradinio "h ^= words" nebėra: žinutė dabar įterpiama kiekviename žingsnyje per w[t]
        for (int round = 0; round < ROUNDS; round++) {
            for (int i = 0; i < 8; i++) {
                int t = round * 8 + i;
                int next = h[(i + 1) & 7];
                int previous = h[(i + 7) & 7];
                int opposite = h[(i + 4) & 7];     // NAUJA: trečias žodis Ch/Maj funkcijoms

                int x = h[i];
                x += next;
                x ^= bigSigma1(previous);
                x ^= smallSigma0(x);
                x += smallSigma1(next);
                x ^= bigSigma0(previous);

                // NAUJA: netiesiškumas + raundo konstanta + žinutės žodis
                x += ch(previous, next, opposite);
                x ^= maj(previous, next, opposite);
                x += K[t] + w[t];

                h[i] = x;
            }
        }

        for (int i = 0; i < 8; i++) {
            h[i] += saved[i];
        }
    }

    // NAUJA: netiesinės funkcijos
    static int ch(int x, int y, int z) {
        return (x & y) ^ (~x & z);
    }

    static int maj(int x, int y, int z) {
        return (x & y) ^ (x & z) ^ (y & z);
    }

    static int bigSigma0(int x) {
        return Integer.rotateRight(x, 2) ^ Integer.rotateRight(x, 13) ^ Integer.rotateRight(x, 22);
    }

    static int bigSigma1(int x) {
        return Integer.rotateRight(x, 6) ^ Integer.rotateRight(x, 11) ^ Integer.rotateRight(x, 25);
    }

    static int smallSigma0(int x) {
        return Integer.rotateRight(x, 7) ^ Integer.rotateRight(x, 18) ^ (x >>> 3);
    }

    static int smallSigma1(int x) {
        return Integer.rotateRight(x, 17) ^ Integer.rotateRight(x, 19) ^ (x >>> 10);
    }

    static byte[] pad(byte[] data) {
        int newLength = ((data.length + 1 + 8 + 31) / 32) * 32;

        byte[] padded = new byte[newLength];
        System.arraycopy(data, 0, padded, 0, data.length);
        padded[data.length] = (byte) 0x80;

        long bitLength = (long) data.length << 3;
        for (int i = 0; i < 8; i++) {
            padded[newLength - 1 - i] = (byte) (bitLength >>> (8 * i));
        }
        return padded;
    }

    static int readWord(byte[] p, int off) {
        return ((p[off] & 0xFF) << 24)
                | ((p[off + 1] & 0xFF) << 16)
                | ((p[off + 2] & 0xFF) << 8)
                | (p[off + 3] & 0xFF);
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    // v0.3: lentelė vietoj String.format("%08x") - tas pats rezultatas (8 mažosios hex raidės, su pradiniais
    // nuliais), bet be formatavimo eilutės analizės kiekvienam žodžiui. Lygiavertiškumas tikrinamas V03Tests.
    static String toHex(int[] h) {
        char[] out = new char[h.length * 8];
        for (int i = 0; i < h.length; i++) {
            int v = h[i];
            for (int j = 7; j >= 0; j--) {
                out[i * 8 + j] = HEX[v & 0xF];
                v >>>= 4;
            }
        }
        return new String(out);
    }
}