package de.tobiasroeser.mill.integrationtest

import os.Path

sealed trait TestInvocation

object TestInvocation {
  final case class Targets(targets: Seq[String], expectedExitCode: Int = 0) extends TestInvocation {
    def run(millPath: Path, cwd: Path): TestInvocationResult = {
      // run mill with test targets
      // ENV=env mill -i testTargets
      val cmdRes = os.proc(millPath, targets).call(cwd, check = false)

      val testRes = if (cmdRes.exitCode == expectedExitCode) {
        TestResult.Success
      } else {
        TestResult.Failed
      }

      TestInvocationResult(this, testRes, cmdRes.out.lines, cmdRes.err.lines)
    }
  }

  object Targets {
    implicit def rw: upickle.default.ReadWriter[Targets] = upickle.default.macroRW
  }

  implicit def rw: upickle.default.ReadWriter[TestInvocation] = upickle.default.macroRW
}