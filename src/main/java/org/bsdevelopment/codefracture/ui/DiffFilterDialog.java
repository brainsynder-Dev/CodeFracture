package org.bsdevelopment.codefracture.ui;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DiffFilterDialog extends Dialog<List<String>> {

    public DiffFilterDialog(List<String> currentPatterns) {
        setTitle("Diff Filter — Configure Patterns");
        setHeaderText(
                "Only compare classes whose name starts with one of the patterns below.\n" +
                "Use dot or slash notation for packages, or just a class name prefix.\n" +
                "Leave empty (with filter enabled) to compare no classes."
        );

        TextArea textArea = new TextArea();
        textArea.setPrefRowCount(12);
        textArea.setPrefColumnCount(44);
        textArea.setPromptText("com/example/entity\ncom.example.model\nPetEntity");
        textArea.setText(String.join("\n", currentPatterns));

        VBox content = new VBox(6, new Label("Patterns — one per line:"), textArea);
        content.setPrefWidth(460);
        getDialogPane().setContent(content);

        ButtonType applyType = new ButtonType("Apply & Re-run", ButtonBar.ButtonData.OK_DONE);
        ButtonType clearType = new ButtonType("Clear All",      ButtonBar.ButtonData.LEFT);
        getDialogPane().getButtonTypes().addAll(applyType, clearType, ButtonType.CANCEL);

        setResultConverter(bt -> {
            if (bt == clearType) return List.of();
            if (bt == applyType) {
                return Arrays.stream(textArea.getText().split("\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }
            return null; // cancelled — no change
        });
    }
}
