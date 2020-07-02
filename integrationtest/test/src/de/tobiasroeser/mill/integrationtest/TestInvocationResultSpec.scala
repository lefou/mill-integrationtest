package de.tobiasroeser.mill.integrationtest

import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import upickle.default._

class TestInvocationResultSpec extends AnyFreeSpec with ScalaCheckPropertyChecks {
  classOf[TestInvocationResult].getName() - {
    "should properly map to JSON and back" in {

      import TestData._

      forAll { (tr: TestInvocationResult) =>
        assert(tr === read[TestInvocationResult](write(tr)))
      }
    }
  }
}
