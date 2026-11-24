package com.murderhecoded.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class StopProgressDialog extends Stage {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ProgressIndicator spinner;
    private final Label titleLabel;
    private final Label subtitleLabel;
    private final TextArea logArea;
    private final Button closeBtn;
    private final Label[] stepLabels;
    private volatile boolean completed = false;

    private static final String[] STEP_TITLES = {
        "World Generation / Init Worker",
        "Game Master Multi-Agent Simulation Loop",
        "Prose Generation Engine & Queue",
        "Novel State & Project Persistence",
        "Finalizing Shutdown"
    };

    public StopProgressDialog(Window owner) {
        initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            initOwner(owner);
        }
        setTitle("Stopping Simulation");

        BorderPane root = new BorderPane();
        root.setPrefSize(560, 460);

        // Header
        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #1e293b; -fx-padding: 16;");

        spinner = new ProgressIndicator();
        spinner.setPrefSize(32, 32);

        VBox titleBox = new VBox(4);
        titleLabel = new Label("Stopping Simulation...");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        subtitleLabel = new Label("Gracefully halting agents, LLM requests, and preserving state...");
        subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);

        header.getChildren().addAll(spinner, titleBox);
        root.setTop(header);

        // Center Content: Steps & Log Area
        VBox centerBox = new VBox(12);
        centerBox.setPadding(new Insets(16));
        centerBox.setStyle("-fx-background-color: #f8fafc;");

        Label stepsHeader = new Label("Subsystem Shutdown Checklist:");
        stepsHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #334155;");
        centerBox.getChildren().add(stepsHeader);

        VBox stepsBox = new VBox(6);
        stepLabels = new Label[STEP_TITLES.length];
        for (int i = 0; i < STEP_TITLES.length; i++) {
            stepLabels[i] = new Label("  ⏳  " + STEP_TITLES[i] + " - Pending");
            stepLabels[i].setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
            stepsBox.getChildren().add(stepLabels[i]);
        }
        centerBox.getChildren().add(stepsBox);

        Label logHeader = new Label("Shutdown Event Log:");
        logHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #334155;");
        centerBox.getChildren().add(logHeader);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(6);
        logArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 11px; "
                + "-fx-control-inner-background: #0f172a; -fx-text-fill: #cbd5e1;");
        VBox.setVgrow(logArea, Priority.ALWAYS);
        centerBox.getChildren().add(logArea);

        root.setCenter(centerBox);

        // Bottom Bar
        HBox bottomBar = new HBox(12);
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        bottomBar.setStyle("-fx-padding: 12 16; -fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1 0 0 0;");

        closeBtn = new Button("Close");
        closeBtn.setDisable(true);
        closeBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 7 20; -fx-background-radius: 4;");
        closeBtn.setOnAction(e -> close());
        bottomBar.getChildren().add(closeBtn);

        root.setBottom(bottomBar);

        Scene scene = new Scene(root);
        setScene(scene);

        setOnCloseRequest(e -> {
            if (!completed) {
                e.consume(); // Prevent closing mid-shutdown
            }
        });
    }

    public void appendLog(String message) {
        String timestamp = LocalTime.now().format(TIME_FMT);
        String entry = "[" + timestamp + "] " + message;
        Platform.runLater(() -> {
            logArea.appendText(entry + "\n");
        });
    }

    public void setStepStatus(int stepIndex, String statusText, boolean isDone) {
        if (stepIndex < 0 || stepIndex >= stepLabels.length) {
            return;
        }
        Platform.runLater(() -> {
            String icon = isDone ? "  ✅  " : "  🔄  ";
            String color = isDone ? "#16a34a" : "#2563eb";
            stepLabels[stepIndex].setText(icon + STEP_TITLES[stepIndex] + " - " + statusText);
            stepLabels[stepIndex].setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        });
    }

    public void markFinished() {
        this.completed = true;
        Platform.runLater(() -> {
            titleLabel.setText("Simulation Stopped Successfully");
            titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #22c55e;");
            subtitleLabel.setText("All background processes halted safely. State preserved.");
            spinner.setVisible(false);
            closeBtn.setDisable(false);
        });
    }
}
