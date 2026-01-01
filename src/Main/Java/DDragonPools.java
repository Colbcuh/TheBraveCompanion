package Main.Java;

import com.google.gson.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data Dragon pools loader (runtime pull; no local asset downloads required).
 *
 * Adds:
 * 1) Disk JSON cache under %APPDATA%\TBC\cache\ddragon\... (JSON only)
 * 2) Precomputed eligible item pools per mapId to speed generation
 *
 * Keeps:
 * - Nested rune types used by RunePage / UB
 * - Existing public methods (backward compatible)
 */
public final class DDragonPools {

    private static final String DDRAGON_BASE = "https://ddragon.leagueoflegends.com";
    private static final String DEFAULT_LANG = "en_US";

    private static volatile DDragonPools INSTANCE;

    public static DDragonPools get() {
        if (INSTANCE == null) {
            synchronized (DDragonPools.class) {
                if (INSTANCE == null) INSTANCE = new DDragonPools(DEFAULT_LANG);
            }
        }
        return INSTANCE;
    }

    private final Gson gson = new Gson();
    private final SecureRandom rng = new SecureRandom();

    private final String locale;

    private volatile boolean loaded = false;
    private String version = null;

    private final Map<String, Champion> championsById = new LinkedHashMap<>();
    private final Map<String, SummonerSpell> spellsById = new LinkedHashMap<>();
    private final Map<String, Item> itemsById = new LinkedHashMap<>();

    // Restored rune pool
    private List<RuneTree> runeTrees = Collections.emptyList();

    // Precomputed item pools by mapId (built once, reused)
    private final Map<Integer, ItemPools> itemPoolsByMap = new ConcurrentHashMap<>();

    public DDragonPools() {
        this(DEFAULT_LANG);
    }

    public DDragonPools(String locale) {
        this.locale = (locale == null || locale.trim().isEmpty()) ? DEFAULT_LANG : locale.trim();
    }

    public synchronized void ensureLoaded() throws IOException {
        if (loaded) return;
        refresh();
    }

    public synchronized void refresh() throws IOException {
        this.version = fetchLatestVersion();

        // Clear computed pools when version changes
        itemPoolsByMap.clear();

        loadChampions();
        loadSummonerSpells();
        loadItems();
        loadRunesReforged();

        this.loaded = true;
    }

    public String getVersion() throws IOException {
        ensureLoaded();
        return version;
    }

    // -------------------------
    // Public accessors
    // -------------------------

    public List<Champion> getChampions() throws IOException {
        ensureLoaded();
        return new ArrayList<>(championsById.values());
    }

    public List<SummonerSpell> getSummonerSpells() throws IOException {
        ensureLoaded();
        return new ArrayList<>(spellsById.values());
    }

    public List<Item> getAllItems() throws IOException {
        ensureLoaded();
        return new ArrayList<>(itemsById.values());
    }

    public List<RuneTree> getRuneTrees() throws IOException {
        ensureLoaded();
        return runeTrees;
    }

    // -------------------------
    // New: Precomputed item pools
    // -------------------------

    public ItemPools getItemPoolsForMap(int mapId) throws IOException {
        ensureLoaded();
        ItemPools existing = itemPoolsByMap.get(mapId);
        if (existing != null) return existing;

        synchronized (itemPoolsByMap) {
            existing = itemPoolsByMap.get(mapId);
            if (existing != null) return existing;

            ItemPools built = buildItemPoolsForMap(mapId);
            itemPoolsByMap.put(mapId, built);
            return built;
        }
    }

    private ItemPools buildItemPoolsForMap(int mapId) {
        String mapKey = String.valueOf(mapId);

        List<Item> finishedNonJungle = new ArrayList<>();
        List<Item> finishedJungle = new ArrayList<>();
        List<Item> finishedAll = new ArrayList<>();

        for (Item it : itemsById.values()) {
            if (it == null) continue;

            if (!isAllowedOnMap(it, mapKey)) continue;
            if (!isPurchasableAndInStore(it)) continue;
            if (!isFinishedItem(it)) continue;
            if (isExcludedCategory(it)) continue;
            if (isChampionLocked(it)) continue;

            finishedAll.add(it);

            if (isJungleItem(it)) finishedJungle.add(it);
            else finishedNonJungle.add(it);
        }

        return new ItemPools(
                Collections.unmodifiableList(finishedAll),
                Collections.unmodifiableList(finishedNonJungle),
                Collections.unmodifiableList(finishedJungle)
        );
    }

