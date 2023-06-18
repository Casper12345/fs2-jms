package com.casper12345.fs2jms.consumer

import cats.effect.{Async, Resource, Sync}
import cats.implicits._
import com.casper12345.fs2jms.util.{ConsumerSettings, IllegalJmsMessageException, Util}
import fs2.Stream

import javax.jms.{Connection, JMSConsumer, Message, MessageConsumer}

class JmsConsumer[F[_] : Sync, A <: Message: Manifest](settings: ConsumerSettings) {

  private val connection = Util.createContext[F](settings)

  private def receive(consumer: JMSConsumer): F[A] = Sync[F].blocking(consumer.receive).flatMap {
    case message: A => Sync[F].delay(message)
    case x          => Sync[F].raiseError(IllegalJmsMessageException(s"Received message of unsupported type: ${x.getClass.toString}"))
  }

  private val consumerStream: Resource[F, F[Stream[F, Either[Throwable, A]]]] =
    connection.map { conn =>
      for {
        _           <- Sync[F].blocking(conn.start())
        _           <- Sync[F].blocking(conn)
        destination <- Sync[F].blocking(conn.createQueue(settings.queue))
        consumer    <- Sync[F].blocking(conn.createConsumer(destination))
      } yield Stream.repeatEval(receive(consumer).attempt)
    }

}

object JmsConsumer {
  def apply[F[_] : Async, A <: Message: Manifest](
    settings: ConsumerSettings
  ): Resource[F, F[Stream[F, Either[Throwable, A]]]] =
    new JmsConsumer[F, A](settings).consumerStream
}
