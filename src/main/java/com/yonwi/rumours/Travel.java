package com.yonwi.rumours;

import java.util.*;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

/**
 * Ways to reach a rumour area or the Hunter Guild, and the choice of the quickest one the player
 * can use. Like Ultimate Farming's route choices: each area lists its options with a rough cost,
 * each option needs unlocks (quests, diaries, spellbook) and items, and the cheapest one the
 * player has unlocked and owns (carried, banked or in the huntsman's kit) is picked.
 *
 * <p>Sources: OSRS wiki pages "Fairy ring" (AJP is in the Avium Savannah; a dramen or lunar staff
 * is needed unless the Lumbridge &amp; Draynor elite diary is done), "Ourania Hunter area" (Ourania
 * Teleport, spirit tree to the Battlefield, Ardougne cloak to the Monastery), "Wyrmscraig" (necklace
 * of passage, slayer ring, Ardeaglais teleport; Fallen From Grace), and the Hunter Rumours plugin's
 * fairy ring codes. UNVERIFIED (from memory): the guild's surface position, which areas are a walk
 * from the guild, the quetzal map icon wording, the digsite pendant's Fossil Island option, and
 * the fairy ring being named "Fairy ring".
 */
final class Travel {
  /** Burrows bounds from the Hunter Rumours plugin's isInBurrows (BSD-2, Joshua Coffey). */
  static boolean inBurrows(WorldPoint p) {
    return p != null
        && p.getPlane() == 0
        && p.getX() >= 1549
        && p.getX() <= 1565
        && p.getY() >= 9449
        && p.getY() <= 9464;
  }

  /** UNVERIFIED: the burrows sit under the guild, so its surface is the burrows minus 6400 in y. */
  static final WorldPoint GUILD = new WorldPoint(1557, 3049, 0);

  /** The underground area around the burrows, which holds the guild's moonlight creatures. */
  static boolean underGuild(WorldPoint p) {
    return p != null && p.getY() > 9300 && p.getY() < 9500 && p.getX() > 1500 && p.getX() < 1620;
  }

  static boolean nearGuild(WorldPoint p) {
    return p != null && p.distanceTo(GUILD) <= 40;
  }

  static final int[] WHISTLES = com.yonwi.rumours.Kit.IDS.get("Quetzal whistle");
  static final int[] FORTIS_TABLET = com.yonwi.rumours.Kit.IDS.get("Civitas illa Fortis teleport");

  /** What the player has and has unlocked. Called on the client thread. */
  interface Kit {
    /** Carried or worn now. */
    boolean carries(int[] ids);

    /** Carried, worn, last seen in the bank, or in the huntsman's kit. */
    boolean owns(int[] ids);

    /** 0 standard, 1 ancient, 2 lunar, 3 arceuus. */
    int spellbook();

    boolean finished(Quest quest);

    boolean started(Quest quest);

    /** Lumbridge &amp; Draynor elite diary: fairy rings work without a staff. */
    boolean staffless();

    /** A house teleport: tablet or construction cape, or the spell with its runes. */
    boolean house(boolean carriedOnly);

    /** From the plugin's "Your house" settings. */
    boolean pohFairy();

    boolean pohSpirit();

    boolean pohNexus();

    /** Any jewellery box (all have the ring of dueling). */
    boolean pohJewellery();

    /** Jewellery box tier: 0 none, 1 basic, 2 fancy, 3 ornate. */
    default int pohJewelleryTier() {
      return pohJewellery() ? 3 : 0;
    }
  }

  /** One way to travel: say it, then highlight its item, spell, map icon or world object. */
  static final class Route {
    final String text;
    /** Items to click to use the route (highlighted in the inventory or worn tab). */
    int[] items = new int[0];
    /** Spell to cast from the spellbook, or null. */
    String spell;
    /** Lower-case words the quetzal map icon should contain, or null. */
    String quetzalSite;
    /** A second leg (e.g. a quetzal ride or a fairy ring) follows the first click. */
    boolean multiLeg;
    /** A world object to click, by name (e.g. "Fairy ring"), or null. */
    String object;
    /** Items the route needs: shown in the kit and the bank filter. */
    final List<String> needs = new ArrayList<>();
    /** Rough cost; lower is quicker. */
    double cost;
    /** Starts with a house teleport; then {@link #pohObject} is used inside the house. */
    boolean house;
    /** Lower-case word naming the house object to use (fairy, spirit, nexus, jewellery). */
    String pohObject;
    /** What to pick on it, e.g. "A K Q" or "Ourania Cave". */
    String pohChoice;

    Route(String text, double cost) {
      this.text = text;
      this.cost = cost;
    }

