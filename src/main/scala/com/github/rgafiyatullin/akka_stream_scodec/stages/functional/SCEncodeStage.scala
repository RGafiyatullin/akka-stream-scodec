package com.github.rgafiyatullin.akka_stream_scodec.stages.functional

import akka.NotUsed
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.github.rgafiyatullin.akka_stream_scodec.SCEncodeError
import com.github.rgafiyatullin.akka_stream_util.custom_stream_stage.Stage
import com.github.rgafiyatullin.akka_stream_util.custom_stream_stage.contexts.{InletPushedContext, OutletPulledContext}
import scodec.Codec
import scodec.interop.akka._

object SCEncodeStage {
  type Shape[T] = FlowShape[T, ByteString]
  type MatValue = NotUsed
  final case class State[T](
    codec: Codec[T],
    shape: Shape[T])
      extends Stage.State[SCEncodeStage[T]]
  {
    override def inletOnPush(ctx: InletPushedContext[SCEncodeStage[T]]): InletPushedContext[SCEncodeStage[T]] =
      codec
        .encode(ctx.peek(shape.in))
        .fold(
          { err => ctx.failStage(SCEncodeError(err, codec)) },
          { bits => ctx.drop(shape.in).push(shape.out, bits.bytes.toByteString) })

    override def outletOnPull(ctx: OutletPulledContext[SCEncodeStage[T]]): OutletPulledContext[SCEncodeStage[T]] =
      ctx.pull(shape.in)
  }

  object State {
    def create[T](codec: Codec[T], shape: Shape[T]): State[T] =
      State(codec, shape)
  }

}

final case class SCEncodeStage[T](codec: Codec[T]) extends Stage[SCEncodeStage[T]] {
  override type Shape = SCEncodeStage.Shape[T]
  override type State = SCEncodeStage.State[T]
  override type MatValue = SCEncodeStage.MatValue

  val inlet: Inlet[T] = Inlet("In")
  val outlet: Outlet[ByteString] = Outlet("Out")

  override def shape: FlowShape[T, ByteString] = FlowShape.of(inlet, outlet)

  override def initialStateAndMatValue
    (logic: Stage.RunnerLogic,
     inheritedAttributes: Attributes)
  : (SCEncodeStage.State[T], NotUsed) =
    (SCEncodeStage.State.create(codec, shape), NotUsed)
}
