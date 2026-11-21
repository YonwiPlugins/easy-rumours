package com.yonwi.rumours;

import java.util.*;
import net.runelite.api.gameval.ItemID;

/**
 * Items each rumour needs, and which rumours each hunter gives.
 *
 * <p>Sources: OSRS wiki "Hunters' Rumours" (hunter pools), "Deadfall" (knife or fletching knife +
 * any logs except redwood/arctic pine), "Net trap (item)" (one-item net trap, ID 34631), "Telekinetic Grab" (33 Magic, 1 air + 1 law; a staff of air replaces the air rune), "Sunlight moth"
 * (butterfly net + jar, or barehanded at 75 Hunter), "Herbiboar" (no items; magic secateurs
 * optional), "Wyrmscraig goat" (cattleprod, or Telekinetic Grab / Dark Lure). Looked up
 * 2026-10-07. IDs from the gameval ItemID table.
 */
final class Kit {
  /** Net trap item ID from the OSRS wiki (item page, released 2026-10-07). */
  static final int NET_TRAP = 34631;

  /**
   * Not an item: a house teleport, from a tablet, a construction cape, or Teleport to House (40
   * Magic, standard spellbook, 1 air + 1 earth + 1 law; wiki).
   */
  static final String HOUSE_TELEPORT = "House teleport";

  /** Not an item: Telekinetic Grab must be castable (33 Magic, standard spellbook). */
  static final String TELEGRAB = "Telekinetic Grab";

  /** One human name to every item ID that counts as it. */
  static final Map<String, int[]> IDS = new LinkedHashMap<>();

