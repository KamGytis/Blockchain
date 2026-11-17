package bc.core;

import bc.model.Transaction;
import bc.model.User;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class State {
    private final Map<String, Long> balances;

    private State(Map<String, Long> balances) {
        this.balances = balances;
    }

    public static State fromUsers(Collection<User> users) {
        Map<String, Long> m = new HashMap<>();
        for (User u : users) {
            if (u.initialBalance() < 0) throw new IllegalArgumentException("negative initial balance");
            if (m.put(u.publicKey(), u.initialBalance()) != null)
                throw new IllegalArgumentException("duplicate public key " + u.publicKey());
        }
        return new State(m);
    }
}
