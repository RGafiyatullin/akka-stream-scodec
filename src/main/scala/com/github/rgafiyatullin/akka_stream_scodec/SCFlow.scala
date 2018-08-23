package com.github.rgafiyatullin.akka_stream_scodec

import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.github.rgafiyatullin.akka_stream_scodec.stages.functional.{SCDecodeStage, SCEncodeStage}

object SCFlow {
  type EncoderMat = SCEncodeStage.MatValue
  type DecoderMat = SCDecodeStage.MatValue

  def encode[T](codec: SCEncodeStage.Encoder[T]): Flow[T, ByteString, EncoderMat] =
    Flow.fromGraph(SCEncodeStage(codec).toGraph)

  def decode[T](codec: SCDecodeStage.Decoder[T]): Flow[ByteString, T, DecoderMat] =
    Flow.fromGraph(SCDecodeStage(codec).toGraph)
}