  static {
    IDS.put("Knife", new int[] {ItemID.KNIFE, ItemID.FLETCHING_KNIFE});
    IDS.put(
        "Logs",
        new int[] {
          ItemID.LOGS, ItemID.OAK_LOGS, ItemID.WILLOW_LOGS, ItemID.MAPLE_LOGS, ItemID.YEW_LOGS,
          ItemID.MAGIC_LOGS, ItemID.TEAK_LOGS, ItemID.MAHOGANY_LOGS, ItemID.ACHEY_TREE_LOGS,
          ItemID.JUNIPER_LOGS, ItemID.BLISTERWOOD_LOGS, ItemID.JATOBA_LOGS, ItemID.CAMPHOR_LOGS,
          ItemID.IRONWOOD_LOGS, ItemID.ROSEWOOD_LOGS
        });
    // The one-item net trap, released 7 October 2026: newer than the cached API (1.13.1), so its ID
    // is from the OSRS wiki item page. UNVERIFIED in game.
    IDS.put("Net trap", new int[] {NET_TRAP});
    IDS.put("Law rune", new int[] {ItemID.LAWRUNE});
    IDS.put(
        "Air rune",
        new int[] {ItemID.AIRRUNE, ItemID.DUSTRUNE, ItemID.MISTRUNE, ItemID.SMOKERUNE});
    IDS.put(
        "Air staff",
        new int[] {
          ItemID.STAFF_OF_AIR, ItemID.AIR_BATTLESTAFF, ItemID.MYSTIC_AIR_STAFF,
          ItemID.SMOKE_BATTLESTAFF, ItemID.MYSTIC_SMOKE_BATTLESTAFF, ItemID.MIST_BATTLESTAFF,
          ItemID.MYSTIC_MIST_BATTLESTAFF, ItemID.DUST_BATTLESTAFF, ItemID.MYSTIC_DUST_BATTLESTAFF
        });
    IDS.put("Box trap", new int[] {ItemID.HUNTING_BOX_TRAP});
    IDS.put(
        "Butterfly net", new int[] {ItemID.HUNTING_BUTTERFLY_NET, ItemID.II_MAGIC_BUTTERFLY_NET});
    IDS.put("Butterfly jar", new int[] {ItemID.BUTTERFLY_JAR});
    IDS.put("Magic secateurs", new int[] {ItemID.FAIRY_ENCHANTED_SECATEURS});
    IDS.put("Teasing stick", new int[] {ItemID.HUNTING_TEASING_STICK});
    IDS.put("Bird snare", new int[] {ItemID.HUNTING_OJIBWAY_BIRD_SNARE});
    IDS.put("Noose wand", new int[] {ItemID.NOOSE_WAND});
    // Travel items, as in Ultimate Farming's FarmingData.
    IDS.put(
        "Quetzal whistle",
        new int[] {
          ItemID.HG_QUETZALWHISTLE_BASIC,
          ItemID.HG_QUETZALWHISTLE_ENHANCED,
          ItemID.HG_QUETZALWHISTLE_PERFECTED,
          ItemID.HG_QUETZALWHISTLE_PERFECTED_INFINITE
        });
    IDS.put("Civitas illa Fortis teleport", new int[] {ItemID.POH_TABLET_FORTISTELEPORT});
    IDS.put(
        "Dramen or lunar staff",
        new int[] {ItemID.DRAMEN_STAFF, ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF});
    IDS.put(
        "Ardougne cloak",
        new int[] {
          ItemID.ARDY_CAPE_EASY, ItemID.ARDY_CAPE_MEDIUM, ItemID.ARDY_CAPE_HARD,
          ItemID.ARDY_CAPE_ELITE, ItemID.SKILLCAPE_MAX_ARDY
        });
    IDS.put(
        "Digsite pendant",
        new int[] {
          ItemID.NECKLACE_OF_DIGSITE_1, ItemID.NECKLACE_OF_DIGSITE_2, ItemID.NECKLACE_OF_DIGSITE_3,
          ItemID.NECKLACE_OF_DIGSITE_4, ItemID.NECKLACE_OF_DIGSITE_5
        });
    IDS.put(
        "Necklace of passage",
        new int[] {
          ItemID.NECKLACE_OF_PASSAGE_1, ItemID.NECKLACE_OF_PASSAGE_2, ItemID.NECKLACE_OF_PASSAGE_3,
          ItemID.NECKLACE_OF_PASSAGE_4, ItemID.NECKLACE_OF_PASSAGE_5
        });
    IDS.put("Crafting cape", new int[] {ItemID.SKILLCAPE_CRAFTING, ItemID.SKILLCAPE_CRAFTING_TRIMMED});
    IDS.put(
        "Ring of dueling",
        new int[] {
          ItemID.RING_OF_DUELING_1, ItemID.RING_OF_DUELING_2, ItemID.RING_OF_DUELING_3,
          ItemID.RING_OF_DUELING_4, ItemID.RING_OF_DUELING_5, ItemID.RING_OF_DUELING_6,
          ItemID.RING_OF_DUELING_7, ItemID.RING_OF_DUELING_8
        });    IDS.put("House teleport tablet", new int[] {ItemID.POH_TABLET_TELEPORTTOHOUSE});
    IDS.put(
        "Construction cape",
        new int[] {ItemID.SKILLCAPE_CONSTRUCTION, ItemID.SKILLCAPE_CONSTRUCTION_TRIMMED});
    IDS.put(
        "Earth rune",
        new int[] {ItemID.EARTHRUNE, ItemID.DUSTRUNE, ItemID.MUDRUNE, ItemID.LAVARUNE});
    IDS.put(
        "Earth staff",
        new int[] {
          ItemID.STAFF_OF_EARTH, ItemID.EARTH_BATTLESTAFF, ItemID.MYSTIC_EARTH_STAFF,
          ItemID.LAVA_BATTLESTAFF, ItemID.MYSTIC_LAVA_STAFF, ItemID.MUD_BATTLESTAFF,
          ItemID.MYSTIC_MUD_STAFF, ItemID.DUST_BATTLESTAFF, ItemID.MYSTIC_DUST_BATTLESTAFF
        });    IDS.put("Ardeaglais teleport", new int[] {ItemID.TELEPORTSCROLL_ARDEAGLAIS});
    IDS.put(
        "Slayer ring",
        new int[] {
          ItemID.SLAYER_RING_1, ItemID.SLAYER_RING_2, ItemID.SLAYER_RING_3, ItemID.SLAYER_RING_4,
          ItemID.SLAYER_RING_5, ItemID.SLAYER_RING_6, ItemID.SLAYER_RING_7, ItemID.SLAYER_RING_8,
          ItemID.SLAYER_RING_ETERNAL
        });
  }

