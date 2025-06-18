// copy from version b53e32731270db7eb1de9f2b0b3fa6f83ea96db5
// Mill version 0.11.13

// plugins
import $file.plugins
import $file.shared
//import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.1`

// imports
import mill._
import mill.scalalib._
import mill.scalalib.api.ZincWorkerUtil
import mill.scalalib.scalafmt.ScalafmtModule
import mill.scalalib.publish.{PomSettings, License, VersionControl, Developer}
import de.tobiasroeser.mill.integrationtest._

trait PlatformConfig {
  def millVersion: String
  def millPlatform: String
  def scalaVersion: String = "2.13.16"
  def testWith: Seq[String]

  def millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
}
object Mill011 extends PlatformConfig {
  override val millVersion = "0.11.0" // scala-steward:off
  override val millPlatform = "0.11"
  override val testWith = Seq("0.11.7", millVersion)
}
object Mill010 extends PlatformConfig {
  override val millVersion = "0.10.0" // scala-steward:off
  override val millPlatform = "0.10"
  override val testWith = Seq("0.10.15", millVersion)
}
object Mill09 extends PlatformConfig {
  override val millVersion = "0.9.3" // scala-steward:off
  override val millPlatform = "0.9"
  override val testWith = Seq("0.9.12", millVersion)
}
object Mill07 extends PlatformConfig {
  override val millVersion = "0.7.0" // scala-steward:off
  override val millPlatform = "0.7"
  override val testWith = Seq("0.8.0", "0.7.4", millVersion)
}
object Mill06 extends PlatformConfig {
  override val millVersion = "0.6.0" // scala-steward:off
  override val millPlatform = "0.6"
  override val scalaVersion = "2.12.20"
  override val testWith = Seq("0.6.3", millVersion)
}

val platforms: Seq[PlatformConfig] = Seq(Mill011, Mill010, Mill09, Mill07, Mill06)
val testVersions = platforms.flatMap(p => p.testWith)

trait PublishConfig extends PublishModule {
  override def publishVersion: T[String] = "1.2.3"
  override def pomSettings: T[PomSettings] = PomSettings(
    description = "Mill plugin to generate dependency report to be process by scala-steward",
    organization = "org.scala-steward",
    url = "https://github.com/scala-steward-org/mill-plugin",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl
      .github(owner = "scala-steward-org", repo = "mill-plugin"),
    developers =
      Seq(Developer("lefou", "Tobias Roeser", "https://github.com/lefou"))
  )
}

object plugin extends Cross[PluginCross](platforms.map(_.millPlatform))
trait PluginCross
    extends ScalaModule
    with PublishConfig
    with ScalafmtModule
    with Cross.Module[String] {
  val millPlatform = crossValue
  val config: PlatformConfig = platforms.find(_.millPlatform == millPlatform).head
  override def scalaVersion: T[String] = config.scalaVersion
  override def artifactName: T[String] = "scala-steward-mill-plugin"
  override def platformSuffix: T[String] = s"_mill${millPlatform}"
  override def artifactId: T[String] =
    artifactName() + platformSuffix() + artifactSuffix()
  override def scalacOptions = Seq("-Ywarn-unused", "-deprecation")
  override def compileIvyDeps: T[Agg[Dep]] = super.compileIvyDeps() ++ Agg(
    config.millScalalib
  )
//  override def sources: Sources = T.sources {
//    super.sources() ++ Seq(PathRef(millSourcePath / s"src-mill${config.millPlatform}"))
//  }
  override def sources = T.sources {
    Seq(PathRef(millSourcePath / "src")) ++
      (ZincWorkerUtil.matchingVersions(millPlatform) ++
        ZincWorkerUtil.versionRanges(millPlatform, platforms.map(_.millPlatform)))
        .map(p => PathRef(millSourcePath / s"src-mill${p}"))
  }
}

object itest extends Cross[ItestCross](testVersions)
trait ItestCross extends MillIntegrationTestModule with Cross.Module[String] {
  val testVersion = crossValue
  val config: PlatformConfig = platforms.find(_.testWith.contains(testVersion)).head
  override def millTestVersion: T[String] = testVersion
  override def pluginsUnderTest = Seq(plugin(config.millPlatform))
  def sources = T.sources {
    super.sources() ++
      (ZincWorkerUtil.matchingVersions(config.millPlatform) ++
        ZincWorkerUtil.versionRanges(config.millPlatform, platforms.map(_.millPlatform)))
        .map(p => PathRef(millSourcePath / s"src-mill${p}"))
  }
  val testBase = millSourcePath / "src"
  override def testInvocations: T[Seq[(PathRef, Seq[TestInvocation.Targets])]] = T {
    testCases().map { pathRef =>
      pathRef -> Seq(
        TestInvocation.Targets(Seq("verify"), noServer = true)
      )
    }
  }
}
