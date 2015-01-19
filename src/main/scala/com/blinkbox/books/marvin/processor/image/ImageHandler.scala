package com.blinkbox.books.marvin.processor.image

import java.io._
import java.net.URI

import akka.actor.ActorRef
import com.blinkbox.books.marvin.processor.image.processor.{ImageProcessor, ImageSettings, ScaleWithoutUpscale}
import com.blinkbox.books.messaging._
import com.blinkbox.books.quartermaster.common.mapping.StorageService
import com.blinkbox.books.schemas.ingestion.book.v2.Book
import com.blinkbox.books.schemas.ingestion.book.v2.Book._
import com.blinkbox.books.schemas.ingestion.file.pending.v2.FilePending._
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.{DateTime, DateTimeZone}

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, TimeoutException}

class ImageHandler(config: ImageOutputConfig, storageService: StorageService, imageProcessor: ImageProcessor,
  publisher: ActorRef, errorHandler: ErrorHandler, retryInterval: FiniteDuration)
  extends ReliableEventHandler(errorHandler, retryInterval) with StrictLogging {

  private val AcceptedFormats = List("png", "jpg", "jpeg", "gif", "svn", "tif", "tiff", "bmp")
  private val IsbnMatcher = """.*((?:97(?:8|9)){1}\d{10}).*""".r

  override protected def handleEvent(event: Event, originalSender: ActorRef): Future[Unit] = for {
    fileSource <- parseMessage(event.body)
    isbn <- getImageIsbn(fileSource)
    uri <- getImageUri(fileSource)
    imageSource <- retrieveImage(uri)
    (normalisedSource, imageSettings) <- normaliseImage(isbn, imageSource)
    newUri <- storeImage(isbn, normalisedSource)
    _ <- sendMetadataMessage(isbn, newUri, imageSettings, normalisedSource.length, fileSource)
  } yield ()

  @tailrec
  final override protected def isTemporaryFailure(e: Throwable): Boolean =
    e.isInstanceOf[TimeoutException] || (Option(e.getCause).isDefined && isTemporaryFailure(e.getCause))

  private def parseMessage(message: EventBody): Future[FileSource] =
    message match {
      case Details(fileSource) =>
       logger.info(s"Received token ${fileSource.uri}/${fileSource.fileName}")
       Future.successful(fileSource)
      case _ =>
        Future.failed(InvalidMessageType(message))
    }

  private def getImageIsbn(fileSource: FileSource): Future[String] =
    fileSource.fileName.split("/").reverse.toList match {
      case IsbnMatcher(isbn) :: _ =>
        Future.successful(isbn)
      case _ =>
        Future.failed(InvalidImageIsbn(fileSource.fileName))
    }

  private def getImageUri(fileSource: FileSource): Future[URI] =
    fileSource.fileName.split("\\.").reverse.toList match {
      case ext :: _ if AcceptedFormats.contains(ext.toLowerCase) =>
        Future(URI.create(s"${fileSource.uri}/${fileSource.fileName}"))
      case ext :: _ =>
        Future.failed(UnsupportedImageExtension(ext))
    }

  private def retrieveImage(uri: URI): Future[InputStream] = {
    log.info(s"Retrieving image: $uri")
    storageService.retrieve(uri)
  }

  private def normaliseImage(isbn: String, input: InputStream): Future[(Array[Byte], ImageSettings)] = Future {
    logger.info(s"Processing image for $isbn")
    val output = new ByteArrayOutputStream()
    val settings = ImageSettings(Some(config.maxWidth), Some(config.maxHeight), Some(ScaleWithoutUpscale))
    var effectiveSettings = settings
    val callback: ImageSettings => Unit = imageSettings => effectiveSettings = imageSettings
    imageProcessor.transform(config.fileType, input, output, settings, Some(callback))
    logger.info(s"Image processed for $isbn")
    (output.toByteArray, effectiveSettings)
  }

  private def storeImage(isbn: String, source: Array[Byte]): Future[URI] = {
    log.info(s"Storing processed image for $isbn")
    storageService.store(source, config.label, config.fileType)
  }

  private def sendMetadataMessage(isbn: String, uri: URI, imageSettings: ImageSettings,
    imageSize: Long, fileSource: FileSource): Future[Unit] = Future {
    val coverMessage = Book.Cover(
      List(
        Classification("isbn", isbn),
        Classification("source_username", fileSource.username)
      ),
      isbn,
      Media(Some(List(
        Image(
          List(Classification("type", "front_cover")),
          uri,
          imageSettings.width.getOrElse(0),
          imageSettings.height.getOrElse(0),
          imageSize.toLong
        )
      ))),
      Source(
        DateTime.now(DateTimeZone.UTC),
        fileSource.uri,
        Option(new URI(fileSource.fileName)),
        fileSource.role,
        fileSource.username,
        Option(fileSource.contentType),
        System("Marvin/image_processor", MessagingApp.Version)
      )
    )
    publisher ! Event.json(EventHeader("image-processor"), coverMessage)
  }
}
