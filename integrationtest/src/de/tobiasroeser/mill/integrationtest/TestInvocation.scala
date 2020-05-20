package de.tobiasroeser.mill.integrationtest

import os.{Path, Pipe, ProcessInput, ProcessOutput, Shellable, proc}
import upickle.default.{ReadWriter => RW, macroRW}

sealed trait TestInvocation

object TestInvocation {
  case class Cmd(cmd: proc, expectedExitCode: Int = 0) extends TestInvocation {
    def run(cwd: Path = null,
            env: Map[String, String] = null,
            stdin: ProcessInput = Pipe,
            stdout: ProcessOutput = Pipe,
            stderr: ProcessOutput = os.Inherit,
            mergeErrIntoOut: Boolean = false,
            timeout: Long = -1,
            check: Boolean = false,
            propagateEnv: Boolean = true): TestInvocationResult = {
      val cmdRes = cmd.call(cwd, env, stdin, stdout, stderr, mergeErrIntoOut, timeout, check, propagateEnv)
      val testRes = if (cmdRes.exitCode == expectedExitCode) {
        TestResult.Success
      } else {
        TestResult.Failed
      }

      TestInvocationResult(this, testRes, cmdRes.out.lines, cmdRes.err.lines)
    }
  }

  object Cmd {
    implicit def shellableRw: RW[Shellable] = macroRW
    implicit def procRw: RW[proc] = macroRW
    implicit def rw: RW[Cmd] = macroRW
  }

  final case class Targets(targets: Seq[String], expectedExitCode: Int = 0) extends TestInvocation {
    def run(millPath: Path, cwd: Path): TestInvocationResult =
      Cmd(proc(millPath, targets), expectedExitCode).run(cwd)
  }

  object Targets {
    implicit def rw: RW[Targets] = macroRW
  }

  implicit def rw: RW[TestInvocation] = macroRW
}
