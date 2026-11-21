package com.yonwi.rumours;

import static org.junit.Assert.*;

import org.junit.Test;

public class RumourDataTest {
  @Test
  public void pityDropsAQuarterOfTheOutfitReductionPerPiece() {
    // One outfit piece, sunlight moth (butterfly net) -> 79.
    assertEquals(79, Trap.BUTTERFLY.pityFor(1));
    assertEquals(80, Trap.BUTTERFLY.pityFor(0));
    assertEquals(76, Trap.BUTTERFLY.pityFor(4));
  }

  @Test
  public void parsesRumourAndHunterFromText() {
    assertEquals(Rumour.SUNLIGHT_MOTH, Rumour.mentionedIn("I've heard of a sunlight moth nearby."));
    assertEquals(Rumour.NONE, Rumour.mentionedIn("Nothing to see here."));
    assertEquals(Hunter.TECO, Hunter.mentionedIn("Teco has one for you"));
    // "Aco" must not match inside other words.
    assertEquals(Hunter.NONE, Hunter.mentionedIn("tobacco and macaroni"));
  }

  @Test
  public void everyKitItemHasIds() {
    for (java.util.List<Rumour> pool : Kit.POOLS.values())
      for (Rumour r : pool)
        for (Kit.Need need : Kit.needs(r, 80))
          assertTrue(need.item, need.ids().length > 0 || need.item.equals(Kit.TELEGRAB));
    assertEquals(7, Kit.POOLS.get(Hunter.TECO).size());
    assertEquals(5, Kit.trapCount(80));
  }

  @Test
  public void everyRumourHasAnArea() {
    for (Rumour r : Rumour.values()) {
      if (r == Rumour.NONE || r == Rumour.RAZOR_BACKED_KEBBIT) continue;
      boolean found = false;
      for (RumourLocation l : RumourLocation.values()) found |= l.getRumour() == r;
      assertTrue(r.name(), found);
    }
  }
}
