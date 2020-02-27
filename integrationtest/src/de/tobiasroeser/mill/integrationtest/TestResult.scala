package de.tobiasroeser.mill.integrationtest

sealed trait TestResult

object TestResult {
  case object Success extends TestResult
  case object Skipped extends TestResult
  case object Failed extends TestResult

  implicit def rw: upickle.default.ReadWriter[TestResult] = upickle.default.readwriter[String].bimap (
    _ match {
      case Success => "success"
      case Skipped => "skipped"
      case Failed => "failed"
    },
    _ match {
      case "success" => Success
      case "skipped" => Skipped
      case "failed" => Failed
    }
  )
}
