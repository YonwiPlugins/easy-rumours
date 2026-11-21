package com.yonwi.rumours;

import net.runelite.api.gameval.ItemID;

/**
 * How each rumour creature is caught, and its pity threshold without and with the full guild
 * hunter outfit.
 *
 * <p>Names and thresholds are from the Hunter Rumours plugin's Trap enum
 * (https://github.com/geel9/runelite-hunter-rumours), BSD-2-Clause, Copyright (c) 2024, Joshua
 * Coffey. Item IDs were re-looked-up in the gameval ItemID table. See THIRD_PARTY_NOTICES.md.
 */
enum Trap {
  SNARE("Bird snare", true, 40, 38, ItemID.HUNTING_OJIBWAY_BIRD_SNARE),
  DEADFALL("Deadfall", false, 30, 28, ItemID.LOGS),
  NET_TRAP("Net trap", false, 50, 46, Kit.NET_TRAP, ItemID.NET, ItemID.ROPE),
  PIT("Pit trap", false, 30, 28, ItemID.HUNTING_TEASING_STICK),
  GOAT_PIT("Goat pit", false, 96, 90, ItemID.CATTLEPROD),
  BOX_TRAP("Box trap", true, 100, 94, ItemID.HUNTING_BOX_TRAP),
  FALCONRY("Falconry", false, 20, 18, ItemID.FALCON_GLOVES),
  BUTTERFLY("Butterfly net", false, 80, 76, ItemID.HUNTING_BUTTERFLY_NET, ItemID.II_MAGIC_BUTTERFLY_NET),
  NOOSE("Tracking", false, 30, 28, ItemID.NOOSE_WAND),
  NOOSE_HERBIBOAR("Tracking", false, 14, 12, ItemID.NOOSE_WAND);

  final String name;
  /** Laid from the inventory ("Lay"), so the trap item itself is the next click. */
  final boolean laidFromInventory;

  final int pity, pityWithOutfit;
  /** The tool or trap item; any one of them will do. */
  final int[] items;

  Trap(String name, boolean laidFromInventory, int pity, int pityWithOutfit, int... items) {
    this.name = name;
    this.laidFromInventory = laidFromInventory;
    this.pity = pity;
    this.pityWithOutfit = pityWithOutfit;
    this.items = items;
  }

  /** Each worn outfit piece takes a quarter of the full outfit's reduction off, rounded down. */
  int pityFor(int outfitPieces) {
    return (int) Math.floor(pity - (pity - pityWithOutfit) / 4d * outfitPieces);
  }
}
