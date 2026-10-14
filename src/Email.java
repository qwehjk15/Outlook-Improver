public static class Email {
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
