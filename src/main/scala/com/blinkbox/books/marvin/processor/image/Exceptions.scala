package com.blinkbox.books.marvin.processor.image

import com.blinkbox.books.messaging.EventBody

case class InvalidMessageType(message: EventBody) extends RuntimeException(
  s"Invalid event type: ${message.contentType.mediaType.toString()}")

case class InvalidImageIsbn(fileName: String) extends RuntimeException(
  s"Could not find ISBN in filename: $fileName")

case class UnsupportedImageExtension(extension: String) extends RuntimeException(
  s"Unsupported image extension: $extension")
