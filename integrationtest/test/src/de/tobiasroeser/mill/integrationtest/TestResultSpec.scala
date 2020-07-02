package de.tobiasroeser.mill.integrationtest

import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import upickle.default._

class TestResultSpec extends AnyFreeSpec with ScalaCheckPropertyChecks {
  classOf[TestResult].getName() - {
    "should properly map to JSON and back" in {

      import TestData._

      forAll {
        (tr: TestResult) =>
        assert(tr === read[TestResult](write(tr)))
      }
    }
  }
}
