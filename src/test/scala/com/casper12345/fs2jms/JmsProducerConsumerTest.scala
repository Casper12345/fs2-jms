package com.casper12345.fs2jms

import cats.effect._
import cats.effect.testing.scalatest.AsyncIOSpec
import com.casper12345.fs2jms.consumer.JmsConsumer
import com.casper12345.fs2jms.producer.JmsProducer
import com.casper12345.fs2jms.test.util.LocalAmq
import com.casper12345.fs2jms.util.{ConsumerSettings, IllegalJmsMessageException, ProducerSettings}
import org.apache.activemq.ActiveMQConnectionFactory
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io
import java.io.Serializable
import javax.jms.{DeliveryMode, Message, ObjectMessage, Session}
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt

class JmsProducerConsumerTest extends AsyncFlatSpec with AsyncIOSpec with Matchers with LocalAmq {

  def fixture(queue: String) = new {

    val connectionFactory = new ActiveMQConnectionFactory(
      "admin",
      "admin",
      s"failover:(tcp://localhost:$amqPort)"
    )

    connectionFactory.setTrustAllPackages(true)

    val producerSettings = ProducerSettings(
      connectionFactory,
      transacted = false,
      Session.AUTO_ACKNOWLEDGE,
      DeliveryMode.NON_PERSISTENT,
      queue
    )

    val consumerSettings = ConsumerSettings(
      connectionFactory,
      transacted = false,
      Session.AUTO_ACKNOWLEDGE,
      queue
    )

    val producer = JmsProducer[IO](producerSettings)
    val consumer = JmsConsumer[IO, ObjectMessage](consumerSettings)

    val offerToSource: Resource[IO, (Serializable, Message => Message) => IO[Unit]] = producer.map {
      io => (serializable: Serializable, m: Message => Message) =>
        {
          io.flatMap { case (offer, session) =>
            offer(m(session.createObjectMessage(serializable)))
          }
        }
    }

  }

  def testCondition(condition: ObjectMessage => Boolean): Either[Throwable, ObjectMessage] => IO[Boolean] = {
    case Right(value) => IO.pure(condition(value))
    case _            => IO.pure(false)
  }

  "JmsConsumer" should "should get message from queue" in {
    val f = fixture("test-1")

    val testObject = TestObject("testing-1", 200)

    var t = false // testing that the condition is true

    val condition: ObjectMessage => Boolean = _.getObject match {
      case to: TestObject => to == testObject
      case _              => false
    }

    (for {
      offerToSource <- f.offerToSource
      cons          <- f.consumer
    } yield {
      for {
        _ <- offerToSource(testObject, identity)
        _ <- IO.sleep(1.seconds)
        _ <- IO(
               cons
                 .flatMap(str => str.evalMap(ee => testCondition(condition)(ee).map(b => t = b)).compile.drain)
                 .unsafeRunTimed(1.seconds)
             )
      } yield {
        t
      }
    }).use(io => IO.sleep(1.seconds) *> io).asserting(_ shouldBe true)

  }

  it should "handle timed message" in {
    val f = fixture("test-2")

    val testObject = TestObject("testing-1", 200)

    var t = false // testing that the condition is true

    val condition: ObjectMessage => Boolean = _.getObject match {
      case to: TestObject => to == testObject
      case _              => false
    }

    (for {
      offerToSource <- f.offerToSource
      cons          <- f.consumer
    } yield {
      for {
        _ <- offerToSource(
               testObject,
               m => { m.setObjectProperty(org.apache.activemq.ScheduledMessage.AMQ_SCHEDULED_DELAY, 200); m }
             )
        _ <- IO.sleep(1.seconds)
        _ <- IO(
               cons
                 .flatMap(str => str.evalMap(ee => testCondition(condition)(ee).map(b => t = b)).compile.drain)
                 .unsafeRunTimed(1.seconds)
             )
      } yield {
        t
      }
    }).use(io => IO.sleep(1.seconds) *> io).asserting(_ shouldBe true)

  }

