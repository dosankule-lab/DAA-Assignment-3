# Assignment 3: Graph Algorithms — Analysis

Student: Dossankul Yerassyl, group SE-2515. Language: Java (`submission.json`: `{"language":"Java"}`). All algorithms are in `src/Java/Algorithms.java`.

## 1. Build, run and test commands

```sh
python tools/run.py --setup
python tools/run.py --check
python tools/run.py --input tests/my_cases.jsonl

mkdir -p .build/tests
javac --release 17 -encoding UTF-8 -d .build/tests src/Java/*.java tests/TestSuite.java tests/Bench.java
java -Xmx384m -cp .build/tests TestSuite
java -Xmx384m -cp .build/tests Bench
```

Results obtained: `--setup` passes; `--check` passes all 36 public cases and the two-batch state-reset check; `TestSuite` reports `passed=9494 failed=0` (`tests/results.txt`). Test details are in `tests/README.md`.

## 2. Shared design

- Edges are stored in compressed adjacency arrays (`start`, `target`, `id`), built by counting sort in `O(n+m)`. The original edge ID is kept next to every adjacency entry, so sorting or reordering never loses IDs.
- Every DFS is iterative: an explicit `int[]` stack plus a per-vertex `cursor` that remembers the next neighbour to examine. A suspended vertex resumes where it stopped, so neighbours are never rescanned. Recursion depth is therefore never a problem: chains of 100,000 vertices pass (`scc chain`, `topo chain`, `dag chain` in `tests/results.txt`).
- State reset: each method allocates all its arrays (parents, sizes, visited flags, heaps, cursors, result containers) inside the call and uses no static mutable fields. `TestSuite.stateResetCases` runs a mixed batch of eight requests three times in one process and requires identical outputs; `tools/run.py --check` does the same for the public batch.
- Totals and distances use `long`; weights are within ±10^9, so a sum over 250,000 edges (2.5·10^14) fits easily. Tests with 200 edges of weight ±10^9 give ±2·10^11, beyond 32 bits.

## 3. Methods

### DSU (`dsu`)

- **Steps:** each vertex starts as its own root with size 1. For each pair, find both roots with path compression (two-pass: locate root, then repoint every vertex on the path); if different, attach the smaller tree to the larger and add sizes. Record `[components, largest]`.
- **Invariant:** `parent` forms a forest whose trees are exactly the current components; `size[root]` is the number of vertices in that tree; `components` equals the number of roots; `largest` equals the maximum size seen so far (sizes only grow, so the running maximum equals the current maximum).
- **Correctness:** a union merges exactly two components when the roots differ and does nothing otherwise, so repeated unions and self-unions leave the partition unchanged. Rank is not used, so size and rank cannot be confused.
- **Boundaries:** `n=0` gives `states=[]` (`largest` starts at 0); `n>0` starts with `largest=1`; no operations gives `[]`.
- **Complexity:** `O(n + q α(n))` time, `O(n+q)` memory including states.

### Kruskal (`kruskal`)

- **Steps:** build one `long` key per edge, `(w + 10^9) << 18 | id`, and sort the primitive array. Scan in order; accept an edge when its endpoints have different DSU roots, then union.
- **Invariant:** after processing a prefix of the sorted edges, the accepted edges are a minimum spanning forest of that prefix, and DSU components match the forest's components.
- **Correctness:** the cut property: the lightest edge crossing between two different components is safe, and an edge joining two vertices already connected closes a cycle and is never needed. Ties are broken by ID, which is harmless. Self-loops always have equal roots and are skipped. Edge IDs come from the key, so they are original IDs.
- **Boundaries:** `n=0` gives cost 0, no edges, 0 components; isolates count as components (`components = n - accepted`); negative weights are handled by the offset; parallel edges are tested with ties.
- **Complexity:** `O(n + m log m)` time, `O(n+m)` memory. IDs fit in 18 bits because `m <= 250000 < 262144`.

### Prim (`prim`)

