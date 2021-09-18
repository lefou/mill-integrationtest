package de.tobiasroeser.mill.integrationtest

case class TestInvocationResult(
    testInvocation: TestInvocation,
    result: TestResult,
    out: Seq[String],
    err: Seq[String],
    logFile: Option[os.Path]
)

object TestInvocationResult {
  implicit def logFileRw: upickle.default.ReadWriter[os.Path] = upickle.default.readwriter[String].bimap[os.Path](
    p => p.toString(),
    s => os.Path(s)
  )
  implicit def rw: upickle.default.ReadWriter[TestInvocationResult] = upickle.default.macroRW
}
