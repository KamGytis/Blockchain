package bc.hash;

/** Testams: prieiga prie paketo vidinių Hash metodų. */
public final class HashAccess {
    private HashAccess() {}

    public static String toHex(int[] words) {
        return Hash.toHex(words);
    }
}
