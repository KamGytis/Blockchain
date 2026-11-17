package bc.core;

import bc.model.Transaction;
import bc.model.User;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class State {
    private final Map<String, Long> balances;

    private State(Map<String, Long> balances) {
    this.balances = balances;
    }

public static State fromUsers(Collection<User> users) {
    Map<String, Long> m = new LinkedHashMap<>();
    for (User u : users) {
        if (u.initialBalance() < 0) throw new IllegalArgumentException("negative initial balance");
        if (m.put(u.publicKey(), u.initialBalance()) != null)
            throw new IllegalArgumentException("duplicate public key " + u.publicKey());
    }
    return new State(m);
}

public State copy() {
    return new State(new LinkedHashMap<>(balances));
}

public boolean hasAccount(String publicKey) {
    return balances.containsKey(publicKey);
}

public long balance(String publicKey) {
    Long b = balances.get(publicKey);
    if (b == null) throw new IllegalArgumentException("unknown account " + publicKey);
    return b;
}

public int accounts() {
    return balances.size();
}

public long total() {
    long sum = 0;
    for (long b : balances.values()) sum = Math.addExact(sum, b);
    return sum;
}

public long minBalance() {
    long min = Long.MAX_VALUE;
    for (long b : balances.values()) min = Math.min(min, b);
    return balances.isEmpty() ? 0 : min;
}

public TxError check(Transaction tx) {
    if (!hasAccount(tx.sender())) return TxError.UNKNOWN_SENDER;
    if (!hasAccount(tx.receiver())) return TxError.UNKNOWN_RECEIVER;
    if (tx.amount() <= 0) return TxError.NON_POSITIVE_AMOUNT;
    if (balance(tx.sender()) < tx.amount()) return TxError.INSUFFICIENT_BALANCE;
    long rb = balance(tx.receiver());
    if (rb > Long.MAX_VALUE - tx.amount()) return TxError.OVERFLOW;
    return null;
}

public void apply(Transaction tx) {
    TxError e = check(tx);
    if (e != null) throw new IllegalStateException("cannot apply " + tx + ": " + e.description());
    balances.put(tx.sender(), balance(tx.sender()) - tx.amount());
    balances.put(tx.receiver(), balance(tx.receiver()) + tx.amount());
}

public Map<String, Long> snapshot() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(balances));
}
}