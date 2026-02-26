# P2 Execution Board

## Objective

Close the final stabilization scope before `v0.2.0` by hardening stress invariants, performance visibility, and ADR evidence traceability.

## Work Items

1. Stress invariants in CI
   - Add a reusable stress harness API.
   - Add deterministic stress invariant tests (bounded queue depth, completion under target, no boundary thread leaks).
   - CI must run these tests explicitly.

2. Performance baseline reporting
   - Add a baseline runner that prints throughput/latency snapshots for the canonical pipeline.
   - Keep output machine-readable enough for future regression parsing.

3. ADR evidence traceability
   - Publish ADR evidence matrix mapping ADR invariants to concrete test names/paths.
   - Ensure matrix references both unit and stress validations.

## Acceptance Criteria

- `sbt -Dsbt.supershell=false test` is green.
- `sbt -Dsbt.supershell=false "Test / testOnly SimpleStreamProcessor.StressInvariantTest"` is green.
- `sbt -Dsbt.supershell=false "Test / runMain SimpleStreamProcessor.PerformanceBaselineReport"` runs successfully.
- CI workflow includes explicit stress invariant job step.
- `docs/adr/evidence-matrix.md` exists and covers ADR-0001 through ADR-0007.
