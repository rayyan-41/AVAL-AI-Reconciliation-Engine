package aval.ui.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.util.Duration;

public class SplashController {

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label logLabel;

    public void initialize() {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, e -> {
                progressBar.setProgress(0.1);
                logLabel.setText("Loading schema...");
            }),
            new KeyFrame(Duration.seconds(1), e -> {
                progressBar.setProgress(0.4);
                logLabel.setText("Waking AI...");
            }),
            new KeyFrame(Duration.seconds(2), e -> {
                progressBar.setProgress(0.8);
                logLabel.setText("Connecting to pgvector...");
            }),
            new KeyFrame(Duration.seconds(3), e -> {
                progressBar.setProgress(1.0);
                logLabel.setText("Boot sequence complete.");
                System.out.println("Load complete");
            })
        );
        timeline.setCycleCount(1);
        timeline.play();
    }
}
