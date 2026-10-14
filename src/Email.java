import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Email {
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

    public static List<Email> dataObtain(String fileAddress) {
        List<Email> emails = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new FileReader(fileAddress))) {

            br.readLine(); // Skip header

            String line;

            while ((line = br.readLine()) != null) {
                String[] c = line.split(
                    ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1
                );

                for (int i = 0; i < c.length; i++) {
                    c[i] = c[i].trim();

                    if (c[i].startsWith("\"")
                            && c[i].endsWith("\"")) {
                        c[i] = c[i].substring(1, c[i].length() - 1);
                    }
                }

                if (c.length >= 8) {
                    LocalDateTime sentDate = LocalDateTime.parse(
                        c[2] + "T" + c[1]
                    );

                    Email email = new Email(
                        String.valueOf(emails.size() + 1), // id
                        c[0], // sender
                        "Email", // platform
                        c[3], // subject
                        c[4], // body
                        sentDate
                    );

                    email.isUrgent = c[5].equalsIgnoreCase("T");
                    email.isReply = c[6].equalsIgnoreCase("T");
                    email.actionStatus = c[7];

                    emails.add(email);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return emails;
    }
}
