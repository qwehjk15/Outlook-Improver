
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Main {

    enum Operation {
        SORT, GROUP
    }

    enum Category {
        COURSEWORK,
        LECTURES,
        EXAMS,
        UNIVERSITY_ADMIN,
        SOCIETIES_EVENTS,
        GENERAL
    }

    enum UpdateType {
        NONE,
        GENERAL_UPDATE,
        DEADLINE_CHANGE,
        GRADE_CHANGE,
        LOCATION_CHANGE,
        CANCELLATION
    }

    enum Field {
        CATEGORY,
        DUE_DATE,
        GRADED,
        POINTS,
        MODULE,
        MESSAGE_SCORE,
        SENT_DATE,
        SENDER,
        PLATFORM,
        URGENCY,
        IS_REPLY,
        IS_UPDATE,
        UPDATE_TYPE,
        ACTION_STATUS
    }

    static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    static class Email {
        String id;
        String sender;
        String platform;
        String subject;
        String body;

        LocalDateTime sentDate;
        LocalDateTime dueDate;

        Category category;
        UpdateType updateType;

        Boolean isUrgent;
        Boolean isReply;
        Boolean graded;
        Boolean isUpdate;

        Integer points;
        String module;
        String actionStatus;
        String relatedMessageId;

        int messageScore;

        Email(String id, String sender, String platform,
              String subject, String body,
              LocalDateTime sentDate, Category category) {

            this.id = id;
            this.sender = sender;
            this.platform = platform;
            this.subject = subject;
            this.body = body;
            this.sentDate = sentDate;
            this.category = category;

            this.updateType = UpdateType.NONE;
            this.isUrgent = false;
            this.isReply = false;
            this.isUpdate = false;
            this.graded = null;
            this.points = null;
            this.dueDate = null;
            this.module = "Not specified";
            this.actionStatus = "Needs action";
            this.relatedMessageId = null;
            this.messageScore = 0;
        }
    }

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

    // Calculate a transparent, rule-based importance score.
    static int calculateScore(Email e, LocalDateTime now) {
        int score = 0;

        if (Boolean.TRUE.equals(e.isUrgent)) score += 5;
        if (Boolean.TRUE.equals(e.graded)) score += 3;

        if (e.dueDate != null) {
            long hours = Duration.between(now, e.dueDate).toHours();

            if (hours < 0) score += 5;
            else if (hours <= 24) score += 5;
            else if (hours <= 72) score += 3;
            else if (hours <= 168) score += 1;
        }

        if ("Needs action".equals(e.actionStatus)) score += 2;

        switch (e.updateType) {
            case DEADLINE_CHANGE:
            case GRADE_CHANGE:
            case CANCELLATION:
                score += 5;
                break;
            case LOCATION_CHANGE:
                score += 3;
                break;
            case GENERAL_UPDATE:
                score += 1;
                break;
            default:
                break;
        }

        return score;
    }

    static void sortEmails(List<Email> emails, Rule rule) {
        Comparator<Email> comparator = (a, b) -> {
            Comparable<?> va = getValue(a, rule.field);
            Comparable<?> vb = getValue(b, rule.field);

            // Unknown values stay at the end, in either direction.
            if (va == null && vb == null) return 0;
            if (va == null) return 1;
            if (vb == null) return -1;

            int result = compareValues(va, vb);
            return rule.ascending ? result : -result;
        };

        emails.sort(comparator);
    }

    @SuppressWarnings("unchecked")
    static int compareValues(Comparable<?> a, Comparable<?> b) {
        return ((Comparable<Object>) a).compareTo(b);
    }

    static Comparable<?> getValue(Email e, Field field) {
        switch (field) {
            case CATEGORY:       return e.category;
            case DUE_DATE:       return e.dueDate;
            case GRADED:         return e.graded;
            case POINTS:         return e.points;
            case MODULE:         return e.module;
            case MESSAGE_SCORE:  return e.messageScore;
            case SENT_DATE:      return e.sentDate;
            case SENDER:         return e.sender;
            case PLATFORM:       return e.platform;
            case URGENCY:        return e.isUrgent;
            case IS_REPLY:       return e.isReply;
            case IS_UPDATE:      return e.isUpdate;
            case UPDATE_TYPE:    return e.updateType;
            case ACTION_STATUS:  return e.actionStatus;
            default:             return null;
        }
    }

    // Group by the selected field, preserving first-seen group order.
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

    // Apply enabled rules in their user-selected order.
    // Sorting affects each existing group; grouping can split groups further.
    static List<List<Email>> processRules(
            List<Email> emails, List<Rule> rules) {

        List<List<Email>> groups = new ArrayList<>();
        groups.add(new ArrayList<>(emails));

        List<Rule> orderedRules = new ArrayList<>();

        for (Rule rule : rules) {
            if (rule.enabled) orderedRules.add(rule);
        }

        orderedRules.sort(Comparator.comparingInt(r -> r.order));

        for (Rule rule : orderedRules) {
            List<List<Email>> nextGroups = new ArrayList<>();

            for (List<Email> group : groups) {
                if (rule.operation == Operation.SORT) {
                    sortEmails(group, rule);
                    nextGroups.add(group);
                } else {
                    nextGroups.addAll(groupEmails(group, rule.field));
                }
            }

            groups = nextGroups;
        }

        return groups;
    }

    // Adds a new message and flags important linked updates.
    static void addOrUpdateEmail(
            List<Email> emails,
            Email incoming,
            LocalDateTime now) {

        if (incoming.relatedMessageId != null) {
            for (Email existing : emails) {
                if (existing.id.equals(incoming.relatedMessageId)) {
                    incoming.isUpdate = true;

                    if (incoming.updateType != UpdateType.NONE) {
                        incoming.isUrgent = true;
                    }

                    break;
                }
            }
        }

        incoming.messageScore = calculateScore(incoming, now);
        emails.add(incoming);
    }

    static void printEmail(Email e) {
        System.out.println("  Subject:       " + e.subject);
        System.out.println("  Sender:        " + e.sender);
        System.out.println("  Platform:      " + e.platform);
        System.out.println("  Category:      " + e.category);
        System.out.println("  Module:        " + e.module);
        System.out.println("  Sent:          " + e.sentDate.format(DATE_FORMAT));

        System.out.println("  Due date:      " +
            (e.dueDate == null ? "Not specified"
                : e.dueDate.format(DATE_FORMAT)));

        System.out.println("  Urgent:        " + e.isUrgent);
        System.out.println("  Reply:         " + e.isReply);
        System.out.println("  Is update:     " + e.isUpdate);
        System.out.println("  Update type:   " + e.updateType);

        System.out.println("  Related email: " +
            (e.relatedMessageId == null ? "None" : e.relatedMessageId));

        System.out.println("  Graded:        " +
            (e.graded == null ? "Unknown" : e.graded));

        System.out.println("  Points:        " +
            (e.points == null ? "Not specified" : e.points));

        System.out.println("  Action:        " + e.actionStatus);
        System.out.println("  Score:         " + e.messageScore);
        System.out.println("  Preview:       " + e.body);
        System.out.println("  ID:            " + e.id);
        System.out.println();
    }

    static Email createEmail(
            String id, String sender, String platform,
            String subject, String body, LocalDateTime sent,
            Category category, String module, String action) {

        Email e = new Email(
            id, sender, platform, subject, body, sent, category
        );

        e.module = module;
        e.actionStatus = action;
        return e;
    }

    public static void main(String[] args) {
        // Fixed reference time makes the demo repeatable.
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 12, 0);

        List<Email> emails = new ArrayList<>();

        // 1. Coursework deadline announcement.
        Email e1 = createEmail(
            "E001", "lecturer@uni.ac.uk", "Outlook",
            "Programming coursework deadline",
            "Submit the Java coursework through Canvas.",
            now.minusDays(3), Category.COURSEWORK,
            "Computer Science", "Needs action"
        );
        e1.dueDate = now.plusDays(3);
        e1.points = 40;
        emails.add(e1);

        // 2. An update to the original coursework email.
        Email e2 = createEmail(
            "E002", "lecturer@uni.ac.uk", "Outlook",
            "UPDATE: Programming coursework deadline changed",
            "The deadline has moved forward to tomorrow.",
            now.minusHours(1), Category.COURSEWORK,
            "Computer Science", "Needs action"
        );
        e2.dueDate = now.plusDays(1);
        e2.points = 40;
        e2.isReply = true;
        e2.isUpdate = true;
        e2.updateType = UpdateType.DEADLINE_CHANGE;
        e2.relatedMessageId = "E001";
        emails.add(e2);

        // 3. Lecture announcement.
        Email e3 = createEmail(
            "E003", "cs-lectures@uni.ac.uk", "Canvas",
            "Lecture moved to Room 205",
            "Friday's lecture will take place in Room 205.",
            now.minusHours(5), Category.LECTURES,
            "Computer Science", "Informational"
        );
        e3.updateType = UpdateType.LOCATION_CHANGE;
        e3.isUpdate = true;
        e3.isUrgent = true;
        emails.add(e3);

        // 4. University administration.
        Email e4 = createEmail(
            "E004", "student-services@uni.ac.uk", "Outlook",
            "Action required: confirm your student details",
            "Please confirm your contact information.",
            now.minusDays(1), Category.UNIVERSITY_ADMIN,
            "University Admin", "Needs action"
        );
        e4.isUrgent = true;
        emails.add(e4);

        // 5. Exam timetable.
        Email e5 = createEmail(
            "E005", "exams@uni.ac.uk", "Canvas",
            "Semester exam timetable published",
            "Check the timetable and report any clashes.",
            now.minusHours(8), Category.EXAMS,
            "Mathematics", "Needs action"
        );
        e5.dueDate = now.plusDays(7);
        emails.add(e5);

        // 6. Grade and feedback.
        Email e6 = createEmail(
            "E006", "lecturer@uni.ac.uk", "Canvas",
            "Coursework grade released",
            "Your coursework mark and feedback are available.",
            now.minusHours(2), Category.COURSEWORK,
            "Computer Science", "Review"
        );
        e6.graded = true;
        e6.points = 34;
        e6.updateType = UpdateType.GRADE_CHANGE;
        e6.isUpdate = true;
        e6.relatedMessageId = "E001";
        emails.add(e6);

        // 7. Society event.
        Email e7 = createEmail(
            "E007", "computing-society@uni.ac.uk", "Outlook",
            "Hackathon this Saturday",
            "Register for the student hackathon.",
            now.minusHours(6), Category.SOCIETIES_EVENTS,
            "Computing Society", "Needs action"
        );
        e7.dueDate = now.plusDays(2);
        emails.add(e7);

        // 8. General university announcement.
        Email e8 = createEmail(
            "E008", "library@uni.ac.uk", "Outlook",
            "Library opening hours this weekend",
            "The library will close earlier on Sunday.",
            now.minusDays(2), Category.GENERAL,
            "General", "Informational"
        );
        emails.add(e8);

        // 9. A normal reply, not an update.
        Email e9 = createEmail(
            "E009", "classmate@uni.ac.uk", "Outlook",
            "Re: Coursework question",
            "I think the lab examples will help.",
            now.minusHours(3), Category.COURSEWORK,
            "Computer Science", "Informational"
        );
        e9.isReply = true;
        emails.add(e9);

        // 10. Event cancellation.
        Email e10 = createEmail(
            "E010", "computing-society@uni.ac.uk", "Outlook",
            "UPDATE: Workshop cancelled",
            "The workshop planned for Friday is cancelled.",
            now.minusMinutes(30), Category.SOCIETIES_EVENTS,
            "Computing Society", "Informational"
        );
        e10.isUpdate = true;
        e10.updateType = UpdateType.CANCELLATION;
        e10.isUrgent = true;
        e10.relatedMessageId = "E007";
        emails.add(e10);

        // Calculate all scores before sorting.
        for (Email email : emails) {
            email.messageScore = calculateScore(email, now);
        }

        // Example of user-configurable rules.
        List<Rule> rules = new ArrayList<>();

        rules.add(new Rule(
            "Group by category", Operation.GROUP,
            Field.CATEGORY, true, true, 1
        ));

        rules.add(new Rule(
            "Most important first", Operation.SORT,
            Field.MESSAGE_SCORE, false, true, 2
        ));

        // Run the processing engine.
        List<List<Email>> results = processRules(emails, rules);

        System.out.println("==========================================");
        System.out.println("          TRACKR EMAIL DASHBOARD");
        System.out.println("==========================================");
        System.out.println("Total messages: " + emails.size());
        System.out.println("Active rules:   " + rules.size());
        System.out.println();

        int groupNumber = 1;

        for (List<Email> group : results) {
            if (group.isEmpty()) continue;

            Category category = group.get(0).category;

            System.out.println("------------------------------------------");
            System.out.println("GROUP " + groupNumber + ": " + category);
            System.out.println("Messages in group: " + group.size());
            System.out.println("------------------------------------------");

            for (Email email : group) {
                printEmail(email);
            }

            groupNumber++;
        }

        System.out.println("==========================================");
        System.out.println("              END OF TRACKR");
        System.out.println("==========================================");
    }
}
