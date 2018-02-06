package com.github.rgafiyatullin.akka_stream_scodec

import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.github.rgafiyatullin.akka_stream_scodec.stages.functional.{SCEncodeStage, SCDecodeStage}
import scodec.Codec

object SCFlow {
  type EncoderMat = SCEncodeStage.MatValue
  type DecoderMat = SCDecodeStage.MatValue

  def encode[T](codec: Codec[T]): Flow[T, ByteString, EncoderMat] =
    Flow.fromGraph(SCEncodeStage(codec).toGraph)

  def decode[T](codec: Codec[T]): Flow[ByteString, T, DecoderMat] =
    Flow.fromGraph(SCDecodeStage(codec).toGraph)
}
