import $file.plugins
import $ivy.`org.scalameta::munit:0.7.29`

import mill._, scalalib._
import org.scalasteward.mill.plugin.StewardPlugin
import mill.eval.Evaluator
import munit.Assertions._

object minimal extends ScalaModule {
  def scalaVersion = "3.1.3"

  object test extends Tests {
    override def ivyDeps = Agg(ivy"org.scalameta::munit:0.7.29")
    override def testFrameworks: T[Seq[String]] = T { Seq("munit.Framework") }
  }
}

object other extends ScalaModule {
  def scalaVersion = "2.13.8"
}

def verify(ev: Evaluator) = T.command {
  val str = StewardPlugin.extractDeps(ev)().toString
  assert(str.contains("munit_3"))
  assert(str.contains("scala-library"))
}
