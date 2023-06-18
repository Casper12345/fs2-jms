package com.casper12345.fs2jms.util

import cats.effect.{Resource, Sync}
import com.typesafe.scalalogging.StrictLogging
import cats.implicits._
import javax.jms.{ConnectionFactory, JMSContext}

final case class SecuritySettings(
 password: String,
 userName: String
)

trait Settings {
  val connectionFactory: ConnectionFactory
  val securitySettings: Option[SecuritySettings]
  def createContext: JMSContext =
    securitySettings.fold(
      connectionFactory.createContext()
    )(s => connectionFactory.createContext(s.userName, s.password))
}

final case class ProducerSettings(
  connectionFactory: ConnectionFactory,
  securitySettings: Option[SecuritySettings] = None,
  deliveryMode: Int,
  queue: String
) extends Settings

final case class ConsumerSettings(
  connectionFactory: ConnectionFactory,
  securitySettings: Option[SecuritySettings] = None,
  queue: String
) extends Settings

object Util extends StrictLogging {

  def createContext[F[_] : Sync](settings: => Settings): Resource[F, JMSContext] = {
    Resource.make(Sync[F].blocking(settings.createContext) <* Sync[F].delay(logger.info("creating jms context")))(
      conn => Sync[F].blocking(conn.close()) <* Sync[F].delay(logger.info("closing jms context"))
    )
  }

}
