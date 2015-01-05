package com.blinkbox.books.marvin.processor.image

import akka.actor.ActorRef
import com.blinkbox.books.messaging._
import com.blinkbox.books.quartermaster.common.mapping.StorageService
import com.blinkbox.books.schemas.ingestion.file.pending.v2.FilePending._
import com.typesafe.scalalogging.StrictLogging

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, TimeoutException}

class ImageHandler(storageService: StorageService, errorHandler: ErrorHandler, retryInterval: FiniteDuration)
  extends ReliableEventHandler(errorHandler, retryInterval) with StrictLogging {

  override protected def handleEvent(event: Event, originalSender: ActorRef): Future[Unit] = Future {
    event.body match {
      case Details(fileSource) =>
        logger.debug("Got valid source: " + fileSource)
      case _ => throw new IllegalArgumentException(s"Invalid file source event: ${event.body.contentType.toString}")
    }
  }

  @tailrec
  final override protected def isTemporaryFailure(e: Throwable): Boolean =
    e.isInstanceOf[TimeoutException] || (Option(e.getCause).isDefined && isTemporaryFailure(e.getCause))
}
