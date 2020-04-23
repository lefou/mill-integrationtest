package de.tobiasroeser.mill.integrationtest

import mill.api.Ctx
import mill.scalalib.publish.Artifact

// TODO: remove this in favoor of mill 0.6.2's LocalIvyPublisher
class LocalIvyPublisher(localIvyRepo: os.Path = os.home / ".ivy2" / "local") {

  def publish(
               jar: os.Path,
               sourcesJar: os.Path,
               docJar: os.Path,
               pom: os.Path,
               ivy: os.Path,
               artifact: Artifact
             )(implicit ctx: Ctx.Log): Unit = {
    ctx.log.info(s"Publishing ${artifact} to ivy repo ${localIvyRepo}")
    val releaseDir = localIvyRepo / artifact.group / artifact.id / artifact.version
    writeFiles(
      jar -> releaseDir / "jars" / s"${artifact.id}.jar",
      sourcesJar -> releaseDir / "srcs" / s"${artifact.id}-sources.jar",
      docJar -> releaseDir / "docs" / s"${artifact.id}-javadoc.jar",
      pom -> releaseDir / "poms" / s"${artifact.id}.pom",
      ivy -> releaseDir / "ivys" / "ivy.xml"
    )
  }

  private def writeFiles(fromTo: (os.Path, os.Path)*): Unit = {
    fromTo.foreach {
      case (from, to) =>
        //        os.makeDir.all(to / os.up)
        os.copy.over(from, to, createFolders = true)
    }
  }

}