    Route items(String group) {
      needs.add(group);
      items = Kit_.ids(group);
      return this;
    }

    Route need(String group) {
      needs.add(group);
      return this;
    }

    /** House teleport, then this object in the house and this choice on it. */
    Route viaHouse(String object, String choice) {
      house = true;
      pohObject = object;
      pohChoice = choice;
      multiLeg = true;
      needs.add(com.yonwi.rumours.Kit.HOUSE_TELEPORT);
      return this;
    }

    boolean usable(Travel.Kit kit) {
      for (String n : needs)
        if (n.equals(com.yonwi.rumours.Kit.HOUSE_TELEPORT) ? !kit.house(false) : !kit.owns(Kit_.ids(n)))
          return false;
      return true;
    }

    boolean carried(Travel.Kit kit) {
      for (String n : needs)
        if (n.equals(com.yonwi.rumours.Kit.HOUSE_TELEPORT) ? !kit.house(true) : !kit.carries(Kit_.ids(n)))
          return false;
      return true;
    }

    @Override
    public String toString() {
      return text;
    }
  }

  /** Shorthand for the item table, which lives in {@link com.yonwi.rumours.Kit}. */
  private static final class Kit_ {
    static int[] ids(String group) {
      return com.yonwi.rumours.Kit.IDS.getOrDefault(group, new int[0]);
    }
  }

  static final Route WALK = new Route("Walk", 0);

  /** Routes that land at (or fly on to) a quetzal site. */
  static List<Route> viaQuetzal(String site, String siteWords, double cost) {
    List<Route> out = new ArrayList<>();
    Route whistle = new Route("Quetzal whistle → " + site, cost).items("Quetzal whistle");
    whistle.quetzalSite = siteWords;
    out.add(whistle);
    Route tablet =
        new Route("Civitas illa Fortis teleport → quetzal to " + site, cost + 8)
            .items("Civitas illa Fortis teleport");
    tablet.quetzalSite = siteWords;
    tablet.multiLeg = true;
    out.add(tablet);
    // Spell routes need runes; every teleport spell takes a law rune (rune pouch). UNVERIFIED: the
    // rest of the rune cost isn't checked.
    Route spell = new Route("Civitas illa Fortis teleport (spell) → quetzal to " + site, cost + 8).need("Law rune");
    spell.spell = "Civitas illa Fortis";
    spell.quetzalSite = siteWords;
    spell.multiLeg = true;
    out.add(spell);
    return out;
  }

  /** The cheapest usable route, preferring one whose items are already carried. */
  static Route best(List<Route> routes, Kit kit) {
    Route best = null;
    double bestCost = Double.MAX_VALUE;
    for (Route r : routes) {
      if (r.spell != null && r.spell.startsWith("Civitas") && kit.spellbook() != 0) continue;
      if (!r.usable(kit)) continue;
      double c = r.cost + (r.carried(kit) ? 0 : 2);
      if (c < bestCost) {
        best = r;
        bestCost = c;
      }
    }
    return best;
  }

  /** The way back to the burrows from wherever the player is. */
  static Route toGuild(WorldPoint here, Kit kit) {
    if (underGuild(here) || here.distanceTo(GUILD) <= 120) return WALK;
    Route r = best(guildOptions(kit), kit);
    return r != null ? r : new Route("Make your way to the Hunter Guild", 1000);
  }

  /** Every way back to the guild from far away. */
  static List<Route> guildOptions(Kit kit) {
    List<Route> all = new ArrayList<>(viaQuetzal("Hunter Guild", "hunter guild", 10));
    // From the house (wiki: fairy ring AJP is in the Avium Savannah; the nexus has Civitas illa
    // Fortis; every jewellery box has the ring of dueling's Fortis Colosseum).
    if (kit.pohFairy())
      all.add(new Route("House teleport → fairy ring A J P, then walk to the guild", 14).viaHouse("fairy", "A J P"));
    if (kit.pohNexus())
      all.add(new Route("House teleport → nexus: Civitas illa Fortis → quetzal to Hunter Guild", 16).viaHouse("nexus", "Civitas illa Fortis"));
    if (kit.pohJewellery())
      all.add(new Route("House teleport → jewellery box: Fortis Colosseum → quetzal to Hunter Guild", 17).viaHouse("jewellery", "Fortis Colosseum"));
    return all;
  }

