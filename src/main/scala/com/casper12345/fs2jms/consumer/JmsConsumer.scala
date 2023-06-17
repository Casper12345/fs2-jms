package com.casper12345.fs2jms.consumer

import cats.effect.{Async, Resource, Sync}
import cats.implicits._
import com.casper12345.fs2jms.util.{ConsumerSettings, IllegalJmsMessageException, Util}
import fs2.Stream
import javax.jms.{Message, MessageConsumer}

class JmsConsumer[F[_] : Sync, A <: Message: Manifest](settings: ConsumerSettings) {

  private val connection = Util.createConnection[F](settings.connectionFactory)

  private def receive(consumer: MessageConsumer): F[A] = Sync[F].blocking(consumer.receive).flatMap {
    case message: A => Sync[F].delay(message)
    case x          => Sync[F].raiseError(IllegalJmsMessageException(s"Received message of unsupported type: ${x.getClass.toString}"))
  }

  private val consumerStream: Resource[F, F[Stream[F, Either[Throwable, A]]]] =
    connection.map { conn =>
      for {
        _           <- Sync[F].blocking(conn.start())
        session     <- Sync[F].blocking(conn.createSession(settings.transacted, settings.acknowledgeMode))
        destination <- Sync[F].blocking(session.createQueue(settings.queue))
        consumer    <- Sync[F].blocking(session.createConsumer(destination))
      } yield Stream.repeatEval(receive(consumer).attempt)
    }

}

object JmsConsumer {
  def apply[F[_] : Async, A <: Message: Manifest](
    settings: ConsumerSettings
  ): Resource[F, F[Stream[F, Either[Throwable, A]]]] =
    new JmsConsumer[F, A](settings).consumerStream
}
