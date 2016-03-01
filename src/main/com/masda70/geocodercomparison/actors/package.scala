package com.masda70.geocodercomparison

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

/**
  * @author David Montoya
  */
package object actors {
  implicit lazy val system = ActorSystem("relasticsearch")
  implicit lazy val materializer = ActorMaterializer()
}
