import scala.util.matching.Regex
import mill._
import mill.scalalib._
import mill.define.{Sources, Task}
import mill.scalalib.publish._
import os.Path

val baseDir = build.millSourcePath
val rtMillVersion = build.version
val integrationtestVersion = "0.3.1"

val crossCases = Seq(
  "0.6.2" -> "2.12.11",
  "0.7.0" -> "2.13.2"
)

object integrationtest extends Cross[IntegrationtestCross](crossCases: _*)
class IntegrationtestCross(millVersion: String, crossScalaVersion: String) extends ScalaModule with PublishModule {
  // correct the two cross-levels
  override def millSourcePath: Path = super.millSourcePath / os.up / os.up
  def publishVersion = integrationtestVersion
  def scalaVersion = crossScalaVersion
  override def artifactName = "de.tobiasroeser.mill.integrationtest"

  override def compileIvyDeps = Agg(
    ivy"com.lihaoyi::os-lib:0.6.3",
    ivy"com.lihaoyi::mill-main:${millVersion}",
    ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  )

  object test extends Tests {
    override def testFrameworks = Seq("org.scalatest.tools.Framework")
    override def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.0.8"
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

  override def skipIdea: Boolean = millVersion != crossCases.head._1
}

object P extends Module {

  def install() = T.command{
    T.traverse(crossCases){case (mv,sv) => integrationtest(mv, sv).publishLocal()}()
//    integrationtest.publishLocal()()
    println(s"Published as: ${integrationtestVersion}")
  }

  def checkRelease: T[Unit] = T.input {
    if(integrationtestVersion.contains("SNAPSHOT")) sys.error("Cannot release a SNAPSHOT version")
    else {
      T.traverse(crossCases){case (mv,sv) => integrationtest(mv, sv).test.testCached}()
      ()
    }
  }

  def release(sonatypeCreds: String, release: Boolean = true) = T.command {
    checkRelease()
    T.traverse(crossCases){case (mv,sv) =>
      integrationtest(mv, sv).publish(
        sonatypeCreds = sonatypeCreds,
        release = release,
        readTimeout = 600000
      )
    }()
    ()
  }
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
