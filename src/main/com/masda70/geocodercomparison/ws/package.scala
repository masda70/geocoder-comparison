package com.masda70.geocodercomparison

import akka.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import akka.util.Timeout
import spray.client.pipelining.sendReceive
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author  David Montoya
  */
package object ws extends StrictLogging {

  def client(implicit actorSystem:ActorSystem) = {

    implicit val requestTimeout = Timeout(21474835 seconds)

    implicit val executionContext = actorSystem.dispatcher

    val clientPipeline = sendReceive

    clientPipeline
  }

}
