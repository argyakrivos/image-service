package com.blinkbox.books.marvin.processor.image


import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.blinkbox.books.test.MockitoSyrup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.language.implicitConversions

@RunWith(classOf[JUnitRunner])
class ImageHandlerTests extends TestKit(ActorSystem("test-system")) with ImplicitSender
  with FlatSpecLike with BeforeAndAfterAll with MockitoSyrup with Matchers {

}
