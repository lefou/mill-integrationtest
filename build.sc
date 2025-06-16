// mill plugins
import $ivy.`com.lihaoyi::mill-contrib-scoverage:`
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.7.2`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.1`

// imports
import scala.util.matching.Regex
import de.tobiasroeser.mill.integrationtest.{MillIntegrationTestModule, TestCase, TestInvocation}
import de.tobiasroeser.mill.vcs.version.VcsVersion
import mill._
import mill.contrib.scoverage.ScoverageModule
import mill.define.{Command, Target, Task, TaskModule}
import mill.scalalib._
import mill.scalalib.publish._
import scala.util.Properties

lazy val baseDir: os.Path = build.millSourcePath
lazy val rtMillVersion = build.version()

sealed trait CrossConfig {
  def millPlatform: String
  def minMillVersion: String
  def scalaVersion: String = "2.13.16"
  def testWithMill: Seq[String] = Seq(minMillVersion)
  def osLibVersion: String
}

val millApiCrossVersions = Seq(
  new CrossConfig {
    override def millPlatform = "0.11"
    override def minMillVersion: String = "0.11.0" // scala-steward:off
    override def testWithMill: Seq[String] = Seq("0.11.13", minMillVersion)
    override def osLibVersion: String = "0.9.1"
  },
  new CrossConfig {
    override def millPlatform = "0.10"
    override def minMillVersion: String = "0.10.0" // scala-steward:off
    override def testWithMill: Seq[String] = Seq("0.10.15", minMillVersion)
    override def osLibVersion: String = "0.8.0"
  },
  new CrossConfig {
    override def millPlatform = "0.9"
    override def minMillVersion: String = "0.9.3" // scala-steward:off
    override def testWithMill = Seq("0.9.12", minMillVersion)
    override def osLibVersion: String = "0.7.1"
  }
)

object Deps {
  val scoverageVersion = "2.3.0"
  val scoveragePlugin = ivy"org.scoverage:::scalac-scoverage-plugin:${scoverageVersion}"
  val scoverageRuntime = ivy"org.scoverage::scalac-scoverage-runtime:${scoverageVersion}"
}

// Tuple: Mill platform -> CrossConfig
val matrix = millApiCrossVersions.map(x => x.millPlatform -> x).toMap

object integrationtest extends Cross[IntegrationtestCross](millApiCrossVersions.map(_.millPlatform))
trait IntegrationtestCross extends CrossScalaModule with PublishModule with ScoverageModule with Cross.Module[String] {
  outer =>

  def millPlatform = crossValue

  private val crossConfig = matrix(millPlatform)
  override def publishVersion = VcsVersion.vcsState().format()
  override def crossScalaVersion = crossConfig.scalaVersion
  override def artifactSuffix = s"_mill${crossConfig.millPlatform}_${artifactScalaVersion()}"
  override def artifactName = s"de.tobiasroeser.mill.integrationtest"
  override def sources = T.sources {
    super.sources() ++ Seq(PathRef(millSourcePath / s"src-${millPlatform.split("[.]").take(2).mkString(".")}"))
  }

  override def compileIvyDeps = Agg(
    // scala-steward:off
    ivy"com.lihaoyi::os-lib:${crossConfig.osLibVersion}",
    ivy"com.lihaoyi::mill-main:${crossConfig.minMillVersion}",
    ivy"com.lihaoyi::mill-scalalib:${crossConfig.minMillVersion}"
    // scala-steward:on
  )

  override def scoverageVersion = Deps.scoverageVersion

  object test extends ScalaModuleTests with ScoverageTests with TestModule.ScalaTest {
    override def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.2.19",
      ivy"org.scalatestplus::scalacheck-1-16:3.2.14.0"
    ) ++ outer.compileIvyDeps()
  }

  override def javacOptions =
    (if (Properties.isJavaAtLeast(9)) Seq()
     else Seq("-source", "1.8", "-target", "1.8")) ++
      Seq("-encoding", "UTF-8", "-deprecation")
  override def scalacOptions = Seq("-release", "8", "-encoding", "UTF-8", "-deprecation")

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

  override def resources = T.sources {
    super.resources() ++ Seq(
      PathRef(millSourcePath / os.up / "README.adoc"),
      PathRef(millSourcePath / os.up / "LICENSE")
    )
  }

  override def skipIdea: Boolean = crossConfig != millApiCrossVersions.head
}

// Tuple: Mill version -> CrossConfig
val itestMillVersions = millApiCrossVersions.flatMap(x => x.testWithMill.map(_ -> x))

object itest extends Cross[ItestCross](itestMillVersions.map(_._1)) with TaskModule {
  override def defaultCommandName(): String = "test"
  def testCached: T[Seq[TestCase]] = itest(itestMillVersions.map(_._1).head).testCached
  def test(args: String*): Command[Seq[TestCase]] = itest(itestMillVersions.map(_._1).head).test(args: _*)
}

trait ItestCross extends MillIntegrationTestModule with Cross.Module[String] {
  def millVersion = crossValue
  // correct cross level
  private val crossConfig = itestMillVersions.toMap.apply(millVersion)
  override def pluginsUnderTest: Seq[PublishModule] = Seq(integrationtest(crossConfig.millPlatform))
  override def millTestVersion: T[String] = millVersion

  val setup = Map(
    "mill-0.9" -> Seq(
      // test with debug print and explicit test target
      TestInvocation.Targets(Seq("-d", "itest[0.9.3].test")),
      TestInvocation.Targets(Seq("-d", "itest[0.9.12].test")),
      // test default target
      TestInvocation.Targets(Seq("itest2"))
    ),
    "mill-0.10" -> Seq(
      // test with debug print and explicit test target
      TestInvocation.Targets(Seq("-d", "itest[0.10.0].test")),
      TestInvocation.Targets(Seq("-d", "itest[0.10.15].test")),
      // test default target
      TestInvocation.Targets(Seq("itest2"))
    ),
    "mill-0.11" -> {
      Seq(
        // test with debug print and explicit test target
        TestInvocation.Targets(Seq("-d", "itest[0.11.0].test")),
        TestInvocation.Targets(Seq("-d", "itest[0.11.13].test"))
      ) ++ {
        sys.props("java.version") match {
          case s"1.$_" | "8" | "9" | "10" =>
            println("Skipping Mill 0.12 itests due to too old JVM version")
            Seq()
          case v =>
            println(s"Including Mill 0.12 itests for JVM version $v")
            Seq(
              TestInvocation.Targets(Seq("-d", "itest[0.12.0].test")),
              TestInvocation.Targets(Seq("-d", "itest[0.12.14].test"))
            )
        }
      } ++
        // test default target
        Seq(TestInvocation.Targets(Seq("itest2")))
    }
  )

  override def testInvocations: Target[Seq[(PathRef, Seq[TestInvocation.Targets])]] = T {
    val whiteList = millVersion match {
      case s"0.9.$_" => Seq("mill-0.9")
      case s"0.10.$_" => Seq("mill-0.10", "mill-0.9")
      case s"0.11.$_" => Seq("mill-0.11")
    }

    super.testInvocations().flatMap { ti =>
      val dir = ti._1.path.last
      if (whiteList.contains(dir)) Seq(ti._1 -> setup(dir))
      else Seq()
    }
  }

  /** Replaces the plugin jar with a scoverage-enhanced version of it. */
  override def pluginUnderTestDetails: Task[Seq[(PathRef, (PathRef, (PathRef, (PathRef, (PathRef, Artifact)))))]] =
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
    val target = mill.util.Util.download("https://raw.githubusercontent.com/lefou/millw/master/millw")
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
