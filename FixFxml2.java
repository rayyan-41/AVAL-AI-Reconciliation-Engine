import java.nio.file.*;
import java.nio.charset.*;
import java.util.regex.*;
public class FixFxml2 {
    public static void main(String[] args) throws Exception {
        fix("src/main/resources/aval/ui/views/Auth.fxml");
        fix("src/main/resources/aval/ui/views/Register.fxml");
    }
    private static void fix(String path) throws Exception {
        Path p = Paths.get(path);
        String c = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        if (!c.contains("<?import javafx.scene.layout.StackPane?>")) {
            c = c.replaceFirst("<\\?import javafx.scene.layout.HBox\\?>", "<?import javafx.scene.layout.HBox?>\n<?import javafx.scene.layout.StackPane?>");
            Files.write(p, c.getBytes(StandardCharsets.UTF_8));
        }
    }
}