- **Steps:** build adjacency without self-loops. For each unvisited vertex, start a new component, mark it visited and push its edges as keys `(w + 10^9) << 18 | id` into a lazy `PriorityQueue<Long>`. Pop the lightest key; if both endpoints are visited, discard it; otherwise visit the new endpoint, record the edge and push the new vertex's edges to unvisited neighbours. When the heap is empty the component is complete; continue with the next unvisited vertex.
- **Invariant:** the recorded edges form a minimum spanning tree of the visited part of the current component, and the heap contains every edge leaving the visited set (plus stale edges that are discarded on pop).
- **Correctness:** the popped valid edge is the lightest edge crossing the cut (visited, unvisited), so it is safe by the cut property. Restarting for each unvisited vertex covers every component, isolates included.
- **Boundaries:** same as Kruskal; `components` is the number of restarts.
- **Complexity:** lazy heap with at most `2m` pushes: `O(n + m log m)` time, `O(n+m)` memory.

### Kosaraju SCC (`scc`)

- **Steps:** (1) iterative DFS on the graph, appending vertices to `finish` when they finish; (2) iterative DFS on the transpose, taking roots in decreasing finish order; every vertex reached from an unlabelled root gets the next label; (3) for every edge with different labels, add the pair once, using a `HashSet<Long>` keyed by `a*n+b`.
- **Invariant:** in pass 2, when a root is taken, all vertices already labelled belong to completed SCCs, and the unlabelled vertices reachable from this root in the transpose are exactly its SCC.
- **Correctness:** the vertex finishing last in a component of the condensation lies in a source SCC of the original graph, so it is a sink SCC of the transpose; searching from it in the transpose cannot leave its SCC. Removing it and repeating gives the standard Kosaraju argument.
- **Boundaries:** `n=0` gives two empty arrays; isolates get their own labels; self-loops and duplicate edges are internal or deduplicated; the hash key uses `max(n,1)` as multiplier and fits `long`.
- **Complexity:** `O(n+m)` expected time and memory.

### Topological order (`topo`)

- **Steps:** three-state iterative DFS (0 unvisited, 1 active, 2 finished). An edge to an active vertex means a cycle: return `acyclic=false`, `order=[]`. When a vertex finishes, write it at position `--placed` from the end of the order array, which is the reverse finish order.
- **Invariant:** the active vertices are exactly the DFS stack, forming a path; finished vertices have all their out-neighbours finished and placed after them.
- **Correctness:** an edge to a finished vertex is a forward or cross edge and cannot close a cycle; an edge to an active vertex closes a cycle with the stack path. If no such edge exists, every edge `u->v` has `v` finished before `u`, so `u` comes before `v` in the reversed finish order. Self-loops hit the active state immediately.
- **Boundaries:** `n=0` gives `acyclic=true`, `order=[]`; every component is a DFS root; a cycle in a later component is detected.
- **Complexity:** `O(n+m)` time and memory.

### DAG shortest paths (`dag`)

- **Steps:** run the same topological routine over the whole graph; if it reports a cycle (even one unreachable from the source), return `false` and two empty arrays. Otherwise set `distance[source]=0`, and for vertices in topological order that already have a distance, relax every outgoing edge, storing the original edge ID in `parent_edge`.
- **Invariant:** when a vertex is processed, its distance is final, because all its predecessors precede it in the order and were already relaxed.
- **Correctness:** by induction along the order, each distance is the minimum over incoming edges from reachable vertices. Negative weights are safe because there are no cycles and each edge is relaxed once. Unreachable vertices are never relaxed from (`null` distance check), so a distance of `-1` is never confused with unreachable. Tied or parallel edges keep the first strict improvement.
- **Boundaries:** source has `parent_edge=-1`; unreachable vertices have `null` and `-1`; `n>=1`; self-loop is a cycle; distances beyond 32 bits are tested.
- **Complexity:** `O(n+m)` time and memory.

## 4. Route-reconstruction helper

`Algorithms.route(int source, int target, int[][] edges, int[] parentEdge)` starts at the target and follows `parent_edge[v]` to `edges[id][0]` until it reaches the source, then reverses the list. **No-route result: an empty list.** It also returns an empty list if a parent edge is missing, does not end at the current vertex, or the walk is longer than `n` vertices (corrupt input). Time and output space are `O(L)` for a route of `L` vertices.

Actual calls (from `tests/results.txt`, verified in `TestSuite.routeHelperCases`):

