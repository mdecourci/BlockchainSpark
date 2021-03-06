import akka.actor.{Props, ActorSystem}
import com.typesafe.config.ConfigFactory
import model.bitcoin.{BlockchainMessage, UnconfirmedTransaction}
import org.apache.spark.SparkConf
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.{StreamingContext, Seconds}

object Main {
  val config = ConfigFactory.load()
  implicit val system = ActorSystem(config.getString("app.name"))

  def main(args: Array[String]) {

    system.logConfiguration()
    val bitcoinWebsocket = new BitcoinWebsocket(system)


    val sparkConf = new SparkConf()
      .setMaster("local[2]") // 2 cores
      .setAppName("Bitcoin")

    val ssc = new StreamingContext(sparkConf, Seconds(5))
    val lines = ssc.actorStream[UnconfirmedTransaction](
      Props(new UnconfirmedTransactionReceiverActor[UnconfirmedTransaction]("akka.tcp://BlockchainSpark@127.0.0.1:2550/user/Bitcoin")),
      "UnconfirmedTransactionReceiverActor", StorageLevel.MEMORY_ONLY)

    val wordsFile = ssc.sparkContext.textFile("/Users/ele/Code/Projects/PoliceSpark/src/main/resources/words.txt")
    val fourLetterWords = wordsFile.collect().filter(line => line.length <= 4).map(_.toUpperCase)

    lines.foreachRDD(rdd => rdd.foreach{ row =>
      UnconfirmedTransaction.extractMessage(row, fourLetterWords)
    })
    ssc.start()
    ssc.awaitTermination()
  }



}