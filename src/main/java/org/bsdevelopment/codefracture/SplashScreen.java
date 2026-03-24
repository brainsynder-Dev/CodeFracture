package org.bsdevelopment.codefracture;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class SplashScreen {
    private static final int WIDTH = 440;
    private static final int HEIGHT = 400;

    private static void triggerStrike(Rectangle flash, ImageView intact, ImageView fractured) {
        FadeTransition flashIn = new FadeTransition(Duration.millis(55), flash);
        flashIn.setToValue(0.80);
        FadeTransition flashOut = new FadeTransition(Duration.millis(550), flash);
        flashOut.setToValue(0.0);
        new SequentialTransition(flashIn, flashOut).play();

        FadeTransition logoOut = new FadeTransition(Duration.millis(160), intact);
        logoOut.setToValue(0.0);
        FadeTransition logoIn = new FadeTransition(Duration.millis(280), fractured);
        logoIn.setToValue(1.0);
        new ParallelTransition(logoOut, logoIn).play();
    }

    private static ImageView makeLogoView(String resourcePath) {
        ImageView iv = new ImageView();
        try (var s = SplashScreen.class.getResourceAsStream(resourcePath)) {
            if (s != null) iv.setImage(new Image(s));
        } catch (Exception ignored) {
        }
        iv.setFitWidth(220);
        iv.setFitHeight(220);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        return iv;
    }

    public void show(Runnable onFinished) {
        Stage stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);

        try (var stream = SplashScreen.class.getResourceAsStream("/logo_256.png")) {
            if (stream != null) stage.getIcons().add(new Image(stream));
        } catch (Exception ignored) {
        }

        ImageView intactView = makeLogoView("/logo_intact_256.png");
        ImageView fracturedView = makeLogoView("/logo_256.png");
        fracturedView.setOpacity(0);

        StackPane logoStack = new StackPane(intactView, fracturedView);

        Label titleLabel = new Label("CodeFracture");
        titleLabel.setStyle(
                "-fx-font-family: 'Consolas','Courier New',monospace;" +
                        "-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #cdd6f4;"
        );
        Label subLabel = new Label("Java Decompiler");
        subLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #6272a4;");

        VBox textBox = new VBox(6, titleLabel, subLabel);
        textBox.setAlignment(Pos.CENTER);

        VBox content = new VBox(28, logoStack, textBox);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(36));
        content.setStyle("-fx-background-color: #1e1e2e;");

        Rectangle flash = new Rectangle(WIDTH, HEIGHT, Color.WHITE);
        flash.setOpacity(0);

        StackPane root = new StackPane(content, flash);
        Scene scene = new Scene(root, WIDTH, HEIGHT);
        scene.setFill(Color.web("#1e1e2e"));
        stage.setScene(scene);

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setX((screen.getWidth() - WIDTH) / 2.0);
        stage.setY((screen.getHeight() - HEIGHT) / 2.0);
        stage.show();

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(2000), e -> triggerStrike(flash, intactView, fracturedView)),
                new KeyFrame(Duration.millis(3000), e -> {
                    if (onFinished != null) onFinished.run();
                    FadeTransition fade = new FadeTransition(Duration.millis(50), root);
                    fade.setToValue(0.0);
                    fade.setOnFinished(ev -> stage.close());
                    fade.play();
                })
        );
        timeline.play();
    }
}
