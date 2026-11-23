package bc.core;

public enum BlockError {
    UNSUPPORTED_VERSION("nepalaikoma formato versija"),
    BAD_PREV_HASH("prev_hash nesutampa su paskutiniu bloku"),
    LOWERED_DIFFICULTY("kandidatas sumazino reikalaujama sudetinguma"),
    BAD_DIFFICULTY("difficulty nesutampa su grandines nustatytu"),
    EMPTY("tuscias blokas"),
    TOO_MANY_TX("virsytas leidziamas transakciju skaicius"),
    BAD_TX_ROOT("tx_root (Merkle saknis) neatitinka turinio"),
    BAD_POW("PoW netenkina salygos (maisa perskaiciuota)"),
    BAD_TX("netinkama transakcija"),
    SUPPLY_CHANGED("pasikeite bendra lesu suma"),
    NEGATIVE_BALANCE("neigiamas balansas");

    private final String description;

    BlockError(String description) {
        this.description = description;
    }

    public String description() { return description; }
}
