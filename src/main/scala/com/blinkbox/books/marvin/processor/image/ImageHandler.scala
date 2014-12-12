package com.blinkbox.books.marvin.processor.image

import akka.actor.ActorRef
import com.blinkbox.books.messaging.{ErrorHandler, Event, ReliableEventHandler}
import com.typesafe.scalalogging.StrictLogging

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, TimeoutException}

class ImageHandler(errorHandler: ErrorHandler, retryInterval: FiniteDuration) extends
  ReliableEventHandler(errorHandler, retryInterval) with StrictLogging {

  override protected def handleEvent(event: Event, originalSender: ActorRef): Future[Unit] = Future {
    logger.debug("received " + event.body.asString())
  }

  @tailrec
  final override protected def isTemporaryFailure(e: Throwable): Boolean =
    e.isInstanceOf[TimeoutException] || Option(e.getCause).isDefined && isTemporaryFailure(e.getCause)
}
