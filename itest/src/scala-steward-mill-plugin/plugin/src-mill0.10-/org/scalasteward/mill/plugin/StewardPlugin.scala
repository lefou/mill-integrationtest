/*
 * Copyright 2018-2022 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.mill.plugin

import coursier.core.Repository
import mill.{Module, T}
import mill.define.{Discover, ExternalModule, Task}
import mill.scalalib.{CoursierModule, Dep, JavaModule}

object StewardPlugin extends ExternalModule with StewardPluginBase {

  implicit def millScoptEvaluatorReads[E]: mill.main.EvaluatorScopt[E] =
    new mill.main.EvaluatorScopt[E]()

  lazy val millDiscover: Discover[this.type] = Discover[this.type]

  override protected def moduleRepositories(module: JavaModule): Task[Seq[Repository]] = T.task { module.repositories }

  override protected def plainCoursierDep(module: CoursierModule): Task[Dep => coursier.Dependency] =
    module.resolveCoursierDependency

}