  /**
   * Recommended extras from the OSRS wiki's "Hunters' Rumours/Strategies" page (2026-10-07):
   * added to the loadout only when owned, never required. {@code traps} empty means every rumour.
   */
  static final class Extra {
    final String name, why;
    final int[] ids;
    final Set<Trap> traps;
    /** Worn or wielded, so it doesn't take an inventory slot. */
    final boolean worn;

    Extra(String name, String why, boolean worn, int[] ids, Trap... traps) {
      this.name = name;
      this.why = why;
      this.worn = worn;
      this.ids = ids;
      this.traps = new HashSet<>(Arrays.asList(traps));
    }
  }

  static final List<Extra> EXTRAS =
      Arrays.asList(
          new Extra("Bonecrusher", "buries bones from catches", false, new int[] {ItemID.BONECRUSHER}),
          new Extra("Bonecrusher necklace", "buries bones, worn", true, new int[] {ItemID.BONECRUSHER_NECKLACE}),
          new Extra(
              "Meat pouch",
              "holds meat from catches",
              false,
              new int[] {
                ItemID.HG_MEATPOUCH_SMALL, ItemID.HG_MEATPOUCH_LARGE,
                ItemID.HG_MEATPOUCH_SMALL_OPEN, ItemID.HG_MEATPOUCH_LARGE_OPEN
              }),
          new Extra(
              "Fur pouch",
              "holds furs from catches",
              false,
              new int[] {
                ItemID.HG_FURPOUCH_SMALL, ItemID.HG_FURPOUCH_MED, ItemID.HG_FURPOUCH_LARGE,
                ItemID.HG_FURPOUCH_SMALL_OPEN, ItemID.HG_FURPOUCH_MED_OPEN, ItemID.HG_FURPOUCH_LARGE_OPEN
              }),
          new Extra("Ring of endurance", "run energy, worn", true, new int[] {ItemID.RING_OF_ENDURANCE}),
          new Extra("Hunter's spear", "box traps and pits, wielded", true, new int[] {ItemID.HG_HUNTER_SPEAR}, Trap.BOX_TRAP, Trap.PIT),
          new Extra(
              "Axe",
              "chop logs for deadfalls",
              false,
              new int[] {
                ItemID.BRONZE_AXE, ItemID.IRON_AXE, ItemID.STEEL_AXE, ItemID.BLACK_AXE, ItemID.MITHRIL_AXE,
                ItemID.ADAMANT_AXE, ItemID.RUNE_AXE, ItemID.DRAGON_AXE, ItemID.INFERNAL_AXE,
                ItemID.CRYSTAL_AXE, ItemID._3A_AXE, ItemID.TRAILBLAZER_AXE, ItemID.TRAILBLAZER_RELOADED_AXE
              },
              Trap.DEADFALL),
          new Extra("Log basket", "carries logs for deadfalls", false, new int[] {ItemID.LOG_BASKET_CLOSED, ItemID.LOG_BASKET_OPEN}, Trap.DEADFALL),
          new Extra(
              "Kandarin headgear",
              "extra logs for deadfalls, worn",
              true,
              new int[] {ItemID.SEERS_HEADBAND_EASY, ItemID.SEERS_HEADBAND_MEDIUM, ItemID.SEERS_HEADBAND_HARD, ItemID.SEERS_HEADBAND_ELITE},
              Trap.DEADFALL),
          new Extra(
              "Herb sack",
              "herbs from herbiboar",
              false,
              new int[] {
                ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN,
                ItemID.SLAYER_HERB_SACK_SILK, ItemID.SLAYER_HERB_SACK_SILK_OPEN
              },
              Trap.NOOSE_HERBIBOAR));
  /** One item a rumour needs. */
  static final class Need {
    final String item;
    /** Traps: one per trap you can lay. Others: 1. */
    final boolean perTrap;
    /** Helps but doesn't hold the guide (e.g. secateurs), or not needed at this Hunter level. */
    final boolean optional;
    /** Shown after the name, e.g. "or barehanded at 75". */
    final String note;

