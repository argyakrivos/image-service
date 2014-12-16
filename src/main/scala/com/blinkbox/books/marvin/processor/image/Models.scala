package com.blinkbox.books.marvin.processor.image

import java.net.URI

import scala.concurrent.Future
import scala.io.Source

trait StorageService {
  def store(source: Source, label: String, extension: String, originalFile: Option[String] = None): Future[URI]
  def retrieve(token: URI): Future[Source]
}
