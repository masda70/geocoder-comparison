package com.masda70.geocodercomparison

import org.json4s.JValue

import scala.concurrent.Future

/**
  * @author David Montoya
  */
trait Geocoder {

  def reverse(longitude: Double, latitude: Double): Future[JValue]
  def direct(query: String, locationBias: Option[(Double, Double)] = None): Future[JValue]

}
