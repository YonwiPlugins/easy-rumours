package com.yonwi.rumours;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar in Ultimate Farming's style: gradient header, then Guide, Kit, Rumours and Stats tabs
 * with bordered cards and item icon grids. Swing (EDT) only; it reads the plugin's latest {@link
 * RumoursPlugin.View} and rebuilds a tab only when what it shows has changed.
 */
final class RumoursPanel extends PluginPanel {
  /** Wrapping width for text inside a card, leaving a safe gap before the scrollbar. */
  private static final int CARD_TEXT = 140;

  private static final Color OK = new Color(120, 200, 130),
      WARN = new Color(255, 140, 110),
      AWAY = Style.GOLD,
      IDLE = Style.CARD_BORDER;

  private final RumoursPlugin plugin;
  private final ItemManager items;
  private final ClientThread clientThread;
  private final JLabel subtitle = new JLabel();
  private final JTabbedPane tabs = new JTabbedPane();
  private final JPanel guidePage = column(),
      kitPage = column(),
      rumoursPage = column(),
      statsPage = column();
  private final JLabel clock = new JLabel("0:00:00");
  /** This rumour's running time, on the Guide tab. */
  private final JLabel rumourClock = new JLabel();
  private final JPanel historyPage = column();
  private int historyShown = -1;
  /** "This run (4:32) would be 2nd", against {@link #paceTimes}. */
  private final JLabel paceLabel = new JLabel();
  private List<Long> paceTimes = new ArrayList<>();
  private final JLabel statRumours = big(), statHour = big(), statLast = big(), statTotal = big();
  private final javax.swing.Timer ticker;
  /** What each tab last showed, so it's only rebuilt when that changes. */
  private String guideShown = "", kitShown = "", rumoursShown = "";

  private boolean disposed;

  RumoursPanel(RumoursPlugin plugin, ItemManager items, ClientThread clientThread) {
    super(false);
    this.plugin = plugin;
    this.items = items;
    this.clientThread = clientThread;
    setLayout(new BorderLayout());
    setBackground(Style.BG);
    setBorder(new EmptyBorder(8, 6, 8, 6));
    JPanel body = column();
    JPanel hero = Style.hero("Easy Rumours", subtitle);
    hero.setToolTipText("Rumours this session, per hour, and how the last rare part came");
    hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
    body.add(hero);
    body.add(Box.createVerticalStrut(8));
    tabs.addTab("Guide", scroll(guidePage));
    tabs.addTab("Kit", scroll(kitPage));
    tabs.addTab("All", scroll(rumoursPage));
    tabs.addTab("Stats", scroll(statsPage));
    body.add(tabs);
    add(body, BorderLayout.CENTER);
    buildStats();
    ticker = new javax.swing.Timer(1000, e -> updateStats());
    ticker.start();
    refresh();
  }

  // ---------------------------------------------------------------- layout helpers

  /**
   * A top-to-bottom panel. As a tab's scroll view it always takes the viewport's width, so nothing
   * can run under the scrollbar; only the height scrolls. (Ultimate Farming's Column.)
   */
  private static final class Column extends JPanel implements Scrollable {
    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
      return 18;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
      return visible.height;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      // Fill a short tab, but scroll a long one.
      return getParent() instanceof JViewport
          && getParent().getHeight() > getPreferredSize().height;
    }