  /**
   * The quickest way to a bank from far away, using only what's owned. Wiki "Ornate jewellery
   * box": the basic box has the ring of dueling (Ferox Enclave), the fancy one adds the skills
   * necklace (Crafting Guild), the ornate one the ring of wealth (Grand Exchange). UNVERIFIED: how
   * far each landing spot is from its bank.
   */
  static List<Route> bankOptions(Kit kit) {
    List<Route> all = new ArrayList<>();
    all.add(new Route("Crafting cape → Crafting Guild bank", 5).items("Crafting cape"));
    all.add(new Route("Ring of dueling → Ferox Enclave bank", 6).items("Ring of dueling"));
    int box = kit.pohJewelleryTier();
    if (box >= 1)
      all.add(new Route("House teleport → jewellery box: Ferox Enclave bank", 9).viaHouse("jewellery", "Ferox Enclave"));
    if (box >= 2)
      all.add(new Route("House teleport → jewellery box: Crafting Guild bank", 10).viaHouse("jewellery", "Crafting Guild"));
    if (box >= 3)
      all.add(new Route("House teleport → jewellery box: Grand Exchange", 11).viaHouse("jewellery", "Grand Exchange"));
    Route w = new Route("Quetzal whistle → Hunter Guild, then the burrows bank", 12).items("Quetzal whistle");
    w.quetzalSite = "hunter guild";
    all.add(w);
    return all;
  }

  /** Away from a bank the route's item has to be on you already. */
  static Route toBank(Kit kit) {
    List<Route> carried = new ArrayList<>();
    for (Route r : bankOptions(kit)) if (r.carried(kit)) carried.add(r);
    Route r = best(carried, kit);
    return r != null ? r : new Route("Make your way to a bank", 1000);
  }

  /** The burrows, where every trip starts. */
  static final WorldPoint BURROWS = new WorldPoint(1557, 9456, 0);

  /**
   * One set of travel items for every rumour the hunter gives, and the way back: the smallest
   * set first, then the quickest routes. Each new item group costs as much as 15 points of route
   * cost, so a route that reuses the loadout's items (e.g. one house teleport for the house fairy
   * ring and the nexus) beats a slightly quicker one that needs another item.
   */
  static final class Loadout {
    final Map<Rumour, Plan> plans = new LinkedHashMap<>();
    Route back;
    /** Item groups (Kit.IDS names, plus Kit.HOUSE_TELEPORT) the loadout carries. */
    final Set<String> groups = new LinkedHashSet<>();
  }

  private static final double ITEM_WEIGHT = 15;

  static Loadout loadout(List<Rumour> pool, Kit kit) {
    Map<Rumour, List<Plan>> choices = new LinkedHashMap<>();
    for (Rumour r : pool) {
      List<Plan> list = new ArrayList<>();
      Map<String, List<RumourLocation>> areas = new LinkedHashMap<>();
      for (RumourLocation l : RumourLocation.values())
        if (l.getRumour() == r) areas.computeIfAbsent(l.getLocationName(), k -> new ArrayList<>()).add(l);
      for (Map.Entry<String, List<RumourLocation>> e : areas.entrySet()) {
        List<WorldPoint> points = new ArrayList<>();
        for (RumourLocation l : e.getValue()) points.add(l.getWorldPoint());
        String ring = e.getValue().get(0).getFairyRingCode();
        if (GUILD_AREAS.contains(e.getKey())) {
          list.add(new Plan(e.getKey(), ring, points, WALK, Collections.emptyList(), 4));
          continue;
        }
        for (Route route : options(e.getKey(), ring, BURROWS, kit))
          if (route.usable(kit) && !(route.spell != null && route.spell.startsWith("Civitas") && kit.spellbook() != 0))
            list.add(new Plan(e.getKey(), ring, points, route, Collections.emptyList(), route.cost));
      }
      if (!list.isEmpty()) choices.put(r, list);
    }
    List<Route> back = new ArrayList<>();
    for (Route route : guildOptions(kit))
      if (route.usable(kit) && !(route.spell != null && route.spell.startsWith("Civitas") && kit.spellbook() != 0))
        back.add(route);

    Map<Rumour, Plan> chosen = new LinkedHashMap<>();
    Route chosenBack = null;
    // Two passes: the second revisits each choice knowing what the others carry.
    for (int pass = 0; pass < 2; pass++) {
      List<Rumour> order = new ArrayList<>(choices.keySet());
      order.sort(Comparator.comparingInt(r -> choices.get(r).size()));
      for (Rumour r : order) {
        Set<String> others = groupsExcept(chosen, chosenBack, r, false);
        Plan best = null;
        double bestScore = Double.MAX_VALUE;
        for (Plan p : choices.get(r)) {
          double s = p.cost + ITEM_WEIGHT * newGroups(p.route, others);
          if (s < bestScore) {
            best = p;
            bestScore = s;
          }
        }
        chosen.put(r, best);
      }
      Set<String> others = groupsExcept(chosen, null, null, true);
      double bestScore = Double.MAX_VALUE;
      for (Route route : back) {
        double s = route.cost + ITEM_WEIGHT * newGroups(route, others);
        if (s < bestScore) {
          chosenBack = route;
          bestScore = s;
        }
      }
    }
    Loadout l = new Loadout();
    l.plans.putAll(chosen);
    l.back = chosenBack;
    for (Plan p : chosen.values()) l.groups.addAll(p.route.needs);
    if (chosenBack != null) l.groups.addAll(chosenBack.needs);
    return l;
  }

