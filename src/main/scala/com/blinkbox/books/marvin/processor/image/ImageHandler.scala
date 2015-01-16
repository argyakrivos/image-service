package com.blinkbox.books.marvin.processor.image

import java.io._
import java.net.URI

import akka.actor.ActorRef
import com.blinkbox.books.marvin.processor.image.processor.{ImageProcessor, ImageSettings, ScaleWithoutUpscale}
import com.blinkbox.books.messaging._
import com.blinkbox.books.quartermaster.common.mapping.StorageService
import com.blinkbox.books.schemas.ingestion.file.pending.v2.FilePending._
import com.typesafe.scalalogging.StrictLogging

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, TimeoutException}

class ImageHandler(config: ImageOutputConfig, storageService: StorageService, imageProcessor: ImageProcessor,
  errorHandler: ErrorHandler, retryInterval: FiniteDuration)
  extends ReliableEventHandler(errorHandler, retryInterval) with StrictLogging {

  private val AcceptedFormats = List("png", "jpg", "jpeg", "gif", "svn", "tif", "tiff", "bmp")

  override protected def handleEvent(event: Event, originalSender: ActorRef): Future[Unit] = for {
    fileSource <- parseMessage(event.body)
    uri <- getImageUri(fileSource)
    imageSource <- storageService.retrieve(uri)
    (normalisedSource, imageSettings) <- normaliseImage(imageSource)
    newUri <- storageService.store(normalisedSource, config.label, config.fileType)
    _ <- sendMetadataMessage(imageSettings, newUri)
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

  private def getImageUri(fileSource: FileSource): Future[URI] = {
    fileSource.fileName.split("\\.").reverse.toList match {
      case ext :: _ if AcceptedFormats.contains(ext.toLowerCase) =>
        Future.successful(URI.create(s"${fileSource.uri}/${fileSource.fileName}"))
      case ext :: _ =>
        Future.failed(new IllegalArgumentException(s"Unsupported extension: $ext"))
      case _ =>
        Future.failed(new IllegalArgumentException(s"Could not detect extension: ${fileSource.fileName}"))
    }
  }

  private def normaliseImage(input: InputStream): Future[(Array[Byte], ImageSettings)] = Future {
    val output = new ByteArrayOutputStream()
    val settings = ImageSettings(Some(config.maxWidth), Some(config.maxHeight), Some(ScaleWithoutUpscale))
    var effectiveSettings = settings
    val callback: ImageSettings => Unit = imageSettings => effectiveSettings = imageSettings
    imageProcessor.transform(config.fileType, input, output, settings, Some(callback))
    (output.toByteArray, effectiveSettings)
  }

  private def sendMetadataMessage(settings: ImageSettings, uri: URI): Future[Unit] = Future {
    ()
  }
}
