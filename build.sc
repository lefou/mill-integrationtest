import mill._
import mill.api.Result
import mill.scalalib._
import mill.define.Sources
import mill.scalalib.publish._

object integrationtest extends ScalaModule with PublishModule {

  def publishVersion = "0.1.3-SNAPSHOT"

  def scalaVersion = "2.12.10"

  def millVersion = "0.5.7"

  def artifactName = "de.tobiasroeser.mill.integrationtest"

  def compileIvyDeps = Agg(
    ivy"com.lihaoyi::os-lib:0.6.2",
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
  def testCached = T { integrationtest.test.testCached() }
  def install = T{
    integrationtest.publishLocal()
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
}