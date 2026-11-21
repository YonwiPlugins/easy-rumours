# Easy Rumours: how it works (v0.1.0)

## The loop
Each tick the step is worked out again from game state, and only switches after it reads the same for 3 ticks.

| Step | When | Shown / highlighted |
|---|---|---|
| Get a rumour | No rumour, in the burrows | Your hunter (Teco until one is known) |
| Return | No rumour or holding the rare part, not in the burrows | Teleport item/spell, then the quetzal map icon; near the guild, the stairs down |
| Travel | Rumour, no part, more than 25 tiles from the nearest area | The route's item/spell and map icon; from the burrows, the stairs up |
| Hunt | Within 25 tiles of an area | Creature NPCs; objects offering "Check" (blue); "Set-trap" spots (orange); box trap / bird snare in the inventory (orange); the net if not wielded |
| Hand in | Holding the rare part in the burrows | Your hunter |

- **Rumour + hunter**: parsed from hunter dialogue in the burrows (DIALOG game message events, plus the ChatLeft widget as a fallback) and from the quetzal whistle's "Your current rumour target is…" game message.
- **Done**: the rumour's rare part is in the inventory.
- **Handed in**: that part leaves the inventory while in the burrows → completions +1.
- **Catches**: Hunter XP drops that equal the creature's catch XP.
- **Pity**: trap base minus a quarter of the outfit reduction per guild hunter piece worn (butterfly net: 80 → 79 with one piece).
- **Route choice**: the area with the lowest cost: walk if within 100 tiles (or a guild area while at the guild); quetzal whistle → Fortis tablet → Fortis spell for guild/Tal Teklan/Auburnvale areas; else the fairy ring code (text only).
- After a teleport (30+ tiles in a tick, not an instance) the item stops being highlighted until the step changes.

## Items (v0.2.0)
- Teco's pool (OSRS wiki, Hunters' Rumours): sabre-toothed kebbit, red salamander, grey chinchompa, carnivorous/red chinchompa, sunlight moth, herbiboar, wyrmscraig goat. Aco and Wolf pools are in too.
- Items (OSRS wiki): deadfall = knife or fletching knife + logs; red salamander = the one-item **net trap** (ID 34631, from the wiki: released 7 Oct 2026, newer than our API, UNVERIFIED in game), one per trap; box trap (one per trap); sunlight moth = butterfly net (any, incl. magic) + butterfly jar, optional at 75+ Hunter (barehanded); herbiboar = nothing (magic secateurs optional); goat = **Telekinetic Grab**: 33 Magic, standard spellbook, a law rune and an air rune (inventory or rune pouch; dust/mist/smoke runes or a worn air/smoke/mist/dust staff count for air).
- Traps you can lay = 1 + Hunter/20, max 5. Only having none at all holds the guide; fewer shows "3/5".
- **Get items** step: rumour known, required item not carried, and you're at the guild or in the burrows. Item in the huntsman's kit (and the kit in your inventory) → highlight the kit. Otherwise highlight the burrows bankers and, with the bank open, the items. Away from the guild it only adds "missing …" to the guide.
- Huntsman's kit: the wiki says stored items can't be used, so they count as "in kit", not carried. Contents come from item container 855 whenever the game sends it (probably when you View/Fill it), saved per account. Bank contents are saved whenever the bank opens.

## Unverified (shown in the debug box where possible)
- Whether container 855 is sent without opening the kit (debug: `huntsman's kit N types / never seen`).
- Butterfly jar quantity: 1 is treated as enough.
- Game text from the Hunter Rumours source, not yet seen by us: "you find a rare piece of the creature!", "Your current rumour target is", the hunter's closing lines ("would you like another rumour?", "here's your reward.", "another one done?").
- Hunter dialogue arrives as `Name|text` DIALOG game message events (debug: `dialog:`).
- Catch XP values per creature (debug: `last hunter xp: N (catch / not a catch)`).
- Guild surface position (1557, 3049) = burrows minus 6400 in y (debug: `guild N`).
- Stairs are found by name containing "stair"/"steps" with "Climb-up"/"Climb-down" (debug: `near:` lists objects within 3 tiles).
- Quetzal map icon wording "hunter guild", "tal teklan", "auburn" (debug: `quetzal icons:` while the map is open).
- Which areas are a walk from the guild: Avium Savannah, West of Hunter Guild, Ralos' Rise, Hunters' Guild, NW of the Locus Oasis.
- Caught traps offer "Check"; unset trap spots offer "Set-trap".
- Spellbook varbit 0 = standard; the Civitas illa Fortis spell isn't checked for level or runes.
- Sunlight moth point (1735, 3012) in the source data looks like a typo; the nearest point is used, so it shouldn't matter.
- Outfit/pet ETA: placeholder until drop rates are looked up.

