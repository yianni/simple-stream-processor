# Simple Stream Processor

This project demonstrates a basic stream processing framework implemented in Scala. The framework provides a series of
transformation methods similar to those found in functional programming, such as `map`, `filter`, and `flatMap`.

## Features

* `Source`: A stream producer, responsible for generating a `Stream` of data.
* `Pipe`: Represents a transformation step in the pipeline. Takes an upstream `Node` and a function that applies to each
  item in the stream.
* `FilterPipe`: A specific type of `Pipe` that filters the stream according to a predicate.
* `Sink`: A terminal node in the stream that aggregates or processes the final stream.
* `Node`: Abstract representation of a stream processing step, which can be a `Source`, `Pipe`, or `FilterPipe`.
* `Fluent` Interface: Each Node provides `map`, `filter`, and `flatMap` methods for chaining transformations, and
  a `toSink` method to generate the terminal `Sink` node.

## Example Usage

```scala
val source = Source[Int](LazyList.fromList((1 to 9999).toList)).withName("source")
val sink = source.map((i: Int) => i * 2).withName("pipe1")
  .filter((i: Int) => i % 2 == 0).withName("pipe2")
  .toSink((acc: Int, i: Int) => acc + i, 0).withName("sink")
```

This code creates a stream of integers from 1 to 9999, multiplies each number by 2, filters out any odd numbers, and
finally sums up the remaining numbers in the stream.

## Testing

Tests are written using ScalaTest. To run them, use the `test` command in sbt.

## Future Work

This is a simple demonstration of a stream processing pipeline and as such, has many areas that could be expanded upon,
such as:

* Support for more types of Node and transformations.
* Parallel processing of the stream.
* Error handling and recovery.
* Backpressure between nodes.
* More robust resource management for `Source` and `Sink`.
* Support for windowed operations on the stream.
* Time watermarks.

This project serves as a good starting point for anyone interested in learning about or building a stream processing
system in Scala.

## Architecture Decisions

Staff-level design contracts are tracked as ADRs in `docs/adr/`.
