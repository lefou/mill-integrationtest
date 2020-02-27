import scala.util.matching.Regex

import mill._
import mill.scalalib._
import mill.define.Sources
import mill.scalalib.publish._

val baseDir = build.millSourcePath
val rtMillVersion = build.version

object integrationtest extends ScalaModule with PublishModule {

  def publishVersion = "0.2.0"

  def scalaVersion = "2.12.10"
  def millVersion = "0.6.0"
  def artifactName = "de.tobiasroeser.mill.integrationtest"

  def compileIvyDeps = Agg(
    ivy"com.lihaoyi::os-lib:0.6.3",
    ivy"com.lihaoyi::mill-main:${millVersion}",
    ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  )

  object test extends Tests {
    def testFrameworks = Seq("org.scalatest.tools.Framework")
    def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.0.8"
    )
  }

  def javacOptions = Seq("-source", "1.8", "-target", "1.8")

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

}

object P extends Module {
  def test() = T.command { integrationtest.test.test()() }
  def testCached = T { integrationtest.test.testCached() }
  def install() = T.command{
    integrationtest.publishLocal()()
    println(s"Published as: ${integrationtest.publishVersion()}")
  }
  def checkRelease: T[Unit] = T{
    if(integrationtest.publishVersion().contains("SNAPSHOT")) sys.error("Cannot release a SNAPSHOT version")
    else {
      testCached()
    }
  }
  def release(sonatypeCreds: String, release: Boolean = true) = T.command {
    checkRelease()
    integrationtest.publish(
      sonatypeCreds = sonatypeCreds,
      release = release,
      readTimeout = 600000
    )()
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