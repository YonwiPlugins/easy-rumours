package com.yonwi.rumours;

import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

/**
 * Guides one Hunter Rumour loop: get a rumour, travel to the creature, catch it until the rare
 * part drops, travel back and hand it in. Every step is re-derived each tick from game state
 * (current rumour, inventory, location); only the rumour itself comes from dialogue.
 *
 * <p>Dialogue parsing, the burrows bounds and the rumour/creature/trap data follow the Hunter
 * Rumours plugin (https://github.com/geel9/runelite-hunter-rumours), BSD-2-Clause, Copyright (c)
 * 2024, Joshua Coffey. See THIRD_PARTY_NOTICES.md.
 */
@PluginDescriptor(
    name = "Easy Rumours",
    description = "Guides one Hunter Rumour at a time and tracks your rumour stats",
    tags = {"hunter", "rumour", "rumor", "guild", "varlamore", "quetzal"})
@PluginDependency(BankTagsPlugin.class)
public class RumoursPlugin extends Plugin {
  enum Step {
    GET_RUMOUR("Get a rumour"),
    GET_ITEMS("Get items"),
    RESTOCK("Restock"),
    TO_AREA("Travel"),
    HUNT("Hunt"),
    RETURN("Return"),
    HAND_IN("Hand in");

    final String label;

    Step(String label) {
      this.label = label;
    }
  }

  /**
   * Everything the overlays and the sidebar read, swapped in whole once per tick so they never see
   * half an update.
   */
  static final class View {
    Hunter hunter = Hunter.NONE;
    Rumour rumour = Rumour.NONE;
    Step step = Step.GET_RUMOUR;
    String instruction = "";
    String area = "";
    int distance = -1;
    int catches, pity;
    boolean hasPart;
    List<NPC> npcs = Collections.emptyList();
    List<TileObject> objects = Collections.emptyList();
    List<TileObject> nextObjects = Collections.emptyList();
    Set<Integer> items = Collections.emptySet();
    Set<Integer> nextItems = Collections.emptySet();
    String spell;
    String quetzalSite;
    /** Item IDs to pick out in the open bank. */
    Set<Integer> bankItems = Collections.emptySet();
    /** Required items not carried, e.g. "Rope (in bank)"; empty when ready. */
    List<String> missing = Collections.emptyList();
    /** One row per rumour the hunter gives, for the sidebar. */
    List<KitRow> kit = Collections.emptyList();
    /** Guild hunter hood, top, legs, boots: worn or not. */
    boolean[] outfit = new boolean[4];
    /** Wyrmscraig goats: remains in the pit now (-1 when not a goat rumour), cleared this rumour. */
    int goatInPit = -1, goatCleared, goatCapacity;
    /** When the bank and the huntsman's kit were last seen (ms), 0 if never. */
    long bankSeen, kitSeen;
    boolean skippedItems;
    /** The chosen way to the area, its items, and the other usable ways. */
    String route = "";
    List<KitItem> travel = Collections.emptyList();
    List<String> alternatives = new ArrayList<>();
    /** One loadout for all the hunter's rumours: travel items, the huntsman's kit and its traps. */
    List<KitItem> loadout = Collections.emptyList();
    /** "Grey Chinchompa: House teleport → fairy ring A K Q", one per rumour, then "Back: …". */
    List<String> loadoutLines = Collections.emptyList();
    String loadoutSlots = "";
    /** Loadout items not carried (or in a carried kit) that are in the bank or an uncarried kit. */
    List<String> loadoutMissing = Collections.emptyList();
    Set<Integer> loadoutBank = Collections.emptySet();
    /** Goat pit slot number shown on each goat. */
    Map<NPC, Integer> npcLabels = Collections.emptyMap();
    /** Goats Telekinetic Grab was cast on, still on their way to the pit. */
    List<NPC> grabbed = Collections.emptyList();
    /** The rare part is found and the trip back hasn't started: celebrate. */
    boolean rainbow;
    /** Goats: the suggested tile to stand on, or null. */
    LocalPoint standTile;
    /** NPCs for the step after this one, drawn in orange. */
    List<NPC> nextNpcs = Collections.emptyList();
    /** The rare part lying on the floor (full inventory), or null. */
    LocalPoint groundPart;
    /** The quickest bank teleport you're carrying right now. */
    String nearestBank = "";
    /** Debug: the goat field's outline, so it can be checked against the ground. */
    List<LocalPoint> goatArea = Collections.emptyList();
    /** The stand ring's tiles, when "Show the stand ring" is on. */
    List<LocalPoint> standRing = Collections.emptyList();
  }

  /** One needed item and where it is right now. */
  static final class KitItem {
    String name, note = "";
    /** Item ID for its icon, or -1 for the spell. */
    int icon = -1;
    int have, want;
    boolean optional;
    /** Where it is when not carried: "in kit", "in bank", "not in bank", ... */
    String where = "";

    boolean ok() {
      return have >= want;
    }
  }

  /** A rumour and the state of each item it needs. */
  static final class KitRow {
    final Rumour rumour;
    final boolean current;
    final List<KitItem> items = new ArrayList<>();
    boolean ready = true;

    KitRow(Rumour rumour, boolean current) {
      this.rumour = rumour;
      this.current = current;
    }
  }

  /** UNVERIFIED (from the Hunter Rumours source, not yet seen by us): game message texts. */
  static final String RARE_PART_MESSAGE = "you find a rare piece of the creature!";

  static final String WHISTLE_MESSAGE = "your current rumour target is";

  /** UNVERIFIED (from the Hunter Rumours source): hunter lines that close a finished rumour. */
  private static final String[] FINISHED_LINES = {
    "would you like another rumour?", "here's your reward.", "another one done?"
  };

  private static final int[] OUTFIT = {
    ItemID.HG_HUNTER_HOOD, ItemID.HG_HUNTER_TOP, ItemID.HG_HUNTER_LEGS, ItemID.HG_HUNTER_BOOTS
  };

  private static final String BANK_TAG = "easy rumours";
  @Inject private TagManager tagManager;
  @Inject private BankTagsService bankTags;
  @Inject private Client client;
  @Inject private ClientThread clientThread;
  @Inject private net.runelite.client.game.ItemManager itemManager;
  @Inject private ConfigManager configManager;
  @Inject private com.google.gson.Gson gson;
  @Inject private ClientToolbar toolbar;
  @Inject private OverlayManager overlays;
  @Inject private RumoursConfig config;
  @Inject private RumoursSceneOverlay sceneOverlay;
  @Inject private RumoursWidgetOverlay widgetOverlay;
  @Inject private RumoursInfoOverlay infoOverlay;
  @Inject private RumoursDebugOverlay debugOverlay;

  private volatile View view = new View();
  /** Everything the bank button shows: the rumour's items plus travel items. */
  private volatile Set<Integer> tagItems = Collections.emptySet();
  /** Items of the chosen route and its alternatives, for the bank filter. */
  private Set<Integer> routeItems = new HashSet<>();
  private Travel.Loadout loadout;
  /** "Set off anyway" on the loadout: don't ask again this session. */
  private volatile boolean skipLoadout;
  private Widget bankButton, bankIcon;
  private volatile boolean active;
  private RumoursPanel panel;
  private NavigationButton navigation;

  // Rumour state, saved per account so it survives logouts.
  private Hunter hunter = Hunter.NONE;
  private Rumour rumour = Rumour.NONE;
  private int catches;

  // Session stats, read by the sidebar.
  private volatile long sessionStart = System.currentTimeMillis();
  private volatile int sessionCompletions;
  private volatile long rumourStart;
  private volatile long lastRumourMillis = -1;
  private volatile String lastResult = "";

  // Tick-to-tick tracking.
  private WorldPoint lastLocation;
  /** One finished rumour, for the history table. Saved as JSON per account. */
  static final class Record {
    String rumour;
    int catches, pity;
    /** Milliseconds from assignment to the rare part, and to the hand-in. */
    long toPart, total;
    long at;
  }

  private final List<Record> records = new ArrayList<>();
  private volatile long donePartAt;
  /** The rare part was found (inventory or "rare piece" game message) and not yet handed in. */
  private boolean partFound;
  private int doneCatches, donePity;

  /** A copy of the history, for the sidebar (any thread). */
  List<Record> records() {
    synchronized (records) {
      return new ArrayList<>(records);
    }
  }

  /** Replaces the history after an edit in Notepad (any thread), and saves it. */
  void replaceRecords(List<Record> edited) {
    synchronized (records) {
      records.clear();
      records.addAll(edited);
      configManager.setRSProfileConfiguration(RumoursConfig.GROUP, "records", gson.toJson(records));
    }
  }

  RumoursConfig config() {
    return config;
  }

  /** When the rare part showed up (ms), or 0 while still hunting. */
  long partAt() {
    return donePartAt;
  }

  /** The rumour whose rare part is in the inventory, until it's handed in. */
  private Rumour heldPart;
  private long doneStart;
  private int hunterXp = -1;
  private Step pendingStep;
  private int pendingTicks;
  private Step committedStep;
  /** A teleport was used during this travel leg, so its item is no longer highlighted. */
  private boolean teleported;
  private boolean lastInstance;
  /** The route being followed, kept from the start of the trip until the step changes. */
  private Travel.Route lockedRoute;
  /** Debug: the current leg and why the last leg ended. */
  private String leg = "-", legReason = "-";
  private boolean loaded;
  /** Item ID to quantity, last seen in the bank and in the huntsman's kit; null if never seen. */
  private Map<Integer, Integer> bank, huntsKit;
  private long bankSeenAt, kitSeenAt;
  /** "Set off anyway": skip the items check until the rumour changes. */
  private volatile boolean skipItems;
  private int goatLast = -1, goatCleared;
  private boolean goatClearing;
  /** Debug: the pit's state and each nearby goat's verdict. */
  private String goatPitDebug = "-";
  private final List<String> goatVerdicts = new ArrayList<>();
  private String goatStepPending, goatStep;
  private int goatStepTicks;

  // Debug.
  private String lastDialog = "-";
  private String lastXp = "-";
  private String lastRoute = "-";
  private final java.util.Deque<String> events = new ArrayDeque<>();

  @Provides
  RumoursConfig provideConfig(ConfigManager manager) {
    return manager.getConfig(RumoursConfig.class);
  }

  View view() {
    return view;
  }

  @Override
  protected void startUp() {
    active = true;
    sessionStart = System.currentTimeMillis();
    sessionCompletions = 0;
    overlays.add(sceneOverlay);
    overlays.add(widgetOverlay);
    overlays.add(infoOverlay);
    overlays.add(debugOverlay);
    tagManager.registerTag(BANK_TAG, id -> tagItems.contains(id));
    clientThread.invoke(this::load);
    SwingUtilities.invokeLater(
        () -> {
          if (!active) return;
          panel = new RumoursPanel(this, itemManager, clientThread);
          navigation =
              NavigationButton.builder()
                  .tooltip("Easy Rumours")
                  .priority(6)
                  .icon(icon())
                  .panel(panel)
                  .build();
          toolbar.addNavigation(navigation);
        });
  }

  @Override
  protected void shutDown() {
    active = false;
    overlays.remove(sceneOverlay);
    overlays.remove(widgetOverlay);
    overlays.remove(infoOverlay);
    overlays.remove(debugOverlay);
    view = new View();
    loaded = false;
    tagManager.unregisterTag(BANK_TAG);
    clientThread.invoke(
        () -> {
          if (BANK_TAG.equals(bankTags.getActiveTag())) bankTags.closeBankTag();
          Widget universe = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
          if (bankButton != null && universe != null) {
            bankButton.setHidden(true);
            if (bankIcon != null) bankIcon.setHidden(true);
            universe.revalidate();
          }
          bankButton = bankIcon = null;
        });
    SwingUtilities.invokeLater(
        () -> {
          if (navigation != null) toolbar.removeNavigation(navigation);
          if (panel != null) panel.dispose();
          navigation = null;
          panel = null;
        });
  }

