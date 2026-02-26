# Metric Schema

This schema defines required metric names, types, and units for v1 observability.

| Metric | Type | Unit | Definition |
| --- | --- | --- | --- |
| `ssp_parmap_inflight` | gauge | tasks | Current in-flight `parMap` tasks. |
| `ssp_boundary_queue_depth` | gauge | items | Current queue depth at async boundary. |
| `ssp_boundary_producer_block_ms` | counter | ms | Cumulative producer blocked time at full boundary queues. |
| `ssp_late_event_dropped_total` | counter | events | Total late events dropped (`eventTs < watermark`). |
| `ssp_watermark_regression_total` | counter | events | Total regressing watermarks ignored. |
| `ssp_resource_close_fail_total` | counter | events | Total resource close failures. |
| `ssp_unhandled_error_total` | counter | events | Total unhandled stream/operator errors. |

## Notes

- Counters are monotonic.
- Gauges represent instantaneous values and should be sampled.
- Metric names are stable API for v1 dashboards/alerts.
