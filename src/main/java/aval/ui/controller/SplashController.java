package aval.ui.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;
import javafx.util.Duration;

public class SplashController {

    private static final String[] MESSAGES = {
        "Initializing AVAL AIRE runtime...",
        "Loading vectorization engine...",
        "Connecting to persistence layer...",
        "Verifying secure context...",
        "Ready.",
    };

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label logLabel;

    public void initialize() {
        Timeline tl = new Timeline();
        for (int i = 0; i < MESSAGES.length; i++) {
            final int idx = i;
            double secs = 0.4 + idx * 0.32;
            tl
                .getKeyFrames()
                .add(
                    new KeyFrame(Duration.seconds(secs), e -> {
                        progressBar.setProgress(
                            (double) (idx + 1) / MESSAGES.length
                        );
                        logLabel.setText(MESSAGES[idx]);
                    })
                );
        }
        tl.setOnFinished(e -> navigateTo("Auth.fxml"));
        tl.play();
    }

    private void navigateTo(String fxml) {
        try {
            Stage stage = (Stage) progressBar.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/aval/ui/views/" + fxml)
            );
            Parent root = loader.load();
            Scene newScene = new Scene(root);

            // Reapply existing stylesheets from the old scene if any
            newScene
                .getStylesheets()
                .addAll(progressBar.getScene().getStylesheets());

            stage.setScene(newScene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
