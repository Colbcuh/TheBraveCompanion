package Main.Java;

import java.util.*;

public final class RunePageGenerator {

    public static RunePage randomRunePage(Random rng, List<DDragonPools.RuneTree> trees) {
        if (trees == null || trees.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 rune trees.");
        }

        DDragonPools.RuneTree primary = trees.get(rng.nextInt(trees.size()));

        DDragonPools.RuneTree secondary;
        do {
            secondary = trees.get(rng.nextInt(trees.size()));
        } while (secondary.id == primary.id);

        // Primary: slots[0]=keystone, slots[1..3]=minor rows
        DDragonPools.Rune keystone = pickOne(rng, primary.slots.get(0).runes);

        List<DDragonPools.Rune> primaryMinors = new ArrayList<>(3);
        for (int slot = 1; slot <= 3; slot++) {
            primaryMinors.add(pickOne(rng, primary.slots.get(slot).runes));
        }

        // Secondary: pick 2 minors from different rows among slots[1..3]
        List<Integer> rows = Arrays.asList(1, 2, 3);
        Collections.shuffle(rows, rng);
        int rowA = rows.get(0);
        int rowB = rows.get(1);

        List<DDragonPools.Rune> secondaryMinors = new ArrayList<>(2);
        secondaryMinors.add(pickOne(rng, secondary.slots.get(rowA).runes));
        secondaryMinors.add(pickOne(rng, secondary.slots.get(rowB).runes));

        return new RunePage(primary, secondary, keystone,
                Collections.unmodifiableList(primaryMinors),
                Collections.unmodifiableList(secondaryMinors));
    }

    private static <T> T pickOne(Random rng, List<T> list) {
        if (list == null || list.isEmpty()) throw new IllegalArgumentException("Empty list");
        return list.get(rng.nextInt(list.size()));
    }
}
