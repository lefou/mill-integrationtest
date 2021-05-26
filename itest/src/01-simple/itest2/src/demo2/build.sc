// mill plugins under test
import $exec.plugins

import mill._
import mill.define.Command

// check we can import the plugin
import org.example.demoplugin.DemoPluginModule

object Demo extends DemoPluginModule {
}

def verify(): Command[Unit] = T.command {
  if(!os.exists(build.millSourcePath / "shared.sc")) {
    sys.error("No shared source file `shared.sc` found")
  }
  if(Demo.demo() != "DemoPlugin") sys.error(s"Expected 'DemoPlugin' but was '${Demo.demo()}'")
  ()
}

def fail(): Command[Unit] = T.command {
  sys.error("Fail on purpose")
  ()
}