    @Override
    protected void addImpl(Component c, Object constraints, int index) {
      if (c instanceof JComponent) {
        JComponent j = (JComponent) c;
        j.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (j instanceof AbstractButton)
          j.setMaximumSize(new Dimension(Integer.MAX_VALUE, j.getPreferredSize().height));
      }
      super.addImpl(c, constraints, index);
    }
  }

  private static JPanel column() {
    JPanel p = new Column();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    p.setBackground(Style.BG);
    p.setAlignmentX(Component.LEFT_ALIGNMENT);
    return p;
  }

  /** Style's bordered card, but every child left-aligned (mixed alignments make BoxLayout centre). */
  private static JPanel card() {
    JPanel card = column();
    card.setBackground(Style.CARD);
    card.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Style.CARD_BORDER), new EmptyBorder(9, 9, 9, 9)));
    return card;
  }

  private static JScrollPane scroll(JPanel p) {
    JScrollPane s = new JScrollPane(p);
    s.setBorder(BorderFactory.createEmptyBorder());
    s.setViewportBorder(null);
    s.getViewport().setBackground(Style.BG);
    // The same right-hand gap on every tab, scrollbar or not.
    p.setBorder(new EmptyBorder(10, 0, 0, 6));
    s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    Style.scrollBar(s.getVerticalScrollBar());
    s.setPreferredSize(new Dimension(225, 440));
    return s;
  }

  /** Adds a card at its natural height with a small gap below. */
  private static void addCard(JPanel page, JPanel card) {
    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
    page.add(card);
    page.add(Box.createRigidArea(new Dimension(0, 5)));
  }

  private static String html(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static JLabel label(String s) {
    JLabel l = new JLabel(s);
    l.setForeground(Style.INK);
    return l;
  }

  /** Wrapped small grey text. */
  private static JLabel muted(String s) {
    JLabel l = label("<html><div style=\"width:" + CARD_TEXT + "px\">" + html(s) + "</div></html>");
    l.setForeground(Style.MUTED);
    l.setFont(Style.small());
    return l;
  }

  private static JLabel heading(String s) {
    JLabel l = label("<html><div style=\"width:" + CARD_TEXT + "px\">" + html(s) + "</div></html>");
    l.setFont(Style.bold());
    return l;
  }

  private static JLabel big() {
    JLabel l = label("-");
    l.setFont(Style.bold().deriveFont(16f));
    return l;
  }

  /** Name on the left, value on the right. */
  private static JPanel row(String name, String value, Color color) {
    JPanel row = new JPanel(new BorderLayout(6, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(1, 0, 1, 0));
    JLabel n = new JLabel(name);
    n.setFont(Style.small());
    n.setForeground(Style.MUTED);
    JLabel v = new JLabel(value);
    v.setFont(Style.small());
    v.setForeground(color);
    v.setHorizontalAlignment(SwingConstants.RIGHT);
    // The small font's last glyph clips without a little room.
    v.setBorder(new EmptyBorder(0, 0, 0, 3));
    row.add(n, BorderLayout.WEST);
    row.add(v, BorderLayout.CENTER);
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
    return row;
  }

  /** A small stat tile: grey caption over a big value. */
  private static JPanel tile(String caption, JLabel value) {
    JPanel t = new JPanel();
    t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
    t.setBackground(Style.SELECT_BACKGROUND);
    t.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Style.CARD_BORDER), new EmptyBorder(5, 7, 6, 7)));
    JLabel c = new JLabel(caption.toUpperCase(Locale.ROOT));
    c.setFont(Style.small());
    c.setForeground(Style.GOLD);
    c.setBorder(new EmptyBorder(0, 0, 0, 4));
    t.add(c);
    t.add(value);
    return t;
  }

  private void icon(JLabel target, int id) {
    if (id <= 0 || clientThread == null) return;
    clientThread.invoke(
        () -> {
          if (!disposed) items.getImage(id).addTo(target);
        });
  }

  /** An inventory-style slot: icon, an amount underneath, and a border showing its state. */
  private JPanel cell(RumoursPlugin.KitItem item) {
    JPanel cell = new JPanel(new BorderLayout());
    cell.setBackground(Style.SELECT_BACKGROUND);
    Color state =
        item.ok() ? OK : item.optional ? IDLE : item.where.startsWith("in ") ? AWAY : WARN;
    cell.setBorder(BorderFactory.createLineBorder(state, item.ok() || item.optional ? 1 : 2));
    JLabel img = new JLabel();
    img.setHorizontalAlignment(SwingConstants.CENTER);
    if (item.icon > 0) icon(img, item.icon);
    else {
      img.setText("Spell");
      img.setFont(Style.small());
      img.setForeground(Style.HERO_TEXT);
    }
    cell.add(img, BorderLayout.CENTER);
    JLabel qty =
        new JLabel(
            item.icon <= 0
                ? (item.ok() ? "ok" : "no")
                : item.want > 1 || !item.ok() ? item.have + "/" + item.want : "ok");
    qty.setFont(Style.small());
    qty.setForeground(state == IDLE ? Style.MUTED : state);
    qty.setHorizontalAlignment(SwingConstants.CENTER);
    cell.add(qty, BorderLayout.SOUTH);
    cell.setPreferredSize(new Dimension(40, 46));
    cell.setToolTipText(
        "<html><b>"
            + html(item.name)
            + "</b><br>"
            + (item.ok() ? "Carried" : item.optional ? "Optional" : html(item.where))
            + (item.want > 1 ? "<br>" + item.have + " of " + item.want + " (one per trap)" : "")
            + (item.note.isEmpty() ? "" : "<br>" + html(item.note))
            + "</html>");
    return cell;
  }

  private JPanel grid(List<RumoursPlugin.KitItem> list) {
    JPanel grid = new JPanel(new GridLayout(0, 4, 3, 3));
    grid.setOpaque(false);
    for (RumoursPlugin.KitItem item : list) grid.add(cell(item));
    // Keep short rows left-aligned at slot size.
    for (int i = list.size(); i < 4; i++) {
      JPanel gap = new JPanel();
      gap.setOpaque(false);
      grid.add(gap);
    }
    return grid;
  }

  private static String duration(long millis) {
    long s = Math.max(0, millis) / 1000;
    return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
  }

  private static String ago(long at) {
    if (at <= 0) return "never";
    long m = (System.currentTimeMillis() - at) / 60_000;
    return m < 1 ? "just now" : m < 60 ? m + " min ago" : m / 60 + " h ago";
  }

  // ---------------------------------------------------------------- refresh

  /** Re-reads the plugin's latest view; rebuilds only the tabs whose content changed. */
  void refresh() {
    if (disposed) return;
    RumoursPlugin.View v = plugin.view();
    String g = guideKey(v);
    if (!g.equals(guideShown)) {
      guideShown = g;
      renderGuide(v);
    }
    String k = kitKey(v);
    if (!k.equals(kitShown)) {
      kitShown = k;
      renderKit(v);
    }
    String r = v.hunter + "|" + v.rumour + kitItemsKey(v);
    if (!r.equals(rumoursShown)) {
      rumoursShown = r;
      renderRumours(v);
    }
    updateStats();
  }

  private static String kitItemsKey(RumoursPlugin.View v) {
    StringBuilder b = new StringBuilder();
    for (RumoursPlugin.KitRow row : v.kit) {
      b.append('|').append(row.rumour).append(row.current);
      for (RumoursPlugin.KitItem i : row.items) b.append(';').append(i.have).append(i.where);
    }
    return b.toString();
  }

  private String guideKey(RumoursPlugin.View v) {
    return v.step + "|" + v.rumour + "|" + v.hunter + "|" + v.instruction + "|" + v.area + "|"
        + v.distance / 5 + "|" + v.catches + "/" + v.pity + "|" + v.hasPart + "|" + v.goatInPit
        + "/" + v.goatCleared + "|" + v.missing + v.skippedItems + v.route + v.loadoutMissing
        + "|" + historyShown;
  }

  private static String kitKey(RumoursPlugin.View v) {
    StringBuilder travel = new StringBuilder(v.route).append(v.alternatives).append(v.loadoutLines).append(v.loadoutMissing).append(v.loadoutSlots).append(v.nearestBank);
    for (RumoursPlugin.KitItem i : v.loadout) travel.append(i.name).append(i.have).append(i.where).append(i.note);
    for (RumoursPlugin.KitItem i : v.travel) travel.append(i.name).append(i.have).append(i.where);
    return travel + kitItemsKey(v) + Arrays.toString(v.outfit) + v.pity + "|" + ago(v.bankSeen) + ago(v.kitSeen)
        + v.missing;
  }

  // ---------------------------------------------------------------- Guide tab

  private void renderGuide(RumoursPlugin.View v) {
    guidePage.removeAll();
    JPanel now = card();
    JLabel step = Style.section("NOW · " + v.step.label.toUpperCase(Locale.ROOT));
    step.setBorder(new EmptyBorder(0, 0, 3, 0));
    now.add(step);
    now.add(heading(v.rumour == Rumour.NONE ? "No rumour yet" : v.rumour.name));
    now.add(Box.createVerticalStrut(2));
    if (v.rumour != Rumour.NONE) {
      rumourClock.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
      rumourClock.setForeground(Style.HERO_TEXT);
      now.add(rumourClock);
    }
    JLabel action = muted(v.instruction.isEmpty() ? "Log in to start" : v.instruction);
    action.setForeground(new Color(150, 205, 255));
    now.add(action);
    addCard(guidePage, now);

    // Bank first, like Ultimate Farming: list what to withdraw, with a way past it.
    List<String> toGet = new ArrayList<>(v.missing);
    if (v.step == RumoursPlugin.Step.GET_ITEMS) for (String m : v.loadoutMissing) if (!toGet.contains(m)) toGet.add(m);
    if (!toGet.isEmpty()
        && (v.step == RumoursPlugin.Step.GET_ITEMS || v.step == RumoursPlugin.Step.TO_AREA)) {
      JPanel bank = card();
      JLabel title = Style.section(v.skippedItems ? "SETTING OFF WITHOUT" : "GET FIRST");
      title.setBorder(new EmptyBorder(0, 0, 3, 0));
      bank.add(title);
      for (String m : toGet) {
        JLabel line = muted("• " + m);
        line.setForeground(WARN);
        bank.add(line);
      }
      if (!v.skippedItems) {
        bank.add(Box.createVerticalStrut(6));
        JButton anyway = new JButton("Set off anyway");
        anyway.setToolTipText("Skip this rumour's items check, and the loadout check for this session");
        anyway.addActionListener(e -> plugin.skipItems());
        bank.add(anyway);
      }
      addCard(guidePage, bank);
    }

    if (v.rumour != Rumour.NONE) {
      JPanel info = card();
      info.add(row("Hunter", v.hunter.toString(), Style.INK));
      info.add(row("Method", v.rumour.creature.trap.name, Style.INK));
      if (!v.route.isEmpty() && !v.hasPart) {
        JLabel route = muted("Route: " + v.route);
        route.setForeground(Style.HERO_TEXT);
        info.add(route);
      }
      if (!v.area.isEmpty()) {
        JLabel area = muted(v.area + (v.distance >= 0 ? " · " + v.distance + " tiles" : ""));
        area.setForeground(Style.HERO_TEXT);
        info.add(area);
      }
      addCard(guidePage, info);

      JPanel luck = card();
      JLabel caption = Style.section(v.hasPart ? "RARE PART FOUND" : "CATCHES");
      caption.setBorder(new EmptyBorder(0, 0, 3, 0));
      luck.add(caption);
      JLabel count = label(v.catches + " / " + v.pity);
      count.setFont(Style.bold().deriveFont(18f));
      count.setForeground(v.hasPart ? OK : Style.INK);
      luck.add(count);
      JProgressBar bar = new JProgressBar(0, Math.max(1, v.pity));
      bar.setValue(Math.min(v.catches, Math.max(1, v.pity)));
      bar.setForeground(v.catches * 4 >= v.pity * 3 ? WARN : new Color(64, 140, 76));
      bar.setBackground(Style.SCROLL_TRACK);
      bar.setBorderPainted(false);
      bar.setPreferredSize(new Dimension(10, 8));
      bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
      luck.add(Box.createVerticalStrut(4));
      luck.add(bar);
      luck.add(Box.createVerticalStrut(4));
      int pct = v.pity > 0 ? 100 * v.catches / v.pity : 0;
      luck.add(
          muted(
              v.hasPart
                  ? "Found at " + pct + "% of pity. Take it back to " + v.hunter.commonName + "."
                  : pct + "% of the way to pity (" + Math.max(0, v.pity - v.catches) + " left)"));
      addCard(guidePage, luck);

      if (v.goatInPit >= 0) {
        JPanel goats = card();
        JLabel title = Style.section("GOAT PIT");
        title.setBorder(new EmptyBorder(0, 0, 4, 0));
        goats.add(title);
        JPanel pair = new JPanel(new GridLayout(1, 2, 4, 0));
        pair.setOpaque(false);
        JLabel inPit = big(), cleared = big();
        inPit.setText(v.goatInPit + " / " + v.goatCapacity);
        cleared.setText(String.valueOf(v.goatCleared));
        pair.add(tile("In pit", inPit));
        pair.add(tile("Cleared", cleared));
        pair.setMaximumSize(new Dimension(Integer.MAX_VALUE, pair.getPreferredSize().height));
        goats.add(pair);
        addCard(guidePage, goats);
      }

      // Past runs of this rumour, fastest first.
      List<RumoursPlugin.Record> mine = new ArrayList<>();
      for (RumoursPlugin.Record r : plugin.records()) if (v.rumour.name().equals(r.rumour)) mine.add(r);
      mine.sort(Comparator.comparingLong(r -> r.total));
      JPanel history = card();
      JLabel title = Style.section("YOUR " + v.rumour.name.toUpperCase(Locale.ROOT) + " TIMES");
      title.setBorder(new EmptyBorder(0, 0, 3, 4));
      history.add(title);
      if (mine.isEmpty()) history.add(muted("Your first one: the time is saved when you hand in."));
      else {
        long sum = 0;
        for (RumoursPlugin.Record r : mine) sum += r.total;
        history.add(row("Best · average", short_(mine.get(0).total) + " · " + short_(sum / mine.size()), Style.GOLD));
        for (int i = 0; i < mine.size() && i < 10; i++) {
          RumoursPlugin.Record r = mine.get(i);
          history.add(row(ordinal(i + 1) + "  " + r.catches + "/" + r.pity, short_(r.total), i == 0 ? OK : Style.INK));
        }
        if (mine.size() > 10) history.add(muted("+" + (mine.size() - 10) + " slower"));
      }
      paceLabel.setFont(Style.small());
      paceLabel.setForeground(Style.HERO_TEXT);
      history.add(paceLabel);
      paceTimes = new ArrayList<>();
      for (RumoursPlugin.Record r : mine) paceTimes.add(r.total);
      addCard(guidePage, history);
    }
    guidePage.add(Box.createVerticalGlue());
    guidePage.revalidate();
    guidePage.repaint();
  }

  // ---------------------------------------------------------------- Kit tab

  private static final int[] OUTFIT_ICONS = {
    net.runelite.api.gameval.ItemID.HG_HUNTER_HOOD,
    net.runelite.api.gameval.ItemID.HG_HUNTER_TOP,
    net.runelite.api.gameval.ItemID.HG_HUNTER_LEGS,
    net.runelite.api.gameval.ItemID.HG_HUNTER_BOOTS
  };
  private static final String[] OUTFIT_NAMES = {"hood", "top", "legs", "boots"};

  private void renderKit(RumoursPlugin.View v) {
    kitPage.removeAll();
    // What to bring: the current rumour's items, or every rumour's when there's none.
    List<RumoursPlugin.KitItem> bring = new ArrayList<>();
    String whose;
    RumoursPlugin.KitRow current = null;
    for (RumoursPlugin.KitRow row : v.kit) if (row.current) current = row;
    if (current != null) {
      bring.addAll(current.items);
      whose = current.rumour.name;
    } else {
      Map<String, RumoursPlugin.KitItem> union = new LinkedHashMap<>();
      for (RumoursPlugin.KitRow row : v.kit)
        for (RumoursPlugin.KitItem i : row.items) {
          RumoursPlugin.KitItem seen = union.get(i.name);
          if (seen == null || i.want > seen.want) union.put(i.name, i);
        }
      bring.addAll(union.values());
      whose = "all of " + (v.hunter == Hunter.NONE ? "Teco" : v.hunter.commonName) + "'s rumours";
    }
    if (!v.missing.isEmpty()) {
      JLabel warn = muted("Not carrying: " + String.join(", ", v.missing));
      warn.setForeground(WARN);
      kitPage.add(warn);
      kitPage.add(Box.createVerticalStrut(8));
    }

    // The one loadout for every rumour: withdraw it once.
    kitPage.add(Style.section("LOADOUT · " + (v.hunter == Hunter.NONE ? "TECO" : v.hunter.commonName.toUpperCase(Locale.ROOT)) + "'S RUMOURS"));
    JPanel load = card();
    load.add(muted("Withdraw this once and keep it on you. Traps live in the huntsman's kit."));
    load.add(Box.createVerticalStrut(4));
    List<RumoursPlugin.KitItem> core = new ArrayList<>(), nice = new ArrayList<>();
    for (RumoursPlugin.KitItem i : v.loadout) (i.optional ? nice : core).add(i);
    load.add(grid(core));
    load.add(Box.createVerticalStrut(4));
    load.add(muted(v.loadoutSlots));
    if (!v.nearestBank.isEmpty()) {
      JLabel bankLine = muted("Nearest bank from anywhere: " + v.nearestBank);
      bankLine.setForeground(Style.HERO_TEXT);
      load.add(bankLine);
    }
    if (!nice.isEmpty()) {
      load.add(Box.createVerticalStrut(6));
      JLabel n = Style.section("NICE TO HAVE · YOU OWN THESE");
      n.setBorder(new EmptyBorder(0, 0, 2, 0));
      load.add(n);
      load.add(grid(nice));
      load.add(muted("From the wiki's Hunters' Rumours strategies. Optional: never needed to set off."));
    }
    if (!v.loadoutMissing.isEmpty()) {
      JLabel get = muted("To withdraw: " + String.join(", ", v.loadoutMissing));
      get.setForeground(WARN);
      load.add(get);
    }
    load.add(Box.createVerticalStrut(6));
    JLabel how = Style.section("HOW IT GETS YOU THERE");
    how.setBorder(new EmptyBorder(0, 0, 2, 0));
    load.add(how);
    for (String line : v.loadoutLines) load.add(muted(line));
    addCard(kitPage, load);

    kitPage.add(Style.section("BRING · " + whose.toUpperCase(Locale.ROOT)));
    JPanel bringCard = card();
    if (bring.isEmpty()) bringCard.add(muted("Nothing to bring."));
    else bringCard.add(grid(bring));
    addCard(kitPage, bringCard);

    if (current != null && !v.route.isEmpty()) {
      kitPage.add(Style.section("TRAVEL"));
      JPanel travel = card();
      travel.add(heading(v.route));
      if (!v.travel.isEmpty()) {
        travel.add(Box.createVerticalStrut(4));
        travel.add(grid(v.travel));
      }
      if (!v.alternatives.isEmpty()) {
        travel.add(Box.createVerticalStrut(4));
        travel.add(muted("Other ways: " + String.join("; ", v.alternatives)));
      }
      addCard(kitPage, travel);
    }

    // Where each missing item is, in words.
    List<String> where = new ArrayList<>();
    List<RumoursPlugin.KitItem> withTravel = new ArrayList<>(bring);
    withTravel.addAll(v.travel);
    for (RumoursPlugin.KitItem i : withTravel)
      if (!i.ok() && !i.optional)
        where.add(i.name + (i.have > 0 ? " " + i.have + "/" + i.want : "") + ": " + i.where);
    if (!where.isEmpty()) {
      JPanel card = card();
      JLabel t = Style.section("WHERE IT IS");
      t.setBorder(new EmptyBorder(0, 0, 3, 0));
      card.add(t);
      for (String w : where) card.add(muted(w));
      addCard(kitPage, card);
    }

    kitPage.add(Style.section("WEAR · GUILD HUNTER OUTFIT"));
    JPanel wear = card();
    JPanel slots = new JPanel(new GridLayout(1, 4, 3, 3));
    slots.setOpaque(false);
    int worn = 0;
    for (int i = 0; i < 4; i++) {
      JLabel slot = new JLabel();
      slot.setHorizontalAlignment(SwingConstants.CENTER);
      slot.setOpaque(true);
      slot.setBackground(Style.SELECT_BACKGROUND);
      slot.setPreferredSize(new Dimension(40, 38));
      slot.setBorder(BorderFactory.createLineBorder(v.outfit[i] ? OK : IDLE));
      slot.setToolTipText("Guild hunter " + OUTFIT_NAMES[i] + (v.outfit[i] ? ": worn" : ": not worn"));
      if (v.outfit[i]) {
        icon(slot, OUTFIT_ICONS[i]);
        worn++;
      } else {
        slot.setText(OUTFIT_NAMES[i]);
        slot.setFont(Style.small());
        slot.setForeground(Style.MUTED);
      }
      slots.add(slot);
    }
    wear.add(slots);
    wear.add(Box.createVerticalStrut(4));
    wear.add(muted(worn + " of 4 pieces worn. Each piece lowers pity by a quarter of the full outfit's reduction."));
    addCard(kitPage, wear);

    kitPage.add(Style.section("SCANNED"));
    JPanel scanned = card();
    scanned.add(row("Inventory & worn", "live", OK));
    scanned.add(row("Bank", ago(v.bankSeen), v.bankSeen > 0 ? Style.INK : WARN));
    scanned.add(row("Huntsman's kit", ago(v.kitSeen), v.kitSeen > 0 ? Style.INK : WARN));
    scanned.add(Box.createVerticalStrut(4));
    scanned.add(
        muted(
            "Open the bank and View your huntsman's kit to update them. Items in the kit must be"
                + " taken out to use."));
    addCard(kitPage, scanned);

    JPanel tip = card();
    tip.add(
        muted(
            "In the bank, the Hunter button at the top shows only these items and the"
                + " route's teleports (chosen and other ways), plus your whistle, Fortis tablet,"
                + " rune pouch, huntsman's kit and outfit."));
    addCard(kitPage, tip);
    kitPage.add(Box.createVerticalGlue());
    kitPage.revalidate();
    kitPage.repaint();
  }

  // ---------------------------------------------------------------- Rumours tab

  private void renderRumours(RumoursPlugin.View v) {
    rumoursPage.removeAll();
    Hunter h = v.hunter == Hunter.NONE ? Hunter.TECO : v.hunter;
    rumoursPage.add(Style.section(h.commonName.toUpperCase(Locale.ROOT) + "'S RUMOURS"));
    int ready = 0;
    for (RumoursPlugin.KitRow row : v.kit) if (row.ready) ready++;
    rumoursPage.add(muted(ready + " of " + v.kit.size() + " ready with what you're carrying."));
    rumoursPage.add(Box.createVerticalStrut(6));
    for (RumoursPlugin.KitRow row : v.kit) {
      JPanel card = card();
      if (row.current)
        card.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Style.GOLD, 2), new EmptyBorder(8, 8, 8, 8)));
      card.add(heading(row.rumour.name));
      card.add(
          row(
              row.rumour.creature.trap.name + " · " + row.rumour.creature.level,
              row.current ? "current" : row.ready ? "ready" : "needs items",
              row.current ? Style.GOLD : row.ready ? OK : WARN));
      card.add(Box.createVerticalStrut(4));
      if (row.items.isEmpty()) card.add(muted("Nothing to bring."));
      else card.add(grid(row.items));
      addCard(rumoursPage, card);
    }
    rumoursPage.add(Box.createVerticalStrut(4));
    rumoursPage.add(muted("Rumour list and items from the OSRS wiki."));
    rumoursPage.add(Box.createVerticalGlue());
    rumoursPage.revalidate();
    rumoursPage.repaint();
  }

  // ---------------------------------------------------------------- Stats tab

  private void buildStats() {
    statsPage.add(Style.section("SESSION"));
    JPanel time = card();
    clock.setForeground(Style.INK);
    clock.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 19));
    time.add(clock);
    addCard(statsPage, time);
    JPanel grid = new JPanel(new GridLayout(2, 2, 4, 4));
    grid.setOpaque(false);
    grid.add(tile("Rumours", statRumours));
    grid.add(tile("Per hour", statHour));
    grid.add(tile("Last", statLast));
    grid.add(tile("All time", statTotal));
    grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
    statsPage.add(grid);
    statsPage.add(Box.createVerticalStrut(6));
    JButton reset = new JButton("Reset session");
    reset.addActionListener(
        e -> {
          plugin.resetSession();
          updateStats();
        });
    statsPage.add(reset);
    statsPage.add(Box.createVerticalStrut(8));
    statsPage.add(historyPage);
    statsPage.add(Style.section("NEXT UNLOCK"));
    JPanel next = card();
    next.add(muted("Outfit and pet ETAs come once their drop rates are looked up."));
    addCard(statsPage, next);
    statsPage.add(Box.createVerticalGlue());
  }

  /**
   * Rumour times, grouped by creature, then by catches used against pity ("13/13", "12/13"),
   * fastest first: 1st, 2nd, 3rd... Each creature also shows its best and average.
   */
  private void renderHistory(List<RumoursPlugin.Record> recs) {
    historyPage.removeAll();
    historyPage.add(Style.section("RUMOUR TIMES"));
    if (recs.isEmpty()) {
      historyPage.add(muted("Times appear here after your first hand-in."));
      historyPage.add(Box.createVerticalStrut(8));
    }
    Map<String, List<RumoursPlugin.Record>> byRumour = new TreeMap<>();
    for (RumoursPlugin.Record r : recs)
      byRumour.computeIfAbsent(r.rumour, k -> new ArrayList<>()).add(r);
    for (Map.Entry<String, List<RumoursPlugin.Record>> e : byRumour.entrySet()) {
      Rumour rumour;
      try {
        rumour = Rumour.valueOf(e.getKey());
      } catch (IllegalArgumentException ex) {
        continue;
      }
      List<RumoursPlugin.Record> list = e.getValue();
      JPanel card = card();
      card.add(
          heading(
              rumour.name + (rumour == Rumour.WYRMSCRAIG_GOAT ? " · goats cleared" : " · caught")));
      long best = Long.MAX_VALUE, sum = 0;
      for (RumoursPlugin.Record r : list) {
        best = Math.min(best, r.total);
        sum += r.total;
      }
      card.add(row("Best · average", short_(best) + " · " + short_(sum / list.size()), Style.GOLD));
      card.add(row("Rumours", String.valueOf(list.size()), Style.INK));
      // Most catches first, as in "13/13" then "12/13".
      Map<Integer, Map<Integer, List<RumoursPlugin.Record>>> buckets =
          new TreeMap<>(Comparator.reverseOrder());
      for (RumoursPlugin.Record r : list)
        buckets
            .computeIfAbsent(r.catches, k -> new TreeMap<>())
            .computeIfAbsent(r.pity, k -> new ArrayList<>())
            .add(r);
      for (Map.Entry<Integer, Map<Integer, List<RumoursPlugin.Record>>> b : buckets.entrySet())
        for (Map.Entry<Integer, List<RumoursPlugin.Record>> p : b.getValue().entrySet()) {
          card.add(Box.createVerticalStrut(4));
          JLabel caught = Style.section(b.getKey() + "/" + p.getKey());
          caught.setBorder(new EmptyBorder(2, 0, 1, 4));
          card.add(caught);
          List<RumoursPlugin.Record> ranked = new ArrayList<>(p.getValue());
          ranked.sort(Comparator.comparingLong(r -> r.total));
          for (int i = 0; i < ranked.size() && i < 5; i++) {
            RumoursPlugin.Record r = ranked.get(i);
            card.add(
                row(
                    ordinal(i + 1),
                    short_(r.total) + (r.toPart > 0 ? " (part " + short_(r.toPart) + ")" : ""),
                    i == 0 ? OK : Style.INK));
          }
          if (ranked.size() > 5) card.add(muted("+" + (ranked.size() - 5) + " slower"));
        }
      addCard(historyPage, card);
    }
    historyPage.revalidate();
    historyPage.repaint();
  }

  private static String ordinal(int n) {
    int t = n % 100, u = n % 10;
    return n + (t >= 11 && t <= 13 ? "th" : u == 1 ? "st" : u == 2 ? "nd" : u == 3 ? "rd" : "th");
  }

  /** "4:32", or "1:04:32" past an hour. */
  private static String short_(long millis) {
    long s = Math.max(0, millis) / 1000;
    return s >= 3600
        ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
  }

  private void updateStats() {
    if (disposed) return;
    long elapsed = System.currentTimeMillis() - plugin.sessionStart();
    int done = plugin.sessionCompletions();
    String perHour =
        done == 0 || elapsed < 60_000
            ? "-"
            : String.format(Locale.ROOT, "%.1f", done * 3_600_000d / elapsed);
    clock.setText(duration(elapsed));
    long start = plugin.rumourStart(), part = plugin.partAt();
    rumourClock.setText(
        start <= 0
            ? "Time: from your next rumour"
            : "Time " + short_(System.currentTimeMillis() - start)
                + (part > start ? " · part at " + short_(part - start) : ""));
    // Where this run would place among past ones if handed in now.
    if (start > 0) {
      long now = System.currentTimeMillis() - start;
      int place = 1;
      for (long t : paceTimes) if (t < now) place++;
      paceLabel.setText("This run (" + short_(now) + ") would be " + ordinal(place));
    } else paceLabel.setText("");
    List<RumoursPlugin.Record> recs = plugin.records();
    if (recs.size() != historyShown) {
      historyShown = recs.size();
      renderHistory(recs);
    }
    statRumours.setText(String.valueOf(done));
    statHour.setText(perHour);
    long last = plugin.lastRumourMillis();
    statLast.setText(last < 0 ? "-" : duration(last).replaceFirst("^0:", ""));
    statTotal.setText(String.valueOf(plugin.totalCompletions()));
    String result = plugin.lastResult();
    subtitle.setText(
        "<html><div style=\"width:" + CARD_TEXT + "px\">" + done + " rumours · " + perHour + "/h"
            + (result.isEmpty() ? "" : "<br>Last: " + html(result)) + "</div></html>");
  }

  void dispose() {
    disposed = true;
    ticker.stop();
    removeAll();
  }
}