  /** A gold quetzal feather on a green disc. */
  static BufferedImage icon() {
    BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = icon.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setPaint(new GradientPaint(0, 16, new Color(40, 92, 52), 16, 0, new Color(96, 168, 92)));
    g.fill(new Ellipse2D.Double(0.5, 0.5, 15, 15));
    Path2D feather = new Path2D.Double();
    feather.moveTo(4, 13);
    feather.curveTo(4, 7, 8, 3, 12.5, 2.5);
    feather.curveTo(12.5, 7.5, 9, 11.5, 4, 13);
    feather.closePath();
    g.setColor(Style.GOLD);
    g.fill(feather);
    g.setColor(new Color(90, 60, 20));
    g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(new java.awt.geom.Line2D.Double(3.5, 13.5, 10.5, 5));
    g.dispose();
    return icon;
  }

  // ---------------------------------------------------------------- saved state

  private void load() {
    if (client.getGameState() != GameState.LOGGED_IN) return;
    hunter = parse(Hunter.class, get("hunter"), Hunter.NONE);
    rumour = parse(Rumour.class, get("rumour"), Rumour.NONE);
    try {
      catches = Integer.parseInt(Objects.toString(get("catches"), "0"));
    } catch (NumberFormatException e) {
      catches = 0;
    }
    rumourStart = parseLong(get("rumourStart"));
    try {
      Record[] saved = gson.fromJson(Objects.toString(get("records"), "[]"), Record[].class);
      synchronized (records) {
        records.clear();
        if (saved != null) for (Record r : saved) if (r != null && r.rumour != null) records.add(r);
      }
    } catch (RuntimeException e) {
      // Damaged history: start a fresh one.
    }
    hunterXp = client.getSkillExperience(Skill.HUNTER);
    heldPart = null;
    bank = decode(get("bank"));
    huntsKit = decode(get("huntsKit"));
    bankSeenAt = parseLong(get("bankSeen"));
    kitSeenAt = parseLong(get("huntsKitSeen"));
    goatCleared = (int) parseLong(get("goatCleared"));
    loaded = true;
  }

