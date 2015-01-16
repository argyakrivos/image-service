package com.blinkbox.books.marvin.processor.image

import java.nio.file.{Path, Paths}
import java.util.concurrent.TimeUnit

import com.blinkbox.books.rabbitmq.RabbitMqConfig
import com.blinkbox.books.rabbitmq.RabbitMqConfirmedPublisher.PublisherConfiguration
import com.blinkbox.books.rabbitmq.RabbitMqConsumer.QueueConfiguration
import com.typesafe.config.Config

import scala.concurrent.duration.{FiniteDuration, _}

case class AppConfig(rabbitmq: RabbitMqConfig, retryInterval: FiniteDuration, actorTimeout: FiniteDuration,
  input: QueueConfiguration, output: PublisherConfiguration, error: PublisherConfiguration,
  storagePath: Path, threads: Int, imageOutput: ImageOutputConfig)

case class ImageOutputConfig(maxWidth: Int, maxHeight: Int, fileType: String, label: String)

object AppConfig {
  val prefix = "service.imageProcessor"
  def apply(config: Config): AppConfig = AppConfig(
    RabbitMqConfig(config.getConfig(s"$prefix")),
    config.getDuration(s"$prefix.retryInterval", TimeUnit.SECONDS).seconds,
    config.getDuration(s"$prefix.actorTimeout", TimeUnit.SECONDS).seconds,
    QueueConfiguration(config.getConfig(s"$prefix.input")),
    PublisherConfiguration(config.getConfig(s"$prefix.output")),
    PublisherConfiguration(config.getConfig(s"$prefix.error")),
    Paths.get(config.getString(s"$prefix.storagePath")),
    config.getInt(s"$prefix.threads"),
    ImageOutputConfig(config, s"$prefix.imageOutput")
  )
}

object ImageOutputConfig {
  def apply(config: Config, prefix: String): ImageOutputConfig = ImageOutputConfig(
    config.getInt(s"$prefix.maxWidth"),
    config.getInt(s"$prefix.maxHeight"),
    config.getString(s"$prefix.fileType"),
    config.getString(s"$prefix.label")
  )
}
