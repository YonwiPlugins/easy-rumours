package com.yonwi.rumours;

import net.runelite.client.config.*;

@ConfigGroup(RumoursConfig.GROUP)
public interface RumoursConfig extends Config {
  /** Keep this stable across renames so saved progress and stats survive. */
  String GROUP = "easy-rumours";

  @ConfigItem(
      keyName = "infoBox",
      name = "On-screen guide",
      description = "Show the current rumour and the next step",
      position = 0)
  default boolean infoBox() {
    return true;
  }

  @ConfigItem(
      keyName = "highlight",
      name = "Highlight next click",
      description = "Highlight the item, spell, map icon, stairs, hunter or creature to click next",
      position = 1)
  default boolean highlight() {
    return true;
  }

  @ConfigItem(
      keyName = "rainbow",
      name = "Rainbow when the part drops",
      description =
          "Tint the screen with slowly changing rainbow colours (20% opacity) from the rare part"
              + " dropping until you teleport back",
      position = 3)
  default boolean rainbow() {
    return true;
  }

  @ConfigSection(
      name = "Goats",
      description = "Wyrmscraig goat pit: which goats are highlighted and how",
      position = 20)
  String GOATS = "goats";

  @Range(min = 1, max = 24)
  @ConfigItem(
      keyName = "goatCount",
      name = "Goats to highlight",
      description = "How many of the nearest grabbable goats to highlight and number",
      section = GOATS,
      position = 21)
  default int goatCount() {
    return 5;
  }

  @Range(min = 1, max = 15)
  @ConfigItem(
      keyName = "goatRange",
      name = "Grab range (tiles)",
      description = "Telekinetic Grab's reach from where you stand",
      section = GOATS,
      position = 22)
  default int goatRange() {
    return 10;
  }

  @Range(min = 10, max = 90)
  @ConfigItem(
      keyName = "goatAngle",
      name = "Fan width (degrees)",
      description =
          "How far off straight-beyond-the-pit a goat may be and still be highlighted (each side)",
      section = GOATS,
      position = 23)
  default int goatAngle() {
    return 75;
  }

  @ConfigItem(
      keyName = "goatTargetColour",
      name = "Target colour",
      description = "Colour of the goats to grab",
      section = GOATS,
      position = 24)
  default java.awt.Color goatTargetColour() {
    return new java.awt.Color(110, 200, 255);
  }

  @ConfigItem(
      keyName = "grabbedColour",
      name = "Grabbed goat colour",
      description = "Colour of a goat after Telekinetic Grab is cast on it, until it lands",
      section = GOATS,
      position = 25)
  default java.awt.Color grabbedColour() {
    return new java.awt.Color(255, 80, 220);
  }

  @ConfigItem(
      keyName = "goatNumbers",
      name = "Show pit numbers",
      description = "Number each goat with the pit slot it will fill",
      section = GOATS,
      position = 26)
  default boolean goatNumbers() {
    return true;
  }

  @ConfigItem(
      keyName = "goatNumberColour",
      name = "Number colour",
      description = "Colour of the pit numbers",
      section = GOATS,
      position = 27)
  default java.awt.Color goatNumberColour() {
    return java.awt.Color.WHITE;
  }

  @ConfigItem(
      keyName = "goatStandTile",
      name = "Show where to stand",
      description = "Highlight the tile next to the pit with the most goats in reach",
      section = GOATS,
      position = 28)
  default boolean goatStandTile() {
    return true;
  }

  @ConfigItem(
      keyName = "goatStandColour",
      name = "Stand tile colour",
      description = "Colour of the suggested tile",
      section = GOATS,
      position = 29)
  default java.awt.Color goatStandColour() {
    return new java.awt.Color(90, 230, 110);
  }

  @Range(min = 1, max = 10)
  @ConfigItem(
      keyName = "goatStandMinimum",
      name = "Move tile below (goats)",
      description = "Keep the suggested tile until fewer than this many goats are in reach from it",
      section = GOATS,
      position = 30)
  default int goatStandMinimum() {
    return 2;
  }

  @Range(min = 1, max = 10)
  @ConfigItem(
      keyName = "goatStandTicks",
      name = "Move tile after (ticks)",
      description = "How many ticks in a row the tile must be below that number before it moves",
      section = GOATS,
      position = 30)
  default int goatStandTicks() {
    return 3;
  }

