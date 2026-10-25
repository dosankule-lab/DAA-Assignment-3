import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class TestSuite {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        fixedCases();
        witnessCheckerCases();
        routeHelperCases();
        stateResetCases();
        randomCases();
        largeCases();
        System.out.println("TOTAL passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + name);
        }
    }

    static Map<String, Object> run(String task, String json) {
        Map<String, Object> request = Json.parseRequest(json);
        Map<String, Object> raw;
        switch (task) {
            case "dsu": raw = Algorithms.dsu(request); break;
            case "kruskal": raw = Algorithms.kruskal(request); break;
            case "prim": raw = Algorithms.prim(request); break;
            case "scc": raw = Algorithms.scc(request); break;
            case "topo": raw = Algorithms.topo(request); break;
            default: raw = Algorithms.dag(request); break;
        }
        return asMap(Json.parse(Json.write(raw)));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object o) {
        return (List<Object>) o;
    }

    static int[] ints(Object o) {
        List<Object> list = asList(o);
        int[] r = new int[list.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = ((Number) list.get(i)).intValue();
        }
        return r;
    }

    static String edgesJson(int[][] e) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < e.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Arrays.toString(e[i]).replace(" ", ""));
        }
        return sb.append(']').toString();
    }

    static String request(String task, int n, int[][] edges, String extra) {
        String key = task.equals("dsu") ? "operations" : "edges";
        return "{\"task\":\"" + task + "\",\"n\":" + n + "," + extra + "\"" + key + "\":" + edgesJson(edges) + "}";
    }

    static int[][] reachability(int n, int[][] edges, boolean undirected) {
        int[][] r = new int[n][n];
        for (int i = 0; i < n; i++) {
            r[i][i] = 1;
        }
        for (int[] e : edges) {
            r[e[0]][e[1]] = 1;
            if (undirected) {
                r[e[1]][e[0]] = 1;
            }
        }
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (r[i][k] == 1 && r[k][j] == 1) {
                        r[i][j] = 1;
                    }
                }
            }
        }
        return r;
    }

    static int countComponents(int n, int[][] edges) {
        int[][] r = reachability(n, edges, true);
        int c = 0;
        for (int i = 0; i < n; i++) {
            boolean first = true;
            for (int j = 0; j < i; j++) {
                if (r[i][j] == 1) {
                    first = false;
                }
            }
            if (first) {
                c++;
            }
        }
        return c;
    }

    static boolean hasCycleOracle(int n, int[][] edges) {
        int[][] r = reachability(n, edges, false);
        for (int[] e : edges) {
            if (e[0] == e[1] || r[e[1]][e[0]] == 1) {
                return true;
            }
        }
        return false;
    }

    static long bruteForestCost(int n, int[][] edges) {
        int c = countComponents(n, edges);
        long best = Long.MAX_VALUE;
        int m = edges.length;
        for (int mask = 0; mask < (1 << m); mask++) {
            if (Integer.bitCount(mask) != n - c) {
                continue;
            }
            int[] label = new int[n];
            for (int i = 0; i < n; i++) {
                label[i] = i;
            }
            boolean ok = true;
            long cost = 0;
            for (int i = 0; i < m && ok; i++) {
                if ((mask >> i & 1) == 0) {
                    continue;
                }
                int a = label[edges[i][0]];
                int b = label[edges[i][1]];
                if (a == b) {
                    ok = false;
                } else {
                    for (int v = 0; v < n; v++) {
                        if (label[v] == b) {
                            label[v] = a;
                        }
                    }
                    cost += edges[i][2];
                }
            }
            if (ok) {
                best = Math.min(best, cost);
            }
        }
        return best;
    }

    static boolean validForest(int n, int[][] edges, Map<String, Object> out, long optimum, int components) {
        int[] ids = ints(out.get("edge_ids"));
        long cost = ((Number) out.get("cost")).longValue();
        if (cost != optimum || ((Number) out.get("components")).intValue() != components) {
            return false;
        }
        if (ids.length != n - components) {
            return false;
        }
        Set<Integer> seen = new HashSet<>();
        int[] label = new int[n];
        for (int i = 0; i < n; i++) {
            label[i] = i;
        }
        long sum = 0;
        for (int id : ids) {
            if (id < 0 || id >= edges.length || !seen.add(id) || edges[id][0] == edges[id][1]) {
                return false;
            }
            int a = label[edges[id][0]];
            int b = label[edges[id][1]];
            if (a == b) {
                return false;
            }
            for (int v = 0; v < n; v++) {
                if (label[v] == b) {
                    label[v] = a;
                }
            }
            sum += edges[id][2];
        }
        return sum == cost;
    }

    static boolean validScc(int n, int[][] edges, Map<String, Object> out) {
        int[] comp = ints(out.get("component"));
        if (comp.length != n) {
            return false;
        }
        int[][] r = reachability(n, edges, false);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                boolean mutual = r[i][j] == 1 && r[j][i] == 1;
                if (mutual != (comp[i] == comp[j])) {
                    return false;
                }
            }
        }
        Set<String> expected = new HashSet<>();
        for (int[] e : edges) {
            if (comp[e[0]] != comp[e[1]]) {
                expected.add(comp[e[0]] + ":" + comp[e[1]]);
            }
        }
        List<Object> given = asList(out.get("condensation"));
        Set<String> actual = new HashSet<>();
        for (Object pair : given) {
            int[] p = ints(pair);
            if (!actual.add(p[0] + ":" + p[1])) {
                return false;
            }
        }
        return actual.equals(expected);
    }

    static boolean validTopo(int n, int[][] edges, Map<String, Object> out) {
        boolean acyclic = (Boolean) out.get("acyclic");
        int[] order = ints(out.get("order"));
        if (acyclic != !hasCycleOracle(n, edges)) {
            return false;
        }
        if (!acyclic) {
            return order.length == 0;
        }
        int[] pos = new int[n];
        Arrays.fill(pos, -1);
        if (order.length != n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (order[i] < 0 || order[i] >= n || pos[order[i]] != -1) {
                return false;
            }
            pos[order[i]] = i;
        }
        for (int[] e : edges) {
            if (pos[e[0]] >= pos[e[1]]) {
                return false;
            }
        }
        return true;
    }

    static boolean validDag(int n, int[][] edges, int source, Map<String, Object> out) {
        boolean acyclic = (Boolean) out.get("acyclic");
        List<Object> dist = asList(out.get("distance"));
        int[] parent = ints(out.get("parent_edge"));
        if (acyclic != !hasCycleOracle(n, edges)) {
            return false;
        }
        if (!acyclic) {
            return dist.isEmpty() && parent.length == 0;
        }
        Long[] best = new Long[n];
        best[source] = 0L;
        for (int pass = 0; pass < n; pass++) {
            for (int[] e : edges) {
                if (best[e[0]] != null && (best[e[1]] == null || best[e[0]] + e[2] < best[e[1]])) {
                    best[e[1]] = best[e[0]] + e[2];
                }
            }
        }
        if (dist.size() != n || parent.length != n) {
            return false;
        }
        for (int v = 0; v < n; v++) {
            Object d = dist.get(v);
            if (best[v] == null) {
                if (d != null || parent[v] != -1) {
                    return false;
                }
                continue;
            }
            if (d == null || ((Number) d).longValue() != best[v]) {
                return false;
            }
            if (v == source) {
                if (parent[v] != -1) {
                    return false;
                }
                continue;
            }
            int id = parent[v];
            if (id < 0 || id >= edges.length || edges[id][1] != v) {
                return false;
            }
            Object du = dist.get(edges[id][0]);
            if (du == null || ((Number) du).longValue() + edges[id][2] != best[v]) {
                return false;
            }
        }
        for (int v = 0; v < n; v++) {
            int steps = 0;
            int cur = v;
            while (best[v] != null && cur != source) {
                cur = edges[parent[cur]][0];
                if (++steps > n) {
                    return false;
                }
            }
        }
        return true;
    }

    static void fixedCases() {
        Map<String, Object> r = run("dsu", "{\"task\":\"dsu\",\"n\":4,\"operations\":[[0,1],[1,2],[0,2],[3,3]]}");
        check("dsu example", Json.write(r.get("states")).equals("[[3,2],[2,3],[2,3],[2,3]]"));
        r = run("dsu", "{\"task\":\"dsu\",\"n\":0,\"operations\":[]}");
        check("dsu n=0", asList(r.get("states")).isEmpty());
        r = run("dsu", "{\"task\":\"dsu\",\"n\":1,\"operations\":[[0,0],[0,0]]}");
        check("dsu single vertex self union", Json.write(r.get("states")).equals("[[1,1],[1,1]]"));
        r = run("dsu", "{\"task\":\"dsu\",\"n\":3,\"operations\":[]}");
        check("dsu no operations", asList(r.get("states")).isEmpty());

        int[][] e1 = {{0, 1, 4}, {1, 2, 2}, {0, 2, 3}};
        for (String task : new String[] {"kruskal", "prim"}) {
            r = run(task, request(task, 4, e1, ""));
            check(task + " example", validForest(4, e1, r, 5, 2));
            r = run(task, "{\"task\":\"" + task + "\",\"n\":0,\"edges\":[]}");
            check(task + " n=0", ((Number) r.get("cost")).longValue() == 0 && ((Number) r.get("components")).intValue() == 0);
            int[][] loops = {{0, 0, -5}, {1, 1, 7}};
            r = run(task, request(task, 3, loops, ""));
            check(task + " only loops, three isolates", validForest(3, loops, r, 0, 3));
            int[][] parallel = {{0, 1, 5}, {0, 1, -3}, {0, 1, -3}, {1, 0, 9}};
            r = run(task, request(task, 2, parallel, ""));
            check(task + " parallel negative", validForest(2, parallel, r, -3, 1));
            int[][] big = new int[200][];
            for (int i = 0; i < 200; i++) {
                big[i] = new int[] {i, i + 1, 1_000_000_000};
            }
            r = run(task, request(task, 201, big, ""));
            check(task + " cost beyond 32 bits", ((Number) r.get("cost")).longValue() == 200_000_000_000L);
            int[][] negs = new int[200][];
            for (int i = 0; i < 200; i++) {
                negs[i] = new int[] {i, i + 1, -1_000_000_000};
            }
            r = run(task, request(task, 201, negs, ""));
            check(task + " negative cost beyond 32 bits", ((Number) r.get("cost")).longValue() == -200_000_000_000L);
        }

        int[][] s1 = {{0, 1}, {1, 0}, {1, 2}, {0, 2}, {2, 3}};
        r = run("scc", request("scc", 4, s1, ""));
        check("scc example", validScc(4, s1, r));
        r = run("scc", "{\"task\":\"scc\",\"n\":0,\"edges\":[]}");
        check("scc n=0", asList(r.get("component")).isEmpty() && asList(r.get("condensation")).isEmpty());
        int[][] s2 = {{0, 0}, {1, 2}, {1, 2}, {2, 1}, {2, 1}, {3, 0}};
        r = run("scc", request("scc", 5, s2, ""));
        check("scc loops, duplicates, isolate", validScc(5, s2, r));

        int[][] t1 = {{0, 2}, {1, 2}, {2, 3}};
        r = run("topo", request("topo", 4, t1, ""));
        check("topo example", validTopo(4, t1, r));
        r = run("topo", "{\"task\":\"topo\",\"n\":0,\"edges\":[]}");
        check("topo n=0", (Boolean) r.get("acyclic") && asList(r.get("order")).isEmpty());
        int[][] t2 = {{0, 1}, {1, 2}, {0, 2}, {3, 4}, {4, 3}};
        r = run("topo", request("topo", 5, t2, ""));
        check("topo cycle in second component, forward edge to finished vertex", validTopo(5, t2, r) && !(Boolean) r.get("acyclic"));
        int[][] t3 = {{0, 1}, {0, 1}, {1, 2}, {0, 2}};
        r = run("topo", request("topo", 3, t3, ""));
        check("topo diamond-like finished-vertex edges are acyclic", validTopo(3, t3, r) && (Boolean) r.get("acyclic"));
        int[][] t4 = {{1, 1}};
        r = run("topo", request("topo", 2, t4, ""));
        check("topo self loop", validTopo(2, t4, r) && !(Boolean) r.get("acyclic"));

        int[][] d1 = {{0, 1, 3}, {0, 2, 8}, {1, 2, -7}, {2, 3, 4}};
        r = run("dag", request("dag", 5, d1, "\"source\":0,"));
        check("dag example", validDag(5, d1, 0, r) && Json.write(r.get("distance")).equals("[0,3,-4,0,null]"));
        r = run("dag", "{\"task\":\"dag\",\"n\":1,\"source\":0,\"edges\":[]}");
        check("dag single vertex", Json.write(r.get("distance")).equals("[0]") && Json.write(r.get("parent_edge")).equals("[-1]"));
        int[][] d2 = {{0, 1, -1}, {2, 3, 1}, {3, 2, 1}};
        r = run("dag", request("dag", 4, d2, "\"source\":0,"));
        check("dag cycle unreachable from source", validDag(4, d2, 0, r) && !(Boolean) r.get("acyclic"));
        int[][] d3 = {{1, 2, -1}, {0, 1, 5}};
        r = run("dag", request("dag", 4, d3, "\"source\":1,"));
        check("dag unreachable vertex has null, not -1", validDag(4, d3, 1, r) && Json.write(r.get("distance")).equals("[null,0,-1,null]"));
        int[][] d4 = {{0, 1, 1_000_000_000}, {1, 2, 1_000_000_000}, {2, 3, 1_000_000_000}};
        r = run("dag", request("dag", 4, d4, "\"source\":0,"));
        check("dag 64-bit distance", Json.write(r.get("distance")).equals("[0,1000000000,2000000000,3000000000]"));
        int[][] d5 = {{0, 1, 4}, {0, 1, 4}, {0, 2, 2}, {2, 1, 2}};
        r = run("dag", request("dag", 3, d5, "\"source\":0,"));
        check("dag tied and parallel edges", validDag(3, d5, 0, r));
        int[][] d6 = {{0, 0, 1}};
        r = run("dag", request("dag", 1, d6, "\"source\":0,"));
        check("dag self loop", validDag(1, d6, 0, r) && !(Boolean) r.get("acyclic"));
    }

    static void witnessCheckerCases() {
        int[][] edges = {{0, 1, 4}, {1, 2, 2}, {0, 2, 3}};
        Map<String, Object> good = Json.parseRequest("{\"cost\":5,\"edge_ids\":[2,1],\"components\":1}");
        Map<String, Object> alternative = Json.parseRequest("{\"cost\":5,\"edge_ids\":[1,2],\"components\":1}");
        Map<String, Object> cyclic = Json.parseRequest("{\"cost\":9,\"edge_ids\":[0,1,2],\"components\":1}");
        Map<String, Object> repeated = Json.parseRequest("{\"cost\":4,\"edge_ids\":[1,1],\"components\":1}");
        Map<String, Object> suboptimal = Json.parseRequest("{\"cost\":6,\"edge_ids\":[0,1],\"components\":1}");
        check("forest checker accepts valid answer", validForest(3, edges, good, 5, 1));
        check("forest checker accepts alternative order of ids", validForest(3, edges, alternative, 5, 1));
        check("forest checker rejects cycle", !validForest(3, edges, cyclic, 5, 1));
        check("forest checker rejects repeated edge id", !validForest(3, edges, repeated, 5, 1));
        check("forest checker rejects non-optimal forest", !validForest(3, edges, suboptimal, 5, 1));

        int[][] g = {{0, 1}, {1, 2}};
        Map<String, Object> order1 = Json.parseRequest("{\"acyclic\":true,\"order\":[0,1,2]}");
        Map<String, Object> order2 = Json.parseRequest("{\"acyclic\":true,\"order\":[2,1,0]}");
        check("topo checker accepts valid order", validTopo(3, g, order1));
        check("topo checker rejects order violating an edge", !validTopo(3, g, order2));
        int[][] two = {{0, 2}, {1, 2}};
        Map<String, Object> alt1 = Json.parseRequest("{\"acyclic\":true,\"order\":[0,1,2]}");
        Map<String, Object> alt2 = Json.parseRequest("{\"acyclic\":true,\"order\":[1,0,2]}");
        check("topo checker accepts both valid orders", validTopo(3, two, alt1) && validTopo(3, two, alt2));

        int[][] dagEdges = {{0, 1, 3}, {0, 1, 3}, {1, 2, 1}};
        Map<String, Object> w1 = Json.parseRequest("{\"acyclic\":true,\"distance\":[0,3,4],\"parent_edge\":[-1,0,2]}");
        Map<String, Object> w2 = Json.parseRequest("{\"acyclic\":true,\"distance\":[0,3,4],\"parent_edge\":[-1,1,2]}");
        Map<String, Object> bad1 = Json.parseRequest("{\"acyclic\":true,\"distance\":[0,3,4],\"parent_edge\":[-1,2,2]}");
        Map<String, Object> bad2 = Json.parseRequest("{\"acyclic\":true,\"distance\":[0,3,5],\"parent_edge\":[-1,0,2]}");
        check("dag checker accepts parallel-edge witness 1", validDag(3, dagEdges, 0, w1));
        check("dag checker accepts parallel-edge witness 2", validDag(3, dagEdges, 0, w2));
        check("dag checker rejects parent edge that ends elsewhere", !validDag(3, dagEdges, 0, bad1));
        check("dag checker rejects wrong distance", !validDag(3, dagEdges, 0, bad2));

        int[][] sg = {{0, 1}, {1, 0}, {1, 2}};
        Map<String, Object> s1 = Json.parseRequest("{\"component\":[5,5,7],\"condensation\":[[5,7]]}");
        Map<String, Object> s2 = Json.parseRequest("{\"component\":[0,0,1],\"condensation\":[[0,1]]}");
        Map<String, Object> s3 = Json.parseRequest("{\"component\":[0,1,2],\"condensation\":[[0,2],[1,2]]}");
        Map<String, Object> s4 = Json.parseRequest("{\"component\":[5,5,7],\"condensation\":[[5,7],[5,7]]}");
        check("scc checker accepts labels 5,5,7", validScc(3, sg, s1));
        check("scc checker accepts labels 0,0,1", validScc(3, sg, s2));
        check("scc checker rejects split SCC", !validScc(3, sg, s3));
        check("scc checker rejects duplicate condensation pair", !validScc(3, sg, s4));
    }

    static void routeHelperCases() {
        int[][] edges = {{0, 1, 3}, {0, 2, 8}, {1, 2, -7}, {2, 3, 4}};
        Map<String, Object> out = run("dag", request("dag", 5, edges, "\"source\":0,"));
        int[] parent = ints(out.get("parent_edge"));
        List<Integer> toThree = Algorithms.route(0, 3, edges, parent);
        List<Integer> toSource = Algorithms.route(0, 0, edges, parent);
        List<Integer> toUnreachable = Algorithms.route(0, 4, edges, parent);
        System.out.println("route(0->3) = " + toThree + ", parent_edge = " + Arrays.toString(parent));
        System.out.println("route(0->0) = " + toSource);
        System.out.println("route(0->4) = " + toUnreachable + " (empty list means no route)");
        check("route reachable target", toThree.equals(List.of(0, 1, 2, 3)));
        check("route to source", toSource.equals(List.of(0)));
        check("route unreachable target", toUnreachable.isEmpty());
        long cost = 0;
        for (int i = 0; i + 1 < toThree.size(); i++) {
            int id = parent[toThree.get(i + 1)];
            check("route edge direction " + id, edges[id][0] == toThree.get(i) && edges[id][1] == toThree.get(i + 1));
            cost += edges[id][2];
        }
        check("route cost equals distance[3]=0", cost == 0);

        int[][] tied = {{0, 1, 4}, {0, 1, 4}, {0, 2, 2}, {2, 1, 2}, {1, 3, 1}};
        out = run("dag", request("dag", 4, tied, "\"source\":0,"));
        parent = ints(out.get("parent_edge"));
        List<Integer> route = Algorithms.route(0, 3, tied, parent);
        System.out.println("tied route(0->3) = " + route + ", parent_edge = " + Arrays.toString(parent)
                + ", distance = " + Json.write(out.get("distance")));
        long total = 0;
        for (int i = 0; i + 1 < route.size(); i++) {
            int id = parent[route.get(i + 1)];
            check("tied route edge direction " + id, tied[id][0] == route.get(i) && tied[id][1] == route.get(i + 1));
            total += tied[id][2];
        }
        check("tied route cost equals distance[3]=5", total == 5 && validDag(4, tied, 0, out));

        int[] corrupt = {-1, 2, -1, -1, -1};
        check("route rejects parent edge that does not end at vertex", Algorithms.route(0, 1, edges, corrupt).isEmpty());
        int[][] loop = {{0, 1, 1}, {1, 2, 1}, {2, 1, 1}};
        check("route terminates on corrupt cyclic parents", Algorithms.route(0, 1, loop, new int[] {-1, 2, 1}).isEmpty());
    }

    static void stateResetCases() {
        String[] lines = {
            "{\"task\":\"dsu\",\"n\":5,\"operations\":[[0,1],[2,3],[1,3]]}",
            "{\"task\":\"kruskal\",\"n\":4,\"edges\":[[0,1,4],[1,2,2],[0,2,3]]}",
            "{\"task\":\"prim\",\"n\":4,\"edges\":[[0,1,4],[1,2,2],[0,2,3]]}",
            "{\"task\":\"scc\",\"n\":4,\"edges\":[[0,1],[1,0],[1,2],[0,2],[2,3]]}",
            "{\"task\":\"topo\",\"n\":3,\"edges\":[[0,1],[1,2],[2,0]]}",
            "{\"task\":\"dag\",\"n\":5,\"source\":0,\"edges\":[[0,1,3],[0,2,8],[1,2,-7],[2,3,4]]}",
            "{\"task\":\"dsu\",\"n\":2,\"operations\":[[0,1]]}",
            "{\"task\":\"topo\",\"n\":3,\"edges\":[[0,1],[1,2]]}"
        };
        String[] first = new String[lines.length];
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < lines.length; i++) {
                String task = (String) Json.parseRequest(lines[i]).get("task");
                String out = Json.write(run(task, lines[i]));
                if (round == 0) {
                    first[i] = out;
                } else {
                    check("state reset round " + round + " request " + i, out.equals(first[i]));
                }
            }
        }
    }

    static void randomCases() {
        Random rnd = new Random(20261025);
        for (int iter = 0; iter < 1500; iter++) {
            int n = 1 + rnd.nextInt(7);
            int m = rnd.nextInt(10);
            int[][] undirected = new int[m][3];
            int[][] directed = new int[m][2];
            int[][] weightedDirected = new int[m][3];
            for (int i = 0; i < m; i++) {
                int u = rnd.nextInt(n);
                int v = rnd.nextInt(n);
                int w = rnd.nextInt(11) - 5;
                undirected[i] = new int[] {u, v, w};
                directed[i] = new int[] {u, v};
                weightedDirected[i] = new int[] {u, v, w};
            }
            long optimum = bruteForestCost(n, undirected);
            int comps = countComponents(n, undirected);
            for (String task : new String[] {"kruskal", "prim"}) {
                check("random " + task + " #" + iter, validForest(n, undirected, run(task, request(task, n, undirected, "")), optimum, comps));
            }
            check("random scc #" + iter, validScc(n, directed, run("scc", request("scc", n, directed, ""))));
            check("random topo #" + iter, validTopo(n, directed, run("topo", request("topo", n, directed, ""))));
            int source = rnd.nextInt(n);
            check("random dag #" + iter, validDag(n, weightedDirected, source, run("dag", request("dag", n, weightedDirected, "\"source\":" + source + ","))));

            int[][] ops = new int[m][2];
            for (int i = 0; i < m; i++) {
                ops[i] = new int[] {rnd.nextInt(n), rnd.nextInt(n)};
            }
            Map<String, Object> r = run("dsu", request("dsu", n, ops, ""));
            List<Object> states = asList(r.get("states"));
            boolean ok = states.size() == m;
            for (int i = 0; ok && i < m; i++) {
                int[][] prefix = Arrays.copyOf(ops, i + 1);
                int[] st = ints(states.get(i));
                int[][] closure = reachability(n, prefix, true);
                int largest = 0;
                for (int a = 0; a < n; a++) {
                    int size = 0;
                    for (int b = 0; b < n; b++) {
                        size += closure[a][b];
                    }
                    largest = Math.max(largest, size);
                }
                ok = st[0] == countComponents(n, prefix) && st[1] == largest;
            }
            check("random dsu #" + iter, ok);
        }
        for (int iter = 0; iter < 400; iter++) {
            int n = 1 + rnd.nextInt(6);
            int m = rnd.nextInt(n * (n - 1) / 2 + 1);
            int[][] dagEdges = new int[m][3];
            for (int i = 0; i < m; i++) {
                int a = rnd.nextInt(n);
                int b = rnd.nextInt(n);
                int lo = Math.min(a, b);
                int hi = Math.max(a, b);
                if (lo == hi) {
                    hi = Math.min(n - 1, lo + 1);
                    if (hi == lo) {
                        lo = 0;
                        hi = 0;
                    }
                }
                dagEdges[i] = new int[] {lo, hi, rnd.nextInt(21) - 10};
            }
            int source = rnd.nextInt(n);
            Map<String, Object> r = run("dag", request("dag", n, dagEdges, "\"source\":" + source + ","));
            check("random acyclic-biased dag #" + iter, validDag(n, dagEdges, source, r));
        }
    }

    static void largeCases() {
        Random rnd = new Random(7);
        int n = 100000;
        int m = 250000;

        int[][] ops = new int[m][2];
        for (int i = 0; i < m; i++) {
            ops[i] = new int[] {rnd.nextInt(n), rnd.nextInt(n)};
        }
        long t = System.nanoTime();
        Map<String, Object> r = run("dsu", request("dsu", n, ops, ""));
        List<Object> states = asList(r.get("states"));
        int[] last = ints(states.get(states.size() - 1));
        check("large dsu states count", states.size() == m);
        check("large dsu monotone final state", last[0] >= 1 && last[1] <= n && last[0] + last[1] - 1 <= n);
        report("dsu random n=100000 q=250000", t);

        int[][] weighted = new int[m][3];
        for (int i = 0; i < m; i++) {
            weighted[i] = new int[] {rnd.nextInt(n), rnd.nextInt(n), rnd.nextInt(2_000_000_001) - 1_000_000_000};
        }
        t = System.nanoTime();
        Map<String, Object> k = run("kruskal", request("kruskal", n, weighted, ""));
        report("kruskal random n=100000 m=250000", t);
        t = System.nanoTime();
        Map<String, Object> p = run("prim", request("prim", n, weighted, ""));
        report("prim random n=100000 m=250000", t);
        check("large kruskal equals prim cost", k.get("cost").equals(p.get("cost")));
        check("large kruskal equals prim components", k.get("components").equals(p.get("components")));
        int comps = ((Number) k.get("components")).intValue();
        check("large kruskal forest size", ints(k.get("edge_ids")).length == n - comps);
        check("large prim forest size", ints(p.get("edge_ids")).length == n - comps);
        check("large forest acyclic and costs sum (kruskal)", forestSumOk(n, weighted, k));
        check("large forest acyclic and costs sum (prim)", forestSumOk(n, weighted, p));

        int[][] extreme = new int[n - 1][3];
        for (int i = 0; i + 1 < n; i++) {
            extreme[i] = new int[] {i, i + 1, 1_000_000_000};
        }
        for (String task : new String[] {"kruskal", "prim"}) {
            r = run(task, request(task, n, extreme, ""));
            check("large " + task + " chain cost 99999e9", ((Number) r.get("cost")).longValue() == 99_999L * 1_000_000_000L);
        }

        int[][] chain = new int[n - 1][2];
        int[][] chainW = new int[n - 1][3];
        for (int i = 0; i + 1 < n; i++) {
            chain[i] = new int[] {i, i + 1};
            chainW[i] = new int[] {i, i + 1, -1_000_000_000};
        }
        t = System.nanoTime();
        r = run("scc", request("scc", n, chain, ""));
        check("deep chain scc: 100000 components", new HashSet<>(asList(r.get("component"))).size() == n && asList(r.get("condensation")).size() == n - 1);
        report("scc chain n=100000", t);
        t = System.nanoTime();
        r = run("topo", request("topo", n, chain, ""));
        int[] order = ints(r.get("order"));
        check("deep chain topo: order is 0..n-1", (Boolean) r.get("acyclic") && order[0] == 0 && order[n - 1] == n - 1);
        report("topo chain n=100000", t);
        t = System.nanoTime();
        r = run("dag", request("dag", n, chainW, "\"source\":0,"));
        List<Object> dist = asList(r.get("distance"));
        check("deep chain dag: distance -99999e9", ((Number) dist.get(n - 1)).longValue() == -99_999L * 1_000_000_000L);
        check("deep chain dag route length", Algorithms.route(0, n - 1, chainW, ints(r.get("parent_edge"))).size() == n);
        report("dag chain n=100000", t);

        int[][] cycleChain = new int[n][2];
        for (int i = 0; i < n; i++) {
            cycleChain[i] = new int[] {i, (i + 1) % n};
        }
        r = run("scc", request("scc", n, cycleChain, ""));
        check("deep cycle scc: one component", new HashSet<>(asList(r.get("component"))).size() == 1 && asList(r.get("condensation")).isEmpty());
        r = run("topo", request("topo", n, cycleChain, ""));
        check("deep cycle topo: cyclic", !(Boolean) r.get("acyclic") && asList(r.get("order")).isEmpty());

        int[][] randomDag = new int[m][3];
        for (int i = 0; i < m; i++) {
            int a = rnd.nextInt(n - 1);
            int b = a + 1 + rnd.nextInt(Math.min(50, n - 1 - a));
            randomDag[i] = new int[] {a, b, rnd.nextInt(2001) - 1000};
        }
        t = System.nanoTime();
        r = run("dag", request("dag", n, randomDag, "\"source\":0,"));
        report("dag random n=100000 m=250000", t);
        check("large random dag acyclic", (Boolean) r.get("acyclic"));
        check("large random dag witness consistent", dagWitnessConsistent(randomDag, 0, r));

        int[][] hiddenCycle = new int[50000 + 3][3];
        for (int i = 0; i < 50000; i++) {
            hiddenCycle[i] = new int[] {i, i + 1, 1};
        }
        hiddenCycle[50000] = new int[] {60000, 60001, 1};
        hiddenCycle[50001] = new int[] {60001, 60002, 1};
        hiddenCycle[50002] = new int[] {60002, 60000, 1};
        r = run("dag", request("dag", n, hiddenCycle, "\"source\":0,"));
        check("large dag cycle unreachable from source is reported", !(Boolean) r.get("acyclic")
                && asList(r.get("distance")).isEmpty() && asList(r.get("parent_edge")).isEmpty());
    }

    static boolean forestSumOk(int n, int[][] edges, Map<String, Object> out) {
        int[] ids = ints(out.get("edge_ids"));
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        long sum = 0;
        for (int id : ids) {
            int a = root(parent, edges[id][0]);
            int b = root(parent, edges[id][1]);
            if (a == b) {
                return false;
            }
            parent[a] = b;
            sum += edges[id][2];
        }
        return sum == ((Number) out.get("cost")).longValue();
    }

    static int root(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    static boolean dagWitnessConsistent(int[][] edges, int source, Map<String, Object> out) {
        List<Object> dist = asList(out.get("distance"));
        int[] parent = ints(out.get("parent_edge"));
        for (int v = 0; v < parent.length; v++) {
            if (dist.get(v) == null) {
                if (parent[v] != -1) {
                    return false;
                }
            } else if (v == source) {
                if (parent[v] != -1 || ((Number) dist.get(v)).longValue() != 0) {
                    return false;
                }
            } else {
                int id = parent[v];
                if (id < 0 || edges[id][1] != v || dist.get(edges[id][0]) == null
                        || ((Number) dist.get(edges[id][0])).longValue() + edges[id][2] != ((Number) dist.get(v)).longValue()) {
                    return false;
                }
            }
        }
        for (int[] e : edges) {
            if (dist.get(e[0]) != null) {
                if (dist.get(e[1]) == null || ((Number) dist.get(e[0])).longValue() + e[2] < ((Number) dist.get(e[1])).longValue()) {
                    return false;
                }
            }
        }
        return true;
    }

    static void report(String name, long startNanos) {
        System.out.printf("TIME %-40s %8.1f ms (build request, parse, run, write, re-parse)%n", name, (System.nanoTime() - startNanos) / 1e6);
    }
}