  private static Set<String> groupsExcept(Map<Rumour, Plan> chosen, Route back, Rumour skip, boolean skipBack) {
    Set<String> out = new HashSet<>();
    for (Map.Entry<Rumour, Plan> e : chosen.entrySet()) if (e.getKey() != skip) out.addAll(e.getValue().route.needs);
    if (!skipBack && back != null) out.addAll(back.needs);
    return out;
  }

  private static int newGroups(Route r, Set<String> have) {
    int n = 0;
    for (String g : r.needs) if (!have.contains(g)) n++;
    return n;
  }

  /**
   * Like {@link #plan(Rumour, WorldPoint, Kit)}, but uses the loadout's area and route for the
   * rumour whenever a teleport is needed, so the guide never asks for anything outside it.
   */
  static Plan plan(Rumour rumour, WorldPoint here, Kit kit, Loadout loadout) {
    Plan normal = plan(rumour, here, kit);
    Plan chosen = loadout == null ? null : loadout.plans.get(rumour);
    if (chosen == null || normal == null || normal.route == WALK && normal.distance(here) <= 100)
      return normal;
    if (chosen.route == WALK) return candidate(chosen.area, chosen.fairyRing, chosen.points, here, kit);
    if (!chosen.route.usable(kit)) return normal;
    List<Route> others = new ArrayList<>();
    for (Route r : options(chosen.area, chosen.fairyRing, here, kit))
      if (!r.text.equals(chosen.route.text) && r.usable(kit)) others.add(r);
    // The fairy ring next to the guild only makes sense from the guild.
    Route route = chosen.route;
    if (route.object != null && !(underGuild(here) || nearGuild(here)))
      for (Route r : options(chosen.area, chosen.fairyRing, here, kit))
        if (r.object != null && !r.house) route = r;
    return new Plan(chosen.area, chosen.fairyRing, chosen.points, route, others, route.cost);
  }

  /** A rumour area (one named group of RumourLocation points) and the way there. */
  static final class Plan {
    final String area;
    final String fairyRing;
    final List<WorldPoint> points;
    final Route route;
    /** Other usable ways to the same area, quickest first. */
    final List<Route> alternatives;
    final double cost;

    Plan(String area, String fairyRing, List<WorldPoint> points, Route route, List<Route> alternatives, double cost) {
      this.area = area;
      this.fairyRing = fairyRing;
      this.points = points;
      this.route = route;
      this.alternatives = alternatives;
      this.cost = cost;
    }

    int distance(WorldPoint p) {
      int best = Integer.MAX_VALUE;
      for (WorldPoint w : points) best = Math.min(best, w.distanceTo(p));
      return best;
    }
  }

  /** UNVERIFIED: areas a short run from the Hunter Guild landing site or its stairs. */
  private static final Set<String> GUILD_AREAS =
      new HashSet<>(
          Arrays.asList(
              "Avium Savannah",
              "West of Hunter Guild",
              "Ralos' Rise",
              "Hunters' Guild",
              "North-west of the Locus Oasis"));

  /** UNVERIFIED: other areas with a quetzal landing site of their own. */
  private static final Map<String, String[]> QUETZAL_AREAS = new HashMap<>();

  static {
    QUETZAL_AREAS.put(
        "Tlati Rainforest (NE of Tal Teklan quetzal)", new String[] {"Tal Teklan", "tal teklan"});
    QUETZAL_AREAS.put("Auburnvale", new String[] {"Auburnvale", "auburn"});
  }

