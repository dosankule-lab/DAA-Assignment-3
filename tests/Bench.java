import java.util.Arrays;
import java.util.Map;
import java.util.Random;

public final class Bench {
    private static final long SEED = 12345L;
    private static final int WARMUP = 5;
    private static final int RUNS = 5;

    public static void main(String[] args) {
        int[][] sizes = {{2000, 5000}, {20000, 50000}, {100000, 250000}};
        System.out.println("seed=" + SEED + " warmup=" + WARMUP + " timed_runs=" + RUNS
                + " java=" + System.getProperty("java.version")
                + " cpus=" + Runtime.getRuntime().availableProcessors());
        System.out.println("| n | m | method | run times (ms) | median (ms) | cost | components |");
        System.out.println("|---|---|--------|----------------|-------------|------|------------|");
        for (int[] size : sizes) {
            int n = size[0];
            int m = size[1];
            Random rnd = new Random(SEED + n);
            StringBuilder sb = new StringBuilder("{\"task\":\"x\",\"n\":" + n + ",\"edges\":[");
            for (int i = 0; i < m; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('[').append(rnd.nextInt(n)).append(',').append(rnd.nextInt(n)).append(',')
                        .append(rnd.nextInt(2_000_001) - 1_000_000).append(']');
            }
            sb.append("]}");
            Map<String, Object> request = Json.parseRequest(sb.toString());

            Map<String, Object> k = Algorithms.kruskal(request);
            Map<String, Object> p = Algorithms.prim(request);
            if (!k.get("cost").equals(p.get("cost")) || !k.get("components").equals(p.get("components"))) {
                throw new IllegalStateException("Kruskal and Prim disagree for n=" + n);
            }
            double[] kt = new double[RUNS];
            double[] pt = new double[RUNS];
            for (int i = 0; i < WARMUP; i++) {
                Algorithms.kruskal(request);
                Algorithms.prim(request);
            }
            for (int i = 0; i < RUNS; i++) {
                long t = System.nanoTime();
                Algorithms.kruskal(request);
                kt[i] = (System.nanoTime() - t) / 1e6;
                t = System.nanoTime();
                Algorithms.prim(request);
                pt[i] = (System.nanoTime() - t) / 1e6;
            }
            print(n, m, "Kruskal", kt, k);
            print(n, m, "Prim", pt, p);
        }
    }

    private static void print(int n, int m, String name, double[] times, Map<String, Object> result) {
        double[] sorted = times.clone();
        Arrays.sort(sorted);
        StringBuilder all = new StringBuilder();
        for (double t : times) {
            if (all.length() > 0) {
                all.append(", ");
            }
            all.append(String.format("%.2f", t));
        }
        System.out.printf("| %d | %d | %s | %s | %.2f | %s | %s |%n", n, m, name, all,
                sorted[sorted.length / 2], result.get("cost"), result.get("components"));
    }
}
