import mill._
import mill.scalalib._
import mill.define.Task
import mill.scalalib.publish._
import ammonite.ops._

object integrationtest extends ScalaModule with PublishModule {

  def scalaVersion = "2.12.8"

  def millVersion = "0.3.6"

  def artifactName = "de.tobiasroeser.mill.integrationtest"
  def publishVersion = "0.0.1-SNAPSHOT"

  def compileIvyDeps = Agg(
    ivy"com.lihaoyi::os-lib:0.2.6",
    ivy"com.lihaoyi::mill-main:${millVersion}",
    ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  )

  object test extends Tests {
    def testFrameworks = Seq("org.scalatest.tools.Framework")
    def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.0.4"
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

}
