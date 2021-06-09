// mill plugins under test
import $exec.plugins
import $ivy.`org.scoverage::scalac-scoverage-runtime:1.4.8`
import de.tobiasroeser.mill.integrationtest._
import mill._
import mill.define.Target
import mill.scalalib._
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}

trait DemoModule extends ScalaModule with PublishModule {
  override def publishVersion: T[String] = "0.0.1"
  override def scalaVersion: T[String] = "2.13.2"
  override def pomSettings: T[PomSettings] = PomSettings(
    description = "Demo Module",
    organization = "org.example",
    url = "http://org.example",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("lefou", "mill-integrationtest"),
    developers = Seq(Developer("lefou", "Tobias Roeser", "https://github.com/lefou"))
  )
}

// Some demo plugin
object demoplugin extends DemoModule {
  override def compileIvyDeps = Agg(
    ivy"com.lihaoyi::mill-main:0.7.0" // scala-steward:off
  )
}

object demoutil extends DemoModule {
}

trait Itest extends MillIntegrationTestModule {
  def millTestVersion = "0.7.3"
  def pluginsUnderTest = Seq(demoplugin)

}

object itest extends Itest {
  override def temporaryIvyModules: Seq[PublishModule] = Seq(demoutil)
  override def testInvocations: Target[Seq[(PathRef, Seq[TestInvocation.Targets])]] = T{ Seq(
    PathRef(millSourcePath / "src" / "demo") -> Seq(
      TestInvocation.Targets(Seq("verify")),
      TestInvocation.Targets(Seq("-d", "fail"), 1)
    )
  )}

}

// try to run with most defaults
object itest2 extends Itest
