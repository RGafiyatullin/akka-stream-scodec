package com.github.rgafiyatullin.akka_stream_scodec.stages.functional

import akka.NotUsed
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.github.rgafiyatullin.akka_stream_scodec.SCDecodeError
import com.github.rgafiyatullin.akka_stream_util.custom_stream_stage.Stage
import com.github.rgafiyatullin.akka_stream_util.custom_stream_stage.contexts._
import scodec.{Attempt, Codec}
import scodec.bits.BitVector
import scodec.interop.akka._

object SCDecodeStage {
  type MatValue = NotUsed
  type Shape[T] = FlowShape[ByteString, T]

  object State {
    def create[T](codec: Codec[T], shape: Shape[T]): State[T] =
      StateNormal(codec, shape, BitVector.empty)
  }

  sealed trait State[T] extends Stage.State[SCDecodeStage[T]]

  final case class StateInletClosed[T]
    (codec: Codec[T],
     shape: Shape[T],
     buffer: BitVector,
     failureOption: Option[Throwable])
      extends State[T]
  {
    def withBuffer(b: BitVector): StateInletClosed[T] =
      copy(buffer = b)

    override def outletOnPull
      (ctx: OutletPulledContext[SCDecodeStage[T]])
    : OutletPulledContext[SCDecodeStage[T]] =
      (codec.decode(buffer), failureOption) match {
        case (Attempt.Successful(result), None) if result.remainder.isEmpty =>
          ctx
            .push(shape.out, result.value)
            .withState(withBuffer(result.remainder))
            .completeStage()

        case (Attempt.Successful(result), Some(failure)) if result.remainder.isEmpty =>
          ctx
            .push(shape.out, result.value)
            .withState(withBuffer(result.remainder))
            .failStage(failure)

        case (Attempt.Successful(result), _) if result.remainder.nonEmpty =>
          ctx
            .push(shape.out, result.value)
            .withState(withBuffer(result.remainder))

        case (Attempt.Failure(reason), _) =>
          ctx.failStage(SCDecodeError(reason, codec))
      }

  }

  final case class StateNormal[T]
    (codec: Codec[T],
     shape: Shape[T],
     buffer: BitVector)
      extends State[T]
  {
    def withBuffer(b: BitVector): StateNormal[T] =
      copy(buffer = b)

    def feedOutletFromBuffer[Ctx <: Context[Ctx, SCDecodeStage[T]]](ctx: Ctx): Ctx =
      codec.decode(buffer) match {
        case Attempt.Successful(result) =>
          ctx
            .push(shape.out, result.value)
            .withState(withBuffer(result.remainder))

        case Attempt.Failure(_: scodec.Err.InsufficientBits) =>
          ctx.pull(shape.in)

        case Attempt.Failure(reason) =>
          ctx.failStage(SCDecodeError(reason, codec))
      }


    override def inletOnUpstreamFinish
      (ctx: InletFinishedContext[SCDecodeStage[T]])
    : InletFinishedContext[SCDecodeStage[T]] =
      ctx.withState(StateInletClosed(codec, shape, buffer, None))


    override def inletOnUpstreamFailure
      (ctx: InletFailedContext[SCDecodeStage[T]])
    : InletFailedContext[SCDecodeStage[T]] =
      ctx.withState(StateInletClosed(codec, shape, buffer, Some(ctx.reason)))

    override def inletOnPush
      (ctx: InletPushedContext[SCDecodeStage[T]])
    : InletPushedContext[SCDecodeStage[T]] =
      withBuffer(buffer ++ ctx.peek(shape.in).toByteVector.bits)
        .feedOutletFromBuffer(ctx.drop(shape.in))

    override def outletOnPull
      (ctx: OutletPulledContext[SCDecodeStage[T]])
    : OutletPulledContext[SCDecodeStage[T]] =
      feedOutletFromBuffer(ctx)
  }
}

final case class SCDecodeStage[T](codec: Codec[T]) extends Stage[SCDecodeStage[T]] {
  override type Shape = SCDecodeStage.Shape[T]
  override type State = SCDecodeStage.State[T]
  override type MatValue = SCDecodeStage.MatValue


  val inlet: Inlet[ByteString] = Inlet("In")
  val outlet: Outlet[T] = Outlet("Out")

  override def shape: SCDecodeStage.Shape[T] =
    FlowShape.of(inlet, outlet)

  override def initialStateAndMatValue
    (logic: Stage.RunnerLogic,
     inheritedAttributes: Attributes)
  : (SCDecodeStage.State[T], MatValue) =
    (SCDecodeStage.State.create(codec, shape), NotUsed)
}
