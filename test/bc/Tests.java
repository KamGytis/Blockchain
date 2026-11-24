package bc;

/** Testų paleidiklis: java -cp out/classes:out/test bc.Tests */
public final class Tests {
    public static void main(String[] args) {
        T.section("v0.1: maisa, serializacija, PoW, balansai");
        V01Tests.all();
        T.section("v0.2: Merkle medis");
        MerkleTests.all();
        T.section("v0.2: transakciju ir bloko validacija (3 etapai)");
        ValidationTests.all();
        T.section("v0.2: penki kandidatai");
        CompetitionTests.all();
        T.section("v0.3: optimizaciju lygiavertiskumas");
        V03Tests.all();
        T.section("bendras teisingumo rinkinys (5 skyrius; tas pats v0.2 ir v0.3)");
        CorrectnessSuite.all();

        System.out.printf("%nRezultatas: %d praejo, %d nepavyko%n", T.passed, T.failed);
        if (T.failed > 0) {
            T.failures.forEach(f -> System.out.println("  nepavyko: " + f));
            System.exit(1);
        }
    }
}
