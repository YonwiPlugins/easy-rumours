package com.yonwi.rumours;

import java.awt.*;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.*;

/** Test aid: what the plugin currently believes, so a run can be reported line by line. */
final class RumoursDebugOverlay extends OverlayPanel {
  private final RumoursPlugin plugin;
  private final RumoursConfig config;
  private final Client client;

  @Inject
  RumoursDebugOverlay(RumoursPlugin plugin, RumoursConfig config, Client client) {
    this.plugin = plugin;
    this.config = config;
    this.client = client;
    setPosition(OverlayPosition.BOTTOM_LEFT);
    setPriority(OverlayPriority.LOW);
    panelComponent.setPreferredSize(new Dimension(300, 0));
  }

  @Override
  public Dimension render(Graphics2D g) {
    if (!config.debug() || client.getGameState() != GameState.LOGGED_IN) return null;
    panelComponent.getChildren().clear();
    panelComponent
        .getChildren()
        .add(TitleComponent.builder().text("ER debug").color(Color.YELLOW).build());
    for (String line : plugin.debugLines())
      panelComponent.getChildren().add(LineComponent.builder().left(line).build());
    return super.render(g);
  }
}
