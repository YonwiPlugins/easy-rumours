package com.yonwi.rumours;

import java.awt.*;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.*;

/** The current rumour, catches against pity, and what to do next. */
final class RumoursInfoOverlay extends OverlayPanel {
  private final RumoursPlugin plugin;
  private final RumoursConfig config;
  private final Client client;

  @Inject
  RumoursInfoOverlay(RumoursPlugin plugin, RumoursConfig config, Client client) {
    this.plugin = plugin;
    this.config = config;
    this.client = client;
    setPosition(OverlayPosition.TOP_LEFT);
    panelComponent.setPreferredSize(new Dimension(190, 0));
  }

  @Override
  public Dimension render(Graphics2D g) {
    if (!config.infoBox() || client.getGameState() != GameState.LOGGED_IN) return null;
    RumoursPlugin.View v = plugin.view();
    panelComponent.getChildren().clear();
    panelComponent
        .getChildren()
        .add(TitleComponent.builder().text("Easy Rumours").color(Style.GOLD).build());
    if (v.rumour != Rumour.NONE) {
      panelComponent
          .getChildren()
          .add(LineComponent.builder().left(v.rumour.name).right(v.hunter.commonName).build());
      panelComponent
          .getChildren()
          .add(
              LineComponent.builder()
                  .left("Catches")
                  .right(v.catches + " / " + v.pity)
                  .rightColor(v.hasPart ? Color.GREEN : Color.WHITE)
                  .build());
      long start = plugin.rumourStart();
      if (start > 0) {
        long s = (System.currentTimeMillis() - start) / 1000;
        panelComponent
            .getChildren()
            .add(LineComponent.builder().left("Time").right(String.format("%d:%02d", s / 60, s % 60)).build());
      }
      if (v.goatInPit >= 0) {
        panelComponent
            .getChildren()
            .add(LineComponent.builder().left("Goats in pit").right(v.goatInPit + " / " + v.goatCapacity).build());
        panelComponent
            .getChildren()
            .add(LineComponent.builder().left("Goats cleared").right(String.valueOf(v.goatCleared)).build());
      }
    }
    panelComponent
        .getChildren()
        .add(LineComponent.builder().left(v.instruction).leftColor(new Color(110, 200, 255)).build());
    return super.render(g);
  }
}
