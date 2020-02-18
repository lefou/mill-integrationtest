package de.tobiasroeser.mill.integrationtest

import java.nio.file.attribute.PosixFilePermission

import scala.util.Try

import mill._
import mill.define.{Command, Sources, Task, TaskModule}
import mill.scalalib._
import mill.scalalib.publish._

/**
 * Run Integration for Mill Plugin.
 */
trait MillIntegrationTestModule extends TaskModule {
  import MillIntegrationTestModule._

  /** Denotes the command which is called when no target in given on the commandline. */
  def defaultCommandName = "test"

  /**
   * Locations where integration tests are located.
   * Each integration test is a sub-directory, containing a complete test mill project.
   */
  def sources: Sources = T.sources(millSourcePath / 'src)

  /**
   * The directories each representing a mill test case.
   * Derived from [[sources]].
   */
  def testCases: T[Seq[PathRef]] = T {
    val tests = sources().flatMap { dir =>
      os.list(dir.path)
        .filter(d => d.toIO.isDirectory())
        .filter(d => (d / "build.sc").toIO.isFile())
    }
    tests.map(PathRef(_))
  }

  /**
   * Run the integration tests.
   */
  def test(): Command[Unit] = T.command {
    val ctx = T.ctx()
    //    cleanTestIvyRepo()

    // publish Local
    val ivyPath = ctx.dest / 'ivyRepo

    T.ctx.log.debug("Publishing plugins under test into test ivy repo")
    val publisher = new LocalIvyPublisher(ivyPath / 'local)
    (pluginUnderTestDetails() ++ temporaryIvyModulesDetails()).foreach { detail =>
      publisher.publish(
        jar = detail._1.path,
        sourcesJar = detail._2._1.path,
        docJar = detail._2._2._1.path,
        pom = detail._2._2._2._1.path,
        ivy = detail._2._2._2._2._1.path,
        artifact = detail._2._2._2._2._2
      )
    }

    //    publishPluginsUnderTest()
    //    publishPluginsUnderTest(pluginsUnderTest, ivyPath)

    //    val publisher = new LocalIvyPublisher(ivyPath)
    //    pluginsUnderTest.foreach { plugin =>
    //      publisher.publish(
    //        jar = plugin.jar().path,
    //        sourcesJar = plugin.sourceJar().path,
    //        docJar = plugin.docJar().path,
    //        pom = plugin.pom().path,
    //        ivy = plugin.ivy().path,
    //        artifact = plugin.artifactMetadata()
    //      )
    //    }

    val artifactMetadata = Task.sequence(pluginsUnderTest.map(_.artifactMetadata))()

    val importFileContents = {
      val header = Seq("// Import a locally published version of the plugin under test")
      val body = artifactMetadata.map { meta =>
        s"import $$ivy.`${meta.group}:${meta.id}:${meta.version}`"
      }
      header ++ body
    }.mkString("\n")

    val tests = testCases()
    ctx.log.info(s"Running ${tests.size} integration tests")
    val results = tests.map { test =>
      // The test dir
      val testPath = ctx.dest / test.path.last

      // start clean
      os.remove.all(testPath)

      // copy test project here
      os.copy(from = test.path, to = testPath, createFolders = true)

      // Write the plugins.sc file
      os.write(testPath / "plugins.sc", importFileContents)

      val millExe = downloadMillTestVersion().path

      // run mill with test targets
      // ENV=env mill -i testTargets
      val result = os.proc(millExe, "-i", testTargets())
        .call(
          cwd = testPath,
          check = false,
          env = Map(
            "JAVA_OPTS" -> s"-Divy.home=${ivyPath}"
          )
        )

      if (result.exitCode == 0) {
        T.ctx().log.info("Finished integration test: " + testPath.last)
      } else {
        T.ctx().log.error("Failed integration test: " + testPath.last)
      }
      TestCase(testPath.last, result.exitCode, result.out.lines, result.err.lines)
    }

    val (succeeded, failed) = results.partition(_.exitCode == 0)

    println(s"\nSucceeded integration tests: ${succeeded.size}\n${succeeded.mkString("- \n", "- \n", "")}")
    println(s"\nFailed integration tests: ${failed.size}\n${failed.mkString("- \n", "- \n", "")}")

    T.ctx().log.info(s"Integration tests: ${tests.size}, ${succeeded.size} succeeded, ${failed.size} failed")

    if (!failed.isEmpty) {
      throw new AssertionError(s"${failed.size} integration test(s) failed:\n${failed.mkString("- \n", "- \n", "")}")
    }

  }

  /**
   * The mill version used for executing the test cases.
   * Used by [[downloadMillTestVersion]] to automatically download.
   * @return
   */
  def millTestVersion: T[String]

  /**
   * Download the mill version as defined by [[millTestVersion]].
   * Override this, if you need to use a custom built mill version.
   * @return The [[PathRef]] to the mill executable (must have the executable flag).
   */
  def downloadMillTestVersion: T[PathRef] = T.persistent {
    val mainVersion = parseVersion(millTestVersion()).get.mkString(".")
    val url = s"https://github.com/lihaoyi/mill/releases/download/${mainVersion}/${millTestVersion()}"

    // we avoid a download, if the previous download was successful
    val target = T.ctx().dest / s"mill-${millTestVersion()}.jar"
    if (!os.exists(target)) {
      T.ctx().log.debug(s"Downloading ${url}")
      val tmpfile = os.temp(dir = T.ctx().dest, deleteOnExit = false)
      os.remove(tmpfile)
      mill.modules.Util.download(url, os.rel / tmpfile.last)
      os.move(tmpfile, target)
      os.perms.set(target, os.perms(target) + PosixFilePermission.OWNER_EXECUTE)
    }

    PathRef(target)
  }

  /**
   * The targets which are called to test the project.
   * Defaults to `verify`, which should implement test result validation.
   */
  def testTargets: T[Seq[String]] = T {
    Seq("verify")
  }

  /**
   * The plugins used in the integration test.
   * You should at least add your plugin under test here.
   * You can also add additional libraries, e.g. those that assist you in the test result validation (e.g. a local test support project).
   * The defined modules will be published into a temporary ivy repository before the tests are executed.
   * In your test `build.sc` file, instead of the typical `import $ivy.` line,
   * you should use `import $exec.plugins` to include all plugins that are defined here.
   */
  def pluginsUnderTest: Seq[PublishModule]

  /**
   * Additional modules you need in the temporary ivy repository, but not in the resulting mill build classpath.
   * The defined modules will be published into a temporary ivy repository before the tests are executed.
   */
  def temporaryIvyModules: Seq[PublishModule] = Seq()

  /**
   * Internal target used to trigger required artifacts of the plugins under test.
   * You should not need to use or override this in you buildfile.
   */
  protected def pluginUnderTestDetails: Task.Sequence[(PathRef, (PathRef, (PathRef, (PathRef, (PathRef, Artifact)))))] =
    Task.traverse(pluginsUnderTest) { plugin =>
      new Task.Zipped(
        plugin.jar,
        new Task.Zipped(
          plugin.sourceJar,
          new Task.Zipped(
            plugin.docJar,
            new Task.Zipped(
              plugin.pom,
              new Task.Zipped(
                plugin.ivy,
                plugin.artifactMetadata
              )
            )
          )
        )
      )
    }

  /**
   * Internal target used to trigger required artifacts of the plugins under test.
   * You should not need to use or override this in you buildfile.
   */
  protected def temporaryIvyModulesDetails: Task.Sequence[(PathRef, (PathRef, (PathRef, (PathRef, (PathRef, Artifact)))))] =
    Task.traverse(temporaryIvyModules) { plugin =>
      new Task.Zipped(
        plugin.jar,
        new Task.Zipped(
          plugin.sourceJar,
          new Task.Zipped(
            plugin.docJar,
            new Task.Zipped(
              plugin.pom,
              new Task.Zipped(
                plugin.ivy,
                plugin.artifactMetadata
              )
            )
          )
        )
      )
    }

}

object MillIntegrationTestModule {

  case class TestCase(name: String, exitCode: Int, out: Seq[String], err: Seq[String]) {
    override def toString(): String =
      s"Test case: ${
        name
      }\nExit code: ${
        exitCode
      }\n\n[out]\n\n${
        out.mkString("\n")
      }\n\n[err]\n\n${
        err.mkString("\n")
      }"

  }

  /** Extract the major, minor and micro version parts of the given version string. */
  def parseVersion(version: String): Try[Array[Int]] = Try {
    version
      .split("[-]", 2)(0)
      .split("[.]", 4)
      .take(3)
      .map(_.toInt)
  }

}