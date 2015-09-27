package org.dryft.colorstreetmap

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

import scala.io.StdIn

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.stream.ActorMaterializer

object Parameters {

  val Width = 10
  val Height = 10
  val Pixels = Width * Height
}

object Directives {

  val Xyz = path(IntNumber / IntNumber / IntNumber ~ ".png")
}

object Routes {

  val Xyz = Directives.Xyz { (z, x, y) =>
    get {
      complete {
        HttpEntity(ContentType(MediaTypes.`image/png`, None), Renderers.asPng(z, x, y))
      }
    }
  }
}

object Handlers {

  implicit def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: Throwable =>
      extractUri { uri =>
        println(s"Request to $uri could not be handled normally")
        println(e)

        complete(HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(e.getMessage)))
      }
  }
}

object Renderers {

  import Parameters._

  private def sanitizeInputs(inputs: Int*): Seq[Int] =
    inputs.map(input => (input max 0) min 255)

  def asPng(z: Int, x: Int, y: Int): Array[Byte] = {
    val Seq(r, g, b) = sanitizeInputs(z, x, y) // which becomes r, g, & b
    val color = r << 16 | g << 8 | b
    println(Integer.toString(color, 16))

    val image = new BufferedImage(Width, Height, BufferedImage.TYPE_INT_RGB)
    image.setRGB(0, 0, Width, Height, Array.fill(Pixels)(color), 0, 0)

    val outputStream = new ByteArrayOutputStream
    ImageIO.write(image, "png", outputStream)
    outputStream.toByteArray
  }
}

object Main extends App {

  implicit val system = ActorSystem("ColorStreetMap")
  implicit val materializer = ActorMaterializer()

  import Handlers._
  import Routes._

  val routes = Xyz

  val bindingFuture = Http().bindAndHandle(routes, "localhost", 8080)

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop")
  StdIn.readLine()

  import system.dispatcher

  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.shutdown())
}
