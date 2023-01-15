package de.tobiasroeser.mill.integrationtest

import mainargs.Flag
import mill.T
import mill.define.Command
import mill.scalalib.OfflineSupportModule

trait MillIntegrationTestModulePlatform extends OfflineSupportModule { this: MillIntegrationTestModule =>
  override def prepareOffline(all: Flag): Command[Unit] = T.command {
    super.prepareOffline(all)()
    downloadMillTestVersion()
    resolvedPrefetchIvyDeps()
    ()
  }

  def resolvedPrefetchIvyDeps = T {
    resolveSeparateDeps(T.task { prefetchIvyDeps().map(bindDependency()) })()
  }
}
