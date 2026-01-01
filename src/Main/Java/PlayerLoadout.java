package Main.Java;

import java.util.List;
import java.util.Objects;

public final class PlayerLoadout {
    public final String player;
    public final DDragonPools.Champion champion;
    public final List<DDragonPools.SummonerSpell> spells; // size 2
    public final List<DDragonPools.Item> items;           // size 6
    public final RunePage runes;

    public PlayerLoadout(String player,
                         DDragonPools.Champion champion,
                         List<DDragonPools.SummonerSpell> spells,
                         List<DDragonPools.Item> items,
                         RunePage runes) {
        this.player = Objects.requireNonNull(player, "player");
        this.champion = Objects.requireNonNull(champion, "champion");
        this.spells = Objects.requireNonNull(spells, "spells");
        this.items = Objects.requireNonNull(items, "items");
        this.runes = Objects.requireNonNull(runes, "runes");
    }
}
