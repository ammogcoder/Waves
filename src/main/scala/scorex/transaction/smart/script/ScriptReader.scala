package scorex.transaction.smart.script

import com.wavesplatform.crypto
import com.wavesplatform.lang.ScriptVersion
import com.wavesplatform.lang.ScriptVersion.Versions.V1
import com.wavesplatform.lang.v1.Serde
import scodec.Attempt.{Failure, Successful}
import scorex.transaction.ValidationError.ScriptParseError
import scorex.transaction.smart.script.v1.ScriptV1

object ScriptReader {

  val checksumLength = 4

  def fromBytes(bytes: Array[Byte]): Either[ScriptParseError, Script] = {
    val checkSum         = bytes.takeRight(checksumLength)
    val computedCheckSum = crypto.secureHash(bytes.dropRight(checksumLength)).take(checksumLength)
    val version          = bytes.head
    val scriptBytes      = bytes.drop(1).dropRight(checksumLength)

    for {
      _ <- Either.cond(checkSum.sameElements(computedCheckSum), (), ScriptParseError("Invalid checksum"))
      sv <- ScriptVersion
        .fromInt(version)
        .fold[Either[ScriptParseError, ScriptVersion]](Left(ScriptParseError(s"Invalid version: $version")))(v => Right(v))
      script <- sv match {
        case V1 =>
          ScriptV1
            .validateBytes(scriptBytes)
            .flatMap { _ =>
              Serde.codec.decode(scodec.bits.BitVector(scriptBytes)) match {
                case Failure(e)    => Left(e.toString())
                case Successful(x) => ScriptV1(x.value, checkSize = false)
              }
            }
            .left
            .map(ScriptParseError)
      }
    } yield script
  }

}
