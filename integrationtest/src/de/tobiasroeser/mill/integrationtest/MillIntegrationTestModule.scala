package de.tobiasroeser.mill.integrationtest

import java.nio.file.attribute.PosixFilePermission

import scala.util.Try
import mill._
import mill.api.{Ctx, Result}
import mill.define.{Command, Sources, Target, Task, TaskModule}
import mill.scalalib._
import mill.scalalib.publish._
import os.{PathRedirect, ProcessOutput}

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

  protected def testTask(args: Task[Seq[String]]): Task[Seq[TestCase]] = T.task {
    val ctx = T.ctx()

    // publish Local
    val ivyPath = ctx.dest / "ivyRepo"

    ctx.log.debug("Publishing plugins under test into test ivy repo")
    val publisher = new LocalIvyPublisher(ivyPath / 'local)
    (pluginUnderTestDetails() ++ temporaryIvyModulesDetails()).foreach { detail =>
      publisher.publish(
        jar = detail._1.path,
        sourcesJar = detail._2._1.path,
        docJar = detail._2._2._1.path,
        pom = detail._2._2._2._1.path,
        ivy = detail._2._2._2._2._1.path,
        artifact = detail._2._2._2._2._2,
        extras = Seq()
      )
    }

    val artifactMetadata = Target.sequence(pluginsUnderTest.map(_.artifactMetadata))()

    val importFileContents = {
      val header = Seq("// Import a locally published version of the plugin under test")
      val body = artifactMetadata.map { meta =>
        s"import $$ivy.`${meta.group}:${meta.id}:${meta.version}`"
      }
      header ++ body
    }.mkString("\n")

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
              s"""set JAVA_OPTS="-Divy.home=${ivyPath.toIO.getAbsolutePath()}"
                 |"${millExe.toIO.getAbsolutePath()}" -i %*
                 |""".stripMargin,
               null
            ) else (
              testPath / "mill",
              s"""#!/usr/bin/env sh
                 |export JAVA_OPTS="-Divy.home=${ivyPath.toIO.getAbsolutePath()}"
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
            if (prevFailed) TestInvocationResult(invocation, TestResult.Skipped, Seq(), Seq(), None)
            else {
              ctx.log.info(s"Invoking ${logLine}: ${invocation}")
              val result = invocation match {
                case invocation @ TestInvocation.Targets(targets, expectedExitCode) =>
                  val outlog = os.temp(
                    dir = testPath,
                    prefix = "out-",
                    suffix = ".log",
                    deleteOnExit = false
                  )
                  val pathRedirect = os.PathRedirect(outlog)

                  // run mill with test targets
                  // ENV=env mill -i testTargets
                  val result = os.proc(runScript, targets)
                    .call(
                      cwd = testPath,
                      check = false,
                      stdout = pathRedirect,
                      // stderr = pathRedirect,
                      mergeErrIntoOut = true
                    )

                  val res = if (result.exitCode == expectedExitCode) {
                    TestResult.Success
                  } else {
                    TestResult.Failed
                  }

                  TestInvocationResult(invocation, res, result.out.lines, result.err.lines, Some(outlog))
              }

              // abort condition
              if (result.result == TestResult.Failed) prevFailed = true
              result
            }
          }

          val finalRes = results.foldLeft[TestResult](TestResult.Success) {
            case (TestResult.Success, TestInvocationResult(_, TestResult.Success, _, _, _)) => TestResult.Success
            case (TestResult.Success, TestInvocationResult(_, TestResult.Skipped, _, _, _)) => TestResult.Success
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
    val succeeded: Seq[TestCase] = categorized.getOrElse(TestResult.Success, Seq())
    val skipped = categorized.getOrElse(TestResult.Skipped, Seq())
    val failed: Seq[TestCase] = categorized.getOrElse(TestResult.Failed, Seq())

    ctx.log.debug(s"\nSucceeded integration tests: ${succeeded.size}\n${succeeded.map(t => s"\n-  $t").mkString}")
    ctx.log.debug(s"\nSkipped integration tests: ${skipped.size}\n${skipped.map(t => s"\n-  $t").mkString}")
    ctx.log.debug(s"\nFailed integration tests: ${failed.size}\n${failed.map(t => s"\n-  $t").mkString}")

    // Also print details for failed integration tests
    ctx.log.error(
      s"\nDetails: ${
        failed
          .map(t => s"\nOutput of failed test: ${t.name}\n${
            t
              .invocations
              .flatMap(i => i.logFile.map(f => i -> f))
              .map(iandf => s"Invocation: ${iandf._1}\n${os.read(iandf._2)}")
              .mkString
          }")
        .mkString
      }"
    )

    ctx.log.info(s"Integration tests: ${tests.size}, ${succeeded.size} succeeded, ${skipped.size} skipped, ${failed.size} failed")

    if (failed.nonEmpty) {
      Result.Failure(s"${failed.size} integration test(s) failed:\n${failed.map(t => s"\n-  $t").mkString}", Some(results))
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
    val mainVersion = parseVersion(fullVersion).get
    val suffix = mainVersion match {
      case Array(0, 0|1|2|3|4, _) => ""
      case _ => "-assembly"
    }
    val url = s"https://github.com/lihaoyi/mill/releases/download/${mainVersion.mkString(".")}/${fullVersion}${suffix}"

    val cacheTarget =  T.env
      .get("XDG_CACHE_HOME")
      .map(os.Path(_))
      .getOrElse(os.home / ".cache") / "mill" / "download" / fullVersion

    if(useCachedMillDownload() && os.exists(cacheTarget)) {
      PathRef(cacheTarget)
    } else {
      // we avoid a download, if the previous download was successful
      val target = T.dest / s"mill-${fullVersion}${suffix}.jar"
      if (!os.exists(target)) {
        T.log.debug(s"Downloading ${url}")
        val tmpfile = os.temp(dir = T.dest, deleteOnExit = false)
        os.remove(tmpfile)
        mill.modules.Util.download(url, os.rel / tmpfile.last)
        os.move(tmpfile, target)
        if (!scala.util.Properties.isWin) {
          os.perms.set(target, os.perms(target) + PosixFilePermission.OWNER_EXECUTE)
        }
      }

      if(useCachedMillDownload()) {
        os.move(target, cacheTarget, createFolders = true)
        PathRef(cacheTarget)
      } else {
        PathRef(target)
      }
    }
  }

  /** If `true`, the downloaded mill version used for tests will be cached to the system cache dir (e.g. `~/.cache`). */
  def useCachedMillDownload: T[Boolean] = T{ true }

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
  protected def pluginUnderTestDetails: Task.Sequence[(PathRef, (PathRef, (PathRef, (PathRef, (PathRef, Artifact)))))] =
    T.traverse(pluginsUnderTest) { p =>
      p.jar zip (p.sourceJar zip (p.docJar zip (p.pom zip (p.ivy zip p.artifactMetadata))))
    }

  /**
   * Internal target used to trigger required artifacts of the plugins under test.
   * You should not need to use or override this in you buildfile.
   */
  protected def temporaryIvyModulesDetails: Task.Sequence[(PathRef, (PathRef, (PathRef, (PathRef, (PathRef, Artifact)))))] =
    T.traverse(temporaryIvyModules) { p =>
      p.jar zip (p.sourceJar zip (p.docJar zip (p.pom zip (p.ivy zip p.artifactMetadata))))
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