package org.scalasteward.mill.plugin

import coursier.core.Repository
import mill.{T, Task}
import mill.define.{Discover, ExternalModule}
import mill.scalalib.{CoursierModule, Dep, JavaModule}

object StewardPlugin extends ExternalModule with StewardPluginBase {

  lazy val millDiscover: Discover[StewardPlugin.this.type] = Discover[this.type]

  override protected def moduleRepositories(module: JavaModule): Task[Seq[Repository]] = module.repositoriesTask

  override protected def plainCoursierDep(module: CoursierModule): Task[Dep => coursier.Dependency] =
    T.task { dep: Dep =>
      val bind = module.bindDependency()
      bind(dep).dep
    }

}
