
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class RuleInput {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        List<String> rules = new ArrayList<>();
        rules.add("Deadline urgency");
        rules.add("Sender importance");
        rules.add("Date sent");
        rules.add("Subject category");

        System.out.println("Available sorting rules:");
        for (int i = 0; i < rules.size(); i++) {
            System.out.println((i + 1) + ". " + rules.get(i));
        }

        System.out.println(
            "Enter the rule numbers in your desired order, "
            + "e.g. 3 1 2 4:"
        );

        String input = scanner.nextLine();
        String[] choices = input.trim().split("\\s+");

        List<String> orderedRules = new ArrayList<>();

        for (String choice : choices) {
            int index = Integer.parseInt(choice) - 1;

            if (index >= 0 && index < rules.size()) {
                orderedRules.add(rules.get(index));
            }
        }

        System.out.println("Your selected rule order:");
        for (int i = 0; i < orderedRules.size(); i++) {
            System.out.println(
                (i + 1) + ". " + orderedRules.get(i)
            );
        }

        scanner.close();
    }
}
