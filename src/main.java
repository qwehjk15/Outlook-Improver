import java.time.LocalDateTime;
import java.util.*;

public class TrackrBackend {

    // 1. All possible operations and rule fields
    enum Operation {
        SORT, GROUP
    }

    enum Field {
        DUE_DATE, GRADED, POINTS, MODULE,
        MESSAGE_SCORE, SENT_DATE, SENDER,
        PLATFORM, URGENCY, IS_REPLY, ACTION_STATUS
    }

    // 2. Email data model
    static class Email {
        String id;
        String sender;
        String platform;
        String subject;
        String body;
        LocalDateTime sentDate;
        LocalDateTime dueDate;

        Boolean isUrgent;
        Boolean isReply;
        Boolean graded;
        Integer points;
        String module;
        String actionStatus;
        String relatedMessageId;

        int messageScore;

        Email(String id, String sender, String platform,
              String subject, String body,
              LocalDateTime sentDate) {
            this.id = id;
            this.sender = sender;
            this.platform = platform;
            this.subject = subject;
            this.body = body;
            this.sentDate = sentDate;

            this.isUrgent = false;
            this.isReply = false;
            this.graded = null;
            this.points = null;
            this.dueDate = null;
            this.module = null;
            this.actionStatus = "Needs action";
            this.messageScore = 0;
        }
    }

    // 3. Configurable user rule
    static class Rule {
        String name;
        Operation operation;
        Field field;
        boolean ascending;
        boolean enabled;
        int order;

        Rule(String name, Operation operation, Field field,
             boolean ascending, boolean enabled, int order) {
            this.name = name;
            this.operation = operation;
            this.field = field;
            this.ascending = ascending;
            this.enabled = enabled;
            this.order = order;
        }
    }

    // 4. Calculate a basic importance score
    // This is rule-based, not AI-generated.
    static int calculateScore(Email e) {
        int score = 0;

        if (Boolean.TRUE.equals(e.isUrgent)) score += 5;
        if (Boolean.TRUE.equals(e.graded)) score += 3;

        if (e.dueDate != null) {
            long hours = java.time.Duration.between(
                LocalDateTime.now(), e.dueDate
            ).toHours();

            if (hours < 0) score += 5;
            else if (hours <= 24) score += 5;
            else if (hours <= 72) score += 3;
            else if (hours <= 168) score += 1;
        }

        if ("Needs action".equals(e.actionStatus)) score += 2;

        String text = (e.subject + " " + e.body)
            .toLowerCase(Locale.ROOT);

        if (text.contains("deadline changed")
                || text.contains("grade changed")) {
            score += 4;
        }

        return score;
    }

    // 5. Sort emails using a selected field
    static void sortEmails(List<Email> emails, Rule rule) {
        Comparator<Email> comparator = (a, b) -> {
            Comparable va = getValue(a, rule.field);
            Comparable vb = getValue(b, rule.field);

            // Unknown values remain at the end in either direction.
            if (va == null && vb == null) return 0;
            if (va == null) return 1;
            if (vb == null) return -1;

            int result = va.compareTo(vb);
            return rule.ascending ? result : -result;
        };

        emails.sort(comparator);
    }

    // 6. Get the value associated with a rule field
    static Comparable getValue(Email e, Field field) {
        switch (field) {
            case DUE_DATE: return e.dueDate;
            case GRADED: return e.graded;
            case POINTS: return e.points;
            case MODULE: return e.module;
            case MESSAGE_SCORE: return e.messageScore;
            case SENT_DATE: return e.sentDate;
            case SENDER: return e.sender;
            case PLATFORM: return e.platform;
            case URGENCY: return e.isUrgent;
            case IS_REPLY: return e.isReply;
            case ACTION_STATUS: return e.actionStatus;
            default: return null;
        }
    }

    // 7. Group emails by the chosen field
    static List<List<Email>> groupEmails(
            List<Email> emails, Field field) {

        Map<Object, List<Email>> grouped = new LinkedHashMap<>();

        for (Email email : emails) {
            Object key = getValue(email, field);

            grouped.computeIfAbsent(
                key, k -> new ArrayList<>()
            ).add(email);
        }

        return new ArrayList<>(grouped.values());
    }

    // 8. Apply rules in priority order
    static List<List<Email>> processRules(
            List<Email> emails, List<Rule> rules) {

        List<List<Email>> groups = new ArrayList<>();
        groups.add(new ArrayList<>(emails));

        rules.stream()
            .filter(rule -> rule.enabled)
            .sorted(Comparator.comparingInt(rule -> rule.order))
            .forEach(rule -> {
                List<List<Email>> nextGroups = new ArrayList<>();

                for (List<Email> group : groups) {
                    if (rule.operation == Operation.SORT) {
                        sortEmails(group, rule);
                        nextGroups.add(group);
                    } else {
                        nextGroups.addAll(
                            groupEmails(group, rule.field)
                        );
                    }
                }

                groups.clear();
                groups.addAll(nextGroups);
            });

        return groups;
    }

    // 9. Add new emails or process updates
    static void addOrUpdateEmail(
            List<Email> emails,
            Email incoming,
            boolean affectsGrade) {

        if (affectsGrade) {
            incoming.isUrgent = true;
            incoming.messageScore += 5;
        }

        // Link the update to the original email using its ID.
        if (incoming.relatedMessageId != null) {
            for (Email existing : emails) {
                if (existing.id.equals(incoming.relatedMessageId)) {
                    // Keep the update as a separate record,
                    // but link it to the original message.
                    break;
                }
            }
        }

        incoming.messageScore += calculateScore(incoming);

        // Add the new record. A real application should
        // deduplicate using the source platform's message ID.
        emails.add(incoming);
    }

    // 10. Demo
    public static void main(String[] args) {
        List<Email> emails = new ArrayList<>();

        Email a = new Email(
            "1", "lecturer@uni.ac.uk", "Outlook",
            "Coursework deadline changed",
            "Deadline changed to tomorrow",
            LocalDateTime.now().minusHours(2)
        );
        a.module = "Computer Science";
        a.graded = true;
        a.points = 40;
        a.dueDate = LocalDateTime.now().plusDays(1);

        Email b = new Email(
            "2", "society@uni.ac.uk", "Outlook",
            "Society meeting",
            "Meeting this Friday",
            LocalDateTime.now()
        );
        b.module = "Society";
        b.actionStatus = "Informational";

        emails.add(a);
        emails.add(b);

        // Example user-selected rules
        List<Rule> rules = new ArrayList<>();
        rules.add(new Rule(
            "Most important", Operation.SORT,
            Field.MESSAGE_SCORE, false, true, 1
        ));
        rules.add(new Rule(
            "Group by module", Operation.GROUP,
            Field.MODULE, true, true, 2
        ));

        // Calculate scores before processing rules
        for (Email email : emails) {
            email.messageScore = calculateScore(email);
        }

        List<List<Email>> results = processRules(emails, rules);

        for (List<Email> group : results) {
            System.out.println("--- Group ---");

            for (Email email : group) {
                System.out.println(
                    email.subject + " | Score: "
                    + email.messageScore
                );
            }
        }
    }
}
