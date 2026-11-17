package bc.model;

import bc.hash.HashUtil;

public final class Transaction {
    private final String sender;
    private final String receiver;
    private final long amount;
    private final long sequence;
    private final String id;

    public Transaction(String sender, String receiver, long amount, long sequence, String id) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.sequence = sequence;
        this.id = id;
    }
    public static Transaction create(String sender, String receiver, long amount, long sequence) {
        return new Transaction(sender, receiver, amount, sequence, computeId(sender, receiver, amount, sequence));
    }

    public static String serialize(String sender, String receiver, long amount, long sequence) {
        return sender + '|' + receiver + '|' + Long.toString(amount) + '|' + Long.toString(sequence);
    }

    public static String computeId(String sender, String receiver, long amount, long sequence) {
        return HashUtil.hashUtf8(serialize(sender, receiver, amount, sequence));
    }

    public String serialize() {
        return serialize(sender, receiver, amount, sequence);
    }

    public boolean hasValidId() {
        return id != null && id.equals(computeId(sender, receiver, amount, sequence));
    }

    public String sender() { return sender; }
    public String receiver() { return receiver; }
    public long amount() { return amount; }
    public long sequence() { return sequence; }
    public String id() { return id; }

    @Override
    public String toString() {
        return "Tx{" + HashUtil.shortHex(String.valueOf(id)) + " " + HashUtil.shortHex(String.valueOf(sender))
                + " -> " + HashUtil.shortHex(String.valueOf(receiver)) + " amount=" + amount + " seq=" + sequence + "}";
    }
}
