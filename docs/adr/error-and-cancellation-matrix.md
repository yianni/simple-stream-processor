# Error and Cancellation Matrix

This matrix defines runtime precedence across processing, cancellation, and close behavior.

| Scenario | Expected behavior |
| --- | --- |
| Processing succeeds, close succeeds | Successful completion |
| Processing fails, close succeeds | Raise processing error |
| Processing succeeds, close fails | Raise close error |
| Processing fails, close fails | Raise processing error; attach close error as suppressed |
| Cancellation requested before start | No work starts; close owned resources |
| Cancellation during processing | Stop scheduling new work; best-effort interrupt; close resources |
| Cancellation while producer blocked on backpressure | Boundary shutdown unblocks producer; close resources |
| Cancellation with pending `parMap` tasks | Do not schedule additional tasks; ignore pending outputs; close resources |

## Notes

- Cancellation is cooperative in v1 and not a hard kill guarantee.
- Resource closing is always attempted, even after cancellation or processing failure.
- Error precedence follows ADR-0005.
