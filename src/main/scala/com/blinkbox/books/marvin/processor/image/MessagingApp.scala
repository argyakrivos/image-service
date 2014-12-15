package com.blinkbox.books.marvin.processor.image

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import com.blinkbox.books.config.Configuration
import com.blinkbox.books.logging.{DiagnosticExecutionContext, Loggers}
import com.blinkbox.books.messaging.{ActorErrorHandler, ErrorHandler}
import com.blinkbox.books.rabbitmq.RabbitMqConfirmedPublisher.PublisherConfiguration
import com.blinkbox.books.rabbitmq.RabbitMqConsumer.QueueConfiguration
import com.blinkbox.books.rabbitmq.{RabbitMq, RabbitMqConfirmedPublisher, RabbitMqConsumer}
import com.typesafe.scalalogging.StrictLogging

object MessagingApp extends App with Configuration with Loggers with StrictLogging {
  val appConfig = AppConfig(config)

  implicit val system = ActorSystem("image-processor-system", config)
  implicit val ec = DiagnosticExecutionContext(system.dispatcher)
  implicit val timeout = Timeout(appConfig.actorTimeout)

  val consumerConnection = RabbitMq.reliableConnection(appConfig.rabbitmq)
  val publisherConnection = RabbitMq.recoveredConnection(appConfig.rabbitmq)

  val msgErrorHandler = errorHandler("message-error", appConfig.error)
  val msgHandler = system.actorOf(Props(
    new ImageHandler(msgErrorHandler, appConfig.retryInterval)), name = "message-handler")
  val msgConsumer = consumer("message-consumer", appConfig.input, msgHandler)

  // kick-off things
  msgConsumer ! RabbitMqConsumer.Init

  private def errorHandler(actorName: String, config: PublisherConfiguration): ErrorHandler =
    new ActorErrorHandler(publisher(actorName, config))

  private def consumer(actorName: String, config: QueueConfiguration, handler: ActorRef): ActorRef =
    system.actorOf(Props(new RabbitMqConsumer(
      consumerConnection.createChannel, config, s"$actorName-msg", handler)), actorName)

  private def publisher(actorName: String, config: PublisherConfiguration): ActorRef =
    system.actorOf(Props(new RabbitMqConfirmedPublisher(publisherConnection, config)), actorName)
}
