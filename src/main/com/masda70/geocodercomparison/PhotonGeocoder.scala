package com.masda70.geocodercomparison

import akka.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import org.json4s._
import spray.client.pipelining._
import spray.http.HttpMethods._
import spray.http.Uri.Query
import spray.http._

import scala.concurrent.ExecutionContext


class PhotonGeocoder(uri: Uri)(implicit actorSystem: ActorSystem, executionContext: ExecutionContext) extends Geocoder with StrictLogging{

  private val serviceUrl = uri
  private val directGeocodingEndpoint = serviceUrl.withPath(Uri.Path./("api"))
  private val reverseGeocodingEndpoint = serviceUrl.withPath(Uri.Path./("reverse"))


  val simpleFormats = org.json4s.DefaultFormats

  object SimpleApiMarshallers extends spray.httpx.Json4sJacksonSupport {
    override implicit def json4sJacksonFormats: Formats = simpleFormats
  }
  import SimpleApiMarshallers._
  val pipeline = ws.client ~>  unmarshal[JValue]

  def reverse(longitude: Double, latitude: Double) = {
    val response = pipeline {
      HttpRequest(method = GET, uri = reverseGeocodingEndpoint.withQuery("lon" -> longitude.toString, "lat" -> latitude.toString))
    }
    response
  }

  def direct(query: String, locationBias: Option[(Double, Double)]) = {
    val baseQuery = Query("q" -> query)
    val finalQuery = locationBias.map{
      case (longitude, latitude) => baseQuery.+:("lon" -> longitude.toString).+:("lat" -> latitude.toString)
    }.getOrElse(baseQuery)
    val response = pipeline {
      HttpRequest(method = GET, uri = directGeocodingEndpoint.withQuery(finalQuery))
    }
    response
  }

}