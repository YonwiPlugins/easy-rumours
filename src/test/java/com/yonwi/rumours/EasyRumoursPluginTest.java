package com.yonwi.rumours;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class EasyRumoursPluginTest {
  public static void main(String[] args) throws Exception {
    ExternalPluginManager.loadBuiltin(RumoursPlugin.class);
    RuneLite.main(args);
  }
}
