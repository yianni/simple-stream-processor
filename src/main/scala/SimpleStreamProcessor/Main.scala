package SimpleStreamProcessor

import SimpleLazyListProcessor.{MultiplyByTwoNode, PrintNode}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

object Main {
  def main(args: Array[String]): Unit = {
//    val jobManager = new JobManager(1, 1)
//    val source = new IntegerSource
//    val multiplyNode = new MultiplyByTwoNode
//    val printNode = new PrintNode
//
//    // Connect nodes and run the job
//    source.connectTo(multiplyNode).connectTo(printNode)
//    val future = jobManager.runJob(source, multiplyNode)
//
//    // This will cause the main method to wait for the Future to complete. Adjust the duration as needed.
//    Await.result(future, 44.seconds)


    val dataStream = new DataStream[Int]

    val multiplyNode = new MultiplyByTwoNode
    val printNode = new PrintNode

    dataStream.connectTo(multiplyNode).connectTo(printNode)

    (1 to 100).foreach(dataStream.addData)

    val jobManager = new JobManager(1, 1)
    val streamStart = dataStream.start()

    val future = jobManager.runJob(dataStream, multiplyNode)

    // We call start on dataStream to process the data

    // This will cause the main method to wait for the Future to complete. 
    Await.result(future, 60.seconds)
    Await.result(streamStart, 60.seconds)
    
  }
}
