package Main.Java;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RosterModel {
    private final List<String> players = new ArrayList<>();

    public synchronized void setPlayers(List<String> newPlayers) {
        players.clear();
        if (newPlayers != null) {
            for (String p : newPlayers) {
                if (p != null) {
                    String t = p.trim();
                    if (!t.isEmpty()) players.add(t);
                }
            }
        }
    }

    public synchronized List<String> getPlayers() {
        return Collections.unmodifiableList(new ArrayList<>(players));
    }
}
