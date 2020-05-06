package de.tobiasroeser.mill.integrationtest

/**
 * Run Integration for Mill Plugin across different versions of Scala.
 */
trait CrossMillIntegrationTestModule extends MillIntegrationTestModule {
  override def millSourcePath = super.millSourcePath / ammonite.ops.up
}
