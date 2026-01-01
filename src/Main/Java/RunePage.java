package Main.Java;

import java.util.Collections;
import java.util.List;

/**
 * Simple model for a generated rune page.
 * Matches the "restored" DDragonPools nested rune types:
 * - DDragonPools.RuneTree
 * - DDragonPools.Rune
 */
public final class RunePage {

    // Primary/Secondary paths
    public final DDragonPools.RuneTree primaryTree;
    public final DDragonPools.RuneTree secondaryTree;

    // Keystone (from primary slot 0)
    public final DDragonPools.Rune keystone;

    // 3 minors from primary (slots 1,2,3)
    public final List<DDragonPools.Rune> primaryMinors;

    // 2 minors from secondary (two different slots among 1..3)
    public final List<DDragonPools.Rune> secondaryMinors;

    public RunePage(DDragonPools.RuneTree primaryTree,
                    DDragonPools.RuneTree secondaryTree,
                    DDragonPools.Rune keystone,
                    List<DDragonPools.Rune> primaryMinors,
                    List<DDragonPools.Rune> secondaryMinors) {

        this.primaryTree = primaryTree;
        this.secondaryTree = secondaryTree;
        this.keystone = keystone;
        this.primaryMinors = primaryMinors == null ? Collections.<DDragonPools.Rune>emptyList() : primaryMinors;
        this.secondaryMinors = secondaryMinors == null ? Collections.<DDragonPools.Rune>emptyList() : secondaryMinors;
    }

    // Optional getters (helps if your generator uses getters)
    public DDragonPools.RuneTree getPrimaryTree() { return primaryTree; }
    public DDragonPools.RuneTree getSecondaryTree() { return secondaryTree; }
    public DDragonPools.Rune getKeystone() { return keystone; }
    public List<DDragonPools.Rune> getPrimaryMinors() { return primaryMinors; }
    public List<DDragonPools.Rune> getSecondaryMinors() { return secondaryMinors; }

    public static RunePage empty() {
        return new RunePage(null, null, null,
                Collections.<DDragonPools.Rune>emptyList(),
                Collections.<DDragonPools.Rune>emptyList());
    }
}
