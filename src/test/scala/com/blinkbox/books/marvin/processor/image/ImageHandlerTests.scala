package com.blinkbox.books.marvin.processor.image

import java.io.{ByteArrayInputStream, InputStream, OutputStream}
import java.net.URI

import akka.actor.{ActorRef, ActorSystem, Props, Status}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import com.blinkbox.books.imageio.{ImageProcessor, ImageSettings}
import com.blinkbox.books.json.DefaultFormats
import com.blinkbox.books.messaging._
import com.blinkbox.books.quartermaster.common.mapping.StorageService
import com.blinkbox.books.schemas.ingestion.book.v2.Book
import com.blinkbox.books.schemas.ingestion.book.v2.Book.{Classification, Image}
import com.blinkbox.books.schemas.ingestion.file.pending.v2.FilePending
import com.blinkbox.books.schemas.ingestion.file.pending.v2.FilePending.{FileSource, SystemDetails}
import com.blinkbox.books.test.MockitoSyrup
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s.JValue
import org.json4s.JsonAST.JNothing
import org.json4s.JsonDSL._
import org.junit.runner.RunWith
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions

@RunWith(classOf[JUnitRunner])
class ImageHandlerTests extends TestKit(ActorSystem("test-system")) with ImplicitSender
  with FlatSpecLike with BeforeAndAfterAll with MockitoSyrup with Matchers {

  implicit val json4sJacksonFormats = DefaultFormats
  val retryInterval = 100.millis

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  behavior of "The Image Processor"

  it must "process a cover image and send a metadata message" in new TestFixture {
    handler ! imageEvent()
    checkNoFailures()
    expectMsgType[Status.Success]
    publisher.expectMsgPF() {
      case Event(EventHeader(_, _, originator, _, _, _), Book.Cover(classification, isbn, media, source)) =>
        originator shouldEqual "image-processor"
        isbn shouldEqual "9780111222333"
        classification should contain (Classification("isbn", "9780111222333"))
        classification should contain (Classification("source_username", "randomhouse_uk"))
        media.images.getOrElse(List.empty) should contain (Image(
          List(Classification("type", "front_cover")),
          new URI("bbbmap:testfile:/mnt/storage/cover/67d7d660-fca9-4ce2-bdd9-c338695422cb.png"),
          2400,
          2400,
          0
        ))
    }
  }

  it must "not process an invalid message" in new TestFixture {
    val event = jsonEvent("field" -> "value")
    handler ! event
    checkFailure[InvalidMessageType](event)
  }

  it must "not process a message without a valid ISBN as part of the filename" in new TestFixture {
    val event = imageEvent(fileName = "xxx")
    handler ! event
    checkFailure[InvalidImageIsbn](event)
  }

  it must "not process a message with an invalid image URI" in new TestFixture {
    val event = imageEvent(fileName = "9780111222333@£%.jpg")
    handler ! event
    checkFailure[IllegalArgumentException](event)
  }

  it must "not process a message with an unsupported extension" in new TestFixture {
    val event = imageEvent(fileName = "9780111222333.test")
    handler ! event
    checkFailure[UnsupportedImageExtension](event)
  }

  trait TestFixture {
    val config = mock[ImageOutputConfig]
    doReturn("png").when(config).fileType
    doReturn(2400).when(config).maxWidth
    doReturn(2400).when(config).maxHeight
    doReturn("cover").when(config).label

    val storageService = mock[StorageService]
    doReturn(
      Future.successful(new ByteArrayInputStream("test".toCharArray.map(_.toByte)))
    ).when(storageService).retrieve(any[URI])
    doReturn(
      Future.successful(new URI("bbbmap:testfile:/mnt/storage/cover/67d7d660-fca9-4ce2-bdd9-c338695422cb.png"))
    ).when(storageService).store(any[Array[Byte]], anyString(), anyString(), any[Option[String]])

    val imageProcessor = mock[ImageProcessor]
    doNothing().when(imageProcessor).transform(
      anyString(), any[InputStream], any[OutputStream], any[ImageSettings], any[Option[(ImageSettings => Unit)]])

    val publisher = TestProbe()

    val errorHandler = mock[ErrorHandler]
    doReturn(Future.successful(())).when(errorHandler).handleError(any[Event], any[Throwable])

    val handler: ActorRef = TestActorRef(Props(
      new ImageHandler(config, storageService, imageProcessor, publisher.ref, errorHandler, retryInterval)))

    def imageEvent(
      uri: URI = new URI("bbbmap:testfile:/mnt/storage"),
      username: String = "randomhouse_uk",
      fileName: String = "path/to/9780111222333.jpg",
      contentType: String = "image/jpeg",
      role: String = "publisher_ftp"): Event =
      Event.json(
        EventHeader("image-processor"),
        FilePending.Details(FileSource(
          DateTime.now(DateTimeZone.UTC), uri, username, fileName, contentType,
          SystemDetails("Marvin/watcher", "xxx"), role
        ))
      )

    def jsonEvent(json: JValue = JNothing, mediaType: String = "application/vnd.blinkbox.books.ingestion.file.pending.v2+json") = {
      implicit object JsonEvent extends JsonEventBody[JValue] {
        val jsonMediaType = MediaType(mediaType)
      }
      Event.json(EventHeader(mediaType), json)
    }

    /** Check no errors were sent. */
    def checkNoFailures(): Unit = {
      verify(errorHandler, times(0)).handleError(any[Event], any[Throwable])
    }

    /** Check that event processing failed and was treated correctly. */
    def checkFailure[T <: Throwable](event: Event)(implicit manifest: Manifest[T]): Unit = {
      // Check event was passed on to error handler, along with the expected exception.
      val expectedExceptionClass = manifest.runtimeClass.asInstanceOf[Class[T]]
      verify(errorHandler).handleError(eql(event), isA(expectedExceptionClass))
    }
  }
}
