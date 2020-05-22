package de.tobiasroeser.mill.integrationtest

import mill.api.Ctx
import os.Path

/** This can be used by overriding [[MillIntegrationTestModule.itestConfig]] to config some itest behaviour.
  * @param useGlobalMillCache
  *        + If true (default), [[MillIntegrationTestModule.downloadMillTestVersion]] will re-use the system
  *          mill assembly downloaded by this scrip: https://github.com/lihaoyi/mill/blob/master/mill
  *          (normally, it is `~/mill/download/<your_millTestVersion>`)
  *          So itest don't need to re-download mill test version
  *          - if there is one there
  *          - and after you run `mill clean` or `rm -Rf out`
  *        + If false, mill will be downloaded to a sub-path of `./out`
  * @param publishEmptySourceAndDoc
  *        + If true, when [[MillIntegrationTestModule]] publish `pluginsUnderTest` and `temporaryIvyModules`
  *          before testing, it will publish empty `-sources.jar` & `-javadoc.jar` to speedup the test process.
  *        + If false, the actual [[mill.scalalib.JavaModule.sourceJar]] and [[mill.scalalib.JavaModule.docJar]]
  *          will be invoked
  */
case class ITestConfig(
  useGlobalMillCache: Boolean = true,
  publishEmptySourceAndDoc: Boolean = true,
) {
  def millTestVersionDownloadPath(fullVersion: String)(implicit ctx: Ctx): Path =
    if (useGlobalMillCache) {
      val cacheDir = ctx.env
        .get("XDG_CACHE_HOME")
        .map(os.Path(_))
        .getOrElse(os.home / ".cache")
      cacheDir / "mill" / "download" / fullVersion
    } else {
      ctx.dest / s"mill-$fullVersion.jar"
    }
}
