package com.github.rgafiyatullin.akka_stream_scodec

import scala.language.existentials

sealed trait SCError extends Exception {
  val scErr: scodec.Err
  val codec: scodec.Codec[_]

  override def getMessage: String =
    scErr.messageWithContext
}

final case class SCEncodeError(scErr: scodec.Err, codec: scodec.Codec[_]) extends SCError
final case class SCDecodeError(scErr: scodec.Err, codec: scodec.Codec[_]) extends SCError
