package com.yonwi.rumours;

import java.util.Locale;
import java.util.regex.Pattern;
import net.runelite.api.gameval.NpcID;

/**
 * The guild hunters who hand out rumours, from the Hunter Rumours plugin's Hunter enum
 * (https://github.com/geel9/runelite-hunter-rumours), BSD-2-Clause, Copyright (c) 2024, Joshua
 * Coffey. NPC IDs checked against the gameval NpcID table (HG_GILMAN..HG_WOLF = 13121..13126).
 */
enum Hunter {
  NONE(-1, "Unknown", "None"),
  WOLF(NpcID.HG_WOLF, "Wolf", "Master"),
  TECO(NpcID.HG_TECO, "Teco", "Expert"),
  ACO(NpcID.HG_ACO, "Aco", "Expert"),
  CERVUS(NpcID.HG_CERVUS, "Cervus", "Adept"),
  ORNUS(NpcID.HG_ORNUS, "Ornus", "Adept"),
  GILMAN(NpcID.HG_GILMAN, "Gilman", "Novice");

  final int npcId;
  final String commonName, tier;
  /** The name as a whole word, so "Aco" doesn't match inside another word. */
  private final Pattern word;

  Hunter(int npcId, String commonName, String tier) {
    this.npcId = npcId;
    this.commonName = commonName;
    this.tier = tier;
    word = Pattern.compile("\\b" + commonName.toLowerCase(Locale.ROOT) + "\\b");
  }

  static Hunter fromNpcId(int id) {
    for (Hunter h : values()) if (h != NONE && h.npcId == id) return h;
    return NONE;
  }

  /** The first hunter named in a line of text, or NONE. */
  static Hunter mentionedIn(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    for (Hunter h : values()) if (h != NONE && h.word.matcher(lower).find()) return h;
    return NONE;
  }

  @Override
  public String toString() {
    return this == NONE ? commonName : commonName + " (" + tier + ")";
  }
}
