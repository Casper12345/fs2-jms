package com.casper12345.fs2jms.util

import cats.effect.{Sync, Resource}
import com.typesafe.scalalogging.StrictLogging
import cats.implicits._
import javax.jms.{Connection, ConnectionFactory}

case class ProducerSettings(
  connectionFactory: ConnectionFactory,
  transacted: Boolean,
  acknowledgeMode: Int,
  deliveryMode: Int,
  queue: String
)

case class ConsumerSettings(
  connectionFactory: ConnectionFactory,
  transacted: Boolean,
  acknowledgeMode: Int,
  queue: String
)

object Util extends StrictLogging {

  def createConnection[F[_] : Sync](connectionFactory: => ConnectionFactory): Resource[F, Connection] =
    Resource.make(Sync[F].blocking(connectionFactory.createConnection()) <* Sync[F].delay(logger.info("creating amq connection")))(
      conn => Sync[F].blocking(conn.close()) <* Sync[F].delay(logger.info("closing amq connection"))
    )

}
