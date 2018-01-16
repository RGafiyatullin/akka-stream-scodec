package com.github.rgafiyatullin.akka_stream_scodec

import akka.stream.scaladsl.Flow
import akka.util.ByteString
import scodec.Codec

object SCFlow {
  def encode[T](codec: Codec[T]): Flow[T, ByteString, _] =
    Flow.fromGraph(SCEncodeStage(codec))

  def decode[T](codec: Codec[T]): Flow[ByteString, T, _] =
    Flow.fromGraph(SCDecodeStage(codec))
}
