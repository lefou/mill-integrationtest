package de.tobiasroeser.mill.integrationtest

import de.tobiasroeser.mill.integrationtest.MillIntegrationTestModule.MillVersion
import org.scalatest.freespec.AnyFreeSpec

import scala.util.Success

class MillIntegrationTestModuleSpec extends AnyFreeSpec {

  s"${classOf[MillIntegrationTestModule].getName()}" - {
    for {
      (version, expected) <- Seq(
        "0.4.2-16-abed13" -> Success(MillVersion(0, 4, 2, None, Some("16-abed13"), None)),
        "0.9.9" -> Success(MillVersion(0, 9, 9)),
        "0.10.0-M2" -> Success(MillVersion(0, 10, 0, Some(2), None, None)),
        "0.10.0-M2-2-927d1fa" -> Success(MillVersion(0, 10, 0, Some(2), Some("2-927d1fa"), None)),
        "0.12.0-RC2" -> Success(MillVersion(0, 12, 0, None, None, Some(2)))
      )
    } {
      s"parseVersion should parse: ${version}" in {
        val parsed = MillIntegrationTestModule.parseVersion(version)
        assert(parsed === expected)
      }
    }

  }
}
