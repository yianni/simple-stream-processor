# ADR Acceptance Criteria

An ADR is promoted from `Proposed` to `Accepted` when all checks below pass.

## Required Evidence

- At least one merged implementation PR references the ADR.
- Behavior described in ADR invariants is covered by automated tests.
- Failure and cancellation behavior is covered by tests where applicable.
- Documentation and examples are updated to reflect implemented behavior.

## Review Requirements

- Author sign-off confirming implementation matches ADR decision text.
- One additional technical reviewer sign-off on semantics and test coverage.

## Quality Gates

- `sbt test` green.
- Stress suite minimums from `docs/adr/README.md` executed and green for relevant ADRs.
- No unresolved TODOs in changed files related to ADR scope.

## Promotion Procedure

1. Open a docs PR that updates ADR status from `Proposed` to `Accepted`.
2. Link merged implementation PRs and test evidence.
3. Merge status update PR after review requirements are satisfied.
