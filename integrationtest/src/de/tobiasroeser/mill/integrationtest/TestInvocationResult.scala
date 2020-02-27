package de.tobiasroeser.mill.integrationtest

case class TestInvocationResult(
  testInvocation: TestInvocation,
  result: TestResult,
  out: Seq[String],
  err: Seq[String]
)

object TestInvocationResult {
  implicit def rw: upickle.default.ReadWriter[TestInvocationResult] = upickle.default.macroRW
}
