package Main.Java;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;

/**
 * Central generation logic.
 *
 * Update in this version:
 * - Items:
 *   - If Smite is rolled => include exactly 1 jungle item + 5 normal finished items
 *   - If Smite is NOT rolled => 6 normal finished items (no Jungle-tagged items)
 *   - Always avoid components: only finished items (into empty)
 */
public final class LoadoutGenerator {

    // Default map for Summoner's Rift in Data Dragon item.json "maps" field
    // (You can change this based on your game mode selection UI.)
    public static final int MAP_SUMMONERS_RIFT = 11;

    private final DDragonPools pools;
    private final SecureRandom rng = new SecureRandom();

    public LoadoutGenerator(DDragonPools pools) {
        this.pools = pools;
    }

    /**
     * Generates only the 6 items portion (with your filtering rules).
     * Call this after you have already rolled spells, so we can detect Smite.
     */
    public List<DDragonPools.Item> rollItems(List<DDragonPools.SummonerSpell> spells, int mapId) throws IOException {
        pools.ensureLoaded();

        boolean hasSmite = containsSmite(spells);

        // Finished items (no components) + map + purchasable filters happen inside pools.
        if (!hasSmite) {
            List<DDragonPools.Item> nonJungle = pools.getFinishedItemsForMap(mapId, false);
            return pickDistinct(nonJungle, 6);
        }

        // has Smite: must include exactly one jungle item
        List<DDragonPools.Item> jungle = pools.getFinishedJungleItemsForMap(mapId);
        List<DDragonPools.Item> nonJungle = pools.getFinishedItemsForMap(mapId, false);

        if (jungle.isEmpty()) {
            // Failsafe: if pool is empty for some reason, fall back to 6 normal items.
            return pickDistinct(nonJungle, 6);
        }

        DDragonPools.Item jungleItem = jungle.get(rng.nextInt(jungle.size()));

        // Ensure we don't duplicate jungleItem if it also exists in nonJungle (should not, but safe)
        List<DDragonPools.Item> nonJungleNoDup = new ArrayList<>(nonJungle.size());
        for (DDragonPools.Item it : nonJungle) {
            if (!Objects.equals(it.id, jungleItem.id)) nonJungleNoDup.add(it);
        }

        List<DDragonPools.Item> rest = pickDistinct(nonJungleNoDup, 5);

        List<DDragonPools.Item> result = new ArrayList<>(6);
        result.add(jungleItem);
        result.addAll(rest);

        // Optional: shuffle so jungle item isn't always first
        Collections.shuffle(result, rng);

        return result;
    }

    private boolean containsSmite(List<DDragonPools.SummonerSpell> spells) {
        if (spells == null) return false;
        for (DDragonPools.SummonerSpell sp : spells) {
            if (sp == null) continue;

            // Smite is typically SummonerSmite / "Smite"
            if ("SummonerSmite".equalsIgnoreCase(sp.id)) return true;
            if ("Smite".equalsIgnoreCase(sp.name)) return true;
        }
        return false;
    }

    private List<DDragonPools.Item> pickDistinct(List<DDragonPools.Item> pool, int count) {
        if (pool == null) pool = Collections.emptyList();
        if (pool.size() < count) {
            // Return as many as possible (still distinct)
            return new ArrayList<>(new LinkedHashSet<>(pool));
        }

        List<DDragonPools.Item> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, rng);

        LinkedHashSet<String> seenIds = new LinkedHashSet<>();
        List<DDragonPools.Item> out = new ArrayList<>(count);

        for (DDragonPools.Item it : copy) {
            if (it == null || it.id == null) continue;
            if (seenIds.add(it.id)) {
                out.add(it);
                if (out.size() == count) break;
            }
        }

        return out;
    }
}
