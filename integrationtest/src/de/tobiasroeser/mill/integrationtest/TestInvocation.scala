package de.tobiasroeser.mill.integrationtest

sealed trait TestInvocation

object TestInvocation {
  final case class Targets(targets: Seq[String], expectedExitCode: Int = 0) extends TestInvocation {
    override def toString: String =
      getClass().getSimpleName() +
        "(targets=" + targets +
        ",expectedExitCode=" + expectedExitCode +
        ")"
  }

  object Targets {
    implicit def rw: upickle.default.ReadWriter[Targets] = upickle.default.macroRW
  }

  implicit def rw: upickle.default.ReadWriter[TestInvocation] = upickle.default.macroRW
}