    public static final class ItemPools {
        public final List<Item> finishedAll;        // finished items allowed on this map
        public final List<Item> finishedNonJungle;  // finished items excluding jungle-tagged
        public final List<Item> finishedJungle;     // finished jungle-tagged items

        ItemPools(List<Item> finishedAll, List<Item> finishedNonJungle, List<Item> finishedJungle) {
            this.finishedAll = finishedAll;
            this.finishedNonJungle = finishedNonJungle;
            this.finishedJungle = finishedJungle;
        }
    }

    // -------------------------
    // Item filtering helpers (now backed by cached pools)
    // -------------------------

    /**
     * Finished items for a mapId.
     * includeJungle=false returns cached "finishedNonJungle".
     * includeJungle=true returns cached "finishedAll".
     */
    public List<Item> getFinishedItemsForMap(int mapId, boolean includeJungle) throws IOException {
        ItemPools pools = getItemPoolsForMap(mapId);
        return includeJungle ? new ArrayList<>(pools.finishedAll) : new ArrayList<>(pools.finishedNonJungle);
    }

    public List<Item> getFinishedJungleItemsForMap(int mapId) throws IOException {
        ItemPools pools = getItemPoolsForMap(mapId);
        return new ArrayList<>(pools.finishedJungle);
    }

    private boolean isAllowedOnMap(Item it, String mapKey) {
        if (it.maps == null) return false;
        Boolean ok = it.maps.get(mapKey);
        return ok != null && ok;
    }

    private boolean isPurchasableAndInStore(Item it) {
        if (it.gold == null) return false;
        if (!it.gold.purchasable) return false;
        if (it.inStore != null && !it.inStore) return false;
        return it.gold.total > 0;
    }

    private boolean isFinishedItem(Item it) {
        return it.into == null || it.into.isEmpty();
    }

    private boolean isJungleItem(Item it) {
        if (it.tags == null) return false;
        for (String t : it.tags) {
            if ("Jungle".equalsIgnoreCase(t)) return true;
        }
        return false;
    }

    private boolean isChampionLocked(Item it) {
        if (it.requiredChampion != null && !it.requiredChampion.trim().isEmpty()) return true;
        if (it.requiredAlly != null && !it.requiredAlly.trim().isEmpty()) return true;
        return false;
    }

    private boolean isExcludedCategory(Item it) {
        if (it.consumed != null && it.consumed) return true;
        if (it.hideFromAll != null && it.hideFromAll) return true;

        if (it.tags != null) {
            for (String t : it.tags) {
                if ("Consumable".equalsIgnoreCase(t)) return true;
                if ("Trinket".equalsIgnoreCase(t)) return true;
            }
        }
        return false;
    }

    // -------------------------
    // Random picks (optional)
    // -------------------------

    public Champion pickRandomChampion() throws IOException {
        ensureLoaded();
        List<Champion> list = getChampions();
        return list.get(rng.nextInt(list.size()));
    }

    // -------------------------
    // Loading implementation + Disk JSON cache
    // -------------------------

    private String fetchLatestVersion() throws IOException {
        URL url = new URL(DDRAGON_BASE + "/api/versions.json");
        String raw = httpGet(url); // always hit network for latest version
        JsonArray arr = gson.fromJson(raw, JsonArray.class);
        if (arr == null || arr.size() == 0) throw new IOException("versions.json returned empty");
        return arr.get(0).getAsString();
    }

