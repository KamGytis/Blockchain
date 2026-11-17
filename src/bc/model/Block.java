package bc.model;

import java.util.List;

public record Block(BlockHeader header, List<Transaction> transactions) {
    public Block {
        transactions = List.copyOf(transactions);
    }

    public String hash() {
        return header.hash();
    }
}
