package bc.hash;

import java.nio.charset.StandardCharsets;

/** Small helpers around {@link Hash}: UTF-8 hashing, hex validation and hex/byte conversion. */
public final class HashUtil {
    public static final int HEX_LENGTH = 64;
    public static final String ZERO_HASH = "0".repeat(HEX_LENGTH);

    private HashUtil() {}

    public static String hashUtf8(String text) {
        return Hash.hash(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String hashBytes(byte[] data) {
        return Hash.hash(data);
    }

    // veikia 64 symboliams
    public static boolean isHash(String s) {
        if (s == null || s.length() != HEX_LENGTH) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    public static boolean hasLeadingZeros(String hex, int zeros) {
        if (zeros < 0 || zeros > hex.length()) return false;
        for (int i = 0; i < zeros; i++) {
            if (hex.charAt(i) != '0') return false;
        }
        return true;
    }

    public static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) throw new IllegalArgumentException("odd hex length");
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(2 * i), 16);
            int lo = Character.digit(hex.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("not hex: " + hex);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static String bytesToHex(byte[] b) {
        char[] out = new char[b.length * 2];
        final char[] hex = "0123456789abcdef".toCharArray();
        for (int i = 0; i < b.length; i++) {
            out[2 * i] = hex[(b[i] >> 4) & 0xF];
            out[2 * i + 1] = hex[b[i] & 0xF];
        }
        return new String(out);
    }

    public static String shortHex(String hex) {
        return hex.length() <= 16 ? hex : hex.substring(0, 16) + "...";
    }
}
