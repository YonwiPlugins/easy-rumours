package com.yonwi.rumours;

import java.awt.*;
import java.util.*;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.Text;

/**
 * Leads the player through the interface: the side tab to open, then the item, worn item or spell,
 * then the quetzal map icon. Adapted from Ultimate Farming's FarmingInventoryOverlay.
 */
final class RumoursWidgetOverlay extends Overlay {
  /** The click to make now: items in blue, world targets in a lighter blue. */
  static final Color ITEM = new Color(0, 140, 255), TARGET = new Color(110, 200, 255);
  /** A click that's ready but not urgent, e.g. laying another trap. */
  static final Color NEXT = new Color(255, 150, 0);

  private static final int INVENTORY_TAB = 3, EQUIPMENT_TAB = 4, MAGIC_TAB = 6;
  private static final int[][] STONES = {
    {
      InterfaceID.Toplevel.STONE3,
      InterfaceID.ToplevelOsrsStretch.STONE3,
      InterfaceID.ToplevelPreEoc.STONE3
    },
    {
      InterfaceID.Toplevel.STONE4,
      InterfaceID.ToplevelOsrsStretch.STONE4,
      InterfaceID.ToplevelPreEoc.STONE4
    },
    {
      InterfaceID.Toplevel.STONE6,
      InterfaceID.ToplevelOsrsStretch.STONE6,
      InterfaceID.ToplevelPreEoc.STONE6
    }
  };
  /** Equipment slot index to its widget in the worn equipment tab. */
  private static final Map<Integer, Integer> WORN_SLOTS = new HashMap<>();

  static {
    int[] slots = {0, 1, 2, 3, 4, 5, 7, 9, 10, 12};
    int[] widgets = {
      InterfaceID.Wornitems.SLOT0,
      InterfaceID.Wornitems.SLOT1,
      InterfaceID.Wornitems.SLOT2,
      InterfaceID.Wornitems.SLOT3,
      InterfaceID.Wornitems.SLOT4,
      InterfaceID.Wornitems.SLOT5,
      InterfaceID.Wornitems.SLOT7,
      InterfaceID.Wornitems.SLOT9,
      InterfaceID.Wornitems.SLOT10,
      InterfaceID.Wornitems.SLOT12
    };
    for (int i = 0; i < slots.length; i++) WORN_SLOTS.put(slots[i], widgets[i]);
  }

  private static final Map<String, Integer> SPELLS = new HashMap<>();

  static {
    SPELLS.put("Civitas illa Fortis", InterfaceID.MagicSpellbook.FORTIS_TELEPORT);
    SPELLS.put("Teleport to House", InterfaceID.MagicSpellbook.TELEPORT_TO_YOUR_HOUSE);
    SPELLS.put("Ourania Teleport", InterfaceID.MagicSpellbook.OURANIA_TELEPORT);
    SPELLS.put("Telekinetic Grab", InterfaceID.MagicSpellbook.TELEGRAB);
  }

  private final RumoursPlugin plugin;
  private final RumoursConfig config;
  private final Client client;

  @Inject
  RumoursWidgetOverlay(RumoursPlugin plugin, RumoursConfig config, Client client) {
    this.plugin = plugin;
    this.config = config;
    this.client = client;
    setPosition(OverlayPosition.DYNAMIC);
    setLayer(OverlayLayer.ABOVE_WIDGETS);
  }

