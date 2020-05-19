package de.tobiasroeser.mill.integrationtest

case class TestCaseResult(
  name: String,
  result: TestResult,
  invocations: Seq[TestInvocationResult]
) {
  override def toString(): String = {
    val prefix = "\n    "
    s"""Test case: $name ==> $result
       |  Invocations: ${
      invocations
        .map(i => s"""${i.testInvocation} ==> ${i.result}""")
        .mkString(prefix, prefix, "")
    }""".stripMargin
  }
}

object TestCaseResult {
  implicit def rw: upickle.default.ReadWriter[TestCaseResult] = upickle.default.macroRW
}