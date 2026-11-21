package com.yonwi.rumours;

import static org.junit.Assert.*;

import java.util.*;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class TravelTest {
  private static final WorldPoint BURROWS = new WorldPoint(1557, 9456, 0);

  /** A test account: owns the named item groups, has finished every quest, standard spellbook. */
  private static boolean house;

  private static Travel.Kit owning(boolean staffless, String... groups) {
    Set<Integer> owned = new HashSet<>();
    for (String g : groups) for (int id : Kit.IDS.get(g)) owned.add(id);
    return new Travel.Kit() {
      public boolean carries(int[] ids) {
        return false;
      }

      public boolean owns(int[] ids) {
        for (int id : ids) if (owned.contains(id)) return true;
        return false;
      }

      public int spellbook() {
        return 0;
      }

      public boolean finished(Quest q) {
        return true;
      }

      public boolean started(Quest q) {
        return true;
      }

      public boolean staffless() {
        return staffless;
      }

      public boolean house(boolean carriedOnly) {
        return house;
      }

      public boolean pohFairy() {
        return house;
      }

      public boolean pohSpirit() {
        return house;
      }

      public boolean pohNexus() {
        return house;
      }

      public boolean pohJewellery() {
        return house;
      }
    };
  }

  @Test
  public void sabreKebbitUsesTheFairyRingNextToTheGuild() {
    Travel.Plan p = Travel.plan(Rumour.SABRE_TOOTHED_KEBBIT, BURROWS, owning(false, "Dramen or lunar staff"));
    assertEquals("Fairy ring AJP (Avium Savannah) → D K S", p.route.text);
    assertEquals(Collections.singletonList("Dramen or lunar staff"), p.route.needs);
  }

  @Test
  public void eliteDiaryDropsTheStaff() {
    Travel.Plan p = Travel.plan(Rumour.SABRE_TOOTHED_KEBBIT, BURROWS, owning(true));
    assertTrue(p.route.needs.isEmpty());
  }

  @Test
  public void goatsUseTheNecklaceOfPassage() {
    Travel.Plan p = Travel.plan(Rumour.WYRMSCRAIG_GOAT, BURROWS, owning(false, "Necklace of passage", "Slayer ring"));
    assertEquals("Necklace of passage → Wyrmscraig, then south", p.route.text);
    assertEquals(1, p.alternatives.size());
  }

  @Test
  public void sunlightMothIsAWalkFromTheBurrows() {
    assertSame(Travel.WALK, Travel.plan(Rumour.SUNLIGHT_MOTH, BURROWS, owning(false)).route);
  }

  @Test
  public void houseFairyRingBeatsWalkingToAjp() {
    house = true;
    try {
      Travel.Plan p = Travel.plan(Rumour.GREY_CHINCHOMPA, BURROWS, owning(false, "Dramen or lunar staff"));
      assertEquals("House teleport → fairy ring A K Q", p.route.text);
      assertTrue(p.route.house);
      Travel.Plan o = Travel.plan(Rumour.RED_SALAMANDER, BURROWS, owning(false));
      assertEquals("House teleport → nexus: Ourania Cave → the salamanders", o.route.text);
    } finally {
      house = false;
    }
  }

  @Test
  public void loadoutSharesTheHouseTeleport() {
    house = true;
    try {
      Travel.Loadout l =
          Travel.loadout(
              Kit.POOLS.get(Hunter.TECO),
              owning(false, "Dramen or lunar staff", "Digsite pendant", "Necklace of passage", "Slayer ring", "Quetzal whistle"));
      StringBuilder dump = new StringBuilder();
      for (Map.Entry<Rumour, Travel.Plan> e : l.plans.entrySet()) dump.append(e.getKey()).append("=").append(e.getValue().route.text).append("; ");
      dump.append("back=").append(l.back);
      assertEquals(
          dump.toString(),
          new LinkedHashSet<>(Arrays.asList(Kit.HOUSE_TELEPORT, "Dramen or lunar staff", "Digsite pendant", "Necklace of passage")),
          new LinkedHashSet<>(l.groups));
      assertTrue(l.back.house);
      assertSame(Travel.WALK, l.plans.get(Rumour.SUNLIGHT_MOTH).route);
      assertEquals("House teleport → nexus: Ourania Cave → the salamanders", l.plans.get(Rumour.RED_SALAMANDER).route.text);
    } finally {
      house = false;
    }
  }

  @Test
  public void herbiboarUsesTheDigsitePendant() {
    Travel.Plan p = Travel.plan(Rumour.HERBIBOAR, BURROWS, owning(false, "Digsite pendant"));
    assertTrue(p.route.text.startsWith("Digsite pendant"));
  }
}
