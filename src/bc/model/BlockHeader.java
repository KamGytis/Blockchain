package bc.model;

import bc.hash.HashUtil;

public record BlockHeader(String prevHash, long timestamp, int version, String txRoot, long nonce, int difficulty) {

    public String serialize() {
        return serializePrefix() + nonce + '|' + difficulty;
    }

    /** Pastovi antraštės dalis kasant: prev_hash|timestamp|version|tx_root| (serialize() = prefix + nonce|difficulty). */
    public String serializePrefix() {
        return prevHash + '|' + timestamp + '|' + version + '|' + txRoot + '|';
    }

    public String hash() {
        return HashUtil.hashUtf8(serialize());
    }

    public BlockHeader withNonce(long newNonce) {
        return new BlockHeader(prevHash, timestamp, version, txRoot, newNonce, difficulty);
    }
}