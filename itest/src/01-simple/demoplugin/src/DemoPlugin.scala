package org.example.demoplugin

import mill.T
import mill.define.Module

trait DemoPluginModule extends Module {
  def demo: T[String] = T{ "DemoPlugin" }
}
