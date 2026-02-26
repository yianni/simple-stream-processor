# Event-Time Example

This example demonstrates watermark-driven closure with late-event dropping.

## Configuration

- Window size: 5 ms (tumbling)
- Current watermark starts at `Long.MinValue`
- Late policy: drop when `eventTs < currentWatermark`

## Event Sequence

1. Event `a@1`
2. Event `b@3`
3. Watermark `3`
4. Event `c@7`
5. Watermark `8`
6. Event `late-x@4` (late, dropped because `4 < 8`)

## Result

- Window `[0,5)` closes at watermark `8` and emits `a,b`.
- Window `[5,10)` remains open until a watermark `>= 10` arrives.
- Late-drop counter increments by `1`.
