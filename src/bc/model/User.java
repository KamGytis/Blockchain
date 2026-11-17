package bc.model;

/**
 * A simulated participant. {@code publicKey} is only a simulated public identifier
 * (64 hex chars); it does not prove key ownership.
 */
public record User(String name, String publicKey, long initialBalance) {}
