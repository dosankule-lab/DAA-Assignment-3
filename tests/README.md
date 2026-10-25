# Tests

Run all commands from the repository root.

| File | Purpose |
|---|---|
| `TestSuite.java` | Self-checking tests with independent oracles, checker accept/reject cases, route helper calls, state reset, full-limit and deep inputs |
| `results.txt` | Actual output of `TestSuite` |
| `my_cases.jsonl` | Hand-written requests for the JSON Lines launcher |
| `my_cases_output.txt` | Actual launcher output for `my_cases.jsonl` |
| `Bench.java` | Kruskal vs Prim benchmark |
| `benchmark_results.txt` | Actual benchmark output |

## Commands

```sh
python tools/run.py --setup
python tools/run.py --check
python tools/run.py --input tests/my_cases.jsonl

mkdir -p .build/tests
javac --release 17 -encoding UTF-8 -d .build/tests src/Java/*.java tests/TestSuite.java tests/Bench.java
java -Xmx384m -cp .build/tests TestSuite
java -Xmx384m -cp .build/tests Bench
```

`src/Java/Main.java` is compiled together with the tests; its `main` is not used by them.

## How answers are checked

Non-unique answers are checked by validity, not by equality.

- **Kruskal / Prim:** edge IDs are distinct and in range, no self-loop is used, the chosen edges form no cycle, the count is `n - c`, the cost equals the sum of chosen weights and equals the optimum. For `n <= 7` the optimum comes from brute force over all edge subsets; for large graphs Kruskal and Prim must agree.
- **SCC:** two vertices share a label exactly when each reaches the other (Floyd–Warshall closure); the condensation must equal the set of distinct cross-component pairs, with no duplicates.
- **Topological order:** `acyclic` must match a closure-based cycle oracle; for acyclic graphs every vertex appears once and every edge goes forward; for cyclic graphs `order` is empty.
- **DAG:** distances match Bellman–Ford; unreachable vertices have `null` and `-1`; every parent edge ends at its vertex, satisfies `dist[u] + w = dist[v]`, and following parents reaches the source. A cyclic graph returns `false` and two empty arrays.
- **DSU:** every state is compared with a closure computed for the prefix of operations.

The checkers themselves are tested: they accept alternative valid answers (different edge ID order, different SCC labels, different valid topological orders, either parallel edge as parent) and reject an edge set with a cycle, a repeated ID, a non-optimal forest, an order violating an edge, a split SCC, a duplicate condensation pair, a parent edge ending at another vertex and a wrong distance.

## Hand-written cases (`my_cases.jsonl`)

| Line | Expected answer and justification | Matches actual |
|---|---|---|
| 1 | DSU example: `[[3,2],[2,3],[2,3],[2,3]]`; third and fourth operations change nothing | yes |
| 2 | `n=0`: `states=[]` | yes |
| 3 | Kruskal: vertex 3 is isolated, so 2 components; cheapest edges 1 (w=2) and 2 (w=3) give cost 5 | yes |
| 4 | Prim: same graph, same cost 5 and 2 components; IDs `[2,1]` are the same set in another order | yes |
| 5 | Kruskal: loop `0-0` is never selected; vertex 2 is isolated; best of the parallel edges is ID 2 (w=-3); cost -3, 2 components | yes |
| 6 | Prim: same graph, same answer | yes |
| 7 | SCC: `{0,1}`, `{2}`, `{3}`; condensation `{0,1}->{2}` and `{2}->{3}`, with the duplicate pair from edges `1->2` and `0->2` listed once | yes |
| 8 | SCC: loop on 0 is internal; `{1,2}` is one SCC; duplicate edges collapse; only `3->0` crosses components | yes |
| 9 | Topological order: `[1,0,2,3]` puts 2 after both 0 and 1 and 3 after 2 | yes |
| 10 | Topological order: cycle `3<->4` in a second component gives `false`, `[]`; forward edge `0->2` to a finished vertex is not a cycle | yes |
| 11 | DAG example: `0->1->2` costs `3-7=-4` against 8 directly; `0->1->2->3` costs 0; vertex 4 unreachable | yes |
| 12 | DAG: the cycle `2<->3` is unreachable from source 0 but still makes the answer `false`, `[]`, `[]` | yes |
| 13 | DAG: parallel edges 0 and 1 and the route through vertex 2 all cost 4; any of them is a valid parent | yes |
