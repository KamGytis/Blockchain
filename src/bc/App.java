package bc;

import bc.sim.Config;
import bc.sim.Simulation;

public final class App {
    public static void main(String[] args) throws Exception {
        new Simulation(Config.parse(args)).run();
    }
}