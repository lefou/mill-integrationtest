package org.scalasteward.mill.plugin

import coursier.core.{Authentication, Repository}
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import mill._
import mill.eval.Evaluator
import mill.scalalib._
import ujson._
import mill.define.Task

trait StewardPluginBase extends Module {

  def extractDeps(ev: Evaluator) = {
    val modules = T.traverse(findModules(ev))(toModuleDep)
    T.command {
      val m = modules()
      Obj(
        "modules" -> Arr.from(m.map(_.toJson))
      )
    }
  }

  protected def plainCoursierDep(module: CoursierModule): Task[Dep => coursier.Dependency]

  protected def moduleRepositories(m: JavaModule): Task[Seq[Repository]]

  def findModules(ev: Evaluator) =
    ev.rootModule.millInternal.modules.collect { case j: JavaModule => j }

  def toModuleDep(m: JavaModule): Task[ModuleDependencies] = {

    // We also want to use mandatoryIvyDeps, but that`s too new, so we hardcode any scala lib here
    val mandatoryIvyDeps = m match {
      case s: ScalaModule => s.scalaLibraryIvyDeps
      case _ => T.task {
          Agg.empty[Dep]
        }
    }

    val dependencies = T.task {

      val convert = plainCoursierDep(m)()

      val ivy = m.ivyDeps() ++ mandatoryIvyDeps() ++ m.compileIvyDeps() ++ m.runIvyDeps()

      ivy.toSeq.map { dep =>
        val resolveName = convert(dep).module.name.value

        val artifactId = ArtifactId(
          dep.dep.module.name.value,
          if (resolveName != dep.dep.module.name.value)
            Option(resolveName)
          else None
        )

        Dependency(
          dep.dep.module.organization.value,
          artifactId,
          dep.dep.version
        )
      }
    }

    T.task {
      val resolvers = moduleRepositories(m)().map(Repo).filterNot(_.isLocal)
      val deps = dependencies()
      ModuleDependencies(m.millModuleSegments.render, resolvers, deps)
    }
  }

  case class ArtifactId(
      name: String,
      maybeCrossName: Option[String]
  ) {
    def toJson =
      Obj(
        "name" -> Str(name),
        "maybeCrossName" -> maybeCrossName.map(Str).getOrElse(Null)
      )
  }

  case class Dependency(
      groupId: String,
      artifactId: ArtifactId,
      version: String
  ) {
    def toJson =
      Obj(
        "groupId" -> Str(groupId),
        "artifactId" -> artifactId.toJson,
        "version" -> Str(version)
      )
  }

  object Dependency {
    def fromDep(dep: Dep, modifiers: Option[(String, String, String)]) = {
      val artifactId = ArtifactId(
        dep.dep.module.name.value,
        modifiers.map { case (binary, full, platform) =>
          dep.artifactName(binary, full, platform)
        }
      )
      Dependency(dep.dep.module.organization.value, artifactId, dep.dep.version)
    }
  }

  case class Repo(repository: Repository) {
    def isLocal =
      repository match {
        case repository: IvyRepository => repository.pattern.string.startsWith("file")
        case repository: MavenRepository => repository.root.startsWith("file")
        case _ => true
      }

    val headerJson: Function1[(String, String), Obj] = {
      case ((key, value)) =>
        Obj(
          "key" -> key,
          "value" -> value
        )
    }

    val authJson = (a: Authentication) =>
      Obj(
        "user" -> Str(a.user),
        "pass" -> a.passwordOpt.map(Str).getOrElse(Null),
        "realm" -> a.realmOpt.map(Str).getOrElse(Null)
      )

    def toJson =
      repository match {
        case m: MavenRepository =>
          Obj(
            "url" -> Str(m.root),
            "type" -> Str("maven"),
            "auth" -> m.authentication
              .map(authJson)
              .getOrElse(Null),
            "headers" -> m.authentication
              .map(headers => Arr(headers.httpHeaders.map(headerJson)))
              .getOrElse(Null)
          )
        case ivy: IvyRepository =>
          Obj(
            "pattern" -> Str(ivy.pattern.string),
            "type" -> Str("ivy"),
            "auth" -> ivy.authentication
              .map(authJson)
              .getOrElse(Null),
            "headers" -> ivy.authentication
              .map(headers => Arr(headers.httpHeaders.map(headerJson)))
              .getOrElse(Null)
          )
        case _ => Null
      }
  }

  case class ModuleDependencies(
      name: String,
      resolvers: Seq[Repo],
      dependencies: Seq[Dependency]
  ) {
    def toJson =
      Obj(
        "name" -> Str(name),
        "repositories" -> Arr.from(resolvers.map(_.toJson).filterNot(_.isNull)),
        "dependencies" -> Arr.from(dependencies.map(_.toJson))
      )
  }

}
