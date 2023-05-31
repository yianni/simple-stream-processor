package SimpleStreamProcessor

import SimpleLazyListProcessor.{MultiplyByTwoNode, PrintNode}

import scala.concurrent.ExecutionContext

object Main {
  def main(args: Array[String]): Unit = {
    val dataStreamEC = ExecutionContext.fromExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
    val jobManagerEC = ExecutionContext.fromExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
    val fooEc = ExecutionContext.fromExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))


    val jobManager = new JobManager(1, 1)(jobManagerEC)
    val dataStream = new DataStream[Int]()(dataStreamEC)
    val multiplyNode = new MultiplyByTwoNode
    val printNode = new PrintNode

    // Connect nodes
    dataStream.connectTo(multiplyNode).connectTo(printNode)

    // Start the stream
    dataStream.start()(dataStreamEC)

    // Add initial data

    def infiniteList(start: Int): LazyList[Int] = {
      start #:: infiniteList(start + 1)
    }

//    val myList = infiniteList(1).foreach{ i => 
    for (i <- 1 to 10) {
      dataStream.addData(i)

//            dataStream.addData(Random.nextInt(100))
    }

    dataStream.signalEndOfStream()

    // Run the job
    val future = jobManager.runJob(dataStream, multiplyNode)

    // After a delay, add more data
    //    Future {
    //      Thread.sleep(1000) // simulate delay
    //      for (i <- 1 to 10) {
    //        dataStream.addData(Random.nextInt(100))
    //      }
    //      dataStream.signalEndOfStream()
    //    }

    //        val allFutures = Future.sequence(Seq(future, streamStart))

    // This will cause the main method to wait for both Futures to complete.
    future.onComplete {
      case scala.util.Success(_) => println("Successful")
      case scala.util.Failure(e) => println(s"Failure: ${e.getMessage}")
    }(fooEc)


  }
}
