package de.tobiasroeser.mill.integrationtest

sealed trait TestInvocation

object TestInvocation {
  final case class Targets(
      targets: Seq[String],
      expectedExitCode: Int = 0,
      env: Map[String, String] = Map()
  ) extends TestInvocation {
    override def toString: String =
      getClass().getSimpleName() +
        "(targets=" + targets +
        ",expectedExitCode=" + expectedExitCode +
        ",env=" + env
        ")"
  }

  object Targets {
    implicit def rw: upickle.default.ReadWriter[Targets] = upickle.default.macroRW

    // for backward compatibility
    def apply(targets: Seq[String], expectedExitCode: Int) =
      new Targets(targets, expectedExitCode)
  }

  implicit def rw: upickle.default.ReadWriter[TestInvocation] = upickle.default.macroRW
}
