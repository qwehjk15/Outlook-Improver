import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;

ArrayList<Message> messages = new ArrayList<>();

try (BufferedReader br = new BufferedReader(
        new FileReader("testData.csv"))) {

    br.readLine(); // Skip header

    String line;

    while ((line = br.readLine()) != null) {
        String[] v = line.split(",", -1);

        messages.add(new Message(
            v[0],                         // sender
            v[1],                         // platform
            v[2],                         // subject
            v[3],                         // body
            v[4],                         // sent date
            v[5],                         // urgent?
            v[6],                         // reply?
            v[7]                          // action status
        ));
    }
}
