package fs2
package io
package net

import cats.effect.IO
import scala.concurrent.duration._
import com.comcast.ip4s._
import fs2.io.net.Network
import fs2.Chunk

import cats.effect.kernel.Resource

class DatagramSocketSuite extends Fs2Suite {

  val message = Chunk.array("hello-udp".getBytes)

  def setup: Resource[IO, (DatagramSocket[IO], DatagramSocket[IO])] =
    for {
      server <- Network[IO].bindDatagramSocket(SocketAddress(ip"127.0.0.1", Port.Wildcard))
      client <- Network[IO].bindDatagramSocket(SocketAddress(ip"127.0.0.1", Port.Wildcard))
    } yield (server, client)

  group("udp") {

    test("send and receive a datagram") {
      setup.use { case (server, client) =>
        val send = client.write(Datagram(server.address.asIpUnsafe, message))
        val receive = server.read.map(_.bytes)
        (send >> receive).map { result =>
          assertEquals(result, message)
        }
      }
    }

    test("connected socket can send without specifying address") {
      setup.use { case (server, client) =>
        for {
          _ <- client.connect(server.address)
          _ <- client.write(message)
          recv <- server.read
        } yield assertEquals(recv.bytes, message)
      }
    }

    test("reads emits multiple messages") {
      val count = 10
      val bytes = Chunk.array("msg".getBytes)

      setup.use { case (server, client) =>
        val sendAll = Stream
          .emit(Datagram(server.address.asIpUnsafe, bytes))
          .repeatN(count.toLong)
          .covary[IO]
          .through(client.writes)

        val receiveAll = server.reads.take(count.toLong).compile.toList

        sendAll.compile.drain >>
          receiveAll.map { received =>
            assertEquals(received.map(_.bytes), List.fill(count)(bytes))
          }
      }
    }


test("writing without address fails after disconnect") {
  setup.use { case (server, client) =>
    for {
      _ <- client.connect(server.address)
      _ <- client.disconnect
      _ <- client.connect(SocketAddress(ip"127.0.0.1", port"9999"))
      _ <- client.write(message)
      maybe <- server.read.timeoutTo(500.millis, IO.pure(null))
    } yield assert(maybe == null)
  }
}


    test("send and receive large datagram") {
  val largeMsg = Chunk.array(Array.fill(65000)(42.toByte))
  setup.use { case (server, client) =>
    for {
      _ <- client.write(Datagram(server.address.asIpUnsafe, largeMsg))
      received <- server.read.map(_.bytes)
    } yield assertEquals(received.size, 65000)
  }
}

  }
}