    private void loadChampions() throws IOException {
        championsById.clear();

        String url = DDRAGON_BASE + "/cdn/" + version + "/data/" + locale + "/champion.json";
        String json = getJsonCached(new URL(url), cachePathFor("champion.json"));

        JsonObject root = gson.fromJson(json, JsonObject.class);
        JsonObject data = root.getAsJsonObject("data");

        for (Map.Entry<String, JsonElement> e : data.entrySet()) {
            JsonObject c = e.getValue().getAsJsonObject();

            Champion ch = new Champion();
            ch.id = c.get("id").getAsString();
            ch.key = c.get("key").getAsString();
            ch.name = c.get("name").getAsString();
            ch.title = c.has("title") ? c.get("title").getAsString() : "";

            if (c.has("image")) ch.image = gson.fromJson(c.get("image"), Image.class);

            championsById.put(ch.id, ch);
        }
    }

    private void loadSummonerSpells() throws IOException {
        spellsById.clear();

        String url = DDRAGON_BASE + "/cdn/" + version + "/data/" + locale + "/summoner.json";
        String json = getJsonCached(new URL(url), cachePathFor("summoner.json"));

        JsonObject root = gson.fromJson(json, JsonObject.class);
        JsonObject data = root.getAsJsonObject("data");

        for (Map.Entry<String, JsonElement> e : data.entrySet()) {
            JsonObject s = e.getValue().getAsJsonObject();

            SummonerSpell sp = new SummonerSpell();
            sp.id = s.get("id").getAsString();
            sp.name = s.get("name").getAsString();

            if (s.has("modes") && s.get("modes").isJsonArray()) {
                List<String> modes = new ArrayList<>();
                JsonArray arr = s.getAsJsonArray("modes");
                for (JsonElement el : arr) {
                    try { modes.add(el.getAsString()); } catch (Exception ignored) {}
                }
                sp.modes = modes;
            } else {
                sp.modes = Collections.emptyList();
            }

            if (s.has("image")) sp.image = gson.fromJson(s.get("image"), Image.class);

            spellsById.put(sp.id, sp);
        }
    }

    private void loadItems() throws IOException {
        itemsById.clear();

        String url = DDRAGON_BASE + "/cdn/" + version + "/data/" + locale + "/item.json";
        String json = getJsonCached(new URL(url), cachePathFor("item.json"));

        JsonObject root = gson.fromJson(json, JsonObject.class);
        JsonObject data = root.getAsJsonObject("data");

        for (Map.Entry<String, JsonElement> e : data.entrySet()) {
            String itemId = e.getKey();
            JsonObject it = e.getValue().getAsJsonObject();

            Item item = gson.fromJson(it, Item.class);
            item.id = itemId;

            if (item.tags == null) item.tags = new ArrayList<String>();
            if (item.into == null) item.into = new ArrayList<String>();
            if (item.from == null) item.from = new ArrayList<String>();
            if (item.maps == null) item.maps = new HashMap<String, Boolean>();

            itemsById.put(item.id, item);
        }
    }

