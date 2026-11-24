package bc;

import bc.core.Merkle;
import bc.core.Pow;
import bc.hash.HashUtil;
import bc.model.BlockHeader;
import bc.model.Transaction;
import bc.sim.Config;
import bc.sim.Simulation;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Našumo palyginimas v0.2 ir v0.3. Naudoja TIK v0.2 viešąją API, todėl tas pats failas kompiliuojamas
 * ir paleidžiamas abiejose versijose. Kiekvienas matavimas kartojamas, rodoma mediana.
 * Paleidimas: java -cp out/classes:out/test bc.Bench
 */
public final class Bench {
    static final int REPS = 7;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        // Fiksuoti duomenys: 100 transakcijų ir 20 antraščių su difficulty 4
        List<Transaction> txs = new ArrayList<>();
        for (int i = 0; i < 100; i++)
            txs.add(Transaction.create(HashUtil.hashUtf8("s" + i), HashUtil.hashUtf8("r" + i), 1 + i, i));
        String root = Merkle.root(txs);
        List<BlockHeader> headers = new ArrayList<>();
        for (int i = 0; i < 20; i++)
            headers.add(new BlockHeader(HashUtil.hashUtf8("prev" + i), 1_700_000_000L + i, 2, root, 0, 4));

        // Teisingumo „parašai“ – turi sutapti tarp versijų
        StringBuilder sig = new StringBuilder();
        long totalAttempts = 0;
        for (BlockHeader h : headers) {
            Pow.Result r = Pow.mine(h);
            sig.append(r.header().nonce()).append(',');
            totalAttempts += r.attempts();
        }
        System.out.println("merkle_root(100 tx):   " + root);
        System.out.println("pow_nonces_hash:       " + HashUtil.hashUtf8(sig.toString()) + " (20 antrasciu, difficulty 4)");
        System.out.println("pow_attempts_total:    " + totalAttempts);

        byte[] headerBytes = headers.get(0).serialize().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final long attempts = totalAttempts;

        double hashNs = median(() -> {
            long t = System.nanoTime();
            for (int i = 0; i < 200_000; i++) HashUtil.hashBytes(headerBytes);
            return (System.nanoTime() - t) / 200_000.0;
        });
        double mineMs = median(() -> {
            long t = System.nanoTime();
            for (BlockHeader h : headers) Pow.mine(h);
            return (System.nanoTime() - t) / 1e6;
        });
        double merkleUs = median(() -> {
            long t = System.nanoTime();
            for (int i = 0; i < 2000; i++) Merkle.root(txs);
            return (System.nanoTime() - t) / 2000 / 1e3;
        });

        System.out.printf("hash(antraste %d B):   %.0f ns/maisa%n", headerBytes.length, hashNs);
        System.out.printf("kasimas:               %.1f ms, %.0f bandymu/s%n", mineMs, attempts / (mineMs / 1000));
        System.out.printf("Merkle 100 tx:         %.1f us%n", merkleUs);

        // Visa simuliacija su ta pačia pradine būsena, duomenimis ir sudėtingumu
        String[] simArgs = {"--fixed-time", "--out", Files.createTempDirectory("bench").toString()};
        PrintStream orig = System.out;
        String[] tip = new String[1];
        double simMs;
        try {
            System.setOut(new PrintStream(new ByteArrayOutputStream()));
            simMs = median(() -> {
                long t = System.nanoTime();
                tip[0] = new Simulation(Config.parse(simArgs)).run();
                return (System.nanoTime() - t) / 1e6;
            });
        } finally {
            System.setOut(orig);
        }
        System.out.printf("simuliacija:           %.0f ms (numatyti parametrai, --fixed-time)%n", simMs);
        System.out.println("paskutinio bloko hash: " + tip[0]);
        Runtime rt = Runtime.getRuntime();
        System.out.printf("JVM: %s, %d branduoliai, max heap %d MB%n", System.getProperty("java.version"),
                rt.availableProcessors(), rt.maxMemory() >> 20);
    }

    interface Measure { double run() throws Exception; }

    static double median(Measure m) throws Exception {
        m.run(); // apšilimas (JIT)
        double[] v = new double[REPS];
        for (int i = 0; i < REPS; i++) v[i] = m.run();
        Arrays.sort(v);
        return v[REPS / 2];
    }
}
