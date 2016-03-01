package com.masda70.geocodercomparison

import java.io.{InputStreamReader, Reader, BufferedReader}
import java.nio.file.{Paths, Path, Files}
import java.util.function.Consumer

import akka.stream.scaladsl.Source
import org.json4s.jackson.Serialization.write
import com.typesafe.scalalogging.StrictLogging
import org.json4s.{CustomSerializer, JValue, JObject}
import org.json4s.JsonAST.{JString, JField, JNull, JArray}
import scopt.Read
import spray.http.Uri
import org.json4s.JsonDSL._
import kantan.csv._                // All kantan.csv types.
import kantan.csv.ops._            // Enriches types with useful methods.

import scala.concurrent.Await


case class Config(compare: Boolean  = false,
                             serviceUri: Option[Uri] = None,
                             result1: Path = null,
                             result2: Option[Path] = None, concurrency: Int = 2)

case class AddressQuery(query:String, longitude: Option[Double], latitude: Option[Double])

case class Geometry(`type`: String, coordinates: Seq[Double])
case class FeatureProperties(extent: Seq[Double],
                             osm_key: String,
                             osm_type: String,
                             osm_value: String,
                             osm_id: String,
                             housenumber: Option[String],
                             street: Option[String],
                             postcode: Option[String],
                             name: Option[String],
                             state: Option[String],
                             country: Option[String],
                             city: Option[String])
case class Feature(`type`: String, geometry: Geometry, properties: FeatureProperties)
case class FeatureCollection(`type`: String, features: Seq[Feature]) extends ResultResult
case class ErrorResult(error: String) extends ResultResult
trait ResultResult
case class Result(locationBias: Option[Seq[Double]], query: String, result: ResultResult)

object ErrorMatch extends PartialMatch
case class ScoreMatch(score: Double) extends PartialMatch
case class LenientHeadMatch(score: Double) extends PartialMatch
object LenientFullMatch extends PartialMatch
trait PartialMatch extends Match
object FullMatch extends Match
trait Match

object Compare extends StrictLogging {
  implicit val pathRead = Read.reads { Paths.get(_) }
  implicit val uriRead = Read.reads { Uri(_) }

  private val parser = new scopt.OptionParser[Config]("geocoder-comparison") {
    head("geocoder-comparison")
    opt[Path]('r', "result1") required() valueName "<file>" action { (x, c) =>
      c.copy(result1 = x)
    } text "result file, e.g. result.json"
    opt[Path]('t', "result2") valueName "<file>" action { (x, c) =>
      c.copy(result2 = Some(x))
    } text "second result file, e.g. result2.json"
    opt[Uri]('s', "service") valueName "<uri>" action { (x, c) =>
      c.copy(serviceUri = Some(x))
    } text "geocoder service url, e.g. http://localhost:2322"
    opt[Int]('c', "concurrency") valueName "<int>" action { (x, c) =>
      c.copy(concurrency = x)
    } text "concurrency" validate {
      concurrency =>
        if(concurrency > 0){
          Right()
        }else{
          Left("concurrency must be a strictly positive integer")
        }
    }
    checkConfig {
      config =>
        if(config.serviceUri.isEmpty && config.result2.isEmpty){
          Left("service URI and second result file cannot be both empty")
        }else if(config.serviceUri.nonEmpty && config.result2.nonEmpty){
          Left("service URI and second result file cannot be both provided")
        }else{
          Right()
        }
    }
  }