| Case | Graph | `parent_edge` | Result | Check |
|---|---|---|---|---|
| Reachable target | edges `0->1 (3)`, `0->2 (8)`, `1->2 (-7)`, `2->3 (4)`, n=5, source 0 | `[-1,0,2,3,-1]` | `route(0,3) = [0,1,2,3]` using edge IDs 0, 2, 3 | every selected edge starts at the previous vertex and ends at the next; cost `3-7+4=0` equals `distance[3]=0` |
| Source | same | same | `route(0,0) = [0]` | no edges used |
| Unreachable target | same | same | `route(0,4) = []` | `distance[4]=null`, `parent_edge[4]=-1` |
| Tied / parallel edges | `0->1 (4)` twice (IDs 0, 1), `0->2 (2)`, `2->1 (2)`, `1->3 (1)`, source 0 | `[-1,0,2,4]` | `route(0,3) = [0,1,3]` using edge IDs 0 and 4 | `distance=[0,4,2,5]`; cost `4+1=5`; ID 1 and the route through vertex 2 are equally good, and the checker accepts them |

## 5. Benchmark: Kruskal vs Prim

- **Graphs:** random undirected graphs with weights in `[-10^6, 10^6]`, generated in `tests/Bench.java` with `java.util.Random(12345 + n)`; loops and parallel edges are possible. Both methods run on the same parsed request for each size.
- **Verification first:** for every size, Kruskal and Prim must return the same cost and component count; otherwise the benchmark aborts. Forest validity is checked in `TestSuite` (acyclic, `n-c` edges, cost equals sum of weights, equal costs on the full-size graph).
- **Method:** 5 untimed warm-up runs per method, then 5 timed runs per method, alternating Kruskal and Prim; median reported. Timer: `System.nanoTime()`.
- **Timing boundary (same for both methods):** one call of `Algorithms.kruskal(request)` or `Algorithms.prim(request)` on an already parsed request. It includes converting the request to `int[][]`, building adjacency (Prim), sorting or heap work, DSU, and building the result map. It excludes compilation, request generation, JSON parsing and JSON output.
- **Environment:** Intel Xeon @ 2.80 GHz, 2 vCPUs, 8 GB RAM, Linux 6.18, OpenJDK 21.0.12 (classes compiled with `--release 17`), `-Xmx384m`.
- **Command:** `java -Xmx384m -cp .build/tests Bench` (output saved in `tests/benchmark_results.txt`).

| n | m | method | run times (ms) | median (ms) | cost | components |
|---|---|--------|----------------|-------------|------|------------|
| 2000 | 5000 | Kruskal | 3.22, 1.55, 1.35, 1.30, 1.42 | 1.42 | -1058484667 | 13 |
| 2000 | 5000 | Prim | 2.12, 2.14, 2.20, 2.69, 1.77 | 2.14 | -1058484667 | 13 |
| 20000 | 50000 | Kruskal | 23.68, 16.83, 16.40, 14.85, 13.18 | 16.40 | -10593590805 | 134 |
| 20000 | 50000 | Prim | 38.27, 48.80, 42.80, 38.56, 41.45 | 41.45 | -10593590805 | 134 |
| 100000 | 250000 | Kruskal | 92.00, 91.90, 119.98, 83.02, 60.02 | 91.90 | -52802449317 | 681 |
| 100000 | 250000 | Prim | 491.86, 443.56, 328.81, 273.29, 345.19 | 345.19 | -52802449317 | 681 |

**Observations.** Both methods agree on cost and components at every size. In these runs Kruskal was faster at all three sizes, by about 1.5× at the smallest and about 3.8× at the largest by median. Both are `O(m log m)` asymptotically, so the difference is a constant factor. A plausible explanation, which these measurements do not isolate, is that Kruskal sorts a primitive `long[]`, whereas this Prim builds adjacency arrays and uses a boxed `PriorityQueue<Long>` with up to `2m` pushes.

**Limitations.** Only one graph per size, one density (m ≈ 2.5n) and one machine with 2 vCPUs; five timed runs are few, and the run-to-run spread at the largest size is large (Prim 273–492 ms), probably from JIT compilation and garbage collection. An indexed heap or a primitive heap would change Prim's constant. The results should not be generalised to dense graphs or other languages.

## 6. Sources and AI assistance

- Algorithms follow the course lectures and Cormen, Leiserson, Rivest & Stein, *Introduction to Algorithms* (4th ed.), chapters on disjoint sets, minimum spanning trees and strongly connected components.
- AI assistance: Claude (Anthropic) was used to generate the first version of the Java implementation, the test suite, the benchmark and the draft of this document. The code and tests were compiled and run in the Claude environment, and the numbers above come from those runs. I remain responsible for the submission; I must review, understand and be able to explain every part before submitting, and re-run the commands on my own machine.
