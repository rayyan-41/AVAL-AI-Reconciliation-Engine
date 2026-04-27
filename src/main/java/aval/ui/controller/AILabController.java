package aval.ui.controller;

import aval.domain.ai.StandardizedTransaction;
import aval.ui.util.MockUIProvider;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

public class AILabController {

    @FXML
    private ListView<StandardizedTransaction> vectorizationList;

    public void initialize() {
        vectorizationList.setItems(javafx.collections.FXCollections.observableArrayList(MockUIProvider.getMockTransactions(15)));

        vectorizationList.setCellFactory(lv -> new ListCell<StandardizedTransaction>() {
            private final Circle pulseDot = new Circle(4, Color.web("#800020"));
            private final FadeTransition animation = new FadeTransition(Duration.seconds(0.8), pulseDot);
            private final Label narrativeLabel = new Label();
            private final HBox layout = new HBox(15, pulseDot, narrativeLabel);

            {
                layout.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                animation.setFromValue(0.2);
                animation.setToValue(1.0);
                animation.setCycleCount(Animation.INDEFINITE);
                animation.setAutoReverse(true);
            }

            @Override
            protected void updateItem(StandardizedTransaction item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    animation.stop();
                } else {
                    narrativeLabel.setText(item.getNarrative());
                    narrativeLabel.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");
                    setGraphic(layout);
                    animation.play();
                }
            }
        });
    }
}
