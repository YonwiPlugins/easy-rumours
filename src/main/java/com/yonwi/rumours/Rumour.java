package com.yonwi.rumours;

import java.util.Locale;
import net.runelite.api.gameval.ItemID;

/**
 * Every rumour: the creature and the rare part that finishes it.
 *
 * <p>From the Hunter Rumours plugin's Rumour enum (https://github.com/geel9/runelite-hunter-rumours),
 * BSD-2-Clause, Copyright (c) 2024, Joshua Coffey. Rare-part IDs came from the old
 * net.runelite.api.ItemID table and were mapped by number to their gameval names (HG_*). The
 * constant names match the source so its RumourLocation table can be reused unchanged. See
 * THIRD_PARTY_NOTICES.md.
 */
enum Rumour {
  NONE(null, "None", -1),
  TROPICAL_WAGTAIL(Creature.TROPICAL_WAGTAIL, "Tropical Wagtail", ItemID.HG_TAILFEATHER),
  WILD_KEBBIT(Creature.WILD_KEBBIT, "Wild Kebbit", ItemID.HG_KEBBITTUFT),
  SAPPHIRE_GLACIALIS(Creature.SAPPHIRE_GLACIALIS, "Sapphire Glacialis", ItemID.HG_BUTTERFLYWING_BLUE),
  SWAMP_LIZARD(Creature.SWAMP_LIZARD, "Swamp Lizard", ItemID.HG_LIZARDCLAW),
  SPINED_LARUPIA(Creature.SPINED_LARUPIA, "Spined Larupia", ItemID.HG_LARUPIA_EAR),
  BARB_TAILED_KEBBIT(Creature.BARB_TAILED_KEBBIT, "Barb-tailed Kebbit", ItemID.HG_KEBBITTUFT),
  SNOWY_KNIGHT(Creature.SNOWY_KNIGHT, "Snowy Knight", ItemID.HG_BUTTERFLYWING_WHITE),
  PRICKLY_KEBBIT(Creature.PRICKLY_KEBBIT, "Prickly Kebbit", ItemID.HG_KEBBITTUFT),
  EMBERTAILED_JERBOA(Creature.EMBERTAILED_JERBOA, "Embertailed Jerboa", ItemID.HG_JERBOATAIL_LARGE),
  HORNED_GRAAHK(Creature.HORNED_GRAAHK, "Horned Graahk", ItemID.HG_GRAAHK_HORN),
  SPOTTED_KEBBIT(Creature.SPOTTED_KEBBIT, "Spotted Kebbit", ItemID.HG_KEBBITTUFT),
  BLACK_WARLOCK(Creature.BLACK_WARLOCK, "Black Warlock", ItemID.HG_BUTTERFLYWING_BLACK),
  ORANGE_SALAMANDER(Creature.ORANGE_SALAMANDER, "Orange Salamander", ItemID.HG_LIZARDCLAW_ORANGE),
  RAZOR_BACKED_KEBBIT(Creature.RAZOR_BACKED_KEBBIT, "Razor-backed Kebbit", ItemID.HG_KEBBITTUFT),
  SABRE_TOOTHED_KEBBIT(Creature.SABRE_TOOTHED_KEBBIT, "Sabre-toothed Kebbit", ItemID.HG_KEBBITTUFT),
  GREY_CHINCHOMPA(Creature.GREY_CHINCHOMPA, "Grey Chinchompa", ItemID.HG_CHINCHOMPATUFT),
  SABRE_TOOTHED_KYATT(Creature.SABRE_TOOTHED_KYATT, "Sabre-toothed Kyatt", ItemID.HG_KYATTTOOTH),
  DARK_KEBBIT(Creature.DARK_KEBBIT, "Dark Kebbit", ItemID.HG_KEBBITTUFT),
  PYRE_FOX(Creature.PYRE_FOX, "Pyre Fox", ItemID.HG_FOXFLUFF),
  RED_SALAMANDER(Creature.RED_SALAMANDER, "Red Salamander", ItemID.HG_LIZARDCLAW_RED),
  WYRMSCRAIG_GOAT(Creature.WYRMSCRAIG_GOAT, "Wyrmscraig Goat", ItemID.HG_GOAT_PIT_HOOF),
  RED_CHINCHOMPA(Creature.RED_CHINCHOMPA, "Carnivorous Chinchompa", ItemID.HG_CHINCHOMPATUFT_RED),
  RED_CHINCHOMPA_2(Creature.RED_CHINCHOMPA, "Red Chinchompa", ItemID.HG_CHINCHOMPATUFT_RED),
  SUNLIGHT_MOTH(Creature.SUNLIGHT_MOTH, "Sunlight Moth", ItemID.HG_MOTHWING_SUN),
  DASHING_KEBBIT(Creature.DASHING_KEBBIT, "Dashing Kebbit", ItemID.HG_KEBBITTUFT),
  SUNLIGHT_ANTELOPE(Creature.SUNLIGHT_ANTELOPE, "Sunlight Antelope", ItemID.HG_ANTELOPEHOOF_SUN),
  MOONLIGHT_MOTH(Creature.MOONLIGHT_MOTH, "Moonlight Moth", ItemID.HG_MOTHWING_MOON),
  TECU_SALAMANDER(Creature.TECU_SALAMANDER, "Tecu Salamander", ItemID.HG_LIZARDCLAW_MOUNTAIN),
  HERBIBOAR(Creature.HERBIBOAR, "Herbiboar", ItemID.HG_HERBYTUFT),
  MOONLIGHT_ANTELOPE(Creature.MOONLIGHT_ANTELOPE, "Moonlight Antelope", ItemID.HG_ANTELOPEHOOF_MOON);

  final Creature creature;
  final String name;
  /** The rare part; having it in the inventory means the rumour is done. */
  final int rarePart;

  Rumour(Creature creature, String name, int rarePart) {
    this.creature = creature;
    this.name = name;
    this.rarePart = rarePart;
  }

  /** The rumour whose creature is named in a line of text, or NONE. */
  static Rumour mentionedIn(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    for (Rumour r : values())
      if (r != NONE && lower.contains(r.name.toLowerCase(Locale.ROOT))) return r;
    return NONE;
  }

  @Override
  public String toString() {
    return this == NONE ? name : name + " (" + creature.trap.name + ")";
  }
}
