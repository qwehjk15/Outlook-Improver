import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class dataObtain {
    public List<String> dataObtain (String fileAddress) {
        List<String> data = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(fileAddress))) {
            String line;

            while ((line = br.readLine()) != null) {
                String[] columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

                if (columns.length >= 5) {
                    String value = columns[4].trim();

                    // Remove surrounding double quotes, if present
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }

                    data.add(value);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return data;
    }
}


