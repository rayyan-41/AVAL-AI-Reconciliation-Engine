import java.nio.file.*;
import java.nio.charset.*;
import java.util.regex.*;
public class FixFxml {
    public static void main(String[] args) throws Exception {
        fix("src/main/resources/aval/ui/views/Auth.fxml", "aval.ui.controller.AuthController");
        fix("src/main/resources/aval/ui/views/Register.fxml", "aval.ui.controller.RegisterController");
    }
    private static void fix(String path, String ctrl) throws Exception {
        Path p = Paths.get(path);
        String c = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        c = c.replace("u2022 ", "\u2022 ");
        c = c.replaceAll("promptText=\"[^\"]{8,30}\"", "promptText=\"\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\"");
        c = c.replace("<Button text=\"?\"", "<Button text=\"\u2715\"");
        if (!c.contains("<StackPane xmlns")) {
            c = c.replaceFirst("<HBox[\\s\\S]*?fx:controller=\"" + ctrl + "\"[^>]*>", "<StackPane xmlns=\"http://javafx.com/javafx/17\" xmlns:fx=\"http://javafx.com/fxml/1\" fx:controller=\"" + ctrl + "\">\n<HBox prefWidth=\"1200\" prefHeight=\"800\" fx:id=\"rootNode\">");
        }
        Files.write(p, c.getBytes(StandardCharsets.UTF_8));
    }
}
