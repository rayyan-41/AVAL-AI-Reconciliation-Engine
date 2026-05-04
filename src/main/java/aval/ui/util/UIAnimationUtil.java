package aval.ui.util;

import java.util.List;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.util.Duration;

public final class UIAnimationUtil {

    private UIAnimationUtil() {}

    public static void playStaggeredEntrance(
        List<? extends Node> nodes,
        Duration perItemDuration,
        Duration stagger
    ) {
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            node.setOpacity(0);
            node.setTranslateY(10);

            FadeTransition fade = new FadeTransition(perItemDuration, node);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setInterpolator(Interpolator.EASE_OUT);
            fade.setDelay(stagger.multiply(i));

            TranslateTransition slide = new TranslateTransition(perItemDuration, node);
            slide.setFromY(10);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            slide.setDelay(stagger.multiply(i));

            new ParallelTransition(fade, slide).play();
        }
    }

    public static void rollNumber(
        Label targetLabel,
        double target,
        int decimals,
        String suffix
    ) {
        DoubleProperty value = new SimpleDoubleProperty(0);
        value.addListener((obs, oldVal, newVal) -> {
            String fmt = decimals > 0 ? "%." + decimals + "f" : "%.0f";
            String text = String.format(fmt, newVal.doubleValue());
            targetLabel.setText(text + (suffix == null ? "" : suffix));
        });

        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(value, 0)),
            new KeyFrame(Duration.millis(1000), new KeyValue(value, target, Interpolator.EASE_BOTH))
        );
        timeline.play();
    }

    public static void applyHoverLift(Node node) {
        node.setOnMouseEntered(e -> animateScale(node, 1.02, Duration.millis(200)));
        node.setOnMouseExited(e -> animateScale(node, 1.0, Duration.millis(200)));
    }

    public static void applyButtonPressFeedback(Node node) {
        node.setOnMousePressed(e -> animateScale(node, 0.95, Duration.millis(90)));
        node.setOnMouseReleased(e -> animateScale(node, 1.0, Duration.millis(110)));
        node.setOnMouseExited(e -> animateScale(node, 1.0, Duration.millis(110)));
    }

    public static void activateDropZone(Region dropZone) {
        animateScale(dropZone, 1.02, Duration.millis(200));
        dropZone.setStyle(
            "-fx-background-color: rgba(128, 0, 32, 0.10);" +
            "-fx-border-color: rgba(128, 0, 32, 0.45);"
        );
    }

    public static void resetDropZone(Region dropZone) {
        animateScale(dropZone, 1.0, Duration.millis(200));
        dropZone.setStyle("");
    }

    private static void animateScale(Node node, double to, Duration duration) {
        ScaleTransition st = new ScaleTransition(duration, node);
        st.setToX(to);
        st.setToY(to);
        st.setInterpolator(Interpolator.EASE_BOTH);
        st.play();
    }
}
