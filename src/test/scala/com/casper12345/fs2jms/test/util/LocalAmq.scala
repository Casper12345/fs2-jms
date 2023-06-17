package com.casper12345.fs2jms.test.util

import com.dimafeng.testcontainers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

trait LocalAmq {
  protected lazy val amqPort: Int = LocalAmq.mappedAmqExternalPort
}

object LocalAmq {

  private lazy val mappedAmqExternalPort = {
    val Port = 61616
    val container =
      GenericContainer(
        dockerImage = "casper12345/activemq:v2",
        exposedPorts = Seq(Port),
        waitStrategy = Wait
          .forListeningPort()
      )

    container.start()

    sys.addShutdownHook(container.stop())

    container.mappedPort(Port)
  }

}