  @Override
  public Dimension render(Graphics2D g) {
    if (!config.highlight() || client.getGameState() != GameState.LOGGED_IN) return null;
    RumoursPlugin.View v = plugin.view();
    int open = client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL);
    if (v.quetzalSite != null) markQuetzalDestination(g, v.quetzalSite);
    if (!v.bankItems.isEmpty()) markBank(g, v.bankItems);
    if (open == INVENTORY_TAB && !v.nextItems.isEmpty()) markInventory(g, v.nextItems, NEXT);
    if (!v.items.isEmpty() && !plugin.selectedItem(v.items)) {
      if (contains(InventoryID.INV, v.items)) {
        if (open != INVENTORY_TAB) mark(g, stone(0));
        else markInventory(g, v.items, ITEM);
        return null;
      }
      if (contains(InventoryID.WORN, v.items)) {
        if (open != EQUIPMENT_TAB) mark(g, stone(1));
        else {
          ItemContainer worn = client.getItemContainer(InventoryID.WORN);
          Item[] items = worn == null ? new Item[0] : worn.getItems();
          for (int i = 0; i < items.length; i++)
            if (v.items.contains(items[i].getId()) && WORN_SLOTS.containsKey(i))
              mark(g, client.getWidget(WORN_SLOTS.get(i)));
        }
        return null;
      }
    }
    // Once a spell is selected, only its target is left to click.
    if (v.spell != null && SPELLS.containsKey(v.spell) && !client.isWidgetSelected()) {
      if (open != MAGIC_TAB) mark(g, stone(2));
      else mark(g, client.getWidget(SPELLS.get(v.spell)));
    }
    return null;
  }

  private void markInventory(Graphics2D g, Set<Integer> ids, Color color) {
    Widget inventory = client.getWidget(InterfaceID.Inventory.ITEMS);
    Widget[] children =
        inventory == null || inventory.isHidden() ? null : inventory.getDynamicChildren();
    if (children == null) return;
    for (Widget item : children)
      if (item != null && !item.isHidden() && ids.contains(item.getItemId()))
        mark(g, item.getBounds(), color);
  }

  /** In the open bank, the missing items to withdraw (only those scrolled into view). */
  private void markBank(Graphics2D g, Set<Integer> ids) {
    Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
    Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
    Widget[] children = items == null || items.isHidden() ? null : items.getDynamicChildren();
    if (children == null || container == null) return;
    Rectangle view = container.getBounds();
    for (Widget item : children)
      if (item != null
          && !item.isHidden()
          && ids.contains(item.getItemId())
          && view.contains(item.getBounds())) mark(g, item.getBounds(), ITEM);
  }

  /**
   * On the quetzal map (whistle or a quetzal NPC), highlights the landing site. UNVERIFIED: that the
   * icon's name or actions contain the site's words.
   */
  private void markQuetzalDestination(Graphics2D g, String site) {
    for (int id : new int[] {InterfaceID.QuetzalwhistleMenu.ICONS, InterfaceID.QuetzalMenu.ICONS}) {
      Widget icons = client.getWidget(id);
      if (icons == null || icons.isHidden()) continue;
      Widget[] children = icons.getDynamicChildren();
      if (children == null) continue;
      for (Widget icon : children) {
        if (icon == null || icon.isHidden()) continue;
        StringBuilder words = new StringBuilder(Text.removeTags(icon.getName()));
        if (icon.getActions() != null)
          for (String a : icon.getActions()) if (a != null) words.append(' ').append(a);
        if (words.toString().toLowerCase(Locale.ROOT).contains(site))
          mark(g, icon.getBounds(), ITEM);
      }
    }
  }

  private boolean contains(int container, Set<Integer> ids) {
    ItemContainer items = client.getItemContainer(container);
    if (items == null) return false;
    for (Item item : items.getItems()) if (ids.contains(item.getId())) return true;
    return false;
  }

  private Widget stone(int tab) {
    for (int id : STONES[tab]) {
      Widget w = client.getWidget(id);
      if (w != null && !w.isHidden()) return w;
    }
    return null;
  }

  static void mark(Graphics2D g, Widget w) {
    if (w == null || w.isHidden()) return;
    mark(g, w.getBounds(), ITEM);
  }

  /** Bright outline with a 20% fill. */
  static void mark(Graphics2D g, Shape shape, Color color) {
    if (shape == null) return;
    g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 51));
    g.fill(shape);
    g.setColor(color);
    g.setStroke(new BasicStroke(2));
    g.draw(shape);
  }
}