  it should "handle messages in order" in {
    val f = fixture("test-3")

    val testObject1 = TestObject("testing-1", 200)
    val testObject2 = TestObject("testing-2", 200)
    val testObject3 = TestObject("testing-3", 200)

    val buffer = ListBuffer.empty[TestObject] // testing that the condition is true

    def toObject[A <: io.Serializable](e: Either[Throwable, ObjectMessage]): A = e match {
      case Right(value) => value.getObject.asInstanceOf[A]
      case _            => throw new Exception("test failed")
    }

    (for {
      offerToSource <- f.offerToSource
      cons          <- f.consumer
    } yield {
      for {
        _ <- offerToSource(testObject1, identity)
        _ <- IO.sleep(100.millis)
        _ <- offerToSource(testObject2, identity)
        _ <- IO.sleep(100.millis)
        _ <- offerToSource(testObject3, identity)
        _ <- IO(
               cons
                 .flatMap(str => str.evalMap(ee => IO(buffer.append(toObject[TestObject](ee)))).compile.drain)
                 .unsafeRunTimed(1.seconds)
             )
      } yield {
        buffer
      }
    }).use(io => IO.sleep(1.seconds) *> io).asserting(_ shouldEqual List(testObject1, testObject2, testObject3))

  }

  it should "handle timed messages in order" in {
    val f = fixture("test-4")

    val testObject1 = TestObject("testing-1", 200)
    val testObject2 = TestObject("testing-2", 200)
    val testObject3 = TestObject("testing-3", 200)

    val buffer = ListBuffer.empty[TestObject] // testing that the condition is true

    def toObject[A <: io.Serializable](e: Either[Throwable, ObjectMessage]): A = e match {
      case Right(value) => value.getObject.asInstanceOf[A]
      case _            => throw new Exception("test failed")
    }

    (for {
      offerToSource <- f.offerToSource
      cons          <- f.consumer
    } yield {
      for {
        _ <- offerToSource(
               testObject1,
               m => { m.setObjectProperty(org.apache.activemq.ScheduledMessage.AMQ_SCHEDULED_DELAY, 300); m }
             )
        _ <- offerToSource(
               testObject2,
               m => { m.setObjectProperty(org.apache.activemq.ScheduledMessage.AMQ_SCHEDULED_DELAY, 200); m }
             )
        _ <- offerToSource(
               testObject3,
               m => { m.setObjectProperty(org.apache.activemq.ScheduledMessage.AMQ_SCHEDULED_DELAY, 100); m }
             )
        _ <- IO(
               cons
                 .flatMap(str => str.evalMap(ee => IO(buffer.append(toObject[TestObject](ee)))).compile.drain)
                 .unsafeRunTimed(1.seconds)
             )
      } yield {
        buffer
      }
    }).use(io => IO.sleep(1.seconds) *> io).asserting(_ shouldBe List(testObject3, testObject2, testObject1))

  }

  it should "report error on wrong message type" in {
    val f = fixture("test-6")

    val buffer = ListBuffer.empty[Either[Throwable, ObjectMessage]] // testing that the condition is true

    val ots: Resource[IO, (String, Message => Message) => IO[Unit]] = f.producer.map {
      io => (s: String, m: Message => Message) =>
        {
          io.flatMap { case (offer, session) =>
            offer(m(session.createTextMessage(s)))
          }
        }
    }

    (for {
      offerToSource <- ots
      cons          <- f.consumer
    } yield {
      for {
        _ <- offerToSource("test-1", identity)
        _ <- IO.sleep(1.seconds)
        _ <- IO(
               cons
                 .flatMap(str => str.evalMap(ee => IO(ee).map(e => buffer.append(e))).compile.drain)
                 .unsafeRunTimed(1.seconds)
             )
      } yield {
        buffer.head.left.get
      }
    }).use(io => IO.sleep(1.seconds) *> io).asserting {
      case _: IllegalJmsMessageException => succeed
      case _                             => fail("test failed")
    }
  }

}

case class TestObject(s: String, i: Int) extends Serializable
