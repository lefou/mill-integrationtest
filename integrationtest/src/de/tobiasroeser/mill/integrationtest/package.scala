package de.tobiasroeser.mill

package object integrationtest {
  @deprecated("use TestCaseResult instead", "0.3.2")
  type TestCase = TestCaseResult

  @deprecated("use TestCaseResult instead", "0.3.2")
  val TestCase = TestCaseResult
}
