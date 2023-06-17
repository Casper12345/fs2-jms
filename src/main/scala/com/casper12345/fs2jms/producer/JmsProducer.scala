package com.casper12345.fs2jms.producer

import cats.effect.{Async, Resource, Sync}
import cats.effect.std.Queue
import cats.implicits._
import cats.effect.kernel.implicits._
import com.casper12345.fs2jms.util.{ProducerSettings, Util}
import com.typesafe.scalalogging.StrictLogging
import fs2.Stream
import javax.jms.{Message, Session}

class JmsProducer[F[_] : Async](settings: ProducerSettings) extends StrictLogging {
  private def logException(t: Throwable): F[Unit] = Sync[F].delay(logger.error("Message producer caught exception", t))

  private val connection = Util.createConnection(settings.connectionFactory)

  private val producerStream: Resource[F, F[(F[Unit], Session, Message => F[Unit])]] =
    connection.map { conn =>
      for {
        _           <- Sync[F].blocking(conn.start())
        session     <- Sync[F].blocking(conn.createSession(settings.transacted, settings.acknowledgeMode))
        destination <- Sync[F].blocking(session.createQueue(settings.queue))
        producer    <- Sync[F].blocking(session.createProducer(destination))
        _           <- Sync[F].delay(producer.setDeliveryMode(settings.deliveryMode))
        messageQueue <- Queue.unbounded[F, Message].map { queue =>
                          (
                            Stream
                              .fromQueueUnterminated(queue)
                              .evalMap(m => Sync[F].blocking(producer.send(m)).handleErrorWith(logException))
                              .compile
                              .drain,
                            session,
                            queue.offer _
                          )
                        }
      } yield {
        messageQueue
      }
    }

  private val producerIO: Resource[F, F[(Message => F[Unit], Session)]] =
    producerStream.flatMap(io =>
      Resource.suspend(io.map { case (str, session, fun) =>
        str.background.map(_ => Sync[F].delay((fun, session)))
      })
    )

}

object JmsProducer {
  def apply[F[_] : Async](
    settings: ProducerSettings
  ): Resource[F, F[(Message => F[Unit], Session)]] = new JmsProducer(settings).producerIO
}
