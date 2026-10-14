
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Main {

    enum Category {
        COURSEWORK, LECTURES, EXAMS,
        UNIVERSITY_ADMIN, SOCIETIES_EVENTS, GENERAL
    }

    enum UpdateType {
        NONE, GENERAL_UPDATE, DEADLINE_CHANGE,
        GRADE_CHANGE, LOCATION_CHANGE, CANCELLATION
    }

    enum Field {
        CATEGORY, IMPORTANCE, SENT_DATE, UPDATE_FIRST
    }

    enum Operation {
        GROUP, SORT
    }

    static final LocalDateTime NOW =
        LocalDateTime.of(2026, 10, 14, 12, 0);

    static final DateTimeFormatter DATE =
        DateTimeFormatter.ofPattern("dd MMM, HH:mm");

    static class Email {
        String id, sender, platform, subject, body;
        String module, actionStatus, relatedId;
        LocalDateTime sentDate, dueDate;
        Category category;
        UpdateType updateType = UpdateType.NONE;

        boolean isReply = false;
        boolean isUpdate = false;
        boolean manuallyUrgent = false;
        boolean graded = false;
        boolean reviewed = false;

        Integer points = null;
        int score = 0;
        String priorityLabel;

        Email(String id, String sender, String platform,
              String subject, String body,
              LocalDateTime sentDate, Category category,
              String module, String actionStatus) {
            this.id = id;
            this.sender = sender;
            this.platform = platform;
            this.subject = subject;
            this.body = body;
            this.sentDate = sentDate;
            this.category = category;
            this.module = module;
            this.actionStatus = actionStatus;
        }
    }

    static class Rule {
        String name;
        Operation operation;
        Field field;
        boolean ascending;
        int order;

        Rule(String name, Operation operation,
             Field field, boolean ascending, int order) {
            this.name = name;
            this.operation = operation;
            this.field = field;
            this.ascending = ascending;
            this.order = order;
        }
    }

    static String content(Email e) {
        return (e.subject + " " + e.body).toLowerCase(Locale.ROOT);
    }

    static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    // Detect likely urgency signals. This is a heuristic,
    // not a guarantee that a message truly is urgent.
    static boolean isUrgent(Email e) {
        if (e.manuallyUrgent) return true;

        if (e.updateType == UpdateType.DEADLINE_CHANGE
                || e.updateType == UpdateType.GRADE_CHANGE
                || e.updateType == UpdateType.CANCELLATION) {
            return true;
        }

        if (e.dueDate != null) {
            long hours = Duration.between(NOW, e.dueDate).toHours();
            if (hours >= 0 && hours <= 24) return true;
        }

        String text = content(e);
        return containsAny(text,
            "action required", "deadline changed",
            "due tomorrow", "immediate action",
            "cancelled", "canceled");
    }

    // Importance score: transparent, rule-based and capped at 20.
    // Negative signals reduce importance but do not prove spam.
    static int calculateScore(Email e) {
        int score = 5; // Neutral starting point.
        String text = content(e);

        if (isUrgent(e)) score += 5;
        if (e.dueDate != null) {
            long hours = Duration.between(NOW, e.dueDate).toHours();

            if (hours < 0) score += 2;
            else if (hours <= 24) score += 4;
            else if (hours <= 72) score += 2;
            else if (hours <= 168) score += 1;
        }

        if ("Needs action".equals(e.actionStatus)) score += 2;
        if (e.graded) score += 3;

        if (e.updateType != UpdateType.NONE) score += 2;

        // Lower priority for likely optional or bulk messages.
        if (containsAny(text,
                "weekly newsletter", "optional social",
                "just for fun", "no action needed")) {
            score -= 3;
        }

        if (containsAny(text,
                "advertisement", "promotional offer",
                "limited-time sale", "unsubscribe")) {
            score -= 5;
        }

        if ("Informational".equals(e.actionStatus)) score -= 1;
        if (e.reviewed) score -= 2;

        return Math.max(0, Math.min(20, score));
    }

    static String priorityLabel(Email e) {
        if (isUrgent(e)) return "URGENT";
        if (e.score >= 12) return "HIGH PRIORITY";
        if (e.score >= 8) return "NORMAL PRIORITY";
        if (e.score >= 4) return "LOW PRIORITY";
        return "LIKELY LOW VALUE";
    }

    static String statusLabel(Email e) {
        if ("Needs action".equals(e.actionStatus)) {
            return "ACTION REQUIRED";
        }
        if ("Review".equals(e.actionStatus)) {
            return "REVIEW WHEN READY";
        }
        return "INFORMATION ONLY";
    }

    static String updateLabel(Email e) {
        switch (e.updateType) {
            case DEADLINE_CHANGE: return "Deadline changed";
            case GRADE_CHANGE: return "Grade update";
            case LOCATION_CHANGE: return "Location changed";
            case CANCELLATION: return "Event cancelled";
            case GENERAL_UPDATE: return "General update";
            default:
                return e.isReply ? "Reply (no update flagged)" : "Original message";
        }
    }

    static Object value(Email e, Field field) {
        switch (field) {
            case CATEGORY: return e.category;
            case IMPORTANCE: return e.score;
            case SENT_DATE: return e.sentDate;
            case UPDATE_FIRST: return e.isUpdate;
            default: return null;
        }
    }

    static void sortEmails(List<Email> emails, Rule rule) {
        emails.sort((a, b) -> {
            Object x = value(a, rule.field);
            Object y = value(b, rule.field);

            int result;
            if (x instanceof Comparable && y instanceof Comparable) {
                result = ((Comparable) x).compareTo(y);
            } else {
                result = 0;
            }

            return rule.ascending ? result : -result;
        });
    }

    static List<List<Email>> groupEmails(
            List<Email> emails, Field field) {
        Map<Object, List<Email>> groups = new LinkedHashMap<>();

        for (Email e : emails) {
            groups.computeIfAbsent(value(e, field),
                k -> new ArrayList<>()).add(e);
        }
        return new ArrayList<>(groups.values());
    }

    static List<List<Email>> processRules(
            List<Email> emails, List<Rule> rules) {
        List<List<Email>> groups = new ArrayList<>();
        groups.add(new ArrayList<>(emails));

        rules.sort(Comparator.comparingInt(r -> r.order));

        for (Rule rule : rules) {
            List<List<Email>> next = new ArrayList<>();

            for (List<Email> group : groups) {
                if (rule.operation == Operation.SORT) {
                    sortEmails(group, rule);
                    next.add(group);
                } else {
                    next.addAll(groupEmails(group, rule.field));
                }
            }
            groups = next;
        }
        return groups;
    }

    static void printRules(List<Rule> rules) {
        System.out.println("\nACTIVE RULES (highest priority first)");
        System.out.println("--------------------------------------");

        for (int i = 0; i < rules.size(); i++) {
            Rule r = rules.get(i);
            System.out.println((i + 1) + ". " + r.name);
        }
    }

    static void printEmail(Email e) {
        System.out.println("[" + priorityLabel(e) +
            " | Importance: " + e.score + "/20] " + e.subject);

        System.out.println("  From: " + e.sender +
            "  |  Source: " + e.platform);
        System.out.println("  Category: " + e.category +
            "  |  Module/group: " + e.module);
        System.out.println("  Sent: " + e.sentDate.format(DATE));

        if (e.dueDate != null) {
            System.out.println("  Deadline: " + e.dueDate.format(DATE));
        }

        System.out.println("  Status: " + statusLabel(e));
        System.out.println("  Message: " + updateLabel(e));

        /*if (e.relatedId != null) {
            System.out.println("  Related to message: " + e.relatedId);
        }
        */
        if (e.graded) {
            System.out.println("  Grade/feedback: Available" +
                (e.points == null ? "" : " (" + e.points + " points)"));
        }

        System.out.println("  Preview: " + e.body);
        System.out.println();
    }

    static Email make(String id, String sender, String platform,
            String subject, String body, int hoursAgo,
            Category category, String module, String status) {
        return new Email(id, sender, platform, subject, body,
            NOW.minusHours(hoursAgo), category, module, status);
    }

    static List<Email> createTestData() {
        List<Email> emails = new ArrayList<>();

        Email e;

        e = make("E001", "lecturer@uni.ac.uk", "Outlook",
            "Programming coursework deadline",
            "Submit the Java coursework on Canvas.",
            72, Category.COURSEWORK, "Computer Science", "Needs action");
        e.dueDate = NOW.plusDays(3);
        e.points = 40;
        emails.add(e);

        e = make("E002", "lecturer@uni.ac.uk", "Outlook",
            "UPDATE: Coursework deadline changed",
            "The submission deadline has moved to tomorrow.",
            1, Category.COURSEWORK, "Computer Science", "Needs action");
        e.dueDate = NOW.plusDays(1);
        e.isUpdate = true;
        e.isReply = true;
        e.updateType = UpdateType.DEADLINE_CHANGE;
        e.relatedId = "E001";
        emails.add(e);

        e = make("E003", "cs-lectures@uni.ac.uk", "Canvas",
            "Lecture moved to Room 205",
            "Friday's lecture will take place in Room 205.",
            5, Category.LECTURES, "Computer Science", "Informational");
        e.isUpdate = true;
        e.updateType = UpdateType.LOCATION_CHANGE;
        e.manuallyUrgent = true;
        emails.add(e);

        e = make("E004", "student-services@uni.ac.uk", "Outlook",
            "Action required: confirm student details",
            "Please confirm your contact information.",
            24, Category.UNIVERSITY_ADMIN, "University Admin", "Needs action");
        e.manuallyUrgent = true;
        emails.add(e);

        e = make("E005", "exams@uni.ac.uk", "Canvas",
            "Semester exam timetable published",
            "Check the timetable and report any clashes.",
            8, Category.EXAMS, "Mathematics", "Needs action");
        e.dueDate = NOW.plusDays(7);
        emails.add(e);

        e = make("E006", "lecturer@uni.ac.uk", "Canvas",
            "Coursework grade released",
            "Your coursework mark and feedback are available.",
            2, Category.COURSEWORK, "Computer Science", "Review");
        e.graded = true;
        e.points = 34;
        e.isUpdate = true;
        e.updateType = UpdateType.GRADE_CHANGE;
        e.relatedId = "E001";
        emails.add(e);

        e = make("E007", "computing-society@uni.ac.uk", "Outlook",
            "Hackathon this Saturday",
            "Register for the student hackathon.",
            6, Category.SOCIETIES_EVENTS, "Computing Society", "Needs action");
        e.dueDate = NOW.plusDays(2);
        emails.add(e);

        e = make("E008", "library@uni.ac.uk", "Outlook",
            "Library opening hours this weekend",
            "The library will close earlier on Sunday.",
            48, Category.GENERAL, "General", "Informational");
        emails.add(e);

        e = make("E009", "classmate@uni.ac.uk", "Outlook",
            "Re: Coursework question",
            "I think the lab examples will help.",
            3, Category.COURSEWORK, "Computer Science", "Informational");
        e.isReply = true;
        emails.add(e);

        e = make("E010", "computing-society@uni.ac.uk", "Outlook",
            "UPDATE: Workshop cancelled",
            "The workshop planned for Friday is cancelled.",
            0, Category.SOCIETIES_EVENTS, "Computing Society", "Informational");
        e.isUpdate = true;
        e.updateType = UpdateType.CANCELLATION;
        e.manuallyUrgent = true;
        e.relatedId = "E007";
        emails.add(e);

        e = make("E011", "news@uni.ac.uk", "Outlook",
            "Weekly newsletter",
            "Optional events and news. No action needed.",
            12, Category.GENERAL, "General", "Informational");
        emails.add(e);

        e = make("E012", "offers@example.com", "Outlook",
            "Promotional offer",
            "Advertisement: limited-time sale. Unsubscribe any time.",
            10, Category.GENERAL, "General", "Informational");
        emails.add(e);

        return emails;
    }

    static List<Rule> chooseRules(Scanner input) {
        System.out.println("\nHow would you like to organise your messages?");
        System.out.println("1. Group by category, most important first");
        System.out.println("2. Show most important messages first");
        System.out.println("3. Show newest messages first");
        System.out.println("4. Show updates first, then most important");
        System.out.print("Choose 1-4: ");

        String choice = input.nextLine().trim();
        List<Rule> rules = new ArrayList<>();

        switch (choice) {
            case "2":
                rules.add(new Rule("Highest importance first",
                    Operation.SORT, Field.IMPORTANCE, false, 1));
                break;

            case "3":
                rules.add(new Rule("Newest messages first",
                    Operation.SORT, Field.SENT_DATE, false, 1));
                break;

            case "4":
                rules.add(new Rule("Updates before original messages",
                    Operation.SORT, Field.UPDATE_FIRST, false, 1));
                rules.add(new Rule("Highest importance first",
                    Operation.SORT, Field.IMPORTANCE, false, 2));
                break;

            case "1":
            default:
                rules.add(new Rule("Group by category",
                    Operation.GROUP, Field.CATEGORY, true, 1));
                rules.add(new Rule("Highest importance first within each group",
                    Operation.SORT, Field.IMPORTANCE, false, 2));
                break;
        }

        return rules;
    }

    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        List<Email> emails = createTestData();

        for (Email e : emails) {
            e.score = calculateScore(e);
            e.priorityLabel = priorityLabel(e);
        }

        List<Rule> rules = chooseRules(input);
        printRules(rules);

        System.out.println("\nTRACKR — YOUR ACADEMIC INBOX");
        System.out.println("Messages found: " + emails.size());
        System.out.println("Urgent messages: " +
            emails.stream().filter(Main::isUrgent).count());
        System.out.println();

        if (rules.stream().anyMatch(r -> r.operation == Operation.GROUP)) {
            List<List<Email>> groups = processRules(emails, rules);
            int number = 1;

            for (List<Email> group : groups) {
                if (group.isEmpty()) continue;
                System.out.println("========== GROUP " + number +
                    ": " + group.get(0).category + " ==========");
                for (Email e : group) printEmail(e);
                number++;
            }
        } else {
            List<List<Email>> result = processRules(emails, rules);
            for (List<Email> group : result) {
                for (Email e : group) printEmail(e);
            }
        }

        System.out.println("Note: Importance scores are estimates, not facts.");
        System.out.println("Low importance does not automatically mean spam.");
        input.close();
    }
}
