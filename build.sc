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
      T.traverse(crossCases){case (mv,sv) => itest(mv, sv).testCached}()
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

//++++ integrationtest the MillIntegrationTestModule
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest:0.3.1`
import de.tobiasroeser.mill.integrationtest._

object itest extends Cross[Itest](crossCases: _*)
class Itest(millVersion: String, crossScalaVersion: String) extends MillIntegrationTestModule {
  import ItestUtil._

  def pluginsUnderTest = Seq(integrationtest(millVersion, crossScalaVersion))

  def millTestVersion = T {
    T.env.getOrElse("TEST_MILL_VERSION", millVersion)
  }

  // correct the two cross-levels
  override def millSourcePath: Path = super.millSourcePath / os.up / os.up

  def testInvocations = T {
    import TestInvocation._

    remoteTestProjects.foreach(_.init(millSourcePath))

    // mill-git test require this git config
    // https://github.com/joan38/mill-git/blob/4254b37/.github/workflows/ci.yml#L21
    // @note when we have TestInvocation.Cmd (not available in `integrationtest:0.3.1`),
    // we should add this as a TestInvocation for mill-git below instead of early running this here
    if (T.env.get("CI").contains("true")) {
      os.proc("git", "config", "--global", "user.name", "CI").call()
    }

    Seq(
      PathRef(millSourcePath / "mill-scalafix") -> Seq(Targets(Seq("itest._.test"))),
      PathRef(millSourcePath / "mill-git")      -> Seq(Targets(Seq("itest._.test")))
    )
  }

  def remoteTestProjects: Seq[RemoteProject] = Seq(
    RemoteProject("https://github.com/ohze/mill-scalafix.git", "4e5c709"),
    RemoteProject("https://github.com/joan38/mill-git.git", "5ed3839")
  )

}

object ItestUtil {
  def replaceContent(p: os.Path, contentChange: String => String): Unit = {
    val s = os.read(p)
    val newContent = contentChange(s)
    os.write.over(p, newContent)
  }

  /** replace the $ivy import by `$exec.plugins` to use the the publishLocal version */
  val itestReplacer: String => String =
    _.replaceFirst(
      """import \$ivy\.`de\.tototec::de\.tobiasroeser\.mill\.integrationtest:.+`""",
      """import \$exec.plugins""")

  case class RemoteProject(gitUrl: String, version: String) {
    def name = gitUrl.substring(gitUrl.lastIndexOf('/') + 1).stripSuffix(".git")

    /** git clone and init a test project into a subdirectory of `into` */
    def init(into: Path): Unit = {
      val d = into / name
      if (os.exists(d)) return

      os.proc("git", "clone", gitUrl, d).call()
      os.proc("git", "checkout", version).call(d)

      // mill-integrationtest will create those files when running test
      os.remove.all(d / "mill")
      os.remove.all(d / "mill.bat")

      replaceContent(d / "build.sc", itestReplacer)
    }
  }
}