    private void loadRunesReforged() throws IOException {
        String url = DDRAGON_BASE + "/cdn/" + version + "/data/" + locale + "/runesReforged.json";
        String json = getJsonCached(new URL(url), cachePathFor("runesReforged.json"));

        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
        List<RuneTree> trees = new ArrayList<>();

        for (JsonElement treeEl : arr) {
            JsonObject treeObj = treeEl.getAsJsonObject();

            RuneTree tree = new RuneTree();
            tree.id = treeObj.get("id").getAsInt();
            tree.key = treeObj.has("key") ? treeObj.get("key").getAsString() : null;
            tree.name = treeObj.has("name") ? treeObj.get("name").getAsString() : null;
            tree.icon = treeObj.has("icon") ? treeObj.get("icon").getAsString() : null;
            tree.iconUrl = (tree.icon == null) ? null : (DDRAGON_BASE + "/cdn/img/" + tree.icon);

            List<RuneSlot> slotsOut = new ArrayList<>();
            if (treeObj.has("slots") && treeObj.get("slots").isJsonArray()) {
                JsonArray slotsArr = treeObj.getAsJsonArray("slots");
                for (JsonElement slotEl : slotsArr) {
                    JsonObject slotObj = slotEl.getAsJsonObject();

                    RuneSlot slot = new RuneSlot();
                    List<Rune> runesOut = new ArrayList<>();

                    if (slotObj.has("runes") && slotObj.get("runes").isJsonArray()) {
                        JsonArray runesArr = slotObj.getAsJsonArray("runes");
                        for (JsonElement runeEl : runesArr) {
                            JsonObject rObj = runeEl.getAsJsonObject();

                            Rune r = new Rune();
                            r.id = rObj.get("id").getAsInt();
                            r.key = rObj.has("key") ? rObj.get("key").getAsString() : null;
                            r.name = rObj.has("name") ? rObj.get("name").getAsString() : null;
                            r.icon = rObj.has("icon") ? rObj.get("icon").getAsString() : null;
                            r.iconUrl = (r.icon == null) ? null : (DDRAGON_BASE + "/cdn/img/" + r.icon);

                            runesOut.add(r);
                        }
                    }

                    slot.runes = Collections.unmodifiableList(runesOut);
                    slotsOut.add(slot);
                }
            }

            tree.slots = Collections.unmodifiableList(slotsOut);
            trees.add(tree);
        }

        this.runeTrees = Collections.unmodifiableList(trees);
    }

    /**
     * Disk JSON cache:
     * %APPDATA%\TBC\cache\ddragon\<version>\<locale>\<name>.json
     */
    private Path cachePathFor(String fileName) {
        Path base;
        try {
            base = AppPaths.cacheDir().toPath();
        } catch (Throwable t) {
            base = Paths.get(System.getProperty("user.dir"), "cache");
        }
        return base.resolve("ddragon").resolve(version).resolve(locale).resolve(fileName);
    }

    private String getJsonCached(URL url, Path cacheFile) throws IOException {
        try {
            if (Files.exists(cacheFile) && Files.size(cacheFile) > 0) {
                byte[] bytes = Files.readAllBytes(cacheFile);
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // If cache read fails, fall back to download below.
        }

        // Download and store
        String raw = httpGet(url);

        try {
            Files.createDirectories(cacheFile.getParent());
            Path tmp = cacheFile.resolveSibling(cacheFile.getFileName().toString() + ".tmp");
            Files.write(tmp, raw.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // Cache write failure should never prevent app operation
            CrashLogger.log("Failed to write DDragon cache file: " + cacheFile, e);
        }

        return raw;
    }

    private String httpGet(URL url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(20_000);
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "TBC/1.0");

        int code = con.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code + " for " + url);

        StringBuilder sb = new StringBuilder(8192);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    // -------------------------
    // Models
    // -------------------------

    public static final class Champion {
        public String id;
        public String key;
        public String name;
        public String title;
        public Image image;
    }

    public static final class SummonerSpell {
        public String id;
        public String name;
        public List<String> modes;
        public Image image;
    }

    public static final class Item {
        public String id;

        public String name;
        public String plaintext;
        public Image image;

        public Gold gold;

        public List<String> tags;
        public Map<String, Boolean> maps;

        public List<String> into;
        public List<String> from;

        public Integer depth;

        public Boolean consumed;
        public Boolean inStore;
        public Boolean hideFromAll;

        public String requiredChampion;
        public String requiredAlly;
    }

    public static final class Gold {
        public int base;
        public int total;
        public int sell;
        public boolean purchasable;
    }

    public static final class Image {
        public String full;
        public String sprite;
        public String group;
        public int x;
        public int y;
        public int w;
        public int h;
    }

    // -------------------------
    // Restored Rune models
    // -------------------------

    public static final class RuneTree {
        public int id;
        public String key;
        public String name;
        public String icon;
        public String iconUrl;
        public List<RuneSlot> slots;
    }

    public static final class RuneSlot {
        public List<Rune> runes;
    }

    public static final class Rune {
        public int id;
        public String key;
        public String name;
        public String icon;
        public String iconUrl;
    }
}
