package com.blinkbox.books.marvin.processor.image

import java.io._
import java.net.URI

import akka.actor.ActorRef
import com.blinkbox.books.marvin.processor.image.processor.{ImageSettings, ScaleWithoutUpscale, ThreadPoolImageProcessor}
import com.blinkbox.books.messaging._
import com.blinkbox.books.quartermaster.common.mapping.StorageService
import com.blinkbox.books.schemas.ingestion.file.pending.v2.FilePending._
import com.typesafe.scalalogging.StrictLogging

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, TimeoutException}

class ImageHandler(config: ImageConfig, storageService: StorageService, errorHandler: ErrorHandler, retryInterval: FiniteDuration)
  extends ReliableEventHandler(errorHandler, retryInterval) with StrictLogging {

  val imageProcessor = new ThreadPoolImageProcessor(2)

  override protected def handleEvent(event: Event, originalSender: ActorRef): Future[Unit] = for {
    fileSource <- parseMessage(event.body)
    uri = URI.create(s"${fileSource.uri}/${fileSource.fileName}")
    _ = println(uri)
    imageSource <- storageService.retrieve(uri)
    (normalisedSource, imageSettings) <- normaliseImage(imageSource)
    uri <- storageService.store(normalisedSource, "cover", config.outputFileType)
    _ <- sendMetadataMessage(imageSettings, uri)
  } yield ()

  @tailrec
  final override protected def isTemporaryFailure(e: Throwable): Boolean =
    e.isInstanceOf[TimeoutException] || (Option(e.getCause).isDefined && isTemporaryFailure(e.getCause))

  private def parseMessage(message: EventBody): Future[FileSource] =
    message match {
      case Details(fileSource) =>
       logger.info(s"Received token ${fileSource.uri}")
       Future.successful(fileSource)
      case _ => Future.failed(new IllegalArgumentException(s"Invalid file source event: ${message.contentType.toString}"))
    }

  def normaliseImage(input: InputStream): Future[(Array[Byte], ImageSettings)] = Future {
    val output = new ByteArrayOutputStream()
    val settings = ImageSettings(width = Some(config.maxWidth), height = Some(config.maxHeight), mode = Some(ScaleWithoutUpscale))
    var effectiveSettings = settings
    val callback: ImageSettings => Unit = imageSettings => effectiveSettings = imageSettings
    imageProcessor.transform(config.outputFileType, input, output, settings, Some(callback))
    (output.toByteArray, effectiveSettings)
  }

  def sendMetadataMessage(settings: ImageSettings, uri: URI): Future[Unit] = Future {
    ()
  }
}
