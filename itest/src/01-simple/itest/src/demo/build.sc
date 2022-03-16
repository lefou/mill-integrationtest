// mill plugins under test
import $exec.plugins

import mill._
import mill.define.Command

// check we can import the plugin
import org.example.demoplugin.DemoPluginModule

// check we can import the util
import $ivy.`org.example::demoutil:0.0.1`
import org.example.demoutil.DemoUtil

object Demo extends DemoPluginModule {}

def verify(): Command[Unit] = T.command {
  if (Demo.demo() != "DemoPlugin") sys.error(s"Expected 'DemoPlugin' but was '${Demo.demo()}'")
  if (DemoUtil.demo != "DemoUtil") sys.error(s"Expected 'DemoUtil' but was '${DemoUtil.demo}'")
  ()
}

def fail(): Command[Unit] = T.command {
  sys.error("Fail on purpose")
  ()
}

def checkEnv(): Command[Unit] = T.command {
  val envVal = T.env.getOrElse("TEST_ENV", "")
  if (envVal != "SET") sys.error(s"Expected env var 'TEST_ENV' to contain value 'SET', but it was: ${envVal}")
}
