// mill plugins
import $ivy.`com.lihaoyi::mill-contrib-scoverage:`
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.6.1`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.3.1`

// imports
import scala.util.matching.Regex
import de.tobiasroeser.mill.integrationtest.{MillIntegrationTestModule, TestCase, TestInvocation}
import de.tobiasroeser.mill.vcs.version.VcsVersion
import mill._
import mill.contrib.scoverage.ScoverageModule
import mill.define.{Command, Sources, Target, Task, TaskModule}
import mill.scalalib._
import mill.scalalib.publish._
import os.Path

val baseDir: os.Path = build.millSourcePath
val rtMillVersion = build.version

sealed trait CrossConfig {
  def millPlatform: String
  def minMillVersion: String
  def scalaVersion: String
  def testWithMill: Seq[String] = Seq(minMillVersion)
}

val millApiCrossVersions = Seq(
  new CrossConfig {
    override def millPlatform = "0.11.0-M8" // only valid for exact milestone releases
    override def minMillVersion: String = "0.11.0-M8-2-f5e4e2" // needs to be an exact milestone release
    override def scalaVersion = "2.13.10"
    override def testWithMill: Seq[String] = Seq(minMillVersion)
  },
  new CrossConfig {
    override def millPlatform = "0.10"
    override def minMillVersion: String = "0.10.0" // scala-steward:off
    override def scalaVersion = "2.13.10"
    override def testWithMill: Seq[String] = Seq("0.10.8", minMillVersion)
  },
  new CrossConfig {
    override def millPlatform = "0.9"
    override def minMillVersion: String = "0.9.3" // scala-steward:off
    override def scalaVersion = "2.13.10"
    override def testWithMill =
      Seq("0.9.12", "0.9.8", minMillVersion)
  }
)

object Deps {
  val scoverageVersion = "2.0.8"
  val scoveragePlugin = ivy"org.scoverage:::scalac-scoverage-plugin:${scoverageVersion}"
  val scoverageRuntime = ivy"org.scoverage::scalac-scoverage-runtime:${scoverageVersion}"
}

// Tuple: Mill platform -> CrossConfig
val matrix = millApiCrossVersions.map(x => x.millPlatform -> x).toMap

object integrationtest extends Cross[IntegrationtestCross](millApiCrossVersions.map(_.millPlatform): _*)
class IntegrationtestCross(millPlatform: String) extends CrossScalaModule with PublishModule with ScoverageModule {
  outer =>
  private val crossConfig = matrix(millPlatform)
  override def publishVersion = VcsVersion.vcsState().format()
  override def crossScalaVersion = crossConfig.scalaVersion
  override def artifactSuffix = s"_mill${crossConfig.millPlatform}_${artifactScalaVersion()}"
  override def artifactName = s"de.tobiasroeser.mill.integrationtest"
  override def sources: Sources = T.sources {
    super.sources() ++ Seq(PathRef(millSourcePath / s"src-${millPlatform.split("[.]").take(2).mkString(".")}"))
  }

  override def compileIvyDeps = Agg(
    // scala-steward:off
    ivy"com.lihaoyi::os-lib:0.7.1",
    ivy"com.lihaoyi::mill-main:${crossConfig.minMillVersion}",
    ivy"com.lihaoyi::mill-scalalib:${crossConfig.minMillVersion}"
    // scala-steward:on
  )

  override def scoverageVersion = Deps.scoverageVersion
  // we need to adapt to changed publishing policy - patch-level
  override def scoveragePluginDep = T {
    Deps.scoveragePlugin
  }

  object test extends Tests with ScoverageTests with TestModule.ScalaTest {
    override def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.2.15",
      ivy"org.scalatestplus::scalacheck-1-14:3.2.2.0"
    ) ++ outer.compileIvyDeps()
  }

  override def javacOptions = Seq("-source", "1.8", "-target", "1.8", "-encoding", "UTF-8")
  override def scalacOptions = Seq("-target:jvm-1.8", "-encoding", "UTF-8", "-deprecation")

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

  override def skipIdea: Boolean = crossConfig != millApiCrossVersions.head
}

// Tuple: Mill version -> CrossConfig
val itestMillVersions = millApiCrossVersions.flatMap(x => x.testWithMill.map(_ -> x))

object itest extends Cross[ItestCross](itestMillVersions.map(_._1): _*) with TaskModule {
  override def defaultCommandName(): String = "test"
  def testCached: T[Seq[TestCase]] = itest(itestMillVersions.map(_._1).head).testCached
  def test(args: String*): Command[Seq[TestCase]] = itest(itestMillVersions.map(_._1).head).test(args: _*)
}

class ItestCross(millVersion: String) extends MillIntegrationTestModule {
  // correct cross level
  private val crossConfig = itestMillVersions.toMap.apply(millVersion)
  override def millSourcePath: Path = super.millSourcePath / os.up
  override def pluginsUnderTest: Seq[PublishModule] = Seq(integrationtest(crossConfig.millPlatform))
  override def millTestVersion: T[String] = millVersion

  override def testInvocations: Target[Seq[(PathRef, Seq[TestInvocation.Targets])]] = Seq(
    PathRef(millSourcePath / "src" / "01-simple") -> Seq(
      // test with debug print and explicit test target
      TestInvocation.Targets(Seq("-d", "itest.test")),
      // test default target
      TestInvocation.Targets(Seq("itest2"))
    )
  )

  /** Replaces the plugin jar with a scoverage-enhanced version of it. */
  override def pluginUnderTestDetails: Task.Sequence[(PathRef, (PathRef, (PathRef, (PathRef, (PathRef, Artifact)))))] =
    Target.traverse(pluginsUnderTest) { p =>
      val jar = p match {
        case p: ScoverageModule => p.scoverage.jar
        case p => p.jar
      }
      jar zip (p.sourceJar zip (p.docJar zip (p.pom zip (p.ivy zip p.artifactMetadata))))
    }

  override def perTestResources = T.sources { Seq(generatedSharedSrc()) }
  def generatedSharedSrc = T {
    os.write(
      T.dest / "shared.sc",
      s"""import $$ivy.`${Deps.scoverageRuntime.dep.module.organization.value}::${Deps.scoverageRuntime.dep.module.name.value}:${Deps.scoverageRuntime.dep.version}`
         |""".stripMargin
    )
    PathRef(T.dest)
  }

}

object P extends Module {

  /**
   * Update the millw script.
   */
  def millw() = T.command {
    val target = mill.modules.Util.download("https://raw.githubusercontent.com/lefou/millw/master/millw")
    val millw = baseDir / "millw"
    val res = os.proc(
      "sed",
      s"""s,\\(^DEFAULT_MILL_VERSION=\\).*$$,\\1${Regex.quoteReplacement(rtMillVersion())},""",
      target.path.toIO.getAbsolutePath()
    ).call(cwd = baseDir)
    os.write.over(millw, res.out.text())
    os.perms.set(millw, os.perms(millw) + java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)
    target
  }
}
