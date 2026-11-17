package bc.sim;

import java.nio.file.Path;

public record Config(int users, int transactions, int blockSize, int difficulty, long seed,
                     long maxAmount, double invalidRate, Path outDir, boolean fixedTime) {

    public static final long DEFAULT_SEED = 20261029L;

    public static Config defaults() {
        return new Config(1000, 10000, 100, 3, DEFAULT_SEED, 50_000, 0.01, Path.of("out"), false);
    }

    public static Config parse(String[] args) {
        Config c = defaults();
        int users = c.users, txs = c.transactions, bs = c.blockSize, diff = c.difficulty;
        long seed = c.seed, maxAmount = c.maxAmount;
        double invalid = c.invalidRate;
        Path out = c.outDir;
        boolean fixedTime = c.fixedTime;
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
                case "-h", "--help" -> {
                    System.out.println("""
                            Naudojimas: java -cp out/classes bc.App [parinktys]
                              --users N         vartotojų skaičius (1000)
                              --txs N           transakcijų skaičius (10000)
                              --block-size N    max transakcijų bloke (100)
                              --difficulty N    reikalingų pradinių hex nulių skaičius (3)
                              --seed N          generavimo seed (20261029)
                              --max-amount N    didžiausia generuojama suma (50000)
                              --invalid-rate X  tyčia sugadintų transakcijų dalis (0.01)
                              --out DIR         ataskaitų katalogas (out)
                              --fixed-time      timestamp = bloko aukštis (pilnai atkuriami bloko hash)""");
                    System.exit(0);
                }
                default -> throw new IllegalArgumentException("nežinoma parinktis: " + a + " (žr. --help)");
            }
        }
        if (users < 2 || txs < 0 || bs < 1 || diff < 0 || diff > 64 || maxAmount < 1 || invalid < 0 || invalid > 1)
            throw new IllegalArgumentException("netinkami parametrai");
        return new Config(users, txs, bs, diff, seed, maxAmount, invalid, out, fixedTime);
    }
}
