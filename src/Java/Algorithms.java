import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class Algorithms {
    private static final long WEIGHT_OFFSET = 1_000_000_000L;
    private static final int ID_BITS = 18;
    private static final long ID_MASK = (1L << ID_BITS) - 1;

    private Algorithms() {
    }

    static Map<String, Object> dsu(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] operations = Input.operations(input);

        int[] parent = new int[n];
        int[] size = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            size[i] = 1;
        }
        int components = n;
        int largest = n > 0 ? 1 : 0;
        int[][] states = new int[operations.length][];
        for (int i = 0; i < operations.length; i++) {
            int a = find(parent, operations[i][0]);
            int b = find(parent, operations[i][1]);
            if (a != b) {
                if (size[a] < size[b]) {
                    int t = a;
                    a = b;
                    b = t;
                }
                parent[b] = a;
                size[a] += size[b];
                components--;
                largest = Math.max(largest, size[a]);
            }
            states[i] = new int[] {components, largest};
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("states", states);
        return result;
    }

    private static int find(int[] parent, int x) {
        int root = x;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[x] != root) {
            int next = parent[x];
            parent[x] = root;
            x = next;
        }
        return root;
    }

    static Map<String, Object> kruskal(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        long[] keys = new long[edges.length];
        for (int i = 0; i < edges.length; i++) {
            keys[i] = ((edges[i][2] + WEIGHT_OFFSET) << ID_BITS) | i;
        }
        Arrays.sort(keys);

        int[] parent = new int[n];
        int[] size = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            size[i] = 1;
        }
        int components = n;
        long cost = 0;
        List<Integer> chosen = new ArrayList<>();
        for (long key : keys) {
            int id = (int) (key & ID_MASK);
            int a = find(parent, edges[id][0]);
            int b = find(parent, edges[id][1]);
            if (a == b) {
                continue;
            }
            if (size[a] < size[b]) {
                int t = a;
                a = b;
                b = t;
            }
            parent[b] = a;
            size[a] += size[b];
            components--;
            cost += edges[id][2];
            chosen.add(id);
        }
        return forest(cost, chosen, components);
    }

    static Map<String, Object> prim(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        int[] start = new int[n + 1];
        for (int[] e : edges) {
            if (e[0] != e[1]) {
                start[e[0] + 1]++;
                start[e[1] + 1]++;
            }
        }
        for (int i = 0; i < n; i++) {
            start[i + 1] += start[i];
        }
        int[] adjacent = new int[start[n]];
        int[] fill = Arrays.copyOf(start, n);
        for (int id = 0; id < edges.length; id++) {
            int u = edges[id][0];
            int v = edges[id][1];
            if (u != v) {
                adjacent[fill[u]++] = id;
                adjacent[fill[v]++] = id;
            }
        }

        boolean[] visited = new boolean[n];
        PriorityQueue<Long> heap = new PriorityQueue<>();
        int components = 0;
        long cost = 0;
        List<Integer> chosen = new ArrayList<>();
        for (int root = 0; root < n; root++) {
            if (visited[root]) {
                continue;
            }
            components++;
            visited[root] = true;
            pushEdges(root, start, adjacent, edges, visited, heap);
            while (!heap.isEmpty()) {
                int id = (int) (heap.poll() & ID_MASK);
                int next = visited[edges[id][0]] ? edges[id][1] : edges[id][0];
                if (visited[next]) {
                    continue;
                }
                visited[next] = true;
                cost += edges[id][2];
                chosen.add(id);
                pushEdges(next, start, adjacent, edges, visited, heap);
            }
        }
        return forest(cost, chosen, components);
    }

    private static void pushEdges(int vertex, int[] start, int[] adjacent, int[][] edges,
                                  boolean[] visited, PriorityQueue<Long> heap) {
        for (int k = start[vertex]; k < start[vertex + 1]; k++) {
            int id = adjacent[k];
            int other = edges[id][0] == vertex ? edges[id][1] : edges[id][0];
            if (!visited[other]) {
                heap.add(((edges[id][2] + WEIGHT_OFFSET) << ID_BITS) | id);
            }
        }
    }

    private static Map<String, Object> forest(long cost, List<Integer> chosen, int components) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cost", cost);
        result.put("edge_ids", chosen);
        result.put("components", components);
        return result;
    }

    private static int[][] buildGraph(int n, int[][] edges, boolean reversed) {
        int[] start = new int[n + 1];
        for (int[] e : edges) {
            start[(reversed ? e[1] : e[0]) + 1]++;
        }
        for (int i = 0; i < n; i++) {
            start[i + 1] += start[i];
        }
        int[] target = new int[edges.length];
        int[] ids = new int[edges.length];
        int[] fill = Arrays.copyOf(start, n);
        for (int id = 0; id < edges.length; id++) {
            int from = reversed ? edges[id][1] : edges[id][0];
            int to = reversed ? edges[id][0] : edges[id][1];
            target[fill[from]] = to;
            ids[fill[from]] = id;
            fill[from]++;
        }
        return new int[][] {start, target, ids};
    }

    static Map<String, Object> scc(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        int[][] forward = buildGraph(n, edges, false);
        int[][] transpose = buildGraph(n, edges, true);

        int[] finish = new int[n];
        int finished = 0;
        boolean[] seen = new boolean[n];
        int[] stack = new int[n];
        int[] cursor = new int[n];
        for (int root = 0; root < n; root++) {
            if (seen[root]) {
                continue;
            }
            int top = 0;
            stack[top++] = root;
            seen[root] = true;
            cursor[root] = forward[0][root];
            while (top > 0) {
                int u = stack[top - 1];
                if (cursor[u] < forward[0][u + 1]) {
                    int v = forward[1][cursor[u]++];
                    if (!seen[v]) {
                        seen[v] = true;
                        cursor[v] = forward[0][v];
                        stack[top++] = v;
                    }
                } else {
                    finish[finished++] = u;
                    top--;
                }
            }
        }

        int[] component = new int[n];
        Arrays.fill(component, -1);
        int labels = 0;
        for (int i = n - 1; i >= 0; i--) {
            int root = finish[i];
            if (component[root] != -1) {
                continue;
            }
            int top = 0;
            stack[top++] = root;
            component[root] = labels;
            while (top > 0) {
                int u = stack[--top];
                for (int k = transpose[0][u]; k < transpose[0][u + 1]; k++) {
                    int v = transpose[1][k];
                    if (component[v] == -1) {
                        component[v] = labels;
                        stack[top++] = v;
                    }
                }
            }
            labels++;
        }

        HashSet<Long> pairs = new HashSet<>();
        List<int[]> condensation = new ArrayList<>();
        for (int[] e : edges) {
            int a = component[e[0]];
            int b = component[e[1]];
            if (a != b && pairs.add((long) a * Math.max(n, 1) + b)) {
                condensation.add(new int[] {a, b});
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("component", component);
        result.put("condensation", condensation);
        return result;
    }

    private static int[] topologicalOrder(int n, int[][] edges) {
        int[][] graph = buildGraph(n, edges, false);
        byte[] state = new byte[n];
        int[] cursor = new int[n];
        int[] stack = new int[n];
        int[] order = new int[n];
        int placed = n;
        for (int root = 0; root < n; root++) {
            if (state[root] != 0) {
                continue;
            }
            int top = 0;
            stack[top++] = root;
            state[root] = 1;
            cursor[root] = graph[0][root];
            while (top > 0) {
                int u = stack[top - 1];
                if (cursor[u] < graph[0][u + 1]) {
                    int v = graph[1][cursor[u]++];
                    if (state[v] == 1) {
                        return null;
                    }
                    if (state[v] == 0) {
                        state[v] = 1;
                        cursor[v] = graph[0][v];
                        stack[top++] = v;
                    }
                } else {
                    state[u] = 2;
                    order[--placed] = u;
                    top--;
                }
            }
        }
        return order;
    }

    static Map<String, Object> topo(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        int[] order = topologicalOrder(n, edges);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("acyclic", order != null);
        result.put("order", order != null ? order : new int[0]);
        return result;
    }

    static Map<String, Object> dag(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int source = Input.source(input);
        int[][] edges = Input.edges(input);

        Map<String, Object> result = new LinkedHashMap<>();
        int[] order = topologicalOrder(n, edges);
        if (order == null) {
            result.put("acyclic", false);
            result.put("distance", new Long[0]);
            result.put("parent_edge", new int[0]);
            return result;
        }

        int[][] graph = buildGraph(n, edges, false);
        Long[] distance = new Long[n];
        int[] parentEdge = new int[n];
        Arrays.fill(parentEdge, -1);
        distance[source] = 0L;
        for (int u : order) {
            if (distance[u] == null) {
                continue;
            }
            for (int k = graph[0][u]; k < graph[0][u + 1]; k++) {
                int v = graph[1][k];
                int id = graph[2][k];
                long candidate = distance[u] + edges[id][2];
                if (distance[v] == null || candidate < distance[v]) {
                    distance[v] = candidate;
                    parentEdge[v] = id;
                }
            }
        }
        result.put("acyclic", true);
        result.put("distance", distance);
        result.put("parent_edge", parentEdge);
        return result;
    }

    static List<Integer> route(int source, int target, int[][] edges, int[] parentEdge) {
        List<Integer> path = new ArrayList<>();
        int current = target;
        path.add(current);
        while (current != source) {
            int id = parentEdge[current];
            if (id < 0 || edges[id][1] != current || path.size() > parentEdge.length) {
                return new ArrayList<>();
            }
            current = edges[id][0];
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }
}
