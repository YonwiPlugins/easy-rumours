package com.yonwi.rumours;

import java.awt.*;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;

/**
 * In the world: the hunter, stairs, creatures and caught traps in blue; trap spots in orange;
 * the goat pit's goats, numbers, stand tile, ring and field outline (styled by the Goats
 * settings); and the rare-part rainbow.
 */
final class RumoursSceneOverlay extends Overlay {
  /** One full trip round the colour wheel; slow, so it never strobes. */
  private static final long RAINBOW_PERIOD_MS = 4000;

  private final RumoursPlugin plugin;
  private final RumoursConfig config;
  private final Client client;

  @Inject
  RumoursSceneOverlay(RumoursPlugin plugin, RumoursConfig config, Client client) {
    this.plugin = plugin;
    this.config = config;
    this.client = client;
    setPosition(OverlayPosition.DYNAMIC);
    setLayer(OverlayLayer.ABOVE_SCENE);
  }

  @Override
  public Dimension render(Graphics2D g) {
    if (client.getGameState() != GameState.LOGGED_IN) return null;
    RumoursPlugin.View v = plugin.view();
    if (v.rainbow) {
      float hue = (System.currentTimeMillis() % RAINBOW_PERIOD_MS) / (float) RAINBOW_PERIOD_MS;
      Color c = Color.getHSBColor(hue, 0.9f, 1f);
      // 20% opacity.
      g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 51));
      g.fillRect(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
    }
    if (!config.highlight()) return null;
    boolean goats = v.goatInPit >= 0;
    if (goats && !v.goatArea.isEmpty()) outline(g, v.goatArea, config.goatAreaColour());
    for (LocalPoint lp : v.standRing) tile(g, lp, config.goatRingColour(), false);
    for (TileObject o : v.nextObjects) draw(g, o, RumoursWidgetOverlay.NEXT);
    for (TileObject o : v.objects) draw(g, o, RumoursWidgetOverlay.TARGET);
    for (NPC npc : v.npcs)
      if (goats) goat(g, npc, config.goatTargetColour());
      else RumoursWidgetOverlay.mark(g, npc.getConvexHull(), RumoursWidgetOverlay.TARGET);
    for (NPC npc : v.nextNpcs) RumoursWidgetOverlay.mark(g, npc.getConvexHull(), RumoursWidgetOverlay.NEXT);
    if (v.groundPart != null) {
      Polygon tile = Perspective.getCanvasTilePoly(client, v.groundPart);
      if (tile != null) {
        RumoursWidgetOverlay.mark(g, tile, Style.GOLD);
        g.setStroke(new BasicStroke(4));
        g.draw(tile);
      }
    }
    for (NPC npc : v.grabbed) goat(g, npc, config.grabbedColour());
    if (config.goatNumbers() && !v.npcLabels.isEmpty()) {
      g.setFont(FontManager.getRunescapeBoldFont().deriveFont((float) config.goatNumberSize()));
      for (Map.Entry<NPC, Integer> e : v.npcLabels.entrySet()) {
        NPC npc = e.getKey();
        String text = String.valueOf(e.getValue());
        net.runelite.api.Point at = npc.getCanvasTextLocation(g, text, npc.getLogicalHeight() + 40);
        if (at != null) OverlayUtil.renderTextLocation(g, at, text, config.goatNumberColour());
      }
    }
    if (v.standTile != null) tile(g, v.standTile, config.goatStandColour(), true);
    return null;
  }

  /** A goat in the chosen style: filled hull, outline only, or its tile. */
  private void goat(Graphics2D g, NPC npc, Color color) {
    switch (config.goatStyle()) {
      case OUTLINE:
        Shape hull = npc.getConvexHull();
        if (hull == null) return;
        g.setColor(color);
        g.setStroke(new BasicStroke(2));
        g.draw(hull);
        break;
      case TILE:
        if (npc.getLocalLocation() != null) tile(g, npc.getLocalLocation(), color, true);
        break;
      case HULL:
      default:
        RumoursWidgetOverlay.mark(g, npc.getConvexHull(), color);
    }
  }

  private void tile(Graphics2D g, LocalPoint lp, Color color, boolean filled) {
    Polygon poly = Perspective.getCanvasTilePoly(client, lp);
    if (poly == null) return;
    if (filled) RumoursWidgetOverlay.mark(g, poly, color);
    else {
      g.setColor(color);
      g.setStroke(new BasicStroke(1.5f));
      g.draw(poly);
    }
  }

  private void outline(Graphics2D g, List<LocalPoint> points, Color color) {
    Polygon outline = new Polygon();
    for (LocalPoint lp : points) {
      net.runelite.api.Point p =
          Perspective.localToCanvas(client, lp, client.getTopLevelWorldView().getPlane());
      if (p == null) return;
      outline.addPoint(p.getX(), p.getY());
    }
    g.setColor(color);
    g.setStroke(new BasicStroke(2));
    g.draw(outline);
  }

  private static void draw(Graphics2D g, TileObject object, Color color) {
    RumoursWidgetOverlay.mark(
        g, object.getClickbox() != null ? object.getClickbox() : object.getCanvasTilePoly(), color);
  }
}