## Bank button (v0.3.0)
Ultimate Farming's button (Quest Helper's position: 408,5, 25px) with the Hunter skill icon. Shows the current rumour's items (all of the hunter's rumours when there's none), plus quetzal whistles, the Fortis tablet, the huntsman's kit, rune pouches, air staves (goat) and the guild outfit.

## Sidebar (v0.4.0)
Built like Ultimate Farming's: gradient header (session rumours, per hour, last result), then tabs.
- **Guide**: NOW card (step, rumour, next action); GET FIRST list with "Set off anyway" (skips the items check until the rumour changes); hunter/method/area; catches vs pity with bar; GOAT PIT tiles for goat rumours.
- **Kit**: BRING grid (item icons; green = carried, gold = in kit/bank, red = missing, grey = optional; "3/5" for traps), WHERE IT IS list, guild outfit slots, SCANNED (inventory live, bank/kit last seen).
- **All**: every rumour your hunter gives, each with its item grid and ready / needs items / current.
- **Stats**: session clock, rumours, per hour, last rumour time, all time, reset.

## Goat counters (v0.4.0)
- In pit: varbit GOAT_PIT_INPIT (15725). UNVERIFIED: assumed to be the goat remains in the pit ("The pit contains N goat remains, out of 18").
- Cleared: every drop in that varbit while the goat rumour is on, saved per account and reset with the rumour.
- Pit size by Hunter level from the wiki: 16 / 18 / 20 / 22 / 24 at 60 / 69 / 77 / 85 / 93.
- Debug shows the raw GOAT_PIT_INPIT, GOAT_PIT_STATE, GOAT_PIT_SPIKES varbits and GOAT_PIT_VARP.
## Goat pit guide (v0.5.0)
Confirmed in game: In pit (GOAT_PIT_INPIT) and Cleared counts are accurate.
One step at a time, re-read every tick, switching after 3 matching ticks:
1. **Spikes**: pit not lined, no spikes item (34016) → spike supply (object 62349, UNVERIFIED that this is the supply) blue, pit orange.
2. **Line**: holding spikes → pit blue.
3. **Grab**: pit lined (impostor GOAT_PIT_MULTI_SPIKES/ONE/SOME) → Telekinetic Grab spell highlighted until selected, then only goats that pass both checks.
4. **Clear**: pit full (FULL impostor or in pit = capacity), or clearing has started → pit blue until empty.
Pit object GOAT_PIT 62343 (multiloc), states 62344 empty … 62348 full; goat NPC 16298.
Grab rule (from an in-game screenshot, v0.5.2): within 10 tiles of your tile (spell range, UNVERIFIED) and on the far side of the pit: the angle between pit→goat and you→pit is at most 75°. Works from any side. Debug lists each goat's distance, in range, angle and verdict.
## Travel routes (v0.6.0)
Like Ultimate Farming's route choices: each area lists options with a cost; the cheapest one you've unlocked and own (carried, bank cache or huntsman's kit) is chosen, carried items preferred.
| Area (Teco rumour) | Options (quickest first) | Source |
|---|---|---|
| Rellekka Hunter area (sabre-toothed kebbit) | Fairy ring AJP → D K S | Hunter Rumours codes; wiki "Fairy ring" |
| Ourania Hunter area (red salamander) | Ourania Teleport (Lunar book, Lunar Diplomacy); Ardougne cloak → Monastery, walk west; Spirit tree → Battlefield, walk west | wiki "Ourania Hunter area" |
| Piscatoris / Isle of Souls / Kourend Woodland (grey chin) | Fairy ring AKQ / BJP | Hunter Rumours codes |
| Feldip / Tlati / Gwenith / Great Conch (red chin) | Fairy ring AKS; quetzal → Tal Teklan for Tlati | Hunter Rumours codes |
| Avium Savannah (sunlight moth) | Walk from the guild; quetzal → Hunter Guild | |
| Mushroom Forest, Fossil Island (herbiboar) | Digsite pendant (Bone Voyage) | UNVERIFIED pendant option |
| Wyrmscraig (goat) | Necklace of passage; Ardeaglais teleport; Slayer ring (Fallen From Grace) | wiki "Wyrmscraig" |
- Fairy rings: need Fairytale II started; a dramen or lunar staff unless the Lumbridge & Draynor elite diary is done (varbit LUMBRIDGE_DIARY_ELITE_COMPLETE). From the guild, walk to fairy ring AJP in the Avium Savannah. The ring is highlighted by the object name "Fairy ring" (UNVERIFIED); a carried but unwielded staff is highlighted first.
- The chosen route's items join the kit while setting off (GET FIRST, Kit tab "TRAVEL" with have/kit/bank/missing), and stop counting once you're at the area.
- Bank filter: the chosen route's items and every alternative's, plus whistles, Fortis tablet, outfit, huntsman's kit and rune pouches.
- Not covered: POH portals, jewellery box and spirit-tree access (the plugin can't see your house), and spell runes.
## House routes and trip legs (v0.7.0)
- Settings → Your house (all off by default): fairy ring, spirit tree, portal nexus (fully unlocked), jewellery box (ornate).
- House teleport = tablet, construction cape, or Teleport to House (40 Magic, standard book, 1 air + 1 earth + 1 law; wiki); runes count from the rune pouch, a worn staff covers air/earth.
- House routes (wiki "Portal nexus", "Ornate jewellery box", "Fairy ring"): any fairy-ring area → house fairy ring CODE (cost 15, beats walking to AJP at 20); red salamanders → nexus Ourania Cave (cost 9; ASSUMED to land where Ourania Teleport does) or house spirit tree → Battlefield; back to the guild → house fairy ring A J P (14), nexus Civitas illa Fortis → quetzal (16), jewellery box Fortis Colosseum → quetzal (17). Whistle (10) still wins when carried.
- Legs, re-read each tick: 1/3 house teleport (tablet/cape/spell highlighted) → 2/3 in house (instance; the house object whose name contains fairy/spirit/nexus/jewellery is highlighted, UNVERIFIED names) → walk. Arriving in the house never ends a leg; leaving it does. Other teleports only end a leg if they land 30+ tiles closer to the destination. The route is locked once the trip starts, until the step changes.
- Debug: "leg … · in house … · teleported … · locked" and "leg reason: …".
## Rumour loadout (v0.8.0)
One set of items for all of the hunter's rumours, withdrawn once:
- **Travel**: the solver picks one route per rumour and one way back, fewest item groups first (each extra group = 15 cost points), then quickest. Shared enablers win, e.g. one house teleport covers the house fairy ring (any code), the nexus (Ourania Cave) and the way back (house fairy ring A J P). Spell routes need a law rune (rest of the runes unchecked). Only routes you own items for are considered.
- **Huntsman's kit**, carried, with every rumour's traps (box traps, net traps, butterfly net) in it; knife, logs, jar and runes carried (runes in the pouch).
- Kit tab LOADOUT card: every item with ok / in kit / in bank / missing, an inventory slot estimate, and one line per rumour saying how the loadout gets there.
- Bank-first: in the burrows, before taking a rumour, the guide asks once for anything in the loadout that's in your bank (or in a kit you're not carrying). "Set off anyway" stops it for the session (Stats → Reset session re-arms it).
- Items in a carried kit count as available; at the area the guide says "Take X out of your huntsman's kit" (kit highlighted) before hunting. Back in the burrows it reminds you to put loose traps back in the kit.
- The rumour's route always comes from the loadout, so it never asks for something outside it.
- Bank button: the loadout (travel items, kit, every rumour's hunting items) plus house-teleport items.
## Nice to have (v0.9.0)
From the wiki's "Hunters' Rumours/Strategies" page (fetched 2026-10-07). Shown in the LOADOUT card only when owned (carried, bank or kit cache), never required, and added to the bank filter. All IDs found in the gameval ItemID table:
- Every rumour: Bonecrusher (13116) or Bonecrusher necklace (22986, worn; the plain one is hidden if you own the necklace), meat pouch (29295/29297 + open), fur pouch (29299/29301/29303 + open), ring of endurance (24736, worn).
- Box traps (chinchompas): Hunter's spear (29305, wielded).
- Deadfalls (sabre-toothed kebbit): any axe, log basket (28140/28142), Kandarin headgear (13137-13140, worn).
- Herbiboar: herb sack / silklined herb sack (13226, 24478, 33135, 33137); magic secateurs were already optional.
Skipped: rope + small fishing net (you use net traps), cattleprod (Telekinetic Grab), teasing stick / tinderbox / chisel / noose wand / ring of pursuit / bird snares / falconry (no Teco rumour uses them), teak logs + celastrus bark (tick manipulation), potions, Rada's blessing, regen bracelet, Horn of Plenty (not hunting kit), max/hunter cape (your cape slot is free for a construction cape), master scroll book (not used by any loadout route), graceful and the guild outfit (already worn and shown under WEAR).
## v0.11.0
- Guide tab "YOUR <RUMOUR> TIMES": best/average and past runs fastest first (catches/pity, time), plus "This run (m:ss) would be Nth", updated every second.
- Rainbow: from the rare part landing in the inventory until a teleport towards the guild, entering the house, or reaching the guild; full-screen tint at 20% opacity, hue changes smoothly over 4 s (no strobe). Setting "Rainbow when the part drops".
- Goats: Telekinetic Grab cast on a goat is read from the menu click ("Telekinetic Grab -> goat", UNVERIFIED target wording); that goat turns the "Grabbed goat colour" until the pit count rises (oldest grab lands first), it despawns, or 20 ticks pass.
- Only the nearest N grabbable goats are highlighted (setting "Goats to highlight", default 5).
- Numbers: each goat shows the pit slot it fills: grabbed goats first in cast order, then targets nearest first, counting on from the pit's current count.
## v0.12.0
- Grabbed goats are tracked by NPC index. When the pit count rises, the grabbed goat nearest the pit is the one that landed (v0.11.0 wrongly dropped the oldest grab, so a second, closer goat landing first turned the first one blue). Dropped also on despawn or after 25 ticks.
- Numbers: goats in flight keep their slots (nearest the pit lands first); every other highlighted goat shows the same next free slot. All labels white, same font.
- Stand tile: the walkable tile 1-2 tiles from the pit (collision map) from which the most goats are grabbable (range + 75° fan), ties to goats nearer the pit, then to the tile nearer you. It only moves when a tile reaches more goats, or the same number at least 4 tiles nearer the pit in total. Drawn green.
- Extra features (setting, default off): right-click the rumour times on the Guide or Stats tab → "Edit times in Notepad". Writes .runelite\easy-rumours\times.txt (one run per line) and times.bak.txt, opens Notepad, and reloads after Notepad closes if the file was saved. Unreadable lines are skipped (and reported); if no line can be read nothing changes.
## v0.13.0
- Rare part on the floor (inventory full): any rumour's rare part is tracked from ground item events; if this rumour's part lies within 15 tiles and isn't carried, the guide says "Pick up the <part> from the floor" (plus "inventory full: drop or bank something first" at 28 items), the tile gets a thick gold outline and the rainbow runs. The rumour only counts as done once the part is in the inventory.
- Falconry (spotted/dark/dashing kebbit, Piscatoris falconry area; Teco gives none, other hunters do). Steps: stile (blue) with Matthias next (orange) → Matthias (NpcID HUNTING_NPC_FALCONER 1340 / _NOBIRD 1341) → with a falcon on your glove (ItemID FALCON_ON_GLOVES 10024) the kebbits → a falcon sitting on a catch (HUNTING_FALCON_ONSPEEDY/ONSPEEDY2/ONSILENT) is highlighted to retrieve; glove without falcon (FALCON_GLOVES 10023) = wait.
- UNVERIFIED: no stile object ID for the falconry fence was found in the API, so the stile is found by name "Stile" with "Climb-over"; "inside" = you're nearer the hunting spots than the stile is.
## v0.14.0
- Restock: away from the guild, when a needed item (this rumour's, or its route's, e.g. the last necklace of passage charge) is neither carried nor in a carried kit but is in the bank, the step becomes "Restock": the quickest bank teleport you're carrying (crafting cape → Crafting Guild; ring of dueling → Ferox Enclave; house → jewellery box Ferox Enclave (basic+), Crafting Guild (fancy+), Grand Exchange (ornate); quetzal whistle → the burrows bank), locked for the trip. Near a bank (objects named "...bank..." with Bank/Use, or NPCs with a Bank option; UNVERIFIED names) it says "Withdraw X" and highlights the bank and, once open, the items. Then the normal route resumes.
- Kit tab LOADOUT card: "Nearest bank from anywhere: …".
- UNVERIFIED: how far each bank teleport lands from its bank.
- Goat stand tile: locked until fewer than 2 goats are grabbable from it for 3 ticks in a row.
## v0.14.1
- Goat stand tile: only the ring of tiles one out round the pit (corners and edges), per an in-game screenshot.
- Goat field: only goats inside GOAT_AREA (RumoursPlugin, tile offsets from the pit centre) are highlighted, numbered or used for the stand tile. ESTIMATED from a tilted in-game screenshot; the fenced pen to the west is outside. With the debug box on, the outline is drawn in red on the ground so it can be checked; edit GOAT_AREA to adjust.
## v0.15.0 — Goats settings section
All live (no restart): goats to highlight (5), grab range (10), fan width (75°), goat highlight style (hull / outline / tile), target colour, grabbed colour, grabbed mark time (15 s), show pit numbers, number colour, number size (16), show where to stand, stand tile colour, move tile below (2 goats), move tile after (3 ticks), show the stand ring + ring colour, only goats in the field, show the field outline (on) + outline colour, highlight pit steps (spikes/line/clear).