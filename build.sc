// mill plugins
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version:0.0.1`
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest:0.3.1`

import scala.util.matching.Regex
import mill._
import mill.scalalib._
import mill.define.{Sources, Task}
import mill.scalalib.publish._
import os.Path
import de.tobiasroeser.mill.vcs.version.VcsVersion
import de.tobiasroeser.mill.integrationtest.MillIntegrationTestModule

val baseDir = build.millSourcePath
val rtMillVersion = build.version

// Tuple: Mill version -> Scala version
val millApiCrossVersions = Seq(
  "0.7.0" -> "2.13.2",
  "0.6.2" -> "2.12.11"
)

// Tuple: Mill version -> Mill API version
val itestMillVersions = Seq(
  "0.7.3" -> "0.7.0",
  "0.7.2" -> "0.7.0",
  "0.7.1" -> "0.7.0",
  "0.6.2" -> "0.6.2"
)

object integrationtest extends Cross[IntegrationtestCross](millApiCrossVersions.map(_._1): _*)
class IntegrationtestCross(millVersion: String) extends CrossScalaModule with PublishModule {
  override def publishVersion = VcsVersion.vcsState().format()
  override def crossScalaVersion = millApiCrossVersions.toMap.apply(millVersion)
  override def artifactName = "de.tobiasroeser.mill.integrationtest"

  override def compileIvyDeps = Agg(
    ivy"com.lihaoyi::os-lib:0.6.3",
    ivy"com.lihaoyi::mill-main:${millVersion}",
    ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  )

  object test extends Tests {
    override def testFrameworks = Seq("org.scalatest.tools.Framework")
    override def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.2.0"
    )
  }

  override def javacOptions = Seq("-source", "1.8", "-target", "1.8")

  override def pomSettings = PomSettings(
    description = "A integration test module useful for mill module development",
    organization = "de.tototec",
    url = "https://github.com/lefou/mill-integrationtest",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("lefou", "mill-integrationtest"),
    developers = Seq(
      Developer("lefou", "Tobias Roeser", "https://github.com/lefou")
    )
  )

  override def resources: Sources = T.sources {
    super.resources() ++ Seq(
      PathRef(millSourcePath / os.up / "README.adoc"),
      PathRef(millSourcePath / os.up / "LICENSE")
    )
  }

  override def skipIdea: Boolean = millVersion != millApiCrossVersions.head._1
}

object itest extends Cross[ItestCross](itestMillVersions.map(_._1): _*)
class ItestCross(millVersion: String) extends MillIntegrationTestModule {
  // correct cross level
  override def millSourcePath: Path = super.millSourcePath / os.up
  override def pluginsUnderTest = Seq(integrationtest(itestMillVersions.toMap.apply(millVersion)))
  override def millTestVersion: T[String] = millVersion
  override def testTargets: T[Seq[String]] = Seq("itest.test")
}

object P extends Module {

  /**
   * Update the millw script.
   */
  def millw() = T.command {
    val target = mill.modules.Util.download("https://raw.githubusercontent.com/lefou/millw/master/millw")
    val millw = baseDir / "millw"
    val res = os.proc(
      "sed", s"""s,\\(^DEFAULT_MILL_VERSION=\\).*$$,\\1${Regex.quoteReplacement(rtMillVersion())},""",
      target.path.toIO.getAbsolutePath()).call(cwd = baseDir)
    os.write.over(millw, res.out.text())
    os.perms.set(millw, os.perms(millw) + java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)
    target
  }
}
