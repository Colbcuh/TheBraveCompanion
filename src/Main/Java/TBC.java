package Main.Java;

import java.util.*;

public class TBC {

    public static void main(String[] args) {

        List<String> players = Arrays.asList(
                "Kyle", "Bobo", "Jon", "Noah", "Colby"
        );

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n--- New Random Order ---");
            printRandomOrder(players, 5);

            System.out.println("\nPress ENTER to generate again, or type 'exit' to quit.");
            String input = scanner.nextLine();

            if (input.equalsIgnoreCase("exit")) {
                break;
            }
        }

        scanner.close();
    }

    public static void printRandomOrder(List<String> players, int lineCount) {
        List<String> uniquePlayers = new ArrayList<>(new LinkedHashSet<>(players));
        Collections.shuffle(uniquePlayers);

        for (int i = 0; i < lineCount; i++) {
            String p1 = uniquePlayers.get(i % uniquePlayers.size());
            String p2 = uniquePlayers.get((i + 1) % uniquePlayers.size());
            System.out.println(p1 + " > " + p2);
        }
    }
}

