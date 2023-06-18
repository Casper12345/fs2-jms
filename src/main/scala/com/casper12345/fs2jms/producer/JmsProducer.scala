package com.casper12345.fs2jms.producer

import cats.effect.{Async, Resource, Sync}
import cats.effect.std.Queue
import cats.implicits._
import cats.effect.kernel.implicits._
import com.casper12345.fs2jms.util.{ProducerSettings, Util}
import com.typesafe.scalalogging.StrictLogging
import fs2.Stream
import javax.jms.{JMSContext, Message}

class JmsProducer[F[_] : Async](settings: ProducerSettings) extends StrictLogging {
  private def logException(t: Throwable): F[Unit] = Sync[F].delay(logger.error("Message producer caught exception", t))

  private val context = Util.createContext(settings)

  private val producerStream: Resource[F, F[(F[Unit], JMSContext, Message => F[Unit])]] =
    context.map { con =>
      for {
        _           <- Sync[F].blocking(con.start())
        destination <- Sync[F].blocking(con.createQueue(settings.queue))
        producer    <- Sync[F].blocking(con.createProducer)
        _           <- Sync[F].delay(producer.setDeliveryMode(settings.deliveryMode))
        messageQueue <- Queue.unbounded[F, Message].map { queue =>
                          (
                            Stream
                              .fromQueueUnterminated(queue)
                              .evalMap(m =>
                                Sync[F].blocking(producer.send(destination, m)).void.handleErrorWith(logException)
                              )
                              .compile
                              .drain,
                            con,
                            queue.offer _
                          )
                        }
      } yield {
        messageQueue
      }
    }

  private val producerIO: Resource[F, F[(Message => F[Unit], JMSContext)]] =
    producerStream.flatMap(io =>
      Resource.suspend(io.map { case (str, con, fun) =>
        str.background.map(_ => Sync[F].delay((fun, con)))
      })
    )

}

object JmsProducer {
  def apply[F[_] : Async](
    settings: ProducerSettings
  ): Resource[F, F[(Message => F[Unit], JMSContext)]] = new JmsProducer(settings).producerIO
}
