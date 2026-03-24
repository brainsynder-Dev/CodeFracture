package org.bsdevelopment.codefracture;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.bsdevelopment.codefracture.controller.MainController;

import java.awt.*;

public class CodeFractureApp extends Application {

    private static final double MAIN_W = 1280;
    private static final double MAIN_H = 800;

    public static Theme resolveTheme(String name) {
        return switch (name) {
            case "Dracula" -> new Dracula();
            case "Primer Dark" -> new PrimerDark();
            case "Primer Light" -> new PrimerLight();
            case "Nord Light" -> new NordLight();
            case "Cupertino Dark" -> new CupertinoDark();
            case "Cupertino Light" -> new CupertinoLight();
            default -> new NordDark();
        };
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Theme theme = resolveTheme(AppConfig.get(AppConfig.THEME, "Nord Dark"));
        Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());

        setupMainWindow(primaryStage);

        Rectangle2D screen = screenContainingCursor().getVisualBounds();
        primaryStage.setX(screen.getMinX() + (screen.getWidth() - MAIN_W) / 2.0);
        primaryStage.setY(screen.getMinY() + (screen.getHeight() - MAIN_H) / 2.0);

        boolean skipSplash = Boolean.parseBoolean(AppConfig.get(AppConfig.SKIP_SPLASH, "false"));
        if (skipSplash) {
            primaryStage.show();
        } else {
            new SplashScreen().show(primaryStage::show);
        }
    }

    private static Screen screenContainingCursor() {
        try {
            Point p = MouseInfo.getPointerInfo().getLocation();
            double x = p.x, y = p.y;
            return Screen.getScreens().stream()
                    .filter(s -> s.getBounds().contains(x, y))
                    .findFirst()
                    .orElse(Screen.getPrimary());
        } catch (Exception ignored) {}
        return Screen.getPrimary();
    }

    private void setupMainWindow(Stage primaryStage) {
        try (var stream = getClass().getResourceAsStream("/logo_256.png")) {
            if (stream != null) primaryStage.getIcons().add(new Image(stream));
        } catch (Exception ignored) {}

        MainController controller = new MainController(primaryStage);
        Scene scene = new Scene(controller.getRoot(), MAIN_W, MAIN_H);
        scene.getStylesheets().add(getClass().getResource("/org/bsdevelopment/codefracture/syntax.css").toExternalForm());

        primaryStage.setTitle("CodeFracture — Java Decompiler");
        primaryStage.setScene(scene);
    }
}
