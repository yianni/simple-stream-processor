# ADR-0001: Operator Taxonomy and API Boundaries

- Status: Accepted
- Date: 2026-02-25

## Context

The pipeline API has grown without a formal taxonomy. This creates naming drift and unclear boundaries between stateless transforms, stateful operators, execution boundaries, and terminals.

## Decision

Define four operator families and keep public APIs within these boundaries:

- Stateless transforms: `map`, `filter`, `flatMap`, `take`
- Stateful transforms: `windowByCount`, event-time windows
- Execution boundaries: `asyncBoundary`, `parMap`
- Terminals: `Sink`, managed sink variants

All new operators must belong to one family and document ordering, error, and completion semantics.

## Invariants

- Every operator belongs to exactly one family.
- Public operator names must be stable and semantically descriptive.
- Every new operator ADR/update must state ordering, error, completion, and cancellation behavior.
- Experimental operators must be clearly marked and isolated from stable API paths.

## Consequences

- API evolution is constrained and reviewable.
- Operator behavior matrix can be tested by family.
- Ad-hoc operators are rejected unless a new family is justified.

## Alternatives Considered

- Flat operator namespace without families: rejected due to semantic drift risk.
- Split APIs per module upfront: deferred until core contracts stabilize.

## Operational Metrics

- Operator adoption by family (for API hygiene).
- Number of experimental operators promoted or removed.

## Rollout and Migration

- Existing operators are mapped to the four families with no behavior change.
- New operators require ADR amendment before merge.

## Non-Goals

- Distributed execution model
- Exactly-once processing guarantees
