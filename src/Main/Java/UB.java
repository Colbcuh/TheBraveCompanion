package Main.Java;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class UB extends JPanel {

    private static final Color BG_DARK    = new Color(44, 47, 51);
    private static final Color BG_MID     = new Color(54, 57, 63);

    private static final Color ACCENT       = new Color(40, 130, 210);
    private static final Color TEXT_PRIMARY = new Color(235, 235, 235);
    private static final Color TEXT_MUTED   = new Color(170, 170, 170);

    private static final String DDRAGON_BASE = "https://ddragon.leagueoflegends.com";

    private final RosterModel rosterModel;

    private final JComboBox<String> categorySelect = new JComboBox<>(new String[]{"Standard", "Alternate", "Legacy / Event"});
    private final JComboBox<String> modeSelect = new JComboBox<>();
    private final JComboBox<String> playerSelect = new JComboBox<>();

    private final JLabel statusLabel = new JLabel("Please choose a game mode.");
    private final RoundedButton generateBtn = new RoundedButton("Generate");

    private final JLabel champNameLabel = new JLabel(" ");
    private final JLabel splashLabel = new JLabel();

    private final JLabel[] spellIcons = new JLabel[]{new JLabel(), new JLabel()};
    private final JLabel[] itemIcons  = new JLabel[]{new JLabel(), new JLabel(), new JLabel(), new JLabel(), new JLabel(), new JLabel()};
    private final JLabel keystoneIcon = new JLabel();
    private final JLabel[] runeIcons  = new JLabel[]{new JLabel(), new JLabel(), new JLabel(), new JLabel(), new JLabel()};

    private final SecureRandom rng = new SecureRandom();
    private final DDragonPools pools = DDragonPools.get();

    private String selectedMode = null;
    private Integer selectedMapId = null;

    private final List<String> players = new ArrayList<>();
    private final Map<String, Loadout> loadouts = new LinkedHashMap<>();

    private final Map<String, ImageIcon> iconCache = new ConcurrentHashMap<>();

    private static final Set<String> SUPPORT_ITEM_NAME_KEYS = new HashSet<String>(Arrays.asList(
            "bloodsong",
            "celestialopposition",
            "dreammaker",
            "solsticesleigh",
            "zazzaksrealmspike",
            "worldatlas",
            "runiccompass",
            "bountyofworlds",
            "watchfulwardstone",
            "vigilantwardstone"
    ));

    // Cache spells-per-mode after DDragon loads (minor but free speed)
    private final Map<String, List<DDragonPools.SummonerSpell>> spellsByModeCache = new ConcurrentHashMap<>();
    private volatile String spellsCacheVersion = null;

    public UB() {
        this(null);
    }

    public UB(RosterModel rosterModel) {
        this.rosterModel = rosterModel;

        setLayout(new BorderLayout());
        setBackground(BG_DARK);
        setBorder(new EmptyBorder(20, 20, 20, 20));

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        installDrilldown();
        installPlayerSelection();
        installGenerateButton();

        refreshFromRoster();
        updateGenerateGate();
    }

    public void refreshFromRoster() {
        if (rosterModel == null) return;
        List<String> newPlayers = readPlayersFromRosterModel();
        setPlayers(newPlayers);
    }

    public void setPlayers(List<String> newPlayers) {
        players.clear();
        loadouts.clear();

        if (newPlayers != null) {
            for (String p : newPlayers) {
                if (p != null) {
                    String t = p.trim();
                    if (!t.isEmpty()) players.add(t);
                }
            }
        }

        playerSelect.removeAllItems();
        for (String p : players) playerSelect.addItem(p);
        if (!players.isEmpty()) playerSelect.setSelectedIndex(0);

        clearDisplay();
        if (selectedMode == null || selectedMapId == null) {
            statusLabel.setText("Please choose a game mode.");
        } else {
            statusLabel.setText(players.isEmpty() ? "No players found. Add names in CRT tab first." : "Roster updated. Please generate.");
        }
        updateGenerateGate();
    }

    @SuppressWarnings("unchecked")
    private List<String> readPlayersFromRosterModel() {
        try {
            Object v = rosterModel.getClass().getMethod("getPlayers").invoke(rosterModel);
            if (v instanceof List) return (List<String>) v;
        } catch (Exception ignored) {}

        try {
            Object v = rosterModel.getClass().getMethod("getNames").invoke(rosterModel);
            if (v instanceof List) return (List<String>) v;
        } catch (Exception ignored) {}

        try {
            Object v = rosterModel.getClass().getMethod("getPlayerNames").invoke(rosterModel);
            if (v instanceof List) return (List<String>) v;
        } catch (Exception ignored) {}

        return Collections.emptyList();
    }

    // ---------------- UI BUILD ----------------

    private JPanel buildTopBar() {
        JPanel top = new JPanel(new GridBagLayout());
        top.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 10);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; top.add(label("Category:"), c);
        c.gridx = 1; top.add(categorySelect, c);

        c.gridx = 2; top.add(label("Mode:"), c);
        c.gridx = 3; top.add(modeSelect, c);

        c.gridx = 4; top.add(label("Player:"), c);
        c.gridx = 5; c.insets = new Insets(0, 0, 0, 0); top.add(playerSelect, c);

        categorySelect.setFocusable(false);
        modeSelect.setFocusable(false);
        playerSelect.setFocusable(false);

        return top;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        champNameLabel.setForeground(TEXT_PRIMARY);
        champNameLabel.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 18));
        champNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel splashWrap = new JPanel(new BorderLayout());
        splashWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        splashWrap.setOpaque(true);
        splashWrap.setBackground(BG_MID);
        splashWrap.setBorder(new EmptyBorder(8, 8, 8, 8));
        splashWrap.setPreferredSize(new Dimension(780, 260));
        splashWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));

        splashLabel.setHorizontalAlignment(SwingConstants.CENTER);
        splashLabel.setOpaque(false);
        splashWrap.add(splashLabel, BorderLayout.CENTER);

        JPanel rows = new JPanel(new GridBagLayout());
        rows.setOpaque(false);
        rows.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints r = new GridBagConstraints();
        r.gridy = 0;
        r.insets = new Insets(0, 0, 0, 25);
        r.anchor = GridBagConstraints.NORTHWEST;

        r.gridx = 0;
        rows.add(buildIconSection("Spells", spellIcons, 40), r);

        r.gridx = 1;
        rows.add(buildIconSection("Items", itemIcons, 40), r);

        r.gridx = 2;
        rows.add(buildRunesSection(), r);

        center.add(champNameLabel);
        center.add(Box.createVerticalStrut(10));
        center.add(splashWrap);
        center.add(Box.createVerticalStrut(14));
        center.add(rows);

        return center;
    }

    private JPanel buildBottom() {
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(new EmptyBorder(16, 0, 0, 0));

        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        generateBtn.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 15));
        generateBtn.setForeground(TEXT_PRIMARY);
        generateBtn.setBackground(ACCENT);
        generateBtn.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        generateBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        generateBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        generateBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e){ generateBtn.setBackground(ACCENT.brighter()); }
            @Override public void mouseExited (MouseEvent e){ generateBtn.setBackground(ACCENT); }
        });

        bottom.add(statusLabel);
        bottom.add(Box.createVerticalStrut(10));
        bottom.add(generateBtn);

        return bottom;
    }

    private JPanel buildIconSection(String title, JLabel[] icons, int size) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel t = new JLabel(title);
        t.setForeground(TEXT_PRIMARY);
        t.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 13));
        t.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);

        for (JLabel l : icons) {
            prepIconLabel(l);
            l.setPreferredSize(new Dimension(size, size));
            row.add(l);
        }

        p.add(t);
        p.add(Box.createVerticalStrut(6));
        p.add(row);

        return p;
    }

    private JPanel buildRunesSection() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel t = new JLabel("Runes");
        t.setForeground(TEXT_PRIMARY);
        t.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 13));
        t.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);

        prepIconLabel(keystoneIcon);
        keystoneIcon.setPreferredSize(new Dimension(44, 44));
        row.add(keystoneIcon);

        for (JLabel l : runeIcons) {
            prepIconLabel(l);
            l.setPreferredSize(new Dimension(34, 34));
            row.add(l);
        }

        p.add(t);
        p.add(Box.createVerticalStrut(6));
        p.add(row);

        return p;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_PRIMARY);
        l.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 13));
        return l;
    }

    private void prepIconLabel(JLabel l) {
        l.setOpaque(false);
        l.setHorizontalAlignment(SwingConstants.CENTER);
    }

    // ---------------- Wiring ----------------

    private void installDrilldown() {
        rebuildModeOptions((String) categorySelect.getSelectedItem());
        modeSelect.setSelectedItem(null);

        categorySelect.addActionListener(e -> {
            rebuildModeOptions((String) categorySelect.getSelectedItem());
            modeSelect.setSelectedItem(null);
            selectedMode = null;
            selectedMapId = null;
            statusLabel.setText("Please choose a game mode.");
            updateGenerateGate();
        });

        modeSelect.addActionListener(e -> {
            Object v = modeSelect.getSelectedItem();
            selectedMode = (v == null) ? null : v.toString().trim().toUpperCase(Locale.ROOT);
            selectedMapId = mapIdForMode(selectedMode);

            if (selectedMode == null || selectedMapId == null) {
                statusLabel.setText("Please choose a game mode.");
            } else {
                statusLabel.setText(players.isEmpty() ? "No players found. Add names in CRT tab first." : "Ready.");
            }
            updateGenerateGate();
        });
    }

    private void installPlayerSelection() {
        playerSelect.addActionListener(e -> {
            String p = (String) playerSelect.getSelectedItem();
            if (p != null) showPlayer(p);
        });
    }

    private void installGenerateButton() {
        generateBtn.addActionListener(e -> generate());
    }

    private void updateGenerateGate() {
        boolean modeChosen = (selectedMode != null && selectedMapId != null);
        boolean hasPlayers = !players.isEmpty();
        generateBtn.setEnabled(modeChosen && hasPlayers);

        if (!modeChosen) statusLabel.setText("Please choose a game mode.");
        else if (!hasPlayers) statusLabel.setText("No players found. Add names in CRT tab first.");
    }

    // ---------------- Mode options ----------------

    private void rebuildModeOptions(String category) {
        modeSelect.removeAllItems();

        if (category == null) category = "Standard";

        if ("Standard".equalsIgnoreCase(category)) {
            modeSelect.addItem("CLASSIC");
            modeSelect.addItem("ARAM");
        } else if ("Alternate".equalsIgnoreCase(category)) {
            modeSelect.addItem("URF");
            modeSelect.addItem("ONEFORALL");
            modeSelect.addItem("ULTBOOK");
            modeSelect.addItem("NEXUSBLITZ");
        } else {
            // Legacy/Event intentionally empty for now
        }
    }

    private Integer mapIdForMode(String mode) {
        if (mode == null) return null;
        switch (mode.toUpperCase(Locale.ROOT)) {
            case "ARAM":       return 12;
            case "NEXUSBLITZ": return 21;
            case "CLASSIC":
            case "URF":
            case "ONEFORALL":
            case "ULTBOOK":
                return 11;
            default:
                return null;
        }
    }

    // ---------------- Generation ----------------

    private void generate() {
        if (selectedMode == null || selectedMapId == null) {
            statusLabel.setText("Please choose a game mode.");
            updateGenerateGate();
            return;
        }
        if (players.isEmpty()) {
            statusLabel.setText("No players found. Add names in CRT tab first.");
            updateGenerateGate();
            return;
        }

        generateBtn.setEnabled(false);
        statusLabel.setText("Generating...");

        final String mode = selectedMode;
        final int mapId = selectedMapId;

        SwingWorker<Map<String, Loadout>, Void> worker = new SwingWorker<Map<String, Loadout>, Void>() {
            @Override
            protected Map<String, Loadout> doInBackground() throws Exception {
                pools.ensureLoaded();
                String version = pools.getVersion();

                // reset spell cache on version change
                if (spellsCacheVersion == null || !spellsCacheVersion.equals(version)) {
                    spellsByModeCache.clear();
                    spellsCacheVersion = version;
                }

                List<DDragonPools.Champion> champs = pools.getChampions();

                List<DDragonPools.SummonerSpell> spells = spellsByModeCache.get(mode);
                if (spells == null) {
                    spells = filterSpellsForMode(pools.getSummonerSpells(), mode);
                    spellsByModeCache.put(mode, spells);
                }

                if (champs.isEmpty()) throw new IllegalStateException("No champions loaded.");
                if (spells.size() < 2) throw new IllegalStateException("Not enough spells for mode: " + mode);

                // Precomputed pools per mapId (fast)
                DDragonPools.ItemPools itemPools = pools.getItemPoolsForMap(mapId);

                List<DDragonPools.Item> finishedNonJungle = itemPools.finishedNonJungle;
                List<DDragonPools.Item> finishedJungle = itemPools.finishedJungle;

                if (finishedNonJungle.size() < 6) {
                    throw new IllegalStateException("Not enough finished items for mapId: " + mapId);
                }

                List<DDragonPools.RuneTree> runeTrees = pools.getRuneTrees();
                if (runeTrees == null || runeTrees.size() < 2) {
                    throw new IllegalStateException("Rune trees not available.");
                }

                LinkedHashMap<String, Loadout> out = new LinkedHashMap<>();
                for (String player : players) {
                    DDragonPools.Champion champ = champs.get(rng.nextInt(champs.size()));
                    List<DDragonPools.SummonerSpell> pickedSpells = pickTwoDistinct(spells);

                    boolean hasSmite = containsSmite(pickedSpells);

                    // Enforce:
                    // - max 1 jungle item, only if Smite is rolled
                    // - max 1 support item total
                    List<DDragonPools.Item> pickedItems;

                    if (hasSmite && !finishedJungle.isEmpty()) {
                        DDragonPools.Item jungle = finishedJungle.get(rng.nextInt(finishedJungle.size()));

                        int supportRemaining = isSupportItem(jungle) ? 0 : 1;

                        List<DDragonPools.Item> restPool = removeById(finishedNonJungle, jungle.id);
                        List<DDragonPools.Item> rest = pickDistinctWithSupportLimit(restPool, 5, supportRemaining);

                        pickedItems = new ArrayList<>(6);
                        pickedItems.add(jungle);
                        pickedItems.addAll(rest);
                        Collections.shuffle(pickedItems, rng);

                    } else {
                        pickedItems = pickDistinctWithSupportLimit(finishedNonJungle, 6, 1);
                    }

                    RunePage runePage = rollRunePage(runeTrees);

                    Loadout l = new Loadout(player, champ, pickedSpells, pickedItems, runePage, version);
                    out.put(player, l);
                }
                return out;
            }

            @Override
            protected void done() {
                try {
                    loadouts.clear();
                    loadouts.putAll(get());

                    statusLabel.setText("Generated for " + loadouts.size() + " player(s).");

                    String p = (String) playerSelect.getSelectedItem();
                    if (p == null && !players.isEmpty()) p = players.get(0);
                    if (p != null) showPlayer(p);

                    // Warm image cache in the background so the UI remains fast
                    warmImagesAsync(p);

                } catch (Exception ex) {
                    statusLabel.setText("Generation failed.");
                    JOptionPane.showMessageDialog(UB.this,
                            "Generation failed:\n\n" + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    updateGenerateGate();
                }
            }
        };

        worker.execute();
    }

    private void warmImagesAsync(String alreadyShownPlayer) {
        SwingWorker<Void, Void> warm = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                for (Map.Entry<String, Loadout> e : loadouts.entrySet()) {
                    String player = e.getKey();
                    Loadout l = e.getValue();
                    if (alreadyShownPlayer != null && alreadyShownPlayer.equals(player)) continue;
                    try {
                        preload(l);
                    } catch (Throwable ignored) {}
                }
                return null;
            }
        };
        warm.execute();
    }

    private void showPlayer(String player) {
        Loadout l = loadouts.get(player);
        if (l == null) {
            clearDisplay();
            return;
        }

        champNameLabel.setText(l.player + " — " + l.champion.name);
        splashLabel.setIcon(loadScaledKeepAspect(l.championSplashUrl(), 780, 250));

        spellIcons[0].setIcon(loadSquare(l.spellIconUrl(0), 40));
        spellIcons[1].setIcon(loadSquare(l.spellIconUrl(1), 40));

        for (int i = 0; i < 6; i++) itemIcons[i].setIcon(loadSquare(l.itemIconUrl(i), 40));

        if (l.runes != null && l.runes.keystone != null) keystoneIcon.setIcon(loadSquare(l.runes.keystone.iconUrl, 44));
        else keystoneIcon.setIcon(null);

        int idx = 0;
        for (DDragonPools.Rune r : l.runes.primaryMinors) {
            if (idx < runeIcons.length) runeIcons[idx++].setIcon(loadSquare(r.iconUrl, 34));
        }
        for (DDragonPools.Rune r : l.runes.secondaryMinors) {
            if (idx < runeIcons.length) runeIcons[idx++].setIcon(loadSquare(r.iconUrl, 34));
        }
        while (idx < runeIcons.length) runeIcons[idx++].setIcon(null);

        repaint();
    }

    private void clearDisplay() {
        champNameLabel.setText(" ");
        splashLabel.setIcon(null);
        for (JLabel l : spellIcons) l.setIcon(null);
        for (JLabel l : itemIcons) l.setIcon(null);
        keystoneIcon.setIcon(null);
        for (JLabel l : runeIcons) l.setIcon(null);
    }

    // ---------------- Spells/items helpers ----------------

    private List<DDragonPools.SummonerSpell> filterSpellsForMode(List<DDragonPools.SummonerSpell> all, String mode) {
        if (all == null) return Collections.emptyList();
        String m = (mode == null) ? "" : mode.trim().toUpperCase(Locale.ROOT);

        ArrayList<DDragonPools.SummonerSpell> out = new ArrayList<>();
        for (DDragonPools.SummonerSpell sp : all) {
            if (sp == null) continue;

            if (sp.modes == null || sp.modes.isEmpty()) {
                out.add(sp);
                continue;
            }
            for (String mm : sp.modes) {
                if (mm != null && mm.trim().toUpperCase(Locale.ROOT).equals(m)) {
                    out.add(sp);
                    break;
                }
            }
        }
        return out;
    }

    private boolean containsSmite(List<DDragonPools.SummonerSpell> spells) {
        if (spells == null) return false;
        for (DDragonPools.SummonerSpell sp : spells) {
            if (sp == null) continue;
            if ("SummonerSmite".equalsIgnoreCase(sp.id)) return true;
            if ("Smite".equalsIgnoreCase(sp.name)) return true;
        }
        return false;
    }

    private List<DDragonPools.SummonerSpell> pickTwoDistinct(List<DDragonPools.SummonerSpell> pool) {
        if (pool.size() < 2) return pool;

        int a = rng.nextInt(pool.size());
        int b = rng.nextInt(pool.size() - 1);
        if (b >= a) b++;

        return Arrays.asList(pool.get(a), pool.get(b));
    }

    private List<DDragonPools.Item> removeById(List<DDragonPools.Item> pool, String removeId) {
        ArrayList<DDragonPools.Item> out = new ArrayList<>();
        for (DDragonPools.Item it : pool) {
            if (it == null || it.id == null) continue;
            if (!it.id.equals(removeId)) out.add(it);
        }
        return out;
    }

    // ---------------- Support item limiting ----------------

    private String normalizeKey(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = Character.toLowerCase(s.charAt(i));
            if (Character.isLetterOrDigit(ch)) sb.append(ch);
        }
        return sb.toString();
    }

    private boolean isSupportItem(DDragonPools.Item it) {
        if (it == null) return false;

        if (it.tags != null) {
            for (String t : it.tags) {
                if (t == null) continue;
                if ("GoldPer".equalsIgnoreCase(t)) return true;
                if ("Support".equalsIgnoreCase(t)) return true;
            }
        }

        String key = normalizeKey(it.name);
        if (SUPPORT_ITEM_NAME_KEYS.contains(key)) return true;

        return key.contains("wardstone");
    }

    private List<DDragonPools.Item> pickDistinctWithSupportLimit(List<DDragonPools.Item> pool, int count, int maxSupport) {
        if (pool == null) return Collections.emptyList();

        ArrayList<DDragonPools.Item> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, rng);

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ArrayList<DDragonPools.Item> out = new ArrayList<>(count);

        int supportCount = 0;

        for (DDragonPools.Item it : copy) {
            if (it == null || it.id == null) continue;
            if (!seen.add(it.id)) continue;

            boolean support = isSupportItem(it);
            if (support && supportCount >= maxSupport) continue;

            out.add(it);
            if (support) supportCount++;

            if (out.size() == count) break;
        }

        if (out.size() < count) {
            throw new IllegalStateException(
                    "Not enough items after support filtering (need " + count + ", got " + out.size() + ")."
            );
        }

        return out;
    }

    // ---------------- Rune generation ----------------

    private RunePage rollRunePage(List<DDragonPools.RuneTree> trees) {
        DDragonPools.RuneTree primary = trees.get(rng.nextInt(trees.size()));
        DDragonPools.RuneTree secondary = trees.get(rng.nextInt(trees.size()));

        int guard = 0;
        while (secondary.id == primary.id && guard++ < 10) {
            secondary = trees.get(rng.nextInt(trees.size()));
        }

        DDragonPools.Rune keystone = pickRuneFromSlot(primary, 0);

        List<DDragonPools.Rune> primaryMinors = new ArrayList<>(3);
        primaryMinors.add(pickRuneFromSlot(primary, 1));
        primaryMinors.add(pickRuneFromSlot(primary, 2));
        primaryMinors.add(pickRuneFromSlot(primary, 3));

        List<Integer> slots = Arrays.asList(1, 2, 3);
        Collections.shuffle(slots, rng);

        List<DDragonPools.Rune> secondaryMinors = new ArrayList<>(2);
        secondaryMinors.add(pickRuneFromSlot(secondary, slots.get(0)));
        secondaryMinors.add(pickRuneFromSlot(secondary, slots.get(1)));

        return new RunePage(primary, secondary, keystone, primaryMinors, secondaryMinors);
    }

    private DDragonPools.Rune pickRuneFromSlot(DDragonPools.RuneTree tree, int slotIndex) {
        if (tree == null || tree.slots == null || tree.slots.size() <= slotIndex) return null;
        DDragonPools.RuneSlot slot = tree.slots.get(slotIndex);
        if (slot == null || slot.runes == null || slot.runes.isEmpty()) return null;
        return slot.runes.get(rng.nextInt(slot.runes.size()));
    }

    // ---------------- Icon loading ----------------

    private void preload(Loadout l) {
        loadScaledKeepAspect(l.championSplashUrl(), 780, 250);
        loadSquare(l.spellIconUrl(0), 40);
        loadSquare(l.spellIconUrl(1), 40);
        for (int i = 0; i < 6; i++) loadSquare(l.itemIconUrl(i), 40);

        if (l.runes != null && l.runes.keystone != null) loadSquare(l.runes.keystone.iconUrl, 44);
        for (DDragonPools.Rune r : l.runes.primaryMinors) if (r != null) loadSquare(r.iconUrl, 34);
        for (DDragonPools.Rune r : l.runes.secondaryMinors) if (r != null) loadSquare(r.iconUrl, 34);
    }

    private ImageIcon loadSquare(String url, int size) {
        if (url == null || url.trim().isEmpty()) return null;

        String key = url + "|sq|" + size;
        ImageIcon cached = iconCache.get(key);
        if (cached != null) return cached;

        try {
            BufferedImage src = ImageIO.read(new URL(url));
            if (src == null) return null;

            BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = out.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(src, 0, 0, size, size, null);
            g2.dispose();

            ImageIcon icon = new ImageIcon(out);
            iconCache.put(key, icon);
            return icon;
        } catch (IOException e) {
            return null;
        }
    }

    private ImageIcon loadScaledKeepAspect(String url, int maxW, int maxH) {
        if (url == null || url.trim().isEmpty()) return null;

        String key = url + "|ka|" + maxW + "x" + maxH;
        ImageIcon cached = iconCache.get(key);
        if (cached != null) return cached;

        try {
            BufferedImage src = ImageIO.read(new URL(url));
            if (src == null) return null;

            int sw = src.getWidth();
            int sh = src.getHeight();
            double scale = Math.min((double) maxW / sw, (double) maxH / sh);

            int tw = Math.max(1, (int) Math.round(sw * scale));
            int th = Math.max(1, (int) Math.round(sh * scale));

            BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = out.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(src, 0, 0, tw, th, null);
            g2.dispose();

            ImageIcon icon = new ImageIcon(out);
            iconCache.put(key, icon);
            return icon;
        } catch (IOException e) {
            return null;
        }
    }

    // ---------------- Loadout model ----------------

    private static final class Loadout {
        final String player;
        final DDragonPools.Champion champion;
        final List<DDragonPools.SummonerSpell> spells;
        final List<DDragonPools.Item> items;
        final RunePage runes;
        final String version;

        Loadout(String player,
                DDragonPools.Champion champion,
                List<DDragonPools.SummonerSpell> spells,
                List<DDragonPools.Item> items,
                RunePage runes,
                String version) {
            this.player = player;
            this.champion = champion;
            this.spells = spells;
            this.items = items;
            this.runes = runes;
            this.version = version;
        }

        String championSplashUrl() {
            return DDRAGON_BASE + "/cdn/img/champion/splash/" + champion.id + "_0.jpg";
        }

        String spellIconUrl(int idx) {
            if (spells == null || spells.size() <= idx) return "";
            DDragonPools.SummonerSpell sp = spells.get(idx);
            if (sp == null || sp.image == null || sp.image.full == null) return "";
            return DDRAGON_BASE + "/cdn/" + version + "/img/spell/" + sp.image.full;
        }

        String itemIconUrl(int idx) {
            if (items == null || items.size() <= idx) return "";
            DDragonPools.Item it = items.get(idx);
            if (it == null || it.image == null || it.image.full == null) return "";
            return DDRAGON_BASE + "/cdn/" + version + "/img/item/" + it.image.full;
        }
    }

    // ---------------- RoundedButton ----------------

    private static class RoundedButton extends JButton {
        private final int arc = 18;

        public RoundedButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, 80));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
        }
    }
}