  @ConfigItem(
      keyName = "goatRing",
      name = "Show the stand ring",
      description = "Outline every tile you may stand on round the pit",
      section = GOATS,
      position = 30)
  default boolean goatRing() {
    return false;
  }

  @ConfigItem(
      keyName = "goatRingColour",
      name = "Stand ring colour",
      description = "Colour of the stand ring",
      section = GOATS,
      position = 30)
  default java.awt.Color goatRingColour() {
    return new java.awt.Color(240, 220, 60);
  }

  enum GoatStyle {
    HULL,
    OUTLINE,
    TILE
  }

  @ConfigItem(
      keyName = "goatStyle",
      name = "Goat highlight style",
      description = "Filled hull, outline only, or the tile under the goat",
      section = GOATS,
      position = 24)
  default GoatStyle goatStyle() {
    return GoatStyle.HULL;
  }

  @Range(min = 8, max = 32)
  @ConfigItem(
      keyName = "goatNumberSize",
      name = "Number size",
      description = "Font size of the pit numbers",
      section = GOATS,
      position = 27)
  default int goatNumberSize() {
    return 16;
  }

  @Range(min = 3, max = 60)
  @ConfigItem(
      keyName = "goatGrabSeconds",
      name = "Grabbed mark time (s)",
      description = "How long a grabbed goat keeps its colour if it hasn't landed",
      section = GOATS,
      position = 25)
  default int goatGrabSeconds() {
    return 15;
  }

  @ConfigItem(
      keyName = "goatSteps",
      name = "Highlight pit steps",
      description = "Highlight the spike supply and the pit for the spikes, lining and clearing steps",
      section = GOATS,
      position = 33)
  default boolean goatSteps() {
    return true;
  }

  @ConfigItem(
      keyName = "goatAreaColour",
      name = "Field outline colour",
      description = "Colour of the goat field outline",
      section = GOATS,
      position = 33)
  default java.awt.Color goatAreaColour() {
    return java.awt.Color.RED;
  }

  @ConfigItem(
      keyName = "goatAreaOnly",
      name = "Only goats in the field",
      description = "Ignore goats outside the goat field around the pit (e.g. the pen to the west)",
      section = GOATS,
      position = 31)
  default boolean goatAreaOnly() {
    return true;
  }

  @ConfigItem(
      keyName = "goatAreaOutline",
      name = "Show the field outline",
      description = "Draw the goat field's edge on the ground",
      section = GOATS,
      position = 32)
  default boolean goatAreaOutline() {
    return false;
  }

  @ConfigSection(
      name = "Your house",
      description = "What your player-owned house has, for routes that start with a house teleport",
      position = 10)
  String HOUSE = "house";

  enum JewelleryBox {
    NONE,
    BASIC,
    FANCY,
    ORNATE
  }

  // Defaults assume no house teleports; players tick what their house has.
  @ConfigItem(
      keyName = "pohFairyRing",
      name = "Fairy ring",
      description = "A fairy ring (or spiritual fairy tree) in your house",
      section = HOUSE,
      position = 11)
  default boolean pohFairyRing() {
    return false;
  }

  @ConfigItem(
      keyName = "pohSpiritTree",
      name = "Spirit tree",
      description = "A spirit tree (or spiritual fairy tree) in your house",
      section = HOUSE,
      position = 12)
  default boolean pohSpiritTree() {
    return false;
  }

  @ConfigItem(
      keyName = "pohNexus",
      name = "Portal nexus, fully unlocked",
      description = "A portal nexus with every destination this plugin uses (Ourania Cave, Civitas illa Fortis)",
      section = HOUSE,
      position = 13)
  default boolean pohNexus() {
    return false;
  }

  @ConfigItem(
      keyName = "pohJewelleryBox",
      name = "Jewellery box",
      description = "Your house's jewellery box",
      section = HOUSE,
      position = 14)
  default JewelleryBox pohJewelleryBox() {
    return JewelleryBox.NONE;
  }

  @ConfigItem(
      keyName = "debug",
      name = "Debug box",
      description = "Show what the plugin currently believes, for testing",
      position = 2)
  default boolean debug() {
    return false;
  }
}
