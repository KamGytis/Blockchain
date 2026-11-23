package bc.sim;

import java.nio.file.Path;

public record Config(int users, int transactions, int blockSize, int difficulty, long seed,
                     long maxAmount, double invalidRate, Path outDir, boolean fixedTime,
                     int candidates, long budgetAttempts, long budgetMs, int maxRounds, boolean verbose) {

    public static final long DEFAULT_SEED = 20261029L;

    public static Config defaults() {
        return new Config(1000, 10000, 100, 3, DEFAULT_SEED, 50_000, 0.01, Path.of("out"), false,
                5, 2000, 5000, 10, false);
    }

    public static Config parse(String[] args) {
        Config c = defaults();
        int users = c.users, txs = c.transactions, bs = c.blockSize, diff = c.difficulty;
        long seed = c.seed, maxAmount = c.maxAmount;
        double invalid = c.invalidRate;
        Path out = c.outDir;
        boolean fixedTime = c.fixedTime, verbose = c.verbose;
        int candidates = c.candidates, maxRounds = c.maxRounds;
        long budget = c.budgetAttempts, budgetMs = c.budgetMs;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--users" -> users = Integer.parseInt(args[++i]);
                case "--txs" -> txs = Integer.parseInt(args[++i]);
                case "--block-size" -> bs = Integer.parseInt(args[++i]);
                case "--difficulty" -> diff = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--max-amount" -> maxAmount = Long.parseLong(args[++i]);
                case "--invalid-rate" -> invalid = Double.parseDouble(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                case "--fixed-time" -> fixedTime = true;
                case "--candidates" -> candidates = Integer.parseInt(args[++i]);
                case "--budget" -> budget = Long.parseLong(args[++i]);
                case "--budget-ms" -> budgetMs = Long.parseLong(args[++i]);
                case "--max-rounds" -> maxRounds = Integer.parseInt(args[++i]);
                case "--verbose" -> verbose = true;
                case "-h", "--help" -> {
                    System.out.println("""
                            Naudojimas: java -cp out/classes bc.App [parinktys]
                              --users N         vartotoju skaicius (1000)
                              --txs N           transakciju skaicius (10000)
                              --block-size N    max transakciju bloke (100)
                              --difficulty N    reikalingu pradiniu hex nuliu skaicius (3)
                              --seed N          generavimo seed (20261029)
                              --max-amount N    didziausia generuojama suma (50000)
                              --invalid-rate X  tycia sugadintu transakciju dalis (0.01)
                              --out DIR         ataskaitu katalogas (out)
                              --fixed-time      timestamp = bloko aukstis (pilnai atkuriami bloko hash)
                              --candidates N    kandidatu kiekvienam aukstiui (5)
                              --budget N        pradinis bandymu biudzetas kandidatui per raunda (2000)
                              --budget-ms N     laiko biudzetas kandidatui per raunda, ms (5000)
                              --max-rounds N    kiek kartu daugiausia dvigubinti biudzeta pries sustojant (10)
                              --verbose         spausdinti kiekvieno kandidato eilute kiekvienam aukstiui""");
                    System.exit(0);
                }
                default -> throw new IllegalArgumentException("nezinoma parinktis: " + a + " (zr. --help)");
            }
        }
        if (users < 2 || txs < 0 || bs < 1 || diff < 0 || diff > 64 || maxAmount < 1 || invalid < 0 || invalid > 1
                || candidates < 1 || budget < 1 || budgetMs < 1 || maxRounds < 1 || maxRounds > 40)
            throw new IllegalArgumentException("netinkami parametrai");
        return new Config(users, txs, bs, diff, seed, maxAmount, invalid, out, fixedTime,
                candidates, budget, budgetMs, maxRounds, verbose);
    }
}
