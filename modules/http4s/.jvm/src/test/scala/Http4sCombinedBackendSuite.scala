/*
 * Copyright 2022 Hossein Naderi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.hnaderi.k8s.client.http4s

import cats.Foldable
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._
import dev.hnaderi.k8s.client.ExecEvent
import dev.hnaderi.k8s.client.ExecInput
import dev.hnaderi.k8s.utils.KSON
import fs2.Stream
import io.k8s.apimachinery.pkg.apis.meta.v1.Status
import munit.CatsEffectSuite
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import org.http4s.Response
import org.http4s.client.Client
import org.http4s.client.websocket._
import scodec.bits.ByteVector

class Http4sCombinedBackendSuite extends CatsEffectSuite {

  // Stubs for the HTTP path — never called in these exec-only tests
  private implicit val ksonEncoder: EntityEncoder[IO, KSON] =
    EntityEncoder.stringEncoder[IO].contramap[KSON](_ => "{}")
  private implicit val ksonDecoder: EntityDecoder[IO, KSON] =
    EntityDecoder.text[IO].map(_ => KSON.KNull)

  private val dummyClient: Client[IO] =
    Client(_ => Resource.eval(IO.pure(Response[IO]())))

  /** Builds a mock WSClient backed by a pre-loaded list of receive frames. When
    * sendCount > 0 the receive stream stays open until that many frames have
    * been sent by the backend, then terminates.
    */
  private def mockWSClient(
      receiveFrames: List[WSFrame],
      sendCount: Int = 0
  ): IO[(WSClient[IO], IO[Option[WSRequest]], IO[List[WSFrame]])] =
    for {
      reqRef <- IO.ref(Option.empty[WSRequest])
      idx <- IO.ref(0)
      sentRef <- IO.ref(List.empty[WSFrame])
      sentSoFar <- IO.ref(0)
      sendsDone <- IO.deferred[Unit]
    } yield {
      val conn = new WSConnection[IO] {
        def send(wsf: WSFrame): IO[Unit] =
          sentRef.update(_ :+ wsf) >>
            sentSoFar.updateAndGet(_ + 1).flatMap { n =>
              if (sendCount > 0 && n >= sendCount) sendsDone.complete(()).void
              else IO.unit
            }

        def sendMany[G[_]: Foldable, A <: WSFrame](wsfs: G[A]): IO[Unit] =
          wsfs.toList.traverse_(send)

        def receive: IO[Option[WSFrame]] =
          idx.modify { i =>
            if (i < receiveFrames.size) (i + 1, IO.pure(receiveFrames.lift(i)))
            // if waiting for sends: block until sendsDone then return None (EOF)
            else if (sendCount > 0) (i, sendsDone.get.as(Option.empty[WSFrame]))
            // otherwise EOF immediately
            else (i, IO.pure(Option.empty[WSFrame]))
          }.flatten

        def subprotocol: Option[String] = None
      }

      val wsClient = WSClient[IO](respondToPings = false) { req =>
        Resource.eval(reqRef.set(Some(req))).as(conn)
      }

      (wsClient, reqRef.get, sentRef.get)
    }

  private def backend(ws: WSClient[IO]): Http4sCombinedBackend[IO, KSON] =
    Http4sCombinedBackend.fromClients[IO, KSON](dummyClient, ws)

  private def run(
      ws: WSClient[IO],
      url: String = "ws://host/exec",
      input: Stream[IO, ExecInput] = Stream.empty
  ): IO[List[ExecEvent]] =
    backend(ws).execConnect(url, Nil, Nil, Nil)(input).compile.toList

  // ---- channel byte parsing (receive side) ----

  test("channel 1 emits Stdout") {
    val data = "hello world".getBytes("UTF-8")
    for {
      mock <- mockWSClient(
        List(WSFrame.Binary(ByteVector(1.toByte) ++ ByteVector(data)))
      )
      (ws, _, _) = mock
      events <- run(ws)
    } yield {
      assertEquals(events.length, 1)
      assertEquals(
        events.head.asInstanceOf[ExecEvent.Stdout].data.toList,
        data.toList
      )
    }
  }

  test("channel 2 emits Stderr") {
    val data = "something failed".getBytes("UTF-8")
    for {
      mock <- mockWSClient(
        List(WSFrame.Binary(ByteVector(2.toByte) ++ ByteVector(data)))
      )
      (ws, _, _) = mock
      events <- run(ws)
    } yield {
      assertEquals(events.length, 1)
      assertEquals(
        events.head.asInstanceOf[ExecEvent.Stderr].data.toList,
        data.toList
      )
    }
  }

  test("channel 3 with valid Status JSON emits Error") {
    // {"status":"Success","code":0} — only the fields Status.decoder reads
    val json = """{"status":"Success","code":0}""".getBytes("UTF-8")
    for {
      mock <- mockWSClient(
        List(WSFrame.Binary(ByteVector(3.toByte) ++ ByteVector(json)))
      )
      (ws, _, _) = mock
      events <- run(ws)
    } yield assertEquals(
      events,
      List(ExecEvent.Error(Status(status = Some("Success"), code = Some(0))))
    )
  }

  test("channel 3 with malformed JSON emits nothing") {
    val garbage = "not json at all".getBytes("UTF-8")
    for {
      mock <- mockWSClient(
        List(WSFrame.Binary(ByteVector(3.toByte) ++ ByteVector(garbage)))
      )
      (ws, _, _) = mock
      events <- run(ws)
    } yield assertEquals(events, Nil)
  }

  test("unknown channel emits nothing") {
    val data = "ignored".getBytes("UTF-8")
    for {
      mock <- mockWSClient(
        List(WSFrame.Binary(ByteVector(9.toByte) ++ ByteVector(data)))
      )
      (ws, _, _) = mock
      events <- run(ws)
    } yield assertEquals(events, Nil)
  }

  test("empty binary frame produces no event") {
    for {
      mock <- mockWSClient(List(WSFrame.Binary(ByteVector.empty)))
      (ws, _, _) = mock
      events <- run(ws)
    } yield assertEquals(events, Nil)
  }

  test("multiple channels in sequence") {
    val out = "out".getBytes("UTF-8")
    val err = "err".getBytes("UTF-8")
    for {
      mock <- mockWSClient(
        List(
          WSFrame.Binary(ByteVector(1.toByte) ++ ByteVector(out)),
          WSFrame.Binary(ByteVector(2.toByte) ++ ByteVector(err))
        )
      )
      (ws, _, _) = mock
      events <- run(ws)
    } yield {
      assertEquals(events.length, 2)
      assertEquals(
        events(0).asInstanceOf[ExecEvent.Stdout].data.toList,
        out.toList
      )
      assertEquals(
        events(1).asInstanceOf[ExecEvent.Stderr].data.toList,
        err.toList
      )
    }
  }

  // ---- input frame encoding (send side) ----

  test("Stdin encodes to channel-0 binary frame") {
    val data = "hello".getBytes("UTF-8")
    for {
      mock <- mockWSClient(Nil, sendCount = 1)
      (ws, _, getSent) = mock
      _ <- run(ws, input = Stream.emit(ExecInput.Stdin(data)))
      sent <- getSent
    } yield {
      assertEquals(sent.length, 1)
      val frame = sent.head.asInstanceOf[WSFrame.Binary]
      assertEquals(frame.data.headOption.map(_ & 0xff), Some(0))
      assertEquals(frame.data.tail.toArray.toList, data.toList)
    }
  }

  test("Resize encodes to channel-4 JSON frame") {
    val expected = """{"Width":80,"Height":24}""".getBytes("UTF-8")
    for {
      mock <- mockWSClient(Nil, sendCount = 1)
      (ws, _, getSent) = mock
      _ <- run(ws, input = Stream.emit(ExecInput.Resize(80, 24)))
      sent <- getSent
    } yield {
      assertEquals(sent.length, 1)
      val frame = sent.head.asInstanceOf[WSFrame.Binary]
      assertEquals(frame.data.headOption.map(_ & 0xff), Some(4))
      assertEquals(frame.data.tail.toArray.toList, expected.toList)
    }
  }

  // ---- URL scheme conversion ----

  test("https scheme is rewritten to wss") {
    for {
      mock <- mockWSClient(Nil)
      (ws, getReq, _) = mock
      _ <- run(
        ws,
        url =
          "https://k8s.example.com:6443/api/v1/namespaces/default/pods/p/exec"
      )
      req <- getReq
    } yield assertEquals(req.flatMap(_.uri.scheme.map(_.value)), Some("wss"))
  }

  test("http scheme is rewritten to ws") {
    for {
      mock <- mockWSClient(Nil)
      (ws, getReq, _) = mock
      _ <- run(
        ws,
        url = "http://localhost:8001/api/v1/namespaces/default/pods/p/exec"
      )
      req <- getReq
    } yield assertEquals(req.flatMap(_.uri.scheme.map(_.value)), Some("ws"))
  }

  // ---- query params ----

  test("params appear in the WS request query string") {
    for {
      mock <- mockWSClient(Nil)
      (ws, getReq, _) = mock
      _ <- backend(ws)
        .execConnect(
          "ws://host/exec",
          Nil,
          Seq("command" -> "ls", "stdout" -> "true"),
          Nil
        )(
          Stream.empty
        )
        .compile
        .drain
      req <- getReq
    } yield {
      val pairs = req.map(_.uri.query.pairs).getOrElse(Nil)
      assert(pairs.contains(("command", Some("ls"))))
      assert(pairs.contains(("stdout", Some("true"))))
    }
  }

  // ---- Sec-WebSocket-Protocol header ----

  test("Sec-WebSocket-Protocol header is set to v4.channel.k8s.io") {
    for {
      mock <- mockWSClient(Nil)
      (ws, getReq, _) = mock
      _ <- run(ws)
      req <- getReq
    } yield {
      val proto = req
        .map(_.headers.headers)
        .getOrElse(Nil)
        .find(_.name.toString.equalsIgnoreCase("Sec-WebSocket-Protocol"))
        .map(_.value)
      assertEquals(proto, Some("v4.channel.k8s.io"))
    }
  }

  // ---- cookie header ----

  test("non-empty cookies produce a Cookie header") {
    for {
      mock <- mockWSClient(Nil)
      (ws, getReq, _) = mock
      _ <- backend(ws)
        .execConnect(
          "ws://host/exec",
          Nil,
          Nil,
          Seq("tok" -> "abc", "sid" -> "xyz")
        )(
          Stream.empty
        )
        .compile
        .drain
      req <- getReq
    } yield {
      val cookieValue = req
        .map(_.headers.headers)
        .getOrElse(Nil)
        .find(_.name.toString.equalsIgnoreCase("Cookie"))
        .map(_.value)
      assertEquals(cookieValue, Some("tok=abc; sid=xyz"))
    }
  }

  test("empty cookies produce no Cookie header") {
    for {
      mock <- mockWSClient(Nil)
      (ws, getReq, _) = mock
      _ <- run(ws)
      req <- getReq
    } yield {
      val hasCookie = req
        .map(_.headers.headers)
        .getOrElse(Nil)
        .exists(_.name.toString.equalsIgnoreCase("Cookie"))
      assert(!hasCookie)
    }
  }
}