  /** Every way to reach an area, before checking what the player has. */
  static List<Route> options(String area, String ring, WorldPoint here, Kit kit) {
    List<Route> out = new ArrayList<>();
    if (GUILD_AREAS.contains(area)) out.addAll(viaQuetzal("Hunter Guild", "hunter guild", 10));
    String[] site = QUETZAL_AREAS.get(area);
    if (site != null) out.addAll(viaQuetzal(site[0], site[1], 12));
    switch (area) {
      case "Ourania Hunter area":
        if (kit.spellbook() == 2 && kit.finished(Quest.LUNAR_DIPLOMACY)) {
          Route r = new Route("Ourania Teleport (Lunar) → straight to the salamanders", 6).need("Law rune");
          r.spell = "Ourania Teleport";
          out.add(r);
        }
        if (kit.pohNexus())
          out.add(new Route("House teleport → nexus: Ourania Cave → the salamanders", 9).viaHouse("nexus", "Ourania Cave"));
        if (kit.pohSpirit() && kit.finished(Quest.TREE_GNOME_VILLAGE))
          out.add(new Route("House teleport → spirit tree: Battlefield, then walk west", 16).viaHouse("spirit", "Battlefield"));
        out.add(
            new Route("Ardougne cloak → Monastery, then walk west", 14).items("Ardougne cloak"));
        // A spirit tree is only reachable here through the house (none near the guild).
        break;
      case "Mushroom Forest on Fossil Island":
        if (kit.finished(Quest.BONE_VOYAGE)) {
          Route r =
              new Route("Digsite pendant → Fossil Island, then north to the Mushroom Forest", 12)
                  .items("Digsite pendant");
          out.add(r);
        }
        break;
      case "Wyrmscraig (Necklace of Passage)":
        if (kit.finished(Quest.FALLEN_FROM_GRACE)) {
          out.add(new Route("Necklace of passage → Wyrmscraig, then south", 8).items("Necklace of passage"));
          out.add(new Route("Ardeaglais teleport → Wyrmscraig, then south", 8.5).items("Ardeaglais teleport"));
          out.add(new Route("Slayer ring → Wyrmscraig, then south", 9).items("Slayer ring"));
        }
        break;
      default:
    }
    if (ring != null && ring.length() == 3 && kit.started(Quest.FAIRYTALE_II__CURE_A_QUEEN)) {
      String code = ring.charAt(0) + " " + ring.charAt(1) + " " + ring.charAt(2);
      boolean local = underGuild(here) || nearGuild(here);
      Route r =
          new Route(
              (local ? "Fairy ring AJP (Avium Savannah) → " : "Fairy ring → ") + code, local ? 20 : 25);
      r.object = "Fairy ring";
      r.multiLeg = true;
      if (!kit.staffless()) r.need("Dramen or lunar staff");
      out.add(r);
      if (kit.pohFairy()) {
        Route h = new Route("House teleport → fairy ring " + code, 15).viaHouse("fairy", code);
        if (!kit.staffless()) h.need("Dramen or lunar staff");
        out.add(h);
      }
    }
    return out;
  }

  /** Picks the rumour area that's quickest to reach, how to get there, and the alternatives. */
  static Plan plan(Rumour rumour, WorldPoint here, Kit kit) {
    Map<String, List<RumourLocation>> areas = new LinkedHashMap<>();
    for (RumourLocation l : RumourLocation.values())
      if (l.getRumour() == rumour)
        areas.computeIfAbsent(l.getLocationName(), k -> new ArrayList<>()).add(l);
    Plan best = null;
    for (Map.Entry<String, List<RumourLocation>> e : areas.entrySet()) {
      List<WorldPoint> points = new ArrayList<>();
      for (RumourLocation l : e.getValue()) points.add(l.getWorldPoint());
      String ring = e.getValue().get(0).getFairyRingCode();
      Plan p = candidate(e.getKey(), ring, points, here, kit);
      if (best == null || p.cost < best.cost) best = p;
    }
    return best;
  }

  private static Plan candidate(
      String area, String ring, List<WorldPoint> points, WorldPoint here, Kit kit) {
    Plan probe = new Plan(area, ring, points, WALK, Collections.emptyList(), 0);
    int fromHere = probe.distance(here);
    boolean guildArea = GUILD_AREAS.contains(area);
    // Already close, or setting off from the guild to one of its own areas: no teleport.
    if (fromHere <= 100 || guildArea && (underGuild(here) || nearGuild(here)))
      return new Plan(area, ring, points, WALK, Collections.emptyList(), fromHere <= 100 ? fromHere / 10.0 : 4);
    List<Route> all = options(area, ring, here, kit);
    Route route = best(all, kit);
    List<Route> others = new ArrayList<>();
    for (Route r : all) if (r != route && r.usable(kit) && !(r.spell != null && r.spell.startsWith("Civitas") && kit.spellbook() != 0)) others.add(r);
    others.sort(Comparator.comparingDouble(r -> r.cost));
    if (route == null)
      return new Plan(area, ring, points, new Route("Make your way to " + area, 1000), others, 1000);
    return new Plan(area, ring, points, route, others, route.cost);
  }

  private Travel() {}
}
