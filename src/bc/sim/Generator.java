package bc.sim;

import bc.hash.HashUtil;
import bc.model.Transaction;
import bc.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Deterministinis duomenų generatorius: tas pats seed -> tie patys vartotojai ir transakcijos. */
public final class Generator {
    public static final long MIN_BALANCE = 100;
    public static final long MAX_BALANCE = 1_000_000;

    private Generator() {}

    public static List<User> users(Random rng, int n) {
        List<User> users = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = String.format("user%04d", i);
            String publicKey = HashUtil.hashUtf8("user|" + i + "|" + rng.nextLong());
            long balance = MIN_BALANCE + (long) rng.nextInt((int) (MAX_BALANCE - MIN_BALANCE + 1));
            users.add(new User(name, publicKey, balance));
        }
        return users;
    }

    /**
     * Generuoja transakcijas. {@code sequence} – siuntėjo transakcijų skaitliukas (0, 1, 2, ...), todėl
     * tos pačios poros ir sumos mokėjimai gauna skirtingus ID. Dalis {@code invalidRate} yra tyčia
     * sugadintos, kad būtų matoma atmetimo logika.
     */
    public static List<Transaction> transactions(Random rng, List<User> users, int n, long maxAmount, double invalidRate) {
        List<Transaction> out = new ArrayList<>(n);
        List<Transaction> valid = new ArrayList<>();
        Map<String, Long> seq = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String sender = users.get(rng.nextInt(users.size())).publicKey();
            String receiver;
            do {
                receiver = users.get(rng.nextInt(users.size())).publicKey();
            } while (receiver.equals(sender));
            long amount = 1 + (long) (rng.nextDouble() * maxAmount);
            if (amount > maxAmount) amount = maxAmount;
            long s = seq.merge(sender, 1L, Long::sum) - 1;

            if (rng.nextDouble() < invalidRate) {
                Transaction bad = switch (rng.nextInt(5)) {
                    case 0 -> Transaction.create(sender, sender, amount, s);                         // SELF_TRANSFER
                    case 1 -> Transaction.create(sender, HashUtil.hashUtf8("ghost|" + i), amount, s); // UNKNOWN_RECEIVER
                    case 2 -> Transaction.create(sender, receiver, 0, s);                            // NON_POSITIVE_AMOUNT
                    case 3 -> new Transaction(sender, receiver, amount + 1, s,                       // BAD_ID
                            Transaction.computeId(sender, receiver, amount, s));
                    default -> valid.isEmpty() ? Transaction.create(sender, sender, amount, s)        // DUPLICATE_IN_POOL
                            : valid.get(rng.nextInt(valid.size()));
                };
                out.add(bad);
            } else {
                Transaction tx = Transaction.create(sender, receiver, amount, s);
                valid.add(tx);
                out.add(tx);
            }
        }
        return out;
    }
}
