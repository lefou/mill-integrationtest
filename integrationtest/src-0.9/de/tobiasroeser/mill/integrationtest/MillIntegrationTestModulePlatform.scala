package de.tobiasroeser.mill.integrationtest

import mill.T
import mill.define.{Command, TaskModule}
import mill.scalalib.OfflineSupportModule

trait MillIntegrationTestModulePlatform extends OfflineSupportModule { this: MillIntegrationTestModule =>
  override def prepareOffline(): Command[Unit] = T.command {
    super.prepareOffline()()
    downloadMillTestVersion()
    resolvedPrefetchIvyDeps()
    ()
  }
}