  def getPathTree(path: Path): Stream[Path] = {
    path #:: {
      if(Files.isDirectory(path)){
        val results = Vector.newBuilder[Path]
        Files.newDirectoryStream(path).forEach(new Consumer[Path] {
          override def accept(p: Path): Unit = {
            results += p
          }
        })
        results.result().toStream.flatMap(getPathTree)
      }else{
        Stream.empty
      }
    }
  }


  class ResultResultSerializer extends CustomSerializer[ResultResult](format => ( {
    case JObject(List(JField("error", JString(error)))) =>
      ErrorResult(error)
    case x =>
      implicit val formats = format
      x.extract[FeatureCollection]
  }, {
    case ErrorResult(error) => JObject(JField("error", JString(error)))
    case x:FeatureCollection =>
      implicit val formats = format
      write(x)
  }))

  def main(args: Array[String]): Unit = {
    implicit val codec = scala.io.Codec.UTF8
    parser.parse(args, Config()) match{
      case Some(config) =>
        import actors._
        import scala.concurrent.ExecutionContext.Implicits.global
        implicit val formats = org.json4s.DefaultFormats + new ResultResultSerializer
        try {
          config.serviceUri match{
            case None =>
              config.result2 match{
                case Some(result2) =>
                  def read(path: Path) = {
                    val br = new BufferedReader(new InputStreamReader(Files.newInputStream(path)))
                    val results = org.json4s.jackson.Serialization.read[Seq[Result]](br)
                    br.close()
                    results.groupBy(x => (x.query, x.locationBias)).mapValues{
                      case (queryResults) =>
                        val h = queryResults.collectFirst{
                          case q if !q.result.isInstanceOf[ErrorResult] => q.result
                        }
                        if(h.nonEmpty){
                          h.head
                        }else{
                          queryResults.map(_.result).head
                        }
                    }
                  }
                  val results1 = read(config.result1)
                  val results2 = read(result2)
                  val comparison = results1.toVector.flatMap{
                    case (query, r1) =>
                      results2.get(query).map{
                        case r2 =>
                          if(r1 == r2){
                            FullMatch
                          }else{
                            (r1,r2) match{
                              case (f1:FeatureCollection, f2:FeatureCollection) =>
                                def stripIds(f: FeatureCollection) = {
                                  f.features.map {
                                    h => h.copy(geometry = Geometry("", Seq()), properties = h.properties.copy(osm_id = "", extent = Seq()))
                                  }
                                }
                                val features1 = stripIds(f1)
                                val features2 = stripIds(f2)
                                val score = features1.toSet.intersect(features2.toSet).size.toDouble / (features1.toSet ++ features2.toSet).size.toDouble
                                if(features1 == features2){
                                  LenientFullMatch
                                }else if(f1.features.headOption == f2.features.headOption){
                                  LenientHeadMatch(score)
                                }else {
                                  ScoreMatch(score)
                                }
                              case _ => ErrorMatch
                            }
                          }
                      }
                  }
                  val fullMatches = comparison.count(x => x == FullMatch)
                  val lenientFullMatches = comparison.count(x => x == LenientFullMatch)
                  val errorMatches = comparison.count(x => x == ErrorMatch)
                  val lenientHeadMatches =
                    comparison.collect{
                      case s:LenientHeadMatch => s.score
                    }
                  val headMatchScore = if(lenientHeadMatches.nonEmpty){
                    lenientHeadMatches.sum/ lenientHeadMatches.size
                  }else{
                    1.0
                  }
                  val scoredMatch = comparison.collect{
                    case s:ScoreMatch => s.score
                  }
                  val score = if(scoredMatch.nonEmpty){
                    scoredMatch.sum / scoredMatch.size
                  }else{
                    1.0
                  }
                  logger.info(f"Comparison between ${config.result1} and ${result2}: $fullMatches/${comparison.size} = ${fullMatches.toDouble/comparison.size.toDouble}%2.3f full matches.")
                  logger.info(f"Comparison between ${config.result1} and ${result2}: $lenientFullMatches/${comparison.size} = ${lenientFullMatches.toDouble/comparison.size.toDouble}%2.3f lenient full matches.")
                  logger.info(f"Comparison between ${config.result1} and ${result2}: ${lenientHeadMatches.size}/${comparison.size} = ${lenientHeadMatches.size.toDouble/comparison.size.toDouble}%2.3f lenient head matches (score $headMatchScore).")
                  logger.info(f"Comparison between ${config.result1} and ${result2}: ${scoredMatch.size}/${comparison.size} = ${scoredMatch.size.toDouble/comparison.size.toDouble}%2.3f partial matches (score $score).")
                  logger.info(f"Comparison between ${config.result1} and ${result2}: ${errorMatches}/${comparison.size} = ${errorMatches.toDouble/comparison.size.toDouble}%2.3f error matches.")
                  ()
                case None => // should not happen
              }
            case Some(serviceUri) =>
              val dataFolder = Paths.get("data")
              val outputFile = config.result1
              val geocoder = new PhotonGeocoder(Uri(serviceUri.toString))
              val dataFiles = getPathTree(dataFolder).filter(p => {
                logger.info(p.toString)
                !Files.isDirectory(p) && p.toString.endsWith(".csv")
              })

              implicit val addressQueryDecoder = RowDecoder.decoder3(AddressQuery.apply)(0, 1, 2)
              val queries = Source.fromIterator(() => dataFiles.iterator).mapConcat {
                case p =>
                  val reader = p.toFile.asCsvReader[AddressQuery](',', header = true)
                  reader.map {
                    decoder => decoder.toOption
                  }.flatten.toVector
              }
              val future = queries.mapAsyncUnordered(config.concurrency) {
                addressQuery =>
                  val locationBias = (addressQuery.longitude, addressQuery.latitude) match {
                    case (Some(longitude), Some(latitude)) => Some((longitude, latitude))
                    case _ => None
                  }
                  geocoder.direct(addressQuery.query, locationBias).recover {
                    case error => ("error" -> error.toString) ~ Nil
                  }.map {
                    result =>
                      val r = ("query" -> addressQuery.query) ~ ("result" -> result) ~ Nil
                      locationBias.map {
                        case (longitude, latitude) =>
                          ("locationBias" -> Seq(longitude, latitude)) ~ r
                      }.getOrElse(r)
                  }
              }.runFold(List[JObject]()) {
                case (v, result) =>
                  v :+ result
              }.map {
                results =>
                  val outputStream = Files.newOutputStream(outputFile)
                  org.json4s.jackson.Serialization.write(JArray(results), outputStream)
                  outputStream.close()
              }
              Await.result(future, scala.concurrent.duration.Duration.Inf)
              future.onFailure {
                case t => throw t
              }
          }
        }finally {
          materializer.shutdown()
          system.shutdown()
        }
      case None =>
    }
  }
}
