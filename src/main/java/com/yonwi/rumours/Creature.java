package com.yonwi.rumours;

import static com.yonwi.rumours.Trap.*;

import java.util.stream.IntStream;
import net.runelite.api.gameval.NpcID;

/**
 * Rumour creatures: their NPC IDs, how they're caught, and the Hunter XP of one catch (used to count
 * catches).
 *
 * <p>Traps, levels and XP drops are from the Hunter Rumours plugin's Creature enum
 * (https://github.com/geel9/runelite-hunter-rumours), BSD-2-Clause, Copyright (c) 2024, Joshua
 * Coffey. Its NPC IDs came from the old net.runelite.api.NpcID table; each was mapped by number to
 * its gameval NpcID name. Extra variants (e.g. MOTH_MOONLIGHT_LOW_WANDER) were added from the gameval
 * table. See THIRD_PARTY_NOTICES.md.
 */
enum Creature {
  TROPICAL_WAGTAIL(SNARE, 19, new int[] {95, 96}, NpcID.MULTICOLOURED_BIRD),
  WILD_KEBBIT(DEADFALL, 23, new int[] {102, 103}, NpcID.HUNTINGBEAST_CLAWS),
  SAPPHIRE_GLACIALIS(BUTTERFLY, 25, new int[] {34}, NpcID.BUTTERFLY_GLACIALIS),
  SWAMP_LIZARD(NET_TRAP, 29, new int[] {152}, NpcID.SALAMANDER_GREEN),
  SPINED_LARUPIA(PIT, 31, new int[] {180}, NpcID.HUNTING_JAGUAR),
  BARB_TAILED_KEBBIT(DEADFALL, 33, new int[] {134, 135}, NpcID.HUNTINGBEAST_BARBEDTAIL),
  SNOWY_KNIGHT(BUTTERFLY, 35, new int[] {44}, NpcID.BUTTERFLY_SNOWY),
  PRICKLY_KEBBIT(DEADFALL, 37, new int[] {204}, NpcID.HUNTINGBEAST_SPIKY),
  EMBERTAILED_JERBOA(BOX_TRAP, 39, new int[] {137}, NpcID.VARLAMORE_HUNTERJERBOA01),
  HORNED_GRAAHK(PIT, 41, new int[] {240}, NpcID.HUNTING_LEOPARD),
  SPOTTED_KEBBIT(FALCONRY, 43, new int[] {104}, NpcID.HUNTINGBEAST_SPEEDY),
  BLACK_WARLOCK(BUTTERFLY, 45, new int[] {54}, NpcID.BUTTERFLY_WARLOCK),
  ORANGE_SALAMANDER(NET_TRAP, 47, new int[] {224}, NpcID.SALAMANDER_ORANGE),
  // The source plugin has no NPC ID for this one (marked TODO there).
  RAZOR_BACKED_KEBBIT(NOOSE, 49, new int[] {348}),
  SABRE_TOOTHED_KEBBIT(DEADFALL, 51, new int[] {160}, NpcID.HUNTINGBEAST_SABRETEETH),
  GREY_CHINCHOMPA(BOX_TRAP, 53, new int[] {198, 199}, NpcID.HUNTING_CHINCHOMPA),
  SABRE_TOOTHED_KYATT(PIT, 53, new int[] {300}, NpcID.HUNTING_SNOW_TIGER),
  DARK_KEBBIT(FALCONRY, 57, new int[] {132}, NpcID.HUNTINGBEAST_SILENT),
  PYRE_FOX(DEADFALL, 57, new int[] {177, 178}, NpcID.VARLAMORE_HUNTERFOX01),
  RED_SALAMANDER(NET_TRAP, 59, new int[] {272}, NpcID.SALAMANDER_RED),
  WYRMSCRAIG_GOAT(GOAT_PIT, 60, IntStream.rangeClosed(100, 179).toArray(), NpcID.GOAT_PIT_GOAT),
  RED_CHINCHOMPA(BOX_TRAP, 63, new int[] {265}, NpcID.HUNTING_CHINCHOMPA_BIG),
  SUNLIGHT_MOTH(BUTTERFLY, 65, new int[] {74}, NpcID.MOTH_SUNLIGHT),
  DASHING_KEBBIT(FALCONRY, 69, new int[] {156}, NpcID.HUNTINGBEAST_SPEEDY2),
  SUNLIGHT_ANTELOPE(PIT, 72, new int[] {380}, NpcID.SUNLIGHT_ANTELOPE),
  MOONLIGHT_MOTH(BUTTERFLY, 75, new int[] {84}, NpcID.MOTH_MOONLIGHT, NpcID.MOTH_MOONLIGHT_LOW_WANDER),
  TECU_SALAMANDER(NET_TRAP, 79, new int[] {344}, NpcID.SALAMANDER_MOUNTAIN),
  HERBIBOAR(NOOSE_HERBIBOAR, 80, IntStream.rangeClosed(1950, 2461).toArray(), NpcID.FOSSIL_HERBIBOAR_VISIBLE),
  MOONLIGHT_ANTELOPE(PIT, 91, new int[] {450}, NpcID.MOONLIGHT_ANTELOPE);

  final Trap trap;
  final int level;
  final int[] xpDrops;
  final int[] npcIds;

  Creature(Trap trap, int level, int[] xpDrops, int... npcIds) {
    this.trap = trap;
    this.level = level;
    this.xpDrops = xpDrops;
    this.npcIds = npcIds;
  }

  boolean isNpc(int id) {
    for (int n : npcIds) if (n == id) return true;
    return false;
  }

  boolean isCatchXp(int xp) {
    for (int x : xpDrops) if (x == xp) return true;
    return false;
  }
}
