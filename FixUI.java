import java.nio.file.*;
import java.nio.charset.*;
import java.util.regex.*;
public class FixUI {
    public static void main(String[] args) throws Exception {
        Path authPath = Paths.get("src/main/resources/aval/ui/views/Auth.fxml");
        String auth = new String(Files.readAllBytes(authPath), StandardCharsets.UTF_8);
        auth = auth.replace("<HBox styleClass=\"auth-wordmark\">", "<HBox styleClass=\"auth-wordmark\" alignment=\"BOTTOM_LEFT\">");
        auth = auth.replaceAll("<HBox spacing=\"8\" alignment=\"CENTER_LEFT\"[\\s\\S]*?<Label text=\"([^\"]*)\" styleClass=\"auth-body\" />\\s*</HBox>", "<Label text=\"\\u2022 $1\" styleClass=\"auth-body\" style=\"-fx-padding: 5 0 5 0;\" />");
        auth = auth.replace("•", "\u2022");
        if (!auth.contains("<StackPane")) {
            auth = auth.replace("<HBox\n    prefWidth=\"1200\"\n    prefHeight=\"800\"\n    xmlns=\"http://javafx.com/javafx/17\"\n    xmlns:fx=\"http://javafx.com/fxml/1\"\n    fx:controller=\"aval.ui.controller.AuthController\"\n>", "<StackPane xmlns=\"http://javafx.com/javafx/17\" xmlns:fx=\"http://javafx.com/fxml/1\" fx:controller=\"aval.ui.controller.AuthController\">\n<HBox prefWidth=\"1200\" prefHeight=\"800\" fx:id=\"rootNode\">");
            auth = auth.replaceAll("</HBox>\\s*$", "</HBox>\n<Button text=\"?\" onAction=\"#handleClose\" StackPane.alignment=\"TOP_RIGHT\" style=\"-fx-background-color: transparent; -fx-font-size: 20px; -fx-text-fill: #666666; -fx-cursor: hand; -fx-padding: 10 20;\" />\n</StackPane>");
        }
        Files.write(authPath, auth.getBytes(StandardCharsets.UTF_8));
        Path regPath = Paths.get("src/main/resources/aval/ui/views/Register.fxml");
        String reg = new String(Files.readAllBytes(regPath), StandardCharsets.UTF_8);
        reg = reg.replace("<HBox styleClass=\"auth-wordmark\">", "<HBox styleClass=\"auth-wordmark\" alignment=\"BOTTOM_LEFT\">");
        if (!reg.contains("<StackPane")) {
            reg = reg.replace("<HBox prefWidth=\"1200\" prefHeight=\"800\" xmlns=\"http://javafx.com/javafx/17\" xmlns:fx=\"http://javafx.com/fxml/1\" fx:controller=\"aval.ui.controller.RegisterController\">", "<StackPane xmlns=\"http://javafx.com/javafx/17\" xmlns:fx=\"http://javafx.com/fxml/1\" fx:controller=\"aval.ui.controller.RegisterController\">\n<HBox prefWidth=\"1200\" prefHeight=\"800\" fx:id=\"rootNode\">");
            reg = reg.replaceAll("</HBox>\\s*$", "</HBox>\n<Button text=\"?\" onAction=\"#handleClose\" StackPane.alignment=\"TOP_RIGHT\" style=\"-fx-background-color: transparent; -fx-font-size: 20px; -fx-text-fill: #666666; -fx-cursor: hand; -fx-padding: 10 20;\" />\n</StackPane>");
        }
        Files.write(regPath, reg.getBytes(StandardCharsets.UTF_8));
        Path authCtrlPath = Paths.get("src/main/java/aval/ui/controller/AuthController.java");
        String authCtrl = new String(Files.readAllBytes(authCtrlPath), StandardCharsets.UTF_8);
        if (!authCtrl.contains("xOffset")) {
            authCtrl = authCtrl.replace("import javafx.stage.Stage;", "import javafx.stage.Stage;\nimport javafx.scene.layout.HBox;\nimport javafx.scene.input.MouseEvent;");
            authCtrl = authCtrl.replace("public class AuthController {", "public class AuthController {\n    @FXML private HBox rootNode;\n    private double xOffset = 0;\n    private double yOffset = 0;\n    @FXML private void handleClose() { System.exit(0); }");
            authCtrl = authCtrl.replace("public void initialize() {", "public void initialize() {\n        if (rootNode != null) {\n            rootNode.setOnMousePressed(e -> {\n                xOffset = e.getSceneX();\n                yOffset = e.getSceneY();\n            });\n            rootNode.setOnMouseDragged(e -> {\n                Stage stage = (Stage) rootNode.getScene().getWindow();\n                stage.setX(e.getScreenX() - xOffset);\n                stage.setY(e.getScreenY() - yOffset);\n            });\n        }");
        }
        authCtrl = authCtrl.replace("Stage stage = (Stage) workspaceIdField.getScene().getWindow();", "Stage currentStage = (Stage) workspaceIdField.getScene().getWindow();");
        authCtrl = authCtrl.replace("stage.setScene(newScene);", "if (fxml.equals(\"Registry.fxml\")) {\n                Stage newStage = new Stage();\n                newStage.setTitle(\"AVAL AIRE\");\n                newStage.initStyle(javafx.stage.StageStyle.DECORATED);\n                newStage.setScene(new javafx.scene.Scene(root, 1200, 800));\n                newStage.centerOnScreen();\n                newStage.show();\n                currentStage.close();\n            } else {\n                currentStage.setScene(newScene);\n                currentStage.centerOnScreen();\n            }");
        Files.write(authCtrlPath, authCtrl.getBytes(StandardCharsets.UTF_8));
        Path regCtrlPath = Paths.get("src/main/java/aval/ui/controller/RegisterController.java");
        String regCtrl = new String(Files.readAllBytes(regCtrlPath), StandardCharsets.UTF_8);
        if (!regCtrl.contains("xOffset")) {
            regCtrl = regCtrl.replace("import javafx.stage.Stage;", "import javafx.stage.Stage;\nimport javafx.scene.layout.HBox;\nimport javafx.scene.input.MouseEvent;");
            regCtrl = regCtrl.replace("public class RegisterController {", "public class RegisterController {\n    @FXML private HBox rootNode;\n    private double xOffset = 0;\n    private double yOffset = 0;\n    @FXML private void handleClose() { System.exit(0); }");
            regCtrl = regCtrl.replace("public void initialize() {", "public void initialize() {\n        if (rootNode != null) {\n            rootNode.setOnMousePressed(e -> {\n                xOffset = e.getSceneX();\n                yOffset = e.getSceneY();\n            });\n            rootNode.setOnMouseDragged(e -> {\n                Stage stage = (Stage) rootNode.getScene().getWindow();\n                stage.setX(e.getScreenX() - xOffset);\n                stage.setY(e.getScreenY() - yOffset);\n            });\n        }");
        }
        Files.write(regCtrlPath, regCtrl.getBytes(StandardCharsets.UTF_8));
        Path splashCtrlPath = Paths.get("src/main/java/aval/ui/controller/SplashController.java");
        String splashCtrl = new String(Files.readAllBytes(splashCtrlPath), StandardCharsets.UTF_8);
        if (!splashCtrl.contains("stage.centerOnScreen();")) {
            splashCtrl = splashCtrl.replace("stage.setScene(newScene);", "stage.setScene(newScene);\n            stage.centerOnScreen();");
            Files.write(splashCtrlPath, splashCtrl.getBytes(StandardCharsets.UTF_8));
        }
    }
}
