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

## Consequences

- API evolution is constrained and reviewable.
- Operator behavior matrix can be tested by family.
- Ad-hoc operators are rejected unless a new family is justified.

## Non-Goals

- Distributed execution model
- Exactly-once processing guarantees