    Need(String item, boolean perTrap, boolean optional, String note) {
      this.item = item;
      this.perTrap = perTrap;
      this.optional = optional;
      this.note = note;
    }

    /** Item IDs that count; empty for the spell itself. */
    int[] ids() {
      return IDS.getOrDefault(item, new int[0]);
    }

    /** Runes also count when they're in the rune pouch. */
    boolean rune() {
      return item.endsWith(" rune");
    }
  }

  private static Need need(String item) {
    return new Need(item, false, false, "");
  }

  private static Need trap(String item) {
    return new Need(item, true, false, "");
  }

  /** What to bring for this rumour's creature. */
  static List<Need> needs(Rumour rumour, int hunterLevel) {
    if (rumour == Rumour.NONE) return Collections.emptyList();
    switch (rumour.creature.trap) {
      case DEADFALL:
        return Arrays.asList(need("Knife"), need("Logs"));
      case NET_TRAP:
        return Collections.singletonList(trap("Net trap"));
      case BOX_TRAP:
        return Collections.singletonList(trap("Box trap"));
      case BUTTERFLY:
        boolean barehanded = hunterLevel >= 75;
        return Arrays.asList(
            new Need("Butterfly net", false, barehanded, barehanded ? "or barehanded" : ""),
            new Need("Butterfly jar", false, barehanded, barehanded ? "or barehanded" : ""));
      case GOAT_PIT:
        // Telekinetic Grab moves the goats into the pit.
        return Arrays.asList(
            need(TELEGRAB), need("Law rune"), new Need("Air rune", false, false, "or an air staff"));
      case NOOSE_HERBIBOAR:
        return Collections.singletonList(new Need("Magic secateurs", false, true, "extra herb"));
      case PIT:
        return Arrays.asList(need("Teasing stick"), need("Knife"), need("Logs"));
      case SNARE:
        return Collections.singletonList(trap("Bird snare"));
      case NOOSE:
        return Collections.singletonList(need("Noose wand"));
      case FALCONRY:
      default:
        // Falconry gloves and the falcon are hired on site.
        return Collections.emptyList();
    }
  }

  /** Traps you can lay at once outside the Wilderness: 1, plus 1 per 20 Hunter levels. */
  static int trapCount(int hunterLevel) {
    return Math.min(5, 1 + hunterLevel / 20);
  }

  /** From the wiki's "Hunters' Rumours" page, 2026-10-07. */
  static final Map<Hunter, List<Rumour>> POOLS = new EnumMap<>(Hunter.class);

  static {
    POOLS.put(
        Hunter.TECO,
        Arrays.asList(
            Rumour.SABRE_TOOTHED_KEBBIT,
            Rumour.RED_SALAMANDER,
            Rumour.GREY_CHINCHOMPA,
            Rumour.RED_CHINCHOMPA,
            Rumour.SUNLIGHT_MOTH,
            Rumour.HERBIBOAR,
            Rumour.WYRMSCRAIG_GOAT));
    POOLS.put(
        Hunter.ACO,
        Arrays.asList(
            Rumour.ORANGE_SALAMANDER,
            Rumour.SABRE_TOOTHED_KEBBIT,
            Rumour.DARK_KEBBIT,
            Rumour.RED_SALAMANDER,
            Rumour.GREY_CHINCHOMPA,
            Rumour.SABRE_TOOTHED_KYATT,
            Rumour.RED_CHINCHOMPA,
            Rumour.TECU_SALAMANDER,
            Rumour.MOONLIGHT_MOTH));
    POOLS.put(
        Hunter.WOLF,
        Arrays.asList(
            Rumour.RED_SALAMANDER,
            Rumour.RED_CHINCHOMPA,
            Rumour.DASHING_KEBBIT,
            Rumour.SUNLIGHT_ANTELOPE,
            Rumour.TECU_SALAMANDER,
            Rumour.HERBIBOAR,
            Rumour.MOONLIGHT_MOTH,
            Rumour.MOONLIGHT_ANTELOPE));
  }

  private Kit() {}
}
