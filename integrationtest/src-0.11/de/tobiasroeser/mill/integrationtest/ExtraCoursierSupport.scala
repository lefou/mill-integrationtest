package de.tobiasroeser.mill.integrationtest

import mil.scalalib.BoundDep
import mill.Agg
import mill.PathRef
import mill.T
import mill.api.Result
import mill.define.Task
import mill.scalalib.CoursierModule
import mill.scalalib.Dep
import mill.scalalib.Lib

trait ExtraCoursierSupport extends CoursierModule {

  /**
   * Resolves each dependency independently.
   *
   * @param deps
   * The dependencies to resolve.
   * @param sources
   * If `true`, resolved the source jars instead of the binary jars.
   * @return
   * Tuples containing each dependencies and it's resolved transitive
   * artifacts.
   */
  protected def resolveSeparateDeps(
      deps: Task[Agg[BoundDep]],
      sources: Boolean = false
  ): Task[Agg[(BoundDep, Agg[PathRef])]] = T.task {
    val pRepositories = repositoriesTask()
    val pDeps = deps()
    val pMapDeps = mapDependencies()
    val pCustomizer = resolutionCustomizer()
    pDeps.map { dep =>
      val Result.Success(resolved) = Lib.resolveDependencies(
        repositories = pRepositories,
        deps = Agg(dep),
        sources = sources,
        mapDependencies = Some(pMapDeps),
        customizer = pCustomizer,
        ctx = Some(implicitly[mill.api.Ctx.Log])
      )
      (dep, resolved)
    }
  }
}
