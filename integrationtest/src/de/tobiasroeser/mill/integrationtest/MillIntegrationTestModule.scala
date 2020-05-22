package de.tobiasroeser.mill.integrationtest

import java.nio.file.attribute.PosixFilePermission

import scala.util.Try

import mill._
import mill.api.{Ctx, Result}
import mill.define.{Command, Sources, Target, Task, TaskModule}
import mill.modules.Jvm.createJar
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
    for {
      src <- sources() if src.path.toIO.isDirectory
      d <- os.list(src.path)
      if (d / "build.sc").toIO.isFile
    } yield PathRef(d)
  }

  /**
   * Run the integration tests.
   */
  def test(args: String*): Command[Seq[TestCase]] = T.command {
    testTask(T.task {
      args
    })
  }

  /**
   * Args to be used by [[testCached]].
   */
  def testCachedArgs: T[Seq[String]] = T {
    Seq[String]()
  }

  /**
   * Run the integration tests (same as `test`), but only if any input has changed since the last run.
   */
  def testCached: T[Seq[TestCase]] = T {
    testTask(testCachedArgs)()
  }

  /** override this to config some itest behaviour.
    * @see [[ITestConfig]] */
  def itestConfig: ITestConfig = ITestConfig()

  protected def testTask(args: Task[Seq[String]]): Task[Seq[TestCase]] = T.task {
    /** Publish [[pluginsUnderTest]] and [[temporaryIvyModules]] to a local ivyPath
      * @return tuple of
      *         + ivyPath: Absolute path to the ivy.home directory that
      *           [[pluginsUnderTest]] and [[temporaryIvyModules]] are published
      *         + The content of `plugins.sc` file to be imported in `build.sc` of test projects
      */
    def publishPlugins() = {
      val ivyPath = T.dest / "ivyRepo"
      val publisher = new LocalIvyPublisher(ivyPath / "local")
      val plugins = pluginUnderTestDetails()
      (plugins ++ temporaryIvyModulesDetails()).foreach {
        case (((((jar, src), doc), pom), ivy), art) =>
          publisher.publish(jar.path, src.path, doc.path, pom.path, ivy.path, art, Nil)
      }
      ivyPath.toIO.getAbsolutePath -> plugins.map {
        case (_, art) => s"import $$ivy.`${art.group}:${art.id}:${art.version}`"
      }.mkString("\n")
    }

    val ctx = T.ctx()

    val (ivyPath, importFileContents) = publishPlugins()

    val testCases = testInvocations()
    //    val testInvocationsMap: Map[PathRef, TestInvocation.Targets] = testCases.toMap
    val tests = testCases.map(_._1)

    ctx.log.debug(s"Running ${tests.size} integration tests")
    val results: Seq[TestCase] = tests.zipWithIndex.map {
      case (test, index) =>
        // TODO flush output streams, should we just wait a bit?
        val logLine = s"integration test [${index + 1}/${tests.size}]: ${test.path.last}"
        if (args().isEmpty || args().contains(test.path.last)) {

          ctx.log.info(s"Starting ${logLine}")

          // per-test preparation

          // The test dir
          val testPath = ctx.dest / test.path.last

          // start clean
          os.remove.all(testPath)

          // copy test project here
          os.copy(from = test.path, to = testPath, createFolders = true)

          // Write the plugins.sc file
          os.write(testPath / "plugins.sc", importFileContents)

          val millExe = downloadMillTestVersion().path

          // also create a runner script, which can be ivoked manually
          val (runScript, scriptBody, perms) =
            if(scala.util.Properties.isWin) (
              testPath / "mill.bat",
              s"""set JAVA_OPTS="-Divy.home=$ivyPath"
                 |"${millExe.toIO.getAbsolutePath()}" -i %*
                 |""".stripMargin,
               null
            ) else (
              testPath / "mill",
              s"""#!/usr/bin/env sh
                 |export JAVA_OPTS="-Divy.home=$ivyPath"
                 |exec ${millExe.toIO.getAbsolutePath()} -i "$$@"
                 |""".stripMargin,
              os.PermSet(0) +
                PosixFilePermission.OWNER_READ +
                PosixFilePermission.OWNER_WRITE +
                PosixFilePermission.OWNER_EXECUTE
            )

          os.write(
            target = runScript,
            data = scriptBody,
            perms = perms
          )

          var prevFailed: Boolean = false

          val results: Seq[TestInvocationResult] = testCases.toMap.apply(test).map { invocation =>
            if (prevFailed) TestInvocationResult(invocation, TestResult.Skipped, Seq(), Seq())
            else {
              val result = invocation match {
                case invocation @ TestInvocation.Targets(targets, expectedExitCode) =>

                  // run mill with test targets
                  // ENV=env mill -i testTargets
                  val result = os.proc(runScript, targets)
                    .call(
                      cwd = testPath,
                      check = false
                    )

                  val res = if (result.exitCode == expectedExitCode) {
                    TestResult.Success
                  } else {
                    TestResult.Failed
                  }

                  TestInvocationResult(invocation, res, result.out.lines, result.err.lines)
              }

              // abort condition
              if (result.result == TestResult.Failed) prevFailed = true
              result
            }
          }

          val finalRes = results.foldLeft[TestResult](TestResult.Success) {
            case (TestResult.Success, TestInvocationResult(_, TestResult.Success, _, _)) => TestResult.Success
            case (TestResult.Success, TestInvocationResult(_, TestResult.Skipped, _, _)) => TestResult.Success
            case (TestResult.Skipped, _) => TestResult.Skipped
            case _ => TestResult.Failed
          }

          finalRes match {
            case TestResult.Success => ctx.log.info(s"Succeeded ${logLine}")
            case TestResult.Skipped => ctx.log.info(s"Skipped ${logLine}")
            case TestResult.Failed => ctx.log.error(s"Failed ${logLine}")
          }

          TestCase(testPath.last, finalRes, results)

        } else {
          ctx.log.debug(s"Skipping ${logLine}")
          TestCase(test.path.last, TestResult.Skipped, Seq())
        }
    }

    val categorized = results.groupBy(_.result)
    val succeeded = categorized.getOrElse(TestResult.Success, Seq())
    val skipped = categorized.getOrElse(TestResult.Skipped, Seq())
    val failed = categorized.getOrElse(TestResult.Failed, Seq())

    ctx.log.debug(s"\nSucceeded integration tests: ${succeeded.size}\n${succeeded.mkString("- \n", "- \n", "")}")
    ctx.log.debug(s"\nSkipped integration tests: ${skipped.size}\n${skipped.mkString("- \n", "- \n", "")}")
    ctx.log.debug(s"\nFailed integration tests: ${failed.size}\n${failed.mkString("- \n", "- \n", "")}")

    ctx.log.info(s"Integration tests: ${tests.size}, ${succeeded.size} succeeded, ${skipped.size} skipped, ${failed.size} failed")

    if (failed.nonEmpty) {
      Result.Failure(s"${failed.size} integration test(s) failed:\n${failed.mkString("- \n", "- \n", "")}", Some(results))
    } else {
      Result.Success(results)
    }

  }

  /**
   * The mill version used for executing the test cases.
   * Used by [[downloadMillTestVersion]] to automatically download.
   *
   * @return
   */
  def millTestVersion: T[String]

  /**
   * Download the mill version as defined by [[millTestVersion]].
   * Override this, if you need to use a custom built mill version.
   *
   * @return The [[PathRef]] to the mill executable (must have the executable flag).
   */
  def downloadMillTestVersion: T[PathRef] = T.persistent {
    val fullVersion = millTestVersion()
    val target = itestConfig.millTestVersionDownloadPath(fullVersion)
    // we avoid a download, if the previous download was successful
    if (!os.exists(target)) {
      val mainVersion = parseVersion(fullVersion).get
      val suffix = mainVersion match {
        case Array(0, 0|1|2|3|4, _) => ""
        case _ => "-assembly"
      }
      val url = s"https://github.com/lihaoyi/mill/releases/download/${mainVersion.mkString(".")}/${fullVersion}${suffix}"
      T.log.debug(s"Downloading ${url}")
      val tmpfile = os.temp(dir = T.dest, deleteOnExit = false)
      mill.modules.Util.download(url, os.rel / tmpfile.last)
      os.move(tmpfile, target, createFolders = true)
      if(!scala.util.Properties.isWin) {
        os.perms.set(target, os.perms(target) + PosixFilePermission.OWNER_EXECUTE)
      }
    }

    PathRef(target)
  }

  /**
   * The targets which are called to test the project.
   * Defaults to `verify`, which should implement test result validation.
   * This target is now deprecated in favor to [[testInvocations]], which is more flexible.
   */
  @deprecated("Use testInvocations instead", "mill-integrationtest-0.1.3-SNAPSHOT")
  def testTargets: T[Seq[String]] = T {
    Seq("verify")
  }

  /**
   * The test invocations to test the project.
   * Defaults to run `TestIncokation.Targets` with the targets from [[testTargets]] and expecting successful execution.
   */
  def testInvocations: Target[Seq[(PathRef, Seq[TestInvocation.Targets])]] = T {
    testCases().map(tc => tc -> Seq(TestInvocation.Targets(testTargets())))
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
   * This is almost the same as [[pluginsUnderTest]], but does not end up in the generated `plugins.sc`.
   */
  def temporaryIvyModules: Seq[PublishModule] = Seq()

  /**
   * Internal target used to trigger required artifacts of the plugins under test.
   * You should not need to use or override this in you buildfile.
   */
  protected def pluginUnderTestDetails: Task.Sequence[(((((PathRef, PathRef), PathRef), PathRef), PathRef), Artifact)] =
    T.traverse(pluginsUnderTest)(publishPlugin)

  /**
   * Internal target used to trigger required artifacts of the plugins under test.
   * You should not need to use or override this in you buildfile.
   */
  protected def temporaryIvyModulesDetails: Task.Sequence[(((((PathRef, PathRef), PathRef), PathRef), PathRef), Artifact)] =
    T.traverse(temporaryIvyModules)(publishPlugin)

  private def publishPlugin(p: PublishModule) = {
    val (src, doc) =
      if (itestConfig.publishEmptySourceAndDoc) (emptyJar, emptyJar)
      else (p.sourceJar, p.docJar)
    p.jar zip src zip doc zip p.pom zip p.ivy zip p.artifactMetadata
  }

  private def emptyJar = T {
    val d = T.dest / "empty"
    os.makeDir.all(d)
    createJar(Agg(d))
  }
}

object MillIntegrationTestModule {

  /** Extract the major, minor and micro version parts of the given version string. */
  def parseVersion(version: String): Try[Array[Int]] = Try {
    version
      .split("[-]", 2)(0)
      .split("[.]", 4)
      .take(3)
      .map(_.toInt)
  }

}