  private static long parseLong(String s) {
    try {
      return s == null ? 0 : Long.parseLong(s);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** Called from the sidebar's "Set off anyway" button (EDT). */
  void skipItems() {
    skipItems = true;
    skipLoadout = true;
  }

  private static Map<Integer, Integer> decode(String s) {
    if (s == null) return null;
    Map<Integer, Integer> items = new HashMap<>();
    for (String pair : s.split(",")) {
      String[] p = pair.split(":");
      try {
        if (p.length == 2) items.put(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
      } catch (NumberFormatException e) {
        // Skip a damaged entry.
      }
    }
    return items;
  }

  private static String encode(Map<Integer, Integer> items) {
    StringBuilder b = new StringBuilder();
    for (Map.Entry<Integer, Integer> e : items.entrySet())
      b.append(b.length() == 0 ? "" : ",").append(e.getKey()).append(":").append(e.getValue());
    return b.toString();
  }

  /** Remembers the bank and the huntsman's kit whenever the game sends their contents. */
  @Subscribe
  public void onItemContainerChanged(ItemContainerChanged e) {
    int id = e.getContainerId();
    if (id != InventoryID.BANK && id != InventoryID.HUNTSMANS_KIT) return;
    Map<Integer, Integer> items = new HashMap<>();
    for (Item item : e.getItemContainer().getItems())
      if (item.getId() > 0 && item.getQuantity() > 0) items.merge(item.getId(), item.getQuantity(), Integer::sum);
    if (id == InventoryID.BANK) {
      bank = items;
      bankSeenAt = System.currentTimeMillis();
      configManager.setRSProfileConfiguration(RumoursConfig.GROUP, "bankSeen", String.valueOf(bankSeenAt));
      configManager.setRSProfileConfiguration(RumoursConfig.GROUP, "bank", encode(items));
    } else {
      huntsKit = items;
      kitSeenAt = System.currentTimeMillis();
      configManager.setRSProfileConfiguration(RumoursConfig.GROUP, "huntsKitSeen", String.valueOf(kitSeenAt));
      configManager.setRSProfileConfiguration(RumoursConfig.GROUP, "huntsKit", encode(items));
      event("huntsman's kit seen: " + items.size() + " item types");
    }
  }

  private String get(String key) {
    return configManager.getRSProfileConfiguration(RumoursConfig.GROUP, key);
  }

  private void save() {
    configManager.setRSProfileConfiguration(RumoursConfig.GROUP, "hunter", hunter.name());
    configManager.setRSProfileConfiguration(RumoursConfig.GROUP, "rumour", rumour.name());
    configManager.setRSProfileConfiguration(RumoursConfig.GROUP, "catches", String.valueOf(catches));
    configManager.setRSProfileConfiguration(RumoursConfig.GROUP, "rumourStart", String.valueOf(rumourStart));
    configManager.setRSProfileConfiguration(RumoursConfig.GROUP, "goatCleared", String.valueOf(goatCleared));
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
    if (name == null) return fallback;
    try {
      return Enum.valueOf(type, name);
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }

  int totalCompletions() {
    String v = configManager.getRSProfileConfiguration(RumoursConfig.GROUP, "completions");
    try {
      return v == null ? 0 : Integer.parseInt(v);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  // ---------------------------------------------------------------- sidebar reads (any thread)

  long sessionStart() {
    return sessionStart;
  }

  int sessionCompletions() {
    return sessionCompletions;
  }

  long rumourStart() {
    return rumourStart;
  }

  long lastRumourMillis() {
    return lastRumourMillis;
  }

  String lastResult() {
    return lastResult;
  }

  /** Called from the sidebar's button (EDT). */
  void resetSession() {
    skipLoadout = false;
    sessionStart = System.currentTimeMillis();
    sessionCompletions = 0;
    lastRumourMillis = -1;
    lastResult = "";
  }

  // ---------------------------------------------------------------- events

  @Subscribe
  public void onGameStateChanged(GameStateChanged e) {
    if (e.getGameState() == GameState.LOGGED_IN && !loaded) load();
    if (e.getGameState() == GameState.LOGIN_SCREEN) loaded = false;
    // A new scene: ground items are sent again.
    if (e.getGameState() == GameState.LOADING || e.getGameState() == GameState.LOGIN_SCREEN)
      partsOnGround.clear();
  }

  @Subscribe
  public void onChatMessage(ChatMessage e) {
    String message = e.getMessage();
    if (e.getType() == ChatMessageType.DIALOG) {
      // NPC dialogue arrives as "Name|text".
      int bar = message.indexOf('|');
      if (bar > 0) dialog(message.substring(0, bar), message.substring(bar + 1));
      return;
    }
    if (e.getType() != ChatMessageType.GAMEMESSAGE) return;
    String text = Text.removeTags(message).toLowerCase(Locale.ROOT);
    if (text.contains(WHISTLE_MESSAGE)) {
      Rumour r = Rumour.mentionedIn(text);
      Hunter h = Hunter.mentionedIn(text);
      event("whistle: " + r.name + " / " + h.commonName);
      if (r != Rumour.NONE) assign(h != Hunter.NONE ? h : hunter, r, "whistle");
    } else if (text.contains(RARE_PART_MESSAGE)) {
      event("rare part message (" + catches + " catches)");
      partFound = true;
    }
  }

  @Subscribe
  public void onStatChanged(StatChanged e) {
    if (e.getSkill() != Skill.HUNTER) return;
    int xp = e.getXp();
    if (hunterXp < 0) {
      hunterXp = xp;
      return;
    }
    int gain = xp - hunterXp;
    hunterXp = xp;
    if (gain <= 0) return;
    boolean counts =
        rumour != Rumour.NONE && !carriesPart() && rumour.creature.isCatchXp(gain);
    lastXp = gain + (counts ? " (catch)" : rumour == Rumour.NONE ? "" : " (not a catch)");
    if (counts) {
      catches++;
      save();
    }
  }

  @Subscribe
  public void onGameTick(GameTick e) {
    if (!loaded) load();
    readDialogWidget();
    update();
  }

  // ---------------------------------------------------------------- rumour detection

  /** Fallback for dialogue that doesn't come through as a DIALOG game message. */
  private String lastWidgetDialog;

  private void readDialogWidget() {
    Widget name = client.getWidget(InterfaceID.ChatLeft.NAME);
    Widget text = client.getWidget(InterfaceID.ChatLeft.TEXT);
    if (name == null || text == null || name.isHidden() || text.isHidden()) {
      lastWidgetDialog = null;
      return;
    }
    String line = name.getText() + "|" + text.getText();
    if (line.equals(lastWidgetDialog)) return;
    lastWidgetDialog = line;
    dialog(name.getText(), text.getText());
  }

  /** A hunter's line in the burrows: may assign, mention or close a rumour. */
  private void dialog(String speaker, String rawText) {
    Player player = client.getLocalPlayer();
    if (player == null || !Travel.inBurrows(player.getWorldLocation())) return;
    Hunter speaking = speaker(Text.removeTags(speaker));
    if (speaking == Hunter.NONE) return;
    String text = Text.removeTags(rawText).replace("<br>", " ").toLowerCase(Locale.ROOT);
    String shown = speaking.commonName + ": " + text;
    if (shown.equals(lastDialog)) return;
    lastDialog = shown;
    for (String line : FINISHED_LINES)
      if (text.contains(line)) {
        if (rumour != Rumour.NONE) event("rumour closed by " + speaking.commonName);
        assign(speaking, Rumour.NONE, "closed");
        return;
      }
    // At First Light: Wolf mentions a creature without assigning a rumour.
    if (text.contains("stopped off for a bit of hunting first")) return;
    Hunter named = Hunter.mentionedIn(text);
    Rumour r = Rumour.mentionedIn(text);
    if (r == Rumour.NONE) return;
    // Gilman offering to swap to another rumour isn't an assignment.
    if (text.contains("would you prefer that one, or a new one entirely")) return;
    assign(named != Hunter.NONE ? named : speaking, r, "dialogue");
  }

  private Hunter speaker(String name) {
    for (Hunter h : Hunter.values()) {
      if (h == Hunter.NONE) continue;
      NPCComposition def = client.getNpcDefinition(h.npcId);
      if (def != null && name.equalsIgnoreCase(def.getName())) return h;
    }
    return Hunter.mentionedIn(name);
  }

  private void assign(Hunter h, Rumour r, String source) {
    if (h == hunter && r == rumour) return;
    if (r != rumour) {
      catches = 0;
      goatCleared = 0;
      partFound = false;
      skipItems = false;
      rumourStart = r == Rumour.NONE ? 0 : System.currentTimeMillis();
    }
    hunter = h;
    rumour = r;
    event("rumour " + r.name + " from " + h.commonName + " (" + source + ")");
    save();
  }

  // ---------------------------------------------------------------- each tick

  private boolean carries(int[] ids) {
    return count(InventoryID.INV, ids) > 0 || count(InventoryID.WORN, ids) > 0;
  }

  private int count(int container, int... ids) {
    ItemContainer items = client.getItemContainer(container);
    if (items == null) return 0;
    int n = 0;
    for (Item item : items.getItems())
      for (int id : ids) if (item.getId() == id) n += Math.max(1, item.getQuantity());
    return n;
  }

  private boolean carriesPart() {
    return rumour != Rumour.NONE && count(InventoryID.INV, rumour.rarePart) > 0;
  }

  int outfitPieces() {
    int n = 0;
    for (int id : OUTFIT) if (count(InventoryID.WORN, id) > 0) n++;
    return n;
  }

  private final Travel.Kit kit =
      new Travel.Kit() {
        @Override
        public boolean carries(int[] ids) {
          return RumoursPlugin.this.carries(ids);
        }

        @Override
        public boolean owns(int[] ids) {
          return RumoursPlugin.this.carries(ids)
              || sum(bank, ids) > 0
              || sum(huntsKit, ids) > 0
              || sum(pouchRunes(), ids) > 0;
        }

        @Override
        public int spellbook() {
          return client.getVarbitValue(VarbitID.SPELLBOOK);
        }

        @Override
        public boolean finished(Quest quest) {
          return questState(quest) == QuestState.FINISHED;
        }

        @Override
        public boolean started(Quest quest) {
          return questState(quest) != QuestState.NOT_STARTED;
        }

        @Override
        public boolean staffless() {
          return client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1;
        }

        @Override
        public boolean house(boolean carriedOnly) {
          return houseTeleport(carriedOnly);
        }

        @Override
        public boolean pohFairy() {
          return config.pohFairyRing();
        }

        @Override
        public boolean pohSpirit() {
          return config.pohSpiritTree();
        }

        @Override
        public boolean pohNexus() {
          return config.pohNexus();
        }

        @Override
        public boolean pohJewellery() {
          return config.pohJewelleryBox() != RumoursConfig.JewelleryBox.NONE;
        }

        @Override
        public int pohJewelleryTier() {
          return config.pohJewelleryBox().ordinal();
        }
      };

  /** Needs that are neither carried nor in a carried kit, but are in the bank. */
  private List<Kit.Need> restockNeeds() {
    List<Kit.Need> out = new ArrayList<>();
    for (Kit.Need need : allNeeds())
      if (!need.optional && available(need) == 0 && whereIs(need).startsWith("in bank")) out.add(need);
    return out;
  }

  /**
   * Restock: the quickest bank teleport you're carrying, then the bank itself (booth, chest or
   * banker, by name, UNVERIFIED), with the missing items highlighted once it's open.
   */
  private void restock(View v, WorldPoint here) {
    List<String> names = new ArrayList<>();
    Set<Integer> ids = new HashSet<>();
    for (Kit.Need need : restockNeeds()) {
      names.add(need.item);
      for (int id : need.ids()) ids.add(id);
      if (need.item.equals(Kit.HOUSE_TELEPORT))
        for (String g : new String[] {"House teleport tablet", "Law rune", "Air rune", "Earth rune"})
          for (int id : Kit.IDS.get(g)) ids.add(id);
    }
    v.bankItems = ids;
    String what = String.join(", ", names);
    List<TileObject> booths =
        objects(15, n -> n.toLowerCase(Locale.ROOT).contains("bank")).stream()
            .filter(o -> hasAction(composition(o), a -> a.equals("bank") || a.equals("use")))
            .collect(java.util.stream.Collectors.toList());
    List<NPC> bankers =
        npcs(
            n -> {
              NPCComposition c = n.getComposition();
              if (c == null || c.getActions() == null) return false;
              for (String a : c.getActions()) if ("Bank".equalsIgnoreCase(a)) return true;
              return false;
            },
            15);
    if (!booths.isEmpty() || !bankers.isEmpty()) {
      v.instruction = "Withdraw " + what + " from the bank";
      v.objects = booths;
      v.npcs = bankers;
      return;
    }
    Travel.Route route = lockedRoute != null ? lockedRoute : Travel.toBank(kit);
    travel(v, route, here, false, "the bank");
    v.instruction = "Restock " + what + ": " + v.instruction;
  }

  /**
   * A house teleport is available: a tablet or construction cape, or Teleport to House castable
   * (standard spellbook, 40 Magic, a law, an air and an earth rune; inventory or rune pouch, or a
   * worn staff for air/earth). With {@code carriedOnly} false, banked tablets, capes and runes count.
   */
  private boolean houseTeleport(boolean carriedOnly) {
    int[] tab = Kit.IDS.get("House teleport tablet"), cape = Kit.IDS.get("Construction cape");
    if (carries(tab) || carries(cape)) return true;
    if (!carriedOnly && (kit.owns(tab) || kit.owns(cape))) return true;
    if (client.getVarbitValue(VarbitID.SPELLBOOK) != 0 || client.getRealSkillLevel(Skill.MAGIC) < 40)
      return false;
    return rune("Law rune", null, carriedOnly)
        && rune("Air rune", "Air staff", carriedOnly)
        && rune("Earth rune", "Earth staff", carriedOnly);
  }

  private boolean rune(String rune, String staff, boolean carriedOnly) {
    int[] ids = Kit.IDS.get(rune);
    if (carried(ids) > 0 || sum(pouchRunes(), ids) > 0) return true;
    if (staff != null && count(InventoryID.WORN, Kit.IDS.get(staff)) > 0) return true;
    return !carriedOnly && (sum(bank, ids) > 0 || staff != null && sum(bank, Kit.IDS.get(staff)) > 0);
  }

  /** Quest states, re-read every 100 ticks: reading them runs a client script. */
  private final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);

  private QuestState questState(Quest quest) {
    return quests.computeIfAbsent(quest, q -> q.getState(client));
  }

  /** The chosen route's items, while travelling out: they're part of the kit until arrival. */
  private List<Kit.Need> travelNeeds = Collections.emptyList();

  /** The rumour's items plus, before setting off, the chosen route's items. */
  private List<Kit.Need> allNeeds() {
    List<Kit.Need> out = new ArrayList<>(Kit.needs(rumour, client.getRealSkillLevel(Skill.HUNTER)));
    out.addAll(travelNeeds);
    return out;
  }

  private void update() {
    Player player = client.getLocalPlayer();
    if (player == null || client.getGameState() != GameState.LOGGED_IN) return;
    WorldPoint here = player.getWorldLocation();
    WorldPoint before = lastLocation;
    lastLocation = here;
    boolean instance = client.getTopLevelWorldView().isInstance();
    boolean burrows = Travel.inBurrows(here);
    boolean part = carriesPart();

    // Completion and hand-in, read from the inventory. Nothing counts until the inventory has
    // loaded, so logging in can't look like a hand-in.
    // The part is followed by its own item ID, so the hunter closing the rumour in dialogue first
    // (rumour becomes None) doesn't hide the hand-in.
    if (client.getItemContainer(InventoryID.INV) != null) {
      if (part && heldPart == null) {
        lastResult =
            catches + " catches" + (pity() > 0 ? " (" + 100 * catches / pity() + "% of pity)" : "");
        event("rare part in inventory: " + lastResult);
        heldPart = rumour;
        doneStart = rumourStart;
        donePartAt = System.currentTimeMillis();
        // Goats are counted by goats cleared from the pit; everything else by catches.
        doneCatches = rumour == Rumour.WYRMSCRAIG_GOAT ? goatCleared : catches;
        donePity = pity();
      } else if (heldPart != null && count(InventoryID.INV, heldPart.rarePart) == 0) {
        if (burrows) completed(heldPart);
        else event("rare part left the inventory outside the burrows");
        heldPart = null;
        donePartAt = 0;
      }
    }

    if (client.getTickCount() % 100 == 0) quests.clear();
    travelNeeds = Collections.emptyList();
    routeItems = new HashSet<>();
    View v = new View();
    v.hunter = hunter;
    v.rumour = rumour;
    v.catches = catches;
    v.pity = pity();
    v.hasPart = part;

    Hunter poolOf = hunter != Hunter.NONE ? hunter : Hunter.TECO;
    if (loadout == null || client.getTickCount() % 10 == 0)
      loadout = Travel.loadout(Kit.POOLS.getOrDefault(poolOf, Collections.emptyList()), kit);
    loadout(v, poolOf);
    boolean atGuild = Travel.underGuild(here) || Travel.nearGuild(here);

    Step step;
    Travel.Plan plan = null;
    if (rumour == Rumour.NONE)
      step =
          !burrows
              ? Step.RETURN
              : !v.loadoutMissing.isEmpty() && !skipLoadout ? Step.GET_ITEMS : Step.GET_RUMOUR;
    else if (part) step = burrows ? Step.HAND_IN : Step.RETURN;
    else {
      plan = Travel.plan(rumour, here, kit, loadout);
      int distance = plan == null ? Integer.MAX_VALUE : plan.distance(here);
      v.area = plan == null ? "" : plan.area;
      v.distance = distance == Integer.MAX_VALUE ? -1 : distance;
      step = distance <= 25 ? Step.HUNT : Step.TO_AREA;
      // Like a "bank first" state: hold the guide at the guild until the kit is carried. Away
      // from the guild it only warns, so a wrong reading can't strand the player.
      // Travel items count until the area is reached; the return trip is planned on its own.
      List<Kit.Need> travel = new ArrayList<>();
      if (step == Step.TO_AREA && plan != null)
        for (String group : plan.route.needs) travel.add(new Kit.Need(group, false, false, "travel"));
      travelNeeds = travel;
      if (plan != null) {
        v.route = plan.route == Travel.WALK ? "Walk" : plan.route.text;
        for (Travel.Route r : plan.alternatives) v.alternatives.add(r.text);
      }
      v.travel = travelItems(travel);
      v.missing = missing(rumour);
      if (step == Step.TO_AREA
          && atGuild
          && (!v.missing.isEmpty() && !skipItems || !v.loadoutMissing.isEmpty() && !skipLoadout))
        step = Step.GET_ITEMS;
      // Away from the guild with something run out (e.g. the last necklace of passage charge)
      // that's in the bank: restock at the quickest bank, then carry on.
      else if ((step == Step.TO_AREA || step == Step.HUNT) && !atGuild && !skipItems && !restockNeeds().isEmpty())
        step = Step.RESTOCK;
    }
    v.nearestBank = Travel.toBank(kit).text;
    v.kit = kitRows(hunter != Hunter.NONE ? hunter : Hunter.TECO);
    for (int i = 0; i < OUTFIT.length; i++) v.outfit[i] = count(InventoryID.WORN, OUTFIT[i]) > 0;
    v.bankSeen = bankSeenAt;
    v.kitSeen = kitSeenAt;
    v.skippedItems = skipItems;
    goats(v);
    tagItems = tagItems(v.kit);
    // Require a few stable ticks before switching steps: the scene is half-loaded right after a
    // teleport.
    if (step != pendingStep) {
      pendingStep = step;
      pendingTicks = 0;
    } else pendingTicks++;
    if (committedStep == null || pendingTicks >= 2 || step == Step.HAND_IN && committedStep == Step.HUNT) {
      if (step != committedStep) {
        event("step " + (committedStep == null ? "-" : committedStep.label) + " -> " + step.label);
        teleported = false;
      }
      committedStep = step;
    }
    step = committedStep;
    v.step = step;

    // Legs of the trip, Ultimate Farming's rule: a teleport only ends the first leg when it lands
    // closer to the destination; arriving in the house (an instance) never counts, and leaving it
    // by its fairy ring, nexus, tree or jewellery box does.
    boolean wasInstance = lastInstance;
    lastInstance = instance;
    boolean jumped =
        before != null
            && (before.getPlane() != here.getPlane() || before.distanceTo(here) > 30 || wasInstance != instance)
            && Travel.underGuild(before) == Travel.underGuild(here);
    if (jumped && (step == Step.TO_AREA || step == Step.RETURN || step == Step.RESTOCK)) {
      if (!wasInstance && instance) legReason = "arrived in house (instance): not the end of the leg";
      else if (wasInstance && !instance) {
        teleported = true;
        legReason = "left the house by teleport";
      } else if (!wasInstance && !instance && step == Step.RESTOCK) {
        teleported = true;
        legReason = "teleported towards the bank";
      } else if (!wasInstance && !instance) {
        int was = step == Step.RETURN ? before.distanceTo(Travel.GUILD) : plan == null ? 0 : plan.distance(before);
        int now = step == Step.RETURN ? here.distanceTo(Travel.GUILD) : plan == null ? 0 : plan.distance(here);
        if (now < was - 30) {
          teleported = true;
          legReason = "teleported " + was + " -> " + now + " tiles from the destination";
        } else legReason = "jump ignored: not closer (" + was + " -> " + now + ")";
      }
      event(legReason);
    }
    // Keep the route chosen at the start of the trip while it's under way: inside the house, or
    // once its teleport is used, the cheapest route can look different.
    if (step != Step.TO_AREA && step != Step.RETURN && step != Step.RESTOCK) lockedRoute = null;
    // A bank trip is fixed from its start: using its teleport can use up the item that chose it.
    else if (step == Step.RESTOCK) {
      if (lockedRoute == null) lockedRoute = Travel.toBank(kit);
    } else if (lockedRoute == null && (instance || teleported))
      lockedRoute = step == Step.RETURN ? Travel.toGuild(before != null ? before : here, kit) : plan == null ? null : plan.route;

    Hunter target = hunter != Hunter.NONE ? hunter : Hunter.TECO;
    switch (step) {
      case GET_RUMOUR:
        v.instruction = "Talk to " + target.commonName + " for a rumour";
        if (kitCarried()) {
          boolean loose = false;
          for (String g : KIT_ITEMS) loose |= count(InventoryID.INV, Kit.IDS.get(g)) > 0;
          if (loose) v.instruction += " · tip: put your traps back in the huntsman's kit";
        }
        v.npcs = npcs(n -> n.getId() == target.npcId, 30);
        break;
      case HAND_IN:
        v.instruction = "Hand the rare part to " + target.commonName;
        v.npcs = npcs(n -> n.getId() == target.npcId, 30);
        break;
      case RETURN:
        travel(v, lockedRoute != null ? lockedRoute : Travel.toGuild(here, kit), here, true,
            rumour == Rumour.NONE ? "Go to the burrows for a rumour" : "Take the part back to the burrows");
        break;
      case TO_AREA:
        if (plan == null) {
          v.instruction = "No known area for " + rumour.name;
          break;
        }
        travel(v, lockedRoute != null ? lockedRoute : plan.route, here, false, plan.area);
        break;
      case HUNT:
        hunt(v, here);
        break;
      case GET_ITEMS:
        getItems(v);
        break;
      case RESTOCK:
        restock(v, here);
        break;
    }
    if (step == Step.TO_AREA && !v.missing.isEmpty())
      v.instruction += " · missing " + String.join(", ", v.missing);
    // Rare part celebration: from the drop until the trip back starts (a teleport, the house)
    // or the guild is reached by any route.
    // A rare part on the floor (dropped because the inventory was full) comes first.
    Tile partTile = groundPartTile();
    if (partTile != null && !part) {
      v.groundPart = partTile.getLocalLocation();
      ItemContainer inv = client.getItemContainer(InventoryID.INV);
      int used = 0;
      if (inv != null) for (Item i : inv.getItems()) if (i.getId() > 0) used++;
      v.instruction =
          "Pick up the " + client.getItemDefinition(rumour.rarePart).getName() + " from the floor"
              + (used >= 28 ? " · inventory full: drop or bank something first" : "");
      v.npcs = Collections.emptyList();
      v.objects = Collections.emptyList();
      v.items = Collections.emptySet();
      v.spell = null;
    }
    // v0.11-0.13 also stopped within 40 tiles of the guild, which covers the sunlight moth area,
    // so the moth wing never flashed. Now it runs until a teleport, the house, or the burrows.
    if (part) partFound = true;
    v.rainbow =
        config.rainbow()
            && (part || v.groundPart != null || partFound && rumour != Rumour.NONE)
            && !teleported
            && !instance
            && !Travel.underGuild(here);
    lastRoute = v.instruction;
    view = v;
    if (panel != null)
      SwingUtilities.invokeLater(
          () -> {
            if (active && panel != null) panel.refresh();
          });
  }

  // ---------------------------------------------------------------- goats

  /**
   * Wyrmscraig goat counters. In the pit: varbit GOAT_PIT_INPIT (UNVERIFIED: assumed to be the
   * goat remains in the pit, as "Inspect" reports them). Cleared: every drop in that count while
   * the rumour is on. Catches still come from XP drops.
   */
  private void goats(View v) {
    if (rumour != Rumour.WYRMSCRAIG_GOAT) {
      goatLast = -1;
      return;
    }
    int inPit = client.getVarbitValue(VarbitID.GOAT_PIT_INPIT);
    if (goatLast >= 0 && inPit < goatLast) {
      goatCleared += goatLast - inPit;
      event("goats cleared: " + (goatLast - inPit) + " (total " + goatCleared + ")");
      save();
    } else if (goatLast >= 0 && inPit > goatLast) event("goat in pit: " + inPit);
    // Once clearing starts, the rest must come out before more goats can go in.
    if (goatLast >= 0 && inPit < goatLast) goatClearing = true;
    if (inPit == 0) goatClearing = false;
    goatLast = inPit;
    v.goatInPit = inPit;
    v.goatCleared = goatCleared;
    v.goatCapacity = goatCapacity(client.getRealSkillLevel(Skill.HUNTER));
  }

  /** Pit size by Hunter level, from the OSRS wiki "Goat pit" page. */
  static int goatCapacity(int level) {
    return level >= 93 ? 24 : level >= 85 ? 22 : level >= 77 ? 20 : level >= 69 ? 18 : 16;
  }

  // ---------------------------------------------------------------- goat pit guide

  /** The pit object (a multiloc) and the states its impostor shows. From the gameval ObjectID table. */
  private static final int PIT = net.runelite.api.gameval.ObjectID.GOAT_PIT;

  private static final int PIT_EMPTY = net.runelite.api.gameval.ObjectID.GOAT_PIT_MULTI_EMPTY,
      PIT_FULL = net.runelite.api.gameval.ObjectID.GOAT_PIT_MULTI_FULL;
  /** UNVERIFIED: assumed to be the spike supply (its gameval name is GOAT_PIT_SPIKES). */
  private static final int SPIKE_SUPPLY = net.runelite.api.gameval.ObjectID.GOAT_PIT_SPIKES;

  /**
   * One step at a time, like Ultimate Farming: spikes from the supply, line the pit, grab goats
   * that will land in it, then clear it when full. The step is re-read from the pit's state each
   * tick and only changes after 3 matching ticks.
   */
  private void goatHunt(View v) {
    GameObject pit = null;
    for (TileObject o : nearbyObjects(30))
      if (o instanceof GameObject && (o.getId() == PIT || o.getId() >= PIT_EMPTY && o.getId() <= PIT_FULL)) pit = (GameObject) o;
    TileObject supply = null;
    for (TileObject o : nearbyObjects(30)) if (o.getId() == SPIKE_SUPPLY) supply = o;
    goatVerdicts.clear();
    goatPit = pit;
    if (pit == null) {
      goatPitDebug = "not found within 30 tiles";
      v.instruction = "Find the goat pit";
      v.npcs = npcs(n -> rumour.creature.isNpc(n.getId()), 20);
      return;
    }
    ObjectComposition c = client.getObjectDefinition(pit.getId());
    ObjectComposition shown = c != null && c.getImpostorIds() != null ? c.getImpostor() : c;
    int state = shown == null ? -1 : shown.getId();
    List<String> acts = new ArrayList<>();
    if (shown != null && shown.getActions() != null) for (String a : shown.getActions()) if (a != null) acts.add(a);
    goatPitDebug = pit.getId() + "->" + state + " " + acts;
    int inPit = v.goatInPit;
    boolean full = state == PIT_FULL || inPit >= v.goatCapacity;
    // Lined: the pit shows spikes or goats. Falls back to the spikes varbit if the impostor is unknown.
    boolean lined =
        state > PIT_EMPTY && state <= PIT_FULL
            || state != PIT_EMPTY && client.getVarbitValue(VarbitID.GOAT_PIT_SPIKES) > 0;
    boolean holdingSpikes = count(InventoryID.INV, ItemID.GOAT_PIT_SPIKES) > 0;

    String step;
    if (inPit > 0 && (full || goatClearing)) step = "clear";
    else if (!lined) step = holdingSpikes ? "line" : "spikes";
    else step = "grab";
    if (!step.equals(goatStepPending)) {
      goatStepPending = step;
      goatStepTicks = 0;
    } else goatStepTicks++;
    if (goatStep == null || goatStepTicks >= 2) goatStep = step;

    List<TileObject> pitOnly = Collections.singletonList(pit);
    switch (goatStep) {
      case "clear":
        v.instruction = "Clear the pit (" + inPit + " left)";
        if (config.goatSteps()) v.objects = pitOnly;
        break;
      case "spikes":
        v.instruction = "Take spikes from the spike supply";
        if (config.goatSteps()) {
          v.objects = supply == null ? Collections.emptyList() : Collections.singletonList(supply);
          v.nextObjects = pitOnly;
        }
        if (supply == null) v.instruction += " (supply not found nearby)";
        break;
      case "line":
        v.instruction = "Line the pit with the spikes";
        if (config.goatSteps()) v.objects = pitOnly;
        break;
      default:
        updateGrabbed(inPit, pit);
        List<NPC> good = grabbable(pit);
        v.npcs = good;
        // Goats in flight land nearest-the-pit first and keep their slots; every other target
        // shows the same next free slot, since whichever is grabbed next fills it.
        Map<NPC, Integer> labels = new LinkedHashMap<>();
        List<NPC> flying = flying(pit);
        int slot = inPit;
        for (NPC g : flying) labels.put(g, ++slot);
        int next = slot + 1;
        for (NPC g : good) labels.put(g, next);
        v.npcLabels = labels;
        v.grabbed = flying;
        LocalPoint stand = standTile(pit);
        if (config.goatStandTile()) v.standTile = stand;
        if (config.goatAreaOutline()) v.goatArea = areaOutline(pit);
        if (config.goatRing()) v.standRing = ring(pit);
        v.instruction =
            good.isEmpty()
                ? "No goat on the far side of the pit in range: stand on the highlighted tile"
                : "Telekinetic Grab a highlighted goat (" + inPit + "/" + v.goatCapacity + ")";
        // The spell, so it's one click away.
        v.spell = "Telekinetic Grab";
    }
  }
  /** Pit centre in scene coordinates. */
  private static double[] centre(GameObject pit) {
    Point min = pit.getSceneMinLocation(), max = pit.getSceneMaxLocation();
    return new double[] {(min.getX() + max.getX()) / 2.0, (min.getY() + max.getY()) / 2.0};
  }

  /**
   * Rule (from an in-game screenshot, 2026-10-07): from where you stand, a goat is grabbable when it's in
   * spell range and on the far side of the pit, within 75° of straight beyond it.
   */
  private boolean eligible(int gx, int gy, int px, int py, double[] c) {
    // Settings: "Grab range" (UNVERIFIED default 10, the usual spell range) and "Fan width" (75).
    if (Math.max(Math.abs(gx - px), Math.abs(gy - py)) > config.goatRange()) return false;
    return angle(gx, gy, px, py, c) <= config.goatAngle();
  }

  /** Angle between "pit → goat" and "you → pit": 0 = straight beyond the pit, 90 = level with it. */
  private static int angle(int gx, int gy, int px, int py, double[] c) {
    double ax = gx - c[0], ay = gy - c[1], bx = c[0] - px, by = c[1] - py;
    double len = Math.hypot(ax, ay) * Math.hypot(bx, by);
    return len == 0 ? 180 : (int) Math.round(Math.toDegrees(Math.acos((ax * bx + ay * by) / len)));
  }

  /**
   * The goat field drawn in green on a top-down screenshot (2026-10-08), as tile offsets from
   * the pit's centre (+x east, +y north), clockwise from the north-west. Measured at about 37
   * pixels a tile (the stand-tile ring corners are 4 tiles apart); the debug box draws it, so
   * edit these points if the outline is off. The fenced pen to the west is left outside.
   */
  static final double[][] GOAT_AREA = {
    {-3, 7}, {0.5, 7}, {0.5, 8}, {4, 8}, {4, 6.5}, {13, 6}, {13, 2}, {8.5, 2}, {8.5, 1},
    {6, 1}, {6, -3}, {3, -3}, {3, -4}, {1.5, -4}, {1.5, -6}, {-3, -6}, {-3, -2.5},
    {-9.5, -2.5}, {-9.5, -1}, {-7, -1}, {-7, 1.5}, {-6, 1.5}, {-6, 2.5}, {-5, 4}, {-3, 4}
  };

  /** The pit while guiding goats, so goats outside the field can be left out. */
  private GameObject goatPit;

  private static boolean inGoatArea(double gx, double gy, double[] c) {
    // Ray casting on the polygon.
    boolean in = false;
    for (int i = 0, j = GOAT_AREA.length - 1; i < GOAT_AREA.length; j = i++) {
      double xi = c[0] + GOAT_AREA[i][0], yi = c[1] + GOAT_AREA[i][1];
      double xj = c[0] + GOAT_AREA[j][0], yj = c[1] + GOAT_AREA[j][1];
      if ((yi > gy) != (yj > gy) && gx < (xj - xi) * (gy - yi) / (yj - yi) + xi) in = !in;
    }
    return in;
  }

  /** Every tile you may stand on: the ring one tile out round the pit. */
  private List<LocalPoint> ring(GameObject pit) {
    Point min = pit.getSceneMinLocation(), max = pit.getSceneMaxLocation();
    List<LocalPoint> out = new ArrayList<>();
    for (int x = min.getX() - 1; x <= max.getX() + 1; x++)
      for (int y = min.getY() - 1; y <= max.getY() + 1; y++)
        if (x < min.getX() || x > max.getX() || y < min.getY() || y > max.getY())
          out.add(LocalPoint.fromScene(x, y, client.getTopLevelWorldView()));
    return out;
  }

  /** The goat field's outline as scene points, for drawing. */
  private List<LocalPoint> areaOutline(GameObject pit) {
    double[] c = centre(pit);
    List<LocalPoint> out = new ArrayList<>();
    for (double[] p : GOAT_AREA)
      out.add(
          new LocalPoint(
              (int) Math.round((c[0] + p[0]) * 128 + 64),
              (int) Math.round((c[1] + p[1]) * 128 + 64),
              client.getTopLevelWorldView()));
    return out;
  }

  /** Goats in the scene, inside the goat field. */
  private List<NPC> goats() {
    List<NPC> out = new ArrayList<>();
    double[] c = goatPit == null || !config.goatAreaOnly() ? null : centre(goatPit);
    for (NPC n : client.getTopLevelWorldView().npcs())
      if (n != null && n.getLocalLocation() != null && rumour.creature.isNpc(n.getId())
          && (c == null || inGoatArea(n.getLocalLocation().getSceneX(), n.getLocalLocation().getSceneY(), c)))
        out.add(n);
    return out;
  }

  /** The nearest few grabbable goats from the player's tile, nearest first; grabbed ones excluded. */
  private List<NPC> grabbable(GameObject pit) {
    List<NPC> good = new ArrayList<>();
    Player player = client.getLocalPlayer();
    LocalPoint me = player.getLocalLocation();
    int px = me.getSceneX(), py = me.getSceneY();
    double[] c = centre(pit);
    for (NPC n : goats()) {
      LocalPoint lp = n.getLocalLocation();
      int gx = lp.getSceneX(), gy = lp.getSceneY();
      int range = Math.max(Math.abs(gx - px), Math.abs(gy - py));
      if (range > 20) continue;
      boolean ok = eligible(gx, gy, px, py, c);
      if (goatVerdicts.size() < 6)
        goatVerdicts.add(
            "goat " + n.getIndex() + " " + range + " tiles · angle " + angle(gx, gy, px, py, c) + "° · " + (ok ? "target" : "no")
                + (grabbed.containsKey(n.getIndex()) ? " · grabbed" : ""));
      if (ok && !grabbed.containsKey(n.getIndex())) good.add(n);
    }
    good.sort(Comparator.comparingInt(n -> n.getWorldLocation().distanceTo(player.getWorldLocation())));
    return good.size() > config.goatCount() ? new ArrayList<>(good.subList(0, config.goatCount())) : good;
  }

  /**
   * Goats Telekinetic Grab was cast on, by NPC index, with the tick of the cast. Kept until that
   * goat lands (the pit count rises and it's the grabbed goat nearest the pit), despawns, or 25
   * ticks pass. v0.11.0 dropped the oldest grab instead, so a second, closer goat landing first
   * turned the first one back to blue.
   */
  private final Map<Integer, Integer> grabbed = new LinkedHashMap<>();

  private int grabbedPitCount = -1;

  @Subscribe
  public void onMenuOptionClicked(MenuOptionClicked e) {
    if (rumour != Rumour.WYRMSCRAIG_GOAT) return;
    MenuEntry entry = e.getMenuEntry();
    NPC npc = entry.getNpc();
    // "Cast" with "Telekinetic Grab -> Wyrmscraig goat" as the target.
    if (npc != null
        && rumour.creature.isNpc(npc.getId())
        && Text.removeTags(entry.getTarget()).toLowerCase(Locale.ROOT).contains("telekinetic grab")) {
      grabbed.put(npc.getIndex(), client.getTickCount());
      event("grab cast on goat " + npc.getIndex());
    }
  }

  /** Grabbed goats still in the scene, nearest the pit first (the next to land). */
  private List<NPC> flying(GameObject pit) {
    double[] c = centre(pit);
    List<NPC> out = new ArrayList<>();
    for (NPC n : goats()) if (grabbed.containsKey(n.getIndex())) out.add(n);
    out.sort(
        Comparator.comparingDouble(
            n -> Math.hypot(n.getLocalLocation().getSceneX() - c[0], n.getLocalLocation().getSceneY() - c[1])));
    return out;
  }

  private void updateGrabbed(int inPit, GameObject pit) {
    if (grabbedPitCount >= 0 && inPit > grabbedPitCount) {
      List<NPC> landing = flying(pit);
      for (int i = 0; i < inPit - grabbedPitCount && i < landing.size(); i++) {
        grabbed.remove(landing.get(i).getIndex());
        event("goat " + landing.get(i).getIndex() + " landed");
      }
    }
    grabbedPitCount = inPit;
    int now = client.getTickCount();
    Set<Integer> present = new HashSet<>();
    for (NPC n : goats()) present.add(n.getIndex());
    int timeout = config.goatGrabSeconds() * 1000 / 600;
    grabbed.entrySet().removeIf(g -> now - g.getValue() > timeout || !present.contains(g.getKey()));
  }

  @Subscribe
  public void onNpcDespawned(NpcDespawned e) {
    grabbed.remove(e.getNpc().getIndex());
  }

  /** The suggested standing tile, kept until a clearly better one appears. */
  private int standX = -1, standY = -1, standLowTicks;

  /**
   * The walkable tile next to the pit (1-2 tiles out) from which the most goats are grabbable;
   * ties go to goats nearer the pit, then to the tile nearer you. It only moves when another tile
   * reaches more goats, or the same number noticeably nearer the pit, so it doesn't flicker.
   */
  private LocalPoint standTile(GameObject pit) {
    WorldView world = client.getTopLevelWorldView();
    Point min = pit.getSceneMinLocation(), max = pit.getSceneMaxLocation();
    double[] c = centre(pit);
    CollisionData[] maps = world.getCollisionMaps();
    int[][] flags = maps == null ? null : maps[world.getPlane()].getFlags();
    List<int[]> goats = new ArrayList<>();
    for (NPC n : goats())
      if (!grabbed.containsKey(n.getIndex()))
        goats.add(new int[] {n.getLocalLocation().getSceneX(), n.getLocalLocation().getSceneY()});
    LocalPoint me = client.getLocalPlayer().getLocalLocation();
    double[] best = null, current = null;
    int bx = -1, by = -1;
    // From an in-game screenshot (2026-10-08): only the ring of tiles one tile out round the pit, corners
    // included.
    for (int x = min.getX() - 1; x <= max.getX() + 1; x++)
      for (int y = min.getY() - 1; y <= max.getY() + 1; y++) {
        if (x >= min.getX() && x <= max.getX() && y >= min.getY() && y <= max.getY()) continue;
        if (x < 0 || y < 0 || x >= 104 || y >= 104) continue;
        if (flags != null && (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0) continue;
        List<Double> near = new ArrayList<>();
        for (int[] gt : goats)
          if (eligible(gt[0], gt[1], x, y, c)) near.add(Math.hypot(gt[0] - c[0], gt[1] - c[1]));
        Collections.sort(near);
        int count = Math.min(near.size(), config.goatCount());
        double spread = 0;
        for (int i = 0; i < count; i++) spread += near.get(i);
        double toMe = Math.max(Math.abs(x - me.getSceneX()), Math.abs(y - me.getSceneY()));
        double[] score = {count, spread, toMe};
        if (x == standX && y == standY) current = score;
        if (best == null || better(score, best, 0)) {
          best = score;
          bx = x;
          by = y;
        }
      }
    if (best == null || best[0] == 0) return standX < 0 ? null : LocalPoint.fromScene(standX, standY, world);
    // Locked: keep the current tile until fewer than 2 goats are grabbable from it for 3 ticks in
    // a row (it moved too often), then take the best tile.
    if (current == null) {
      standX = bx;
      standY = by;
      standLowTicks = 0;
    } else if (current[0] < config.goatStandMinimum()) {
      if (++standLowTicks >= config.goatStandTicks()) {
        standX = bx;
        standY = by;
        standLowTicks = 0;
      }
    } else standLowTicks = 0;
    return LocalPoint.fromScene(standX, standY, world);
  }

  /** More goats wins; then less total goat-to-pit distance (by more than {@code margin}); then nearer you. */
  private static boolean better(double[] a, double[] b, double margin) {
    if (a[0] != b[0]) return a[0] > b[0];
    if (Math.abs(a[1] - b[1]) > margin) return a[1] < b[1];
    return margin == 0 && a[2] < b[2];
  }
  // ---------------------------------------------------------------- bank button

  /**
   * The current rumour's items (every rumour of the hunter's when there's none), plus the
   * whistle, Fortis tablet, huntsman's kit and guild outfit.
   */
  private Set<Integer> tagItems(List<KitRow> rows) {
    Set<Integer> ids = new HashSet<>();
    int level = client.getRealSkillLevel(Skill.HUNTER);
    for (KitRow row : rows)
      if (rumour == Rumour.NONE || row.current)
        for (Kit.Need need : Kit.needs(row.rumour, level)) {
          for (int id : need.ids()) ids.add(id);
          if (need.item.equals("Air rune")) for (int id : Kit.IDS.get("Air staff")) ids.add(id);
        }
    // The loadout: its travel items, the huntsman's kit and every rumour's hunting items.
    ids.add(ItemID.HUNTSMANS_KIT);
    ids.addAll(extraIds);
    if (loadout != null)
      for (String g : loadout.groups) for (int id : Kit.IDS.getOrDefault(g, new int[0])) ids.add(id);
    for (Rumour r : Kit.POOLS.getOrDefault(hunter != Hunter.NONE ? hunter : Hunter.TECO, Collections.emptyList()))
      for (Kit.Need need : Kit.needs(r, level)) for (int id : need.ids()) ids.add(id);
    // House teleports: tablets, construction cape and the spell's runes.
    for (String g : new String[] {"House teleport tablet", "Construction cape", "Law rune", "Air rune", "Earth rune"})
      for (int id : Kit.IDS.get(g)) ids.add(id);
    for (int id : Travel.WHISTLES) ids.add(id);
    for (int id : Travel.FORTIS_TABLET) ids.add(id);
    for (int id : OUTFIT) ids.add(id);
    ids.add(ItemID.HUNTSMANS_KIT);
    ids.add(ItemID.BH_RUNE_POUCH);
    ids.add(ItemID.DIVINE_RUNE_POUCH);
    return ids;
  }

  @Subscribe
  public void onScriptPostFired(ScriptPostFired e) {
    if (e.getScriptId() == ScriptID.BANKMAIN_BUILD) {
      addBankButton();
      styleBankButton();
    }
  }

  @Subscribe
  public void onWidgetLoaded(WidgetLoaded e) {
    if (e.getGroupId() == InterfaceID.BANKMAIN) addBankButton();
  }

  @Subscribe
  public void onWidgetClosed(WidgetClosed e) {
    if (e.getGroupId() == InterfaceID.BANKMAIN) bankButton = bankIcon = null;
  }

  /** A Hunter button in the bank's top-left corner that shows only the rumour's items. */
  private void addBankButton() {
    Widget universe = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
    if (universe == null) return;
    Widget[] children = universe.getDynamicChildren();
    if (bankButton != null && children != null && Arrays.asList(children).contains(bankButton))
      return;
    // Ultimate Farming's button, itself Quest Helper's: 25px square at (408, 5), 19px icon inset by 3.
    bankButton = universe.createChild(-1, WidgetType.GRAPHIC);
    bankIcon = universe.createChild(-1, WidgetType.GRAPHIC);
    for (Widget w : new Widget[] {bankButton, bankIcon}) {
      int inset = w == bankIcon ? 3 : 0;
      w.setOriginalX(408 + inset);
      w.setOriginalY(5 + inset);
      w.setOriginalWidth(25 - 2 * inset);
      w.setOriginalHeight(25 - 2 * inset);
    }
    bankIcon.setSpriteId(SpriteID.SKILL_HUNTER);
    bankButton.setAction(0, "View Easy Rumours items");
    bankButton.setName("<col=ff9040>Easy Rumours</col>");
    bankButton.setHasListener(true);
    bankButton.setNoClickThrough(true);
    bankButton.setOnMouseOverListener(
        (JavaScriptCallback) ev -> bankButton.setSpriteId(SpriteID.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED));
    bankButton.setOnMouseLeaveListener((JavaScriptCallback) ev -> styleBankButton());
    bankButton.setOnOpListener(
        (JavaScriptCallback)
            ev -> {
              if (BANK_TAG.equals(bankTags.getActiveTag())) bankTags.closeBankTag();
              else bankTags.openBankTag(BANK_TAG, BankTagsService.OPTION_NO_LAYOUT);
              styleBankButton();
            });
    styleBankButton();
    bankButton.revalidate();
    bankIcon.revalidate();
  }

  /** The button stays pressed while the rumour items are shown. */
  private void styleBankButton() {
    if (bankButton == null) return;
    bankButton.setSpriteId(
        BANK_TAG.equals(bankTags.getActiveTag())
            ? SpriteID.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED
            : SpriteID.UNKNOWN_BUTTON_SQUARE_SMALL);
  }

  // ---------------------------------------------------------------- loadout

  /** Items that live in the huntsman's kit between rumours (wiki: the kit stores these). */
  private static final Set<String> KIT_ITEMS =
      new HashSet<>(Arrays.asList("Box trap", "Net trap", "Butterfly net", "Bird snare", "Noose wand", "Teasing stick"));

  /**
   * The rumour loadout: the solver's travel items, the huntsman's kit, and every hunting item of
   * the hunter's rumours (kept in the kit where it can hold them). Some players keep all of it on them.
   */
  /** Owned wiki-recommended extras, for the bank filter. */
  private Set<Integer> extraIds = new HashSet<>();

  private void loadout(View v, Hunter pool) {
    extraIds = new HashSet<>();
    List<KitItem> items = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    Set<Integer> bankIds = new HashSet<>();
    int level = client.getRealSkillLevel(Skill.HUNTER);
    int slots = 0;
    boolean pouch = false;
    for (String g : loadout.groups) {
      Kit.Need need = new Kit.Need(g, false, false, "travel");
      KitItem item = travelItems(Collections.singletonList(need)).get(0);
      items.add(item);
      if (g.equals("Dramen or lunar staff")) item.note = "travel · wield it";
      else if (g.equals(Kit.HOUSE_TELEPORT)) {
        boolean cape = kit.owns(Kit.IDS.get("Construction cape"));
        boolean tabs = kit.owns(Kit.IDS.get("House teleport tablet"));
        item.note = cape ? "travel · construction cape" : tabs ? "travel · house tablets" : "travel · runes in your rune pouch";
        if (!cape && tabs) slots++;
        if (!cape && !tabs) pouch = true;
      } else slots++;
      if (!item.ok() && !item.where.startsWith("no") && !item.where.startsWith("not") && !item.where.startsWith("bank not")) {
        missing.add(item.name + " (" + item.where + ")");
        if (g.equals(Kit.HOUSE_TELEPORT))
          for (String h : new String[] {"House teleport tablet", "Construction cape", "Law rune", "Air rune", "Earth rune"})
            for (int id : Kit.IDS.get(h)) bankIds.add(id);
        else for (int id : Kit.IDS.getOrDefault(g, new int[0])) bankIds.add(id);
      }
    }
    // The huntsman's kit, carried, with every rumour's trap items in it.
    KitItem kitItem = new KitItem();
    kitItem.name = "Huntsman's kit";
    kitItem.icon = ItemID.HUNTSMANS_KIT;
    kitItem.have = count(InventoryID.INV, ItemID.HUNTSMANS_KIT);
    kitItem.want = 1;
    kitItem.note = "holds the traps";
    if (!kitItem.ok()) {
      kitItem.where = sum(bank, new int[] {ItemID.HUNTSMANS_KIT}) > 0 ? "in bank" : bank == null ? "bank not seen" : "not in bank";
      if (kitItem.where.equals("in bank")) {
        missing.add("Huntsman's kit (in bank)");
        bankIds.add(ItemID.HUNTSMANS_KIT);
      }
    }
    items.add(kitItem);
    slots++;
    Map<String, Kit.Need> hunting = new LinkedHashMap<>();
    Map<String, Integer> wants = new HashMap<>();
    for (Rumour r : Kit.POOLS.getOrDefault(pool, Collections.emptyList()))
      for (Kit.Need need : Kit.needs(r, level)) {
        if (need.optional || need.item.equals(Kit.TELEGRAB)) continue;
        hunting.putIfAbsent(need.item, need);
        wants.merge(need.item, want(need), Math::max);
      }
    for (Kit.Need need : hunting.values()) {
      KitItem item = new KitItem();
      item.name = need.item;
      item.icon = need.ids().length > 0 ? need.ids()[0] : -1;
      item.want = wants.get(need.item);
      int inKit = kitCarried() ? sum(huntsKit, need.ids()) : 0;
      item.have = have(need) + inKit;
      boolean storable = KIT_ITEMS.contains(need.item);
      item.note = storable ? (inKit > 0 ? "in your kit" : "store in your kit") : need.rune() ? "rune pouch" : "";
      if (!storable && !need.rune()) slots++;
      if (need.rune()) pouch = true;
      if (!item.ok()) {
        item.where = whereIs(need);
        if (item.where.equals("in bank") || item.where.startsWith("in kit (kit not")) {
          missing.add(need.item + (item.want > 1 ? " x" + (item.want - item.have) : "") + " (" + item.where + (storable ? ", then into the kit" : "") + ")");
          for (int id : need.ids()) bankIds.add(id);
        }
      }
      items.add(item);
    }
    if (pouch) slots++;
    // Wiki-recommended extras, only those owned; optional, so they never hold the guide.
    Set<Trap> traps = new HashSet<>();
    for (Rumour r : Kit.POOLS.getOrDefault(pool, Collections.emptyList())) traps.add(r.creature.trap);
    int extraSlots = 0;
    for (Kit.Extra x : Kit.EXTRAS) {
      if (!x.traps.isEmpty() && Collections.disjoint(x.traps, traps)) continue;
      if (!kit.owns(x.ids)) continue;
      // Only one bonecrusher.
      if (x.name.equals("Bonecrusher") && kit.owns(new int[] {ItemID.BONECRUSHER_NECKLACE})) continue;
      KitItem item = new KitItem();
      item.name = x.name;
      item.icon = x.ids[0];
      item.want = 1;
      item.have = carried(x.ids) + (kitCarried() ? sum(huntsKit, x.ids) : 0);
      item.optional = true;
      item.note = "recommended: " + x.why;
      if (!item.ok()) item.where = sum(bank, x.ids) > 0 ? "in bank" : "in kit (kit not carried)";
      if (!x.worn) extraSlots++;
      items.add(item);
      extraIds.addAll(Arrays.stream(x.ids).boxed().collect(java.util.stream.Collectors.toList()));
    }
    List<String> lines = new ArrayList<>();
    for (Map.Entry<Rumour, Travel.Plan> e : loadout.plans.entrySet())
      lines.add(e.getKey().name + ": " + (e.getValue().route == Travel.WALK ? "walk from the guild" : e.getValue().route.text));
    if (loadout.back != null) lines.add("Back to the guild: " + loadout.back.text);
    v.loadout = items;
    v.loadoutLines = lines;
    v.loadoutSlots =
        "About " + slots + " inventory slots" + (pouch ? ", runes in your rune pouch" : "")
            + (extraSlots > 0 ? ", +" + extraSlots + " for the recommended extras" : "") + ".";
    v.loadoutMissing = missing;
    v.loadoutBank = bankIds;
  }

  /** What a need gives now, counting items stored in a carried huntsman's kit. */
  private int available(Kit.Need need) {
    return have(need) + (kitCarried() && !need.rune() ? sum(huntsKit, need.ids()) : 0);
  }

  // ---------------------------------------------------------------- kit

  private int sum(Map<Integer, Integer> items, int[] ids) {
    if (items == null) return 0;
    int n = 0;
    for (int id : ids) n += items.getOrDefault(id, 0);
    return n;
  }

  private int carried(int[] ids) {
    return count(InventoryID.INV, ids) + count(InventoryID.WORN, ids);
  }

  /** Items stored in the kit can't be used, so they only count once taken out. */
  private boolean kitCarried() {
    return count(InventoryID.INV, ItemID.HUNTSMANS_KIT) > 0;
  }

  /**
   * How many of a need are usable now: carried or worn; runes also in the rune pouch; an air staff
   * worn covers air runes; the spell is 1 when castable.
   */
  private int have(Kit.Need need) {
    if (need.item.equals(Kit.HOUSE_TELEPORT)) return houseTeleport(true) ? 1 : 0;
    if (need.item.equals(Kit.TELEGRAB))
      return client.getRealSkillLevel(Skill.MAGIC) >= 33
              && client.getVarbitValue(VarbitID.SPELLBOOK) == 0
          ? 1
          : 0;
    int n = carried(need.ids());
    if (need.rune()) n += sum(pouchRunes(), need.ids());
    if (need.item.equals("Air rune") && count(InventoryID.WORN, Kit.IDS.get("Air staff")) > 0)
      n = Math.max(n, 1);
    return n;
  }

  private static final int[][] POUCH_SLOTS = {
    {VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_QUANTITY_1},
    {VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_QUANTITY_2},
    {VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_QUANTITY_3},
    {VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_QUANTITY_4},
    {VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_QUANTITY_5},
    {VarbitID.RUNE_POUCH_TYPE_6, VarbitID.RUNE_POUCH_QUANTITY_6}
  };

  /** Rune item IDs and amounts inside the rune pouch, read from its varbits (as Ultimate Farming). */
  private Map<Integer, Integer> pouchRunes() {
    Map<Integer, Integer> runes = new HashMap<>();
    EnumComposition types = client.getEnum(EnumID.RUNEPOUCH_RUNE);
    for (int[] slot : POUCH_SLOTS) {
      int type = client.getVarbitValue(slot[0]), amount = client.getVarbitValue(slot[1]);
      if (type <= 0 || amount <= 0 || types == null) continue;
      int id = types.getIntValue(type);
      if (id > 0) runes.merge(id, amount, Integer::sum);
    }
    return runes;
  }

  private int want(Kit.Need need) {
    return need.perTrap ? Kit.trapCount(client.getRealSkillLevel(Skill.HUNTER)) : 1;
  }

  /** Where a not-carried item is: "in kit", "in bank", "not in bank" or "bank not seen". */
  private String whereIs(Kit.Need need) {
    if (need.item.equals(Kit.HOUSE_TELEPORT))
      return houseTeleport(false) ? "in bank (tablets, cape or runes)" : "no tablets, cape or runes seen";
    if (need.item.equals(Kit.TELEGRAB))
      return client.getRealSkillLevel(Skill.MAGIC) < 33 ? "needs 33 Magic" : "standard spellbook";
    int[] ids = need.ids();
    if (sum(huntsKit, ids) > 0) return kitCarried() ? "in kit" : "in kit (kit not carried)";
    if (sum(bank, ids) > 0) return "in bank";
    return bank == null ? "bank not seen" : "not in bank";
  }

  /** Required items for this rumour not carried, with where they are. */
  private List<String> missing(Rumour r) {
    List<String> out = new ArrayList<>();
    // Items in a carried huntsman's kit count: they're taken out at the area.
    for (Kit.Need need : allNeeds())
      if (!need.optional && available(need) == 0)
        out.add(need.item + " (" + whereIs(need) + ")");
    return out;
  }

  private List<KitRow> kitRows(Hunter h) {
    List<KitRow> rows = new ArrayList<>();
    List<Rumour> pool = new ArrayList<>(Kit.POOLS.getOrDefault(h, Collections.emptyList()));
    if (rumour != Rumour.NONE && !pool.contains(rumour)) pool.add(0, rumour);
    int level = client.getRealSkillLevel(Skill.HUNTER);
    for (Rumour r : pool) {
      KitRow row = new KitRow(r, r == rumour);
      List<Kit.Need> needs = Kit.needs(r, level);
      for (Kit.Need need : needs) {
        KitItem item = new KitItem();
        item.name = need.item;
        item.note = need.note;
        item.icon = need.ids().length > 0 ? need.ids()[0] : -1;
        item.have = have(need);
        item.want = want(need);
        item.optional = need.optional;
        if (!item.ok()) item.where = whereIs(need);
        if (item.have == 0 && !need.optional) row.ready = false;
        row.items.add(item);
      }
      rows.add(row);
    }
    return rows;
  }

  private static final int[] BANKERS = {
    net.runelite.api.gameval.NpcID.HG_BANK_BUFFALO, net.runelite.api.gameval.NpcID.HG_BANKER_DWARF
  };

  /** Bank first: take the item out of the huntsman's kit, or withdraw it at the burrows bank. */
  private void getItems(View v) {
    // This rumour's own items first (unless skipped), then the loadout.
    Set<Integer> ids = new HashSet<>();
    if (!skipItems)
      for (Kit.Need need : allNeeds())
        if (!need.optional && available(need) == 0) {
          for (int id : need.ids()) ids.add(id);
          if (need.item.equals(Kit.HOUSE_TELEPORT))
            for (String g : new String[] {"House teleport tablet", "Construction cape", "Law rune", "Air rune", "Earth rune"})
              for (int id : Kit.IDS.get(g)) ids.add(id);
        }
    if (!ids.isEmpty()) v.instruction = "Get " + String.join(", ", v.missing);
    else {
      ids.addAll(v.loadoutBank);
      v.instruction = "Withdraw your rumour loadout: " + String.join(", ", v.loadoutMissing);
    }
    if (bank == null) v.instruction += " · open the bank once so I can check it";
    v.bankItems = ids;
    v.npcs =
        npcs(
            n -> {
              for (int b : BANKERS) if (n.getId() == b) return true;
              return false;
            },
            30);
  }
  int pity() {
    return rumour == Rumour.NONE ? 0 : rumour.creature.trap.pityFor(outfitPieces());
  }

  private void completed(Rumour done) {
    sessionCompletions++;
    if (doneStart > 0) {
      long now = System.currentTimeMillis();
      lastRumourMillis = now - doneStart;
      Record r = new Record();
      r.rumour = done.name();
      r.catches = doneCatches;
      r.pity = donePity;
      r.toPart = donePartAt > doneStart ? donePartAt - doneStart : 0;
      r.total = now - doneStart;
      r.at = now;
      synchronized (records) {
        records.add(r);
        configManager.setRSProfileConfiguration(RumoursConfig.GROUP, "records", gson.toJson(records));
      }
      event("recorded " + done.name + " " + r.catches + "/" + r.pity + " in " + r.total / 1000 + "s");
    }
    configManager.setRSProfileConfiguration(
        RumoursConfig.GROUP, "completions", String.valueOf(totalCompletions() + 1));
    event("handed in: " + done.name + " #" + sessionCompletions);
    partFound = false;
    // A new rumour may already have been given in the same conversation; keep it.
    if (rumour == done) {
      rumour = Rumour.NONE;
      catches = 0;
      rumourStart = 0;
      save();
    }
  }

  /** Travel guidance: the stairs, a walk, or the teleport item/spell then the quetzal map icon. */
  private void travel(View v, Travel.Route route, WorldPoint here, boolean toGuild, String where) {
    boolean burrows = Travel.inBurrows(here);
    boolean inHouse = client.getTopLevelWorldView().isInstance();
    v.route = route.text;
    if (route.house && !teleported) {
      String obj = route.pohObject;
      String name =
          obj.equals("fairy") ? "fairy ring" : obj.equals("spirit") ? "spirit tree" : obj.equals("nexus") ? "portal nexus" : "jewellery box";
      if (inHouse) {
        leg = "2/3 in house";
        v.instruction = "Use the " + name + " → " + route.pohChoice;
        int[] staff = Kit.IDS.get("Dramen or lunar staff");
        if (route.needs.contains("Dramen or lunar staff")
            && count(InventoryID.WORN, staff) == 0
            && count(InventoryID.INV, staff) > 0) {
          Set<Integer> ids = new HashSet<>();
          for (int id : staff) ids.add(id);
          v.items = ids;
          v.instruction = "Wield your staff, then use the " + name + " → " + route.pohChoice;
          return;
        }
        // UNVERIFIED house object names: matched by a word in the name ("fairy", "spirit",
        // "nexus", "jewellery").
        v.objects = objects(40, n -> n.toLowerCase(Locale.ROOT).contains(obj));
        return;
      }
      leg = "1/3 house teleport";
      v.instruction = "Teleport to your house, then the " + name + " → " + route.pohChoice;
      int[] tab = Kit.IDS.get("House teleport tablet"), cape = Kit.IDS.get("Construction cape");
      Set<Integer> ids = new HashSet<>();
      if (carries(tab)) for (int id : tab) ids.add(id);
      else if (carries(cape)) for (int id : cape) ids.add(id);
      if (!ids.isEmpty()) v.items = ids;
      else v.spell = "Teleport to House";
      return;
    }
    leg = route == Travel.WALK ? "walk" : teleported ? "after teleport" : "1 teleport";
    if (route.house) {
      v.instruction = "Walk to " + (toGuild ? "the Hunter Guild" : where);
      if (toGuild && Travel.nearGuild(here)) {
        v.instruction = "Take the stairs down to the burrows";
        v.objects = stairs("climb-down", 40);
      }
      return;
    }
    if (route == Travel.WALK) {
      if (toGuild && Travel.nearGuild(here)) {
        v.instruction = where + ": take the stairs down";
        v.objects = stairs("climb-down", 40);
      } else if (!toGuild && burrows) {
        v.instruction = "Climb the stairs up, then walk to " + where;
        v.objects = stairs("climb-up", 20);
      } else if (toGuild) {
        v.instruction = where + ": walk to the Hunter Guild";
      } else {
        v.instruction = "Walk to " + where + (v.distance >= 0 ? " (" + v.distance + " tiles)" : "");
      }
      return;
    }
    v.instruction = route.text + (toGuild ? "" : " (" + where + ")");
    v.quetzalSite = route.quetzalSite;
    if (teleported) {
      // The item or spell is used; only the second leg or the walk is left.
      if (route.multiLeg && route.quetzalSite != null) v.instruction = "Take the quetzal to " + (toGuild ? "Hunter Guild" : where);
      else v.instruction = "Walk to " + (toGuild ? "the Hunter Guild" : where);
      return;
    }
    if (route.items.length > 0) {
      Set<Integer> ids = new HashSet<>();
      for (int id : route.items) ids.add(id);
      v.items = ids;
    }
    v.spell = route.spell;
    if (route.object != null) {
      // Fairy rings: wield the staff first, then the ring itself. UNVERIFIED object name.
      int[] staff = Kit.IDS.get("Dramen or lunar staff");
      if (route.needs.contains("Dramen or lunar staff")
          && count(InventoryID.WORN, staff) == 0
          && count(InventoryID.INV, staff) > 0) {
        Set<Integer> ids = new HashSet<>();
        for (int id : staff) ids.add(id);
        v.items = ids;
        v.instruction = "Wield your staff, then " + route.text;
      } else {
        String name = route.object.toLowerCase(Locale.ROOT);
        v.objects = objects(25, o -> name.equals(o.toLowerCase(Locale.ROOT)));
      }
    }
  }

  /** Objects within range whose name matches. */
  private List<TileObject> objects(int range, java.util.function.Predicate<String> name) {
    List<TileObject> found = new ArrayList<>();
    for (TileObject o : nearbyObjects(range)) {
      ObjectComposition c = composition(o);
      if (c != null && c.getName() != null && name.test(c.getName())) found.add(o);
    }
    return found;
  }

  /** Kit view of the route's items. */
  private List<KitItem> travelItems(List<Kit.Need> needs) {
    List<KitItem> out = new ArrayList<>();
    for (Kit.Need need : needs) {
      KitItem item = new KitItem();
      item.name = need.item;
      item.note = "travel";
      item.icon =
          need.ids().length > 0
              ? need.ids()[0]
              : need.item.equals(Kit.HOUSE_TELEPORT) ? ItemID.POH_TABLET_TELEPORTTOHOUSE : -1;
      item.have = have(need);
      item.want = 1;
      if (!item.ok()) item.where = whereIs(need);
      out.add(item);
    }
    return out;
  }

  /** At the area: the creature, traps ready to check, and the trap item to lay. */
  private void hunt(View v, WorldPoint here) {
    // Items stored in the huntsman's kit can't be used: take them out first.
    for (Kit.Need need : Kit.needs(rumour, client.getRealSkillLevel(Skill.HUNTER)))
      if (!need.optional && have(need) == 0 && kitCarried() && sum(huntsKit, need.ids()) > 0) {
        v.instruction = "Take " + need.item + (need.perTrap ? "s" : "") + " out of your huntsman's kit";
        v.items = Collections.singleton(ItemID.HUNTSMANS_KIT);
        return;
      }
    if (rumour == Rumour.WYRMSCRAIG_GOAT) {
      goatHunt(v);
      return;
    }
    if (rumour.creature.trap == Trap.FALCONRY) {
      falconry(v);
      return;
    }
    Creature creature = rumour.creature;
    Trap trap = creature.trap;
    v.instruction = "Catch " + rumour.name + " (" + trap.name + ")";
    v.npcs = npcs(n -> creature.isNpc(n.getId()), 20);
    // UNVERIFIED: caught traps offer "Check"; unset trap spots offer "Set-trap".
    v.objects = objects(a -> a.equals("check"), 15);
    if (!trap.laidFromInventory && trap != Trap.BUTTERFLY && trap != Trap.FALCONRY)
      v.nextObjects = objects(a -> a.equals("set-trap"), 8);
    Set<Integer> next = new HashSet<>();
    if (trap.laidFromInventory) for (int id : trap.items) next.add(id);
    v.nextItems = next;
    // A butterfly net must be wielded before the creature can be caught.
    List<String> lacking = missing(rumour);
    if (!lacking.isEmpty()) v.instruction = "Missing " + String.join(", ", lacking);
    else if (trap == Trap.BUTTERFLY && count(InventoryID.WORN, trap.items) == 0) {
      Set<Integer> net = new HashSet<>();
      for (int id : trap.items) net.add(id);
      v.items = net;
      v.instruction = "Wield your butterfly net";
    }
  }

  // ---------------------------------------------------------------- rare part on the floor

  /** Tiles where a rare part (any rumour's) lies, from ground item events. */
  private final Map<Tile, Integer> partsOnGround = new HashMap<>();

  private static final Set<Integer> RARE_PARTS = new HashSet<>();

  static {
    for (Rumour r : Rumour.values()) if (r != Rumour.NONE) RARE_PARTS.add(r.rarePart);
  }

  @Subscribe
  public void onItemSpawned(ItemSpawned e) {
    if (RARE_PARTS.contains(e.getItem().getId())) {
      partsOnGround.put(e.getTile(), e.getItem().getId());
      if (rumour != Rumour.NONE && e.getItem().getId() == rumour.rarePart) event("rare part on the floor");
    }
  }

  @Subscribe
  public void onItemDespawned(ItemDespawned e) {
    if (RARE_PARTS.contains(e.getItem().getId())) partsOnGround.remove(e.getTile());
  }

  /** The nearest tile (within 15) with this rumour's rare part on it, or null. */
  private Tile groundPartTile() {
    if (rumour == Rumour.NONE) return null;
    Player player = client.getLocalPlayer();
    Tile best = null;
    int bestDistance = 16;
    for (Map.Entry<Tile, Integer> e : partsOnGround.entrySet()) {
      if (e.getValue() != rumour.rarePart) continue;
      int d = e.getKey().getWorldLocation().distanceTo(player.getWorldLocation());
      if (d < bestDistance) {
        best = e.getKey();
        bestDistance = d;
      }
    }
    return best;
  }

  // ---------------------------------------------------------------- falconry

  /** Matthias, with and without a falcon on his arm (gameval NpcID). */
  private static final int[] FALCONERS = {
    net.runelite.api.gameval.NpcID.HUNTING_NPC_FALCONER, net.runelite.api.gameval.NpcID.HUNTING_NPC_FALCONER_NOBIRD
  };
  /** A falcon sitting on a caught kebbit, waiting to be retrieved. */
  private static final int[] FALCON_ON_KEBBIT = {
    net.runelite.api.gameval.NpcID.HUNTING_FALCON_ONSPEEDY,
    net.runelite.api.gameval.NpcID.HUNTING_FALCON_ONSPEEDY2,
    net.runelite.api.gameval.NpcID.HUNTING_FALCON_ONSILENT
  };

  /**
   * Falconry (Piscatoris falconry area; Teco gives none, other hunters do), one step at a time:
   * over the fence stile, a falcon from Matthias, then the kebbits; a falcon sitting on a catch is
   * retrieved first. UNVERIFIED: the stile is found by its name ("Stile") and "Climb-over"; you're
   * taken to be inside the fence when you're nearer the hunting spots than the stile is.
   */
  private void falconry(View v) {
    Creature creature = rumour.creature;
    boolean falcon = count(InventoryID.WORN, ItemID.FALCON_ON_GLOVES) > 0;
    boolean glove = falcon || count(InventoryID.WORN, ItemID.FALCON_GLOVES) > 0;
    List<NPC> waiting = npcs(n -> Arrays.stream(FALCON_ON_KEBBIT).anyMatch(id -> id == n.getId()), 20);
    if (glove && !waiting.isEmpty()) {
      v.instruction = "Retrieve your falcon";
      v.npcs = waiting;
      return;
    }
    if (falcon) {
      v.instruction = "Send your falcon after a " + rumour.name;
      v.npcs = npcs(n -> creature.isNpc(n.getId()), 20);
      return;
    }
    if (glove) {
      v.instruction = "Wait for your falcon to come back";
      return;
    }
    List<TileObject> stiles =
        objects(20, name -> name.equalsIgnoreCase("stile")).stream()
            .filter(o -> hasAction(composition(o), a -> a.equals("climb-over")))
            .collect(java.util.stream.Collectors.toList());
    List<NPC> falconers = npcs(n -> Arrays.stream(FALCONERS).anyMatch(id -> id == n.getId()), 25);
    WorldPoint here = client.getLocalPlayer().getWorldLocation();
    Travel.Plan plan = Travel.plan(rumour, here, kit);
    boolean inside =
        stiles.isEmpty()
            || plan != null && plan.distance(here) < plan.distance(stiles.get(0).getWorldLocation());
    if (!inside) {
      v.instruction = "Climb over the stile into the falconry area";
      v.objects = stiles;
      v.nextNpcs = falconers;
      return;
    }
    v.instruction = "Get a falcon from Matthias";
    v.npcs = falconers;
  }

  private List<NPC> npcs(java.util.function.Predicate<NPC> match, int range) {
    Player player = client.getLocalPlayer();
    List<NPC> found = new ArrayList<>();
    for (NPC n : client.getTopLevelWorldView().npcs())
      if (n != null
          && match.test(n)
          && n.getWorldLocation().distanceTo(player.getWorldLocation()) <= range) found.add(n);
    return found;
  }

  /** Stairs (any object named "...stair...") with this action, within range. UNVERIFIED names. */
  private List<TileObject> stairs(String action, int range) {
    List<TileObject> found = new ArrayList<>();
    for (TileObject o : nearbyObjects(range)) {
      ObjectComposition c = composition(o);
      if (c == null || c.getName() == null) continue;
      String name = c.getName().toLowerCase(Locale.ROOT);
      if ((name.contains("stair") || name.contains("steps")) && hasAction(c, a -> a.equals(action)))
        found.add(o);
    }
    return found;
  }

  private List<TileObject> objects(java.util.function.Predicate<String> action, int range) {
    List<TileObject> found = new ArrayList<>();
    for (TileObject o : nearbyObjects(range)) {
      ObjectComposition c = composition(o);
      if (c != null && hasAction(c, action)) found.add(o);
    }
    return found;
  }

  private ObjectComposition composition(TileObject o) {
    ObjectComposition c = client.getObjectDefinition(o.getId());
    if (c != null && c.getImpostorIds() != null) c = c.getImpostor();
    return c;
  }

  private static boolean hasAction(ObjectComposition c, java.util.function.Predicate<String> match) {
    String[] actions = c.getActions();
    if (actions == null) return false;
    for (String a : actions) if (a != null && match.test(a.toLowerCase(Locale.ROOT))) return true;
    return false;
  }

  private List<TileObject> nearbyObjects(int range) {
    List<TileObject> found = new ArrayList<>();
    Player player = client.getLocalPlayer();
    if (player == null) return found;
    LocalPoint local = player.getLocalLocation();
    WorldView world = client.getTopLevelWorldView();
    Tile[][] plane = world.getScene().getTiles()[world.getPlane()];
    int sx = local.getSceneX(), sy = local.getSceneY();
    for (int x = Math.max(0, sx - range); x <= Math.min(plane.length - 1, sx + range); x++)
      for (int y = Math.max(0, sy - range); y <= Math.min(plane[x].length - 1, sy + range); y++) {
        Tile tile = plane[x][y];
        if (tile == null) continue;
        for (GameObject o : tile.getGameObjects()) if (o != null) found.add(o);
        if (tile.getGroundObject() != null) found.add(tile.getGroundObject());
        if (tile.getWallObject() != null) found.add(tile.getWallObject());
        if (tile.getDecorativeObject() != null) found.add(tile.getDecorativeObject());
      }
    return found;
  }

  /** Whether the player has this item selected ("Use"), ready to click its target. */
  boolean selectedItem(Set<Integer> ids) {
    if (!client.isWidgetSelected()) return false;
    Widget selected = client.getSelectedWidget();
    return selected != null && ids.contains(selected.getItemId());
  }

  // ---------------------------------------------------------------- debug

  private void event(String text) {
    String line = text.length() > 44 ? text.substring(0, 44) + "…" : text;
    if (line.equals(events.peekFirst())) return;
    events.addFirst(line);
    while (events.size() > 4) events.removeLast();
  }

  /** What the plugin currently believes, for the debug box. Called on the client thread. */
  List<String> debugLines() {
    View v = view;
    List<String> lines = new ArrayList<>();
    lines.add("hunter " + v.hunter + " · rumour " + v.rumour.name);
    if (v.rumour != Rumour.NONE) {
      Creature c = v.rumour.creature;
      lines.add("creature " + c.name() + " npc " + Arrays.toString(c.npcIds) + " xp " + Arrays.toString(c.xpDrops.length > 4 ? Arrays.copyOf(c.xpDrops, 2) : c.xpDrops));
      lines.add("catches " + v.catches + "/" + v.pity + " · outfit " + outfitPieces() + " · part " + v.hasPart);
    }
    lines.add("step " + v.step.label + (teleported ? " (teleported)" : "") + " · area " + (v.area.isEmpty() ? "-" : v.area) + " · dist " + v.distance);
    lines.add("route " + lastRoute);
    lines.add(
        "leg " + leg + " · in house " + client.getTopLevelWorldView().isInstance()
            + " · teleported " + teleported + (lockedRoute != null ? " · locked" : ""));
    lines.add("leg reason: " + legReason);
    Player player = client.getLocalPlayer();
    if (player != null) {
      WorldPoint p = player.getWorldLocation();
      lines.add("you " + p.getX() + "," + p.getY() + "," + p.getPlane() + " burrows " + Travel.inBurrows(p) + " guild " + p.distanceTo(Travel.GUILD));
    }
    lines.add("lit: npcs " + v.npcs.size() + " objs " + v.objects.size() + "/" + v.nextObjects.size() + " items " + v.items + v.nextItems + (v.spell != null ? " spell" : ""));
    lines.add("dialog: " + (lastDialog.length() > 60 ? lastDialog.substring(0, 60) + "…" : lastDialog));
    lines.add("last hunter xp: " + lastXp);
    if (v.goatInPit >= 0) {
      lines.add("goat step " + goatStep + " · pit " + goatPitDebug);
      for (String g : goatVerdicts) lines.add("  " + g);
    }
    if (v.goatInPit >= 0)
      lines.add(
          "goats: in pit " + v.goatInPit + "/" + v.goatCapacity + " cleared " + v.goatCleared
              + " · varbits inpit " + client.getVarbitValue(VarbitID.GOAT_PIT_INPIT)
              + " state " + client.getVarbitValue(VarbitID.GOAT_PIT_STATE)
              + " spikes " + client.getVarbitValue(VarbitID.GOAT_PIT_SPIKES)
              + " varp " + client.getVarpValue(VarPlayerID.GOAT_PIT_VARP));
    lines.add(
        "huntsman's kit " + (huntsKit == null ? "never seen" : huntsKit.size() + " types")
            + " · carried " + kitCarried() + " · bank " + (bank == null ? "never seen" : bank.size() + " types"));
    if (!v.missing.isEmpty()) lines.add("missing: " + String.join(", ", v.missing));
    lines.add("quetzal varbits HG " + client.getVarbitValue(VarbitID.QUETZAL_HUNTERGUILD) + " TT " + client.getVarbitValue(VarbitID.QUETZAL_TALTEKLAN) + " AV " + client.getVarbitValue(VarbitID.QUETZAL_AUBURNVALLEY) + " book " + client.getVarbitValue(VarbitID.SPELLBOOK));
    // While a quetzal map is open, list its icons so the map highlight's wording can be checked.
    for (int id : new int[] {InterfaceID.QuetzalwhistleMenu.ICONS, InterfaceID.QuetzalMenu.ICONS}) {
      Widget icons = client.getWidget(id);
      Widget[] children = icons == null || icons.isHidden() ? null : icons.getDynamicChildren();
      if (children == null) continue;
      List<String> names = new ArrayList<>();
      for (Widget w : children)
        if (w != null && w.getName() != null && !w.getName().isEmpty())
          names.add(Text.removeTags(w.getName()));
      lines.add("quetzal icons: " + String.join(", ", names));
    }
    // Objects with actions right next to the player, to check stairs/trap wording.
    if (player != null) {
      Set<String> near = new LinkedHashSet<>();
      for (TileObject o : nearbyObjects(3)) {
        ObjectComposition c = composition(o);
        if (c == null || c.getName() == null || c.getName().equals("null")) continue;
        List<String> acts = new ArrayList<>();
        for (String a : c.getActions()) if (a != null) acts.add(a);
        if (!acts.isEmpty()) near.add(c.getName() + "[" + String.join("/", acts) + "]");
      }
      if (!near.isEmpty()) lines.add("near: " + String.join(", ", near));
    }
    for (String e : events) lines.add("> " + e);
    return lines;
  }
}
