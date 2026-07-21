# ihawu-benchmark

JMH benchmarks comparing the masking backends (issue #102). **Not a published artifact** — the module
exists so the performance claims in [ADR 0008](../docs/adr/0008-kotlinx-serialization-masking.md) rest on
authoritative JMH numbers rather than a nanoTime harness.

## What it measures

Four benchmarks, each on a `small` payload (one `@IhawuResource` employee with masked fields by
strategy/type, a nested resource, and a map of resources) and a `large` one (a list of 100 of them):

| Benchmark | What it is |
| --- | --- |
| `plainJackson` | Jackson serialization, no Ihawu — baseline. |
| `ihawuJackson` | `IhawuModule` masking (streaming writer-wrapping). |
| `plainKotlinx` | kotlinx.serialization, no Ihawu — baseline. |
| `ihawuKotlinx` | `maskingSerializer` (JsonElement rewrite). |

The plain baselines answer the headline question — **what masking costs over plain serialization** — and
the ihawu pair gives the cross-backend comparison. Throughput (`ops/s`) and allocation
(`gc.alloc.rate.norm`, B/op via the `gc` profiler) come from one run.

## Run it

```bash
./gradlew :benchmark:jmh
```

Takes a few minutes (2 forks × 5×1s warmup + 5×1s measurement per case). Results print to the console and
land in `benchmark/build/results/jmh/results.txt`. Benchmarks are **not** run in CI — too slow and too
noisy — but `check` compiles them so they cannot rot.

The recorded numbers (with the run environment) live in the
[ADR 0008 Performance section](../docs/adr/0008-kotlinx-serialization-masking.md#performance).
