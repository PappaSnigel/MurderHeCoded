package com.murderhecoded.ui;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.Chapter;
import com.murderhecoded.domain.Scene;
import com.murderhecoded.domain.SceneCategory;
import com.murderhecoded.domain.TimeLocationEvent;
import com.murderhecoded.repository.SimulationRepository;
import com.murderhecoded.service.EditorService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Presenter for the 'Scenes' tab displaying curated scenes,
 * editorial drama scores, cut status, jump-to-timeline controls, and generated prose.
 * Supports the 3-step novel generation workflow: Events -> Scenes -> Prose,
 * including regeneration of scenes and prose.
 */
@SuppressWarnings("PMD")
public class ScenesTabPresenter {

    private final TableView<Chapter> scenesTable = new TableView<>();
    private final VBox detailBox = new VBox(14);
    private final TextField searchField = new TextField();
    private final ComboBox<String> categoryCombo = new ComboBox<>();
    private final ComboBox<String> cutCombo = new ComboBox<>();
    private final Label statsLabel = new Label("No scenes available.");
    private final Button generateScenesBtn = new Button("🎬 Generate Scenes");
    private final Button generateProseBtn = new Button("✍️ Generate Prose");

    private final ObservableList<Chapter> masterChaptersList = FXCollections.observableArrayList();
    private final ObservableList<Chapter> filteredChaptersList = FXCollections.observableArrayList();

    private SimulationRepository currentState;
    private Consumer<Integer> onJumpToStep;
    private Runnable onGenerateScenes;
    private Runnable onGenerateProse;
    private Consumer<Chapter> onRegenerateSingleSceneProse;

    public BorderPane createView(Consumer<Integer> onJumpToStep,
                                 Runnable onGenerateScenes,
                                 Runnable onGenerateProse,
                                 Consumer<Chapter> onRegenerateSingleSceneProse) {
        this.onJumpToStep = onJumpToStep;
        this.onGenerateScenes = onGenerateScenes;
        this.onGenerateProse = onGenerateProse;
        this.onRegenerateSingleSceneProse = onRegenerateSingleSceneProse;

        BorderPane rootPane = new BorderPane();
        rootPane.setPadding(new Insets(10));

        // --- Top Bar: Search, Category Filter, Cut Filter, Stats, and Action Buttons ---
        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 0, 10, 0));

        searchField.setPromptText("Filter scenes by focus, location, step, or prose text...");
        searchField.setPrefWidth(280);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        categoryCombo.setItems(FXCollections.observableArrayList(
                "All Categories",
                SceneCategory.ESSENTIAL.name(),
                SceneCategory.SUPPORTING.name(),
                SceneCategory.DISPENSABLE.name()
        ));
        categoryCombo.setValue("All Categories");
        categoryCombo.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        cutCombo.setItems(FXCollections.observableArrayList(
                "All Scenes",
                "Final Cut Only",
                "Cut / Fluff Only"
        ));
        cutCombo.setValue("All Scenes");
        cutCombo.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        statsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Step 2 Button: Generate / Regenerate Scenes
        generateScenesBtn.setStyle("-fx-background-color: #d97706; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 6 12; -fx-background-radius: 4;");
        generateScenesBtn.setOnAction(e -> {
            if (this.onGenerateScenes != null) {
                this.onGenerateScenes.run();
            }
        });

        // Step 3 Button: Generate / Regenerate Prose
        generateProseBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 6 12; -fx-background-radius: 4;");
        generateProseBtn.setOnAction(e -> {
            if (this.onGenerateProse != null) {
                this.onGenerateProse.run();
            }
        });

        topBar.getChildren().addAll(
                new Label("Search:"),
                searchField,
                new Label("Category:"),
                categoryCombo,
                new Label("Status:"),
                cutCombo,
                spacer,
                statsLabel,
                generateScenesBtn,
                generateProseBtn
        );
        rootPane.setTop(topBar);

        // --- SplitPane: Left TableView, Right Detail View ---
        SplitPane splitPane = new SplitPane();

        VBox leftPane = new VBox(8);
        leftPane.setPadding(new Insets(6));
        leftPane.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-radius: 6;");

        Label tableHeader = new Label("🎬 Story Scenes & Dramaturgy Framing");
        tableHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");

        setupTableColumns();
        Label placeholder = new Label("No scenes curated yet. Run simulation and click '🎬 3. Generate Scenes'.");
        placeholder.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
        scenesTable.setPlaceholder(placeholder);
        scenesTable.setItems(filteredChaptersList);
        VBox.setVgrow(scenesTable, Priority.ALWAYS);

        scenesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selectedChapter) -> {
            renderSceneDetail(selectedChapter);
        });

        leftPane.getChildren().addAll(tableHeader, scenesTable);

        // Right ScrollPane
        detailBox.setPadding(new Insets(16));
        detailBox.setStyle("-fx-background-color: #f8fafc;");
        ScrollPane rightScroll = new ScrollPane(detailBox);
        rightScroll.setFitToWidth(true);
        rightScroll.setStyle("-fx-background-color: transparent; -fx-background: #f8fafc;");

        splitPane.getItems().addAll(leftPane, rightScroll);
        splitPane.setDividerPositions(0.48);

        rootPane.setCenter(splitPane);

        renderSceneDetail(null);
        return rootPane;
    }

    // Overload for backward compatibility
    public BorderPane createView(Consumer<Integer> onJumpToStep, Runnable onGenerateProse) {
        return createView(onJumpToStep, null, onGenerateProse, null);
    }

    private void setupTableColumns() {
        scenesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Chapter, Number> numCol = new TableColumn<>("#");
        numCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().sceneNumber()));
        numCol.setPrefWidth(42);
        numCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<Chapter, String> timeCol = new TableColumn<>("Time / Span");
        timeCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().timeSpan() != null && !c.getValue().timeSpan().isBlank()
                        ? c.getValue().timeSpan()
                        : ("Step " + c.getValue().timeStep())
        ));
        timeCol.setPrefWidth(125);

        TableColumn<Chapter, String> locCol = new TableColumn<>("Location");
        locCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().locationName() != null ? c.getValue().locationName() : "Unknown"
        ));
        locCol.setPrefWidth(120);

        TableColumn<Chapter, String> focusCol = new TableColumn<>("Dramatic Focus");
        focusCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().dramaticFocus() != null ? c.getValue().dramaticFocus() : "Scene Narrative"
        ));
        focusCol.setPrefWidth(180);

        TableColumn<Chapter, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().category() != null ? c.getValue().category().name() : "SUPPORTING"
        ));
        catCol.setPrefWidth(95);
        catCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    badge.setStyle(getCategoryBadgeStyle(item));
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<Chapter, String> impCol = new TableColumn<>("Imp");
        impCol.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.ROOT, "%.1f", c.getValue().importanceScore())));
        impCol.setPrefWidth(48);
        impCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<Chapter, String> suspCol = new TableColumn<>("Susp");
        suspCol.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.ROOT, "%.1f", c.getValue().suspenseScore())));
        suspCol.setPrefWidth(48);
        suspCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<Chapter, String> cutCol = new TableColumn<>("Cut");
        cutCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isSelected() ? "Final Cut" : "Cut/Fluff"));
        cutCol.setPrefWidth(85);
        cutCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    boolean inCut = "Final Cut".equals(item);
                    Label badge = new Label(inCut ? "✓ Included" : "✂ Cut");
                    String style = inCut
                            ? "-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4;"
                            : "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4;";
                    badge.setStyle(style);
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        scenesTable.getColumns().setAll(numCol, timeCol, locCol, focusCol, catCol, impCol, suspCol, cutCol);
    }

    public void updateScenes(List<Chapter> chapters, SimulationRepository state) {
        this.currentState = state;
        List<Chapter> available = (chapters != null) ? new ArrayList<>(chapters) : Collections.emptyList();

        masterChaptersList.setAll(available);
        applyFilters();

        // Update button texts depending on state
        boolean hasScenes = !masterChaptersList.isEmpty();
        boolean hasProse = masterChaptersList.stream().anyMatch(Chapter::hasProse);

        generateScenesBtn.setText(hasScenes ? "🔄 Regenerate Scenes" : "🎬 Generate Scenes");
        generateProseBtn.setText(hasProse ? "🔄 Regenerate Prose" : "✍️ Generate Prose");
        generateProseBtn.setDisable(!hasScenes);

        // Preserve selection or select first
        if (!filteredChaptersList.isEmpty()) {
            Chapter currentSelected = scenesTable.getSelectionModel().getSelectedItem();
            if (currentSelected != null && filteredChaptersList.contains(currentSelected)) {
                scenesTable.getSelectionModel().select(currentSelected);
            } else {
                scenesTable.getSelectionModel().select(0);
            }
        } else {
            renderSceneDetail(null);
        }
    }

    private void applyFilters() {
        String search = searchField.getText();
        String cat = categoryCombo.getValue();
        String cut = cutCombo.getValue();

        List<Chapter> filtered = filterChapters(masterChaptersList, search, cat, cut);
        filteredChaptersList.setAll(filtered);

        long inCutCount = masterChaptersList.stream().filter(Chapter::isSelected).count();
        int totalCount = masterChaptersList.size();
        int showingCount = filteredChaptersList.size();
        int totalWords = masterChaptersList.stream().mapToInt(Chapter::wordCount).sum();

        if (totalWords > 0) {
            statsLabel.setText(String.format("Showing %d of %d scenes | In Final Cut: %d | Total Words: %,d", showingCount, totalCount, inCutCount, totalWords));
        } else {
            statsLabel.setText(String.format("Showing %d of %d scenes | In Final Cut: %d", showingCount, totalCount, inCutCount));
        }
    }

    private void renderSceneDetail(Chapter chapter) {
        detailBox.getChildren().clear();

        if (chapter == null) {
            VBox emptyPlaceholderBox = new VBox(12);
            emptyPlaceholderBox.setAlignment(Pos.CENTER);
            emptyPlaceholderBox.setPadding(new Insets(40));

            Label placeholder = new Label("No scene selected.");
            placeholder.setStyle("-fx-text-fill: #64748b; -fx-font-weight: bold; -fx-font-size: 16px;");

            Label desc = new Label("Select a scene from the table on the left, or click '🎬 Generate Scenes' to frame scenes from recorded simulation events.");
            desc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
            desc.setWrapText(true);

            emptyPlaceholderBox.getChildren().addAll(placeholder, desc);
            detailBox.getChildren().add(emptyPlaceholderBox);
            return;
        }

        // --- Card 1: Header & Dramaturgy Framing ---
        VBox headerCard = new VBox(10);
        headerCard.setPadding(new Insets(16));
        headerCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbd5e1; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.03), 4, 0, 0, 1);");

        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(String.format("Scene %d: %s", chapter.sceneNumber(),
                chapter.dramaticFocus() != null ? chapter.dramaticFocus() : "Scene Narrative"));
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        titleLabel.setWrapText(true);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        titleRow.getChildren().add(titleLabel);

        // Subtitle row: Location & Time
        HBox metaRow = new HBox(16);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        Label locLabel = new Label("📍 Location: " + (chapter.locationName() != null ? chapter.locationName() : "Unknown"));
        locLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155; -fx-font-size: 13px;");

        String timeInfo = chapter.timeSpan() != null && !chapter.timeSpan().isBlank() ? chapter.timeSpan() : ("Step " + chapter.timeStep());
        Label timeLabel = new Label("🕒 " + timeInfo);
        timeLabel.setStyle("-fx-text-fill: #475569; -fx-font-size: 13px;");

        metaRow.getChildren().addAll(locLabel, timeLabel);

        // Badges row
        HBox badgesRow = new HBox(8);
        badgesRow.setAlignment(Pos.CENTER_LEFT);

        String catName = chapter.category() != null ? chapter.category().name() : "SUPPORTING";
        Label catBadge = new Label("Category: " + catName);
        catBadge.setStyle(getCategoryBadgeStyle(catName));

        boolean inCut = chapter.isSelected();
        Label cutBadge = new Label(inCut ? "✓ In Final Cut" : "✂ Excluded from Cut");
        cutBadge.setStyle(inCut
                ? "-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4;"
                : "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4;");

        badgesRow.getChildren().addAll(catBadge, cutBadge);

        if (chapter.isPivotal()) {
            Label pivotalBadge = new Label("⚡ PIVOTAL CLIMAX");
            pivotalBadge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4;");
            badgesRow.getChildren().add(pivotalBadge);
        }
        if (chapter.isFluff()) {
            Label fluffBadge = new Label("☕ ROUTINE / FLUFF");
            fluffBadge.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #4b5563; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4;");
            badgesRow.getChildren().add(fluffBadge);
        }

        // Metrics pills
        HBox metricsRow = new HBox(12);
        metricsRow.setPadding(new Insets(6, 0, 0, 0));
        metricsRow.getChildren().addAll(
                buildMetricCard("Narrative Importance", String.format(Locale.ROOT, "★ %.1f / 10", chapter.importanceScore()), "#2563eb"),
                buildMetricCard("Suspense Tension", String.format(Locale.ROOT, "⚡ %.1f / 10", chapter.suspenseScore()), "#d97706"),
                buildMetricCard("Dramatic Composite", String.format(Locale.ROOT, "◆ %.1f / 10",
                        (chapter.importanceScore() * 0.6) + (chapter.suspenseScore() * 0.4)), "#7c3aed")
        );

        // Actions: Jump to timeline
        HBox actionsRow = new HBox(10);
        actionsRow.setAlignment(Pos.CENTER_LEFT);
        actionsRow.setPadding(new Insets(6, 0, 0, 0));

        int start = chapter.startStep() > 0 ? chapter.startStep() : chapter.timeStep();
        int end = chapter.endStep() > 0 ? chapter.endStep() : start;

        Button jumpStartBtn = new Button("⏱️ Jump to Scene Start (Step " + start + ")");
        jumpStartBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 5 12; -fx-background-radius: 4;");
        jumpStartBtn.setOnAction(e -> {
            if (onJumpToStep != null) {
                onJumpToStep.accept(start);
            }
        });
        actionsRow.getChildren().add(jumpStartBtn);

        if (end > start) {
            Button jumpEndBtn = new Button("⏱️ Climax / Cut (Step " + end + ")");
            jumpEndBtn.setStyle("-fx-background-color: #475569; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 5 12; -fx-background-radius: 4;");
            jumpEndBtn.setOnAction(e -> {
                if (onJumpToStep != null) {
                    onJumpToStep.accept(end);
                }
            });
            actionsRow.getChildren().add(jumpEndBtn);
        }

        headerCard.getChildren().addAll(titleRow, metaRow, badgesRow, metricsRow, actionsRow);
        detailBox.getChildren().add(headerCard);

        // --- Card 1.5: Director's Editorial Guidance (if available) ---
        if ((chapter.directorNotes() != null && !chapter.directorNotes().isBlank())
                || (chapter.openingHook() != null && !chapter.openingHook().isBlank())
                || (chapter.sceneButton() != null && !chapter.sceneButton().isBlank())
                || (chapter.subtext() != null && !chapter.subtext().isEmpty())) {

            VBox directorCard = new VBox(8);
            directorCard.setPadding(new Insets(14));
            directorCard.setStyle("-fx-background-color: #fefce8; -fx-border-color: #fde047; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.02), 4, 0, 0, 1);");

            Label directorCardTitle = new Label("🎬 Director's Editorial Guidance");
            directorCardTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #854d0e;");
            directorCard.getChildren().add(directorCardTitle);

            if (chapter.directorNotes() != null && !chapter.directorNotes().isBlank()) {
                Label notesLabel = new Label("Directorial Notes: " + chapter.directorNotes());
                notesLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #713f12; -fx-font-size: 12px;");
                notesLabel.setWrapText(true);
                directorCard.getChildren().add(notesLabel);
            }

            if (chapter.openingHook() != null && !chapter.openingHook().isBlank()) {
                Label hookLabel = new Label("🎣 Opening Hook: " + chapter.openingHook());
                hookLabel.setStyle("-fx-text-fill: #1e3a8a; -fx-font-size: 12px; -fx-font-weight: bold;");
                hookLabel.setWrapText(true);
                directorCard.getChildren().add(hookLabel);
            }

            if (chapter.sceneButton() != null && !chapter.sceneButton().isBlank()) {
                Label buttonLabel = new Label("🎯 Scene Button / Climax: " + chapter.sceneButton());
                buttonLabel.setStyle("-fx-text-fill: #991b1b; -fx-font-size: 12px; -fx-font-weight: bold;");
                buttonLabel.setWrapText(true);
                directorCard.getChildren().add(buttonLabel);
            }

            if (chapter.subtext() != null && !chapter.subtext().isEmpty()) {
                VBox subtextBox = new VBox(4);
                Label subtextTitle = new Label("🕵️ Subtext & Concealed Secrets:");
                subtextTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 11px;");
                subtextBox.getChildren().add(subtextTitle);
                for (String s : chapter.subtext()) {
                    Label item = new Label("• " + s);
                    item.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px;");
                    item.setWrapText(true);
                    subtextBox.getChildren().add(item);
                }
                directorCard.getChildren().add(subtextBox);
            }

            detailBox.getChildren().add(directorCard);
        }

        // --- Card 2: Prose & Narrative (with Single-Scene Regeneration) ---
        VBox proseCard = new VBox(10);
        proseCard.setPadding(new Insets(16));
        proseCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbd5e1; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.03), 4, 0, 0, 1);");

        HBox proseHeaderRow = new HBox(10);
        proseHeaderRow.setAlignment(Pos.CENTER_LEFT);

        boolean isResolution = chapter.dramaticFocus() != null
                && (chapter.dramaticFocus().contains("The Grand Reveal") || chapter.dramaticFocus().contains("Resolution") || "Finale & Denouement".equalsIgnoreCase(chapter.timeSpan()));
        boolean hasFailedProse = chapter.text() != null && chapter.text().contains("[UPPLÖSNINGSKAPITLET KUNDE INTE GENERERAS");

        Label proseCardTitle = new Label(isResolution ? "🎬 The Grand Reveal & Resolution (Finale)" : "📖 Scene Prose & Narrative");
        proseCardTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        proseHeaderRow.getChildren().add(proseCardTitle);

        boolean hasProse = chapter.hasProse();

        if (hasProse) {
            int wordCount = chapter.wordCount();
            if (!hasFailedProse) {
                Label wordsBadge = new Label(String.format("%,d words", wordCount));
                wordsBadge.setStyle("-fx-background-color: #ede9fe; -fx-text-fill: #6d28d9; -fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4;");
                proseHeaderRow.getChildren().add(wordsBadge);
            } else {
                Label failedBadge = new Label("⚠️ LLM Failed");
                failedBadge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4;");
                proseHeaderRow.getChildren().add(failedBadge);
            }

            Region proseSpacer = new Region();
            HBox.setHgrow(proseSpacer, Priority.ALWAYS);
            proseHeaderRow.getChildren().add(proseSpacer);

            // Single-scene regenerate button!
            Button regenSceneBtn = new Button(isResolution ? "🔄 Regenerate Grand Reveal via LLM" : "🔄 Regenerate Prose for this Scene");
            regenSceneBtn.setStyle("-fx-background-color: " + (isResolution ? "#d97706" : "#8e44ad") + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 4;");
            regenSceneBtn.setOnAction(e -> {
                if (onRegenerateSingleSceneProse != null) {
                    onRegenerateSingleSceneProse.accept(chapter);
                }
            });
            proseHeaderRow.getChildren().add(regenSceneBtn);

            if (hasFailedProse) {
                HBox warningBox = new HBox(12);
                warningBox.setAlignment(Pos.CENTER_LEFT);
                warningBox.setPadding(new Insets(10, 14, 10, 14));
                warningBox.setStyle("-fx-background-color: #fef2f2; -fx-border-color: #f87171; -fx-border-radius: 6px; -fx-background-radius: 6px;");

                Label warningIcon = new Label("⚠️");
                warningIcon.setStyle("-fx-font-size: 20px;");

                VBox warnTextBox = new VBox(2);
                Label warningTitle = new Label("Genereringen av upplösningen via LLM misslyckades.");
                warningTitle.setStyle("-fx-text-fill: #991b1b; -fx-font-weight: bold; -fx-font-size: 13px;");
                Label warningSub = new Label("Ett utkast visas nedan. Du kan klicka för att försöka generera finalen igen med LLM.");
                warningSub.setStyle("-fx-text-fill: #b91c1c; -fx-font-size: 11px;");
                warnTextBox.getChildren().addAll(warningTitle, warningSub);

                Region wSpacer = new Region();
                HBox.setHgrow(wSpacer, Priority.ALWAYS);

                Button retryBtn = new Button("🔄 Generera om med LLM");
                retryBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 5 12; -fx-background-radius: 4;");
                retryBtn.setOnAction(e -> {
                    if (onRegenerateSingleSceneProse != null) {
                        onRegenerateSingleSceneProse.accept(chapter);
                    }
                });

                warningBox.getChildren().addAll(warningIcon, warnTextBox, wSpacer, retryBtn);
                proseCard.getChildren().add(warningBox);
            }

            TextArea proseArea = new TextArea(chapter.text());
            proseArea.setWrapText(true);
            proseArea.setEditable(false);
            proseArea.setStyle("-fx-font-family: 'Georgia', 'Cambria', serif; -fx-font-size: 14px; -fx-line-spacing: 4px; -fx-text-fill: #1e293b; -fx-background-color: #fdfdfd; -fx-border-color: #e2e8f0; -fx-border-radius: 6;");
            proseArea.setPrefRowCount(Math.max(6, Math.min(20, chapter.text().split("\n").length + 2)));

            proseCard.getChildren().addAll(proseHeaderRow, proseArea);
        } else {
            VBox noProseBox = new VBox(8);
            noProseBox.setPadding(new Insets(12));
            noProseBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6;");

            Label noProseLabel = new Label("Prose has not been generated for this scene yet.");
            noProseLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 13px;");

            Label noProseDesc = new Label("This scene has been curated and framed from simulation events. Click 'Generate Prose' to compose literary prose for all scenes, or write just this scene.");
            noProseDesc.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
            noProseDesc.setWrapText(true);

            HBox proseActionBox = new HBox(10);
            proseActionBox.setAlignment(Pos.CENTER_LEFT);

            Button writeSceneBtn = new Button("✍️ Write Prose for this Scene");
            writeSceneBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 6 12; -fx-background-radius: 4;");
            writeSceneBtn.setOnAction(e -> {
                if (onRegenerateSingleSceneProse != null) {
                    onRegenerateSingleSceneProse.accept(chapter);
                }
            });

            Button writeAllBtn = new Button("✍️ Generate Prose for All Scenes");
            writeAllBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 6 12; -fx-background-radius: 4;");
            writeAllBtn.setOnAction(e -> {
                if (onGenerateProse != null) {
                    onGenerateProse.run();
                }
            });

            proseActionBox.getChildren().addAll(writeSceneBtn, writeAllBtn);
            noProseBox.getChildren().addAll(noProseLabel, noProseDesc, proseActionBox);
            proseCard.getChildren().addAll(proseHeaderRow, noProseBox);
        }

        detailBox.getChildren().add(proseCard);

        // --- Card 3: Underlying Simulation Events ---
        VBox eventsCard = buildEventsCard(chapter);
        if (eventsCard != null) {
            detailBox.getChildren().add(eventsCard);
        }
    }

    private VBox buildEventsCard(Chapter chapter) {
        if (currentState == null) {
            return null;
        }

        int start = chapter.startStep() > 0 ? chapter.startStep() : chapter.timeStep();
        int end = chapter.endStep() > 0 ? chapter.endStep() : start;

        List<TimeLocationEvent> sceneEvents = new ArrayList<>();
        boolean foundAny = false;
        for (int s = start; s <= end; s++) {
            List<TimeLocationEvent> stepEvents = currentState.getEventsForStep(s);
            if (stepEvents != null && !stepEvents.isEmpty()) {
                foundAny = true;
                for (TimeLocationEvent ev : stepEvents) {
                    if (chapter.locationName() == null
                            || chapter.locationName().equalsIgnoreCase(ev.getLocationName())
                            || Objects.equals(chapter.locationName(), ev.getLocationId())) {
                        sceneEvents.add(ev);
                    }
                }
            }
        }

        if (!foundAny) {
            List<TimeLocationEvent> allEvents = currentState.ensureOrSynthesizeTimelineEvents();
            if (allEvents != null) {
                sceneEvents = allEvents.stream()
                        .filter(e -> e.getTimeStep() >= start && e.getTimeStep() <= end)
                        .collect(Collectors.toList());
            }
        }

        VBox eventsCard = new VBox(10);
        eventsCard.setPadding(new Insets(16));
        eventsCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbd5e1; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.03), 4, 0, 0, 1);");

        Label eventsTitle = new Label(String.format("⚡ Underlying Simulation Events (Steps %d - %d: %d Events)", start, end, sceneEvents.size()));
        eventsTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        eventsCard.getChildren().add(eventsTitle);

        if (sceneEvents.isEmpty()) {
            Label noEvents = new Label("No raw simulation events recorded for this step interval.");
            noEvents.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 12px;");
            eventsCard.getChildren().add(noEvents);
            return eventsCard;
        }

        VBox listContainer = new VBox(8);
        for (TimeLocationEvent ev : sceneEvents) {
            VBox evBox = new VBox(4);
            evBox.setPadding(new Insets(8));
            evBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6;");

            HBox topRow = new HBox(8);
            topRow.setAlignment(Pos.CENTER_LEFT);

            Label stepBadge = new Label(String.format("Step %d (%s)", ev.getTimeStep(), currentState.getClockTime(ev.getTimeStep())));
            stepBadge.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #1e293b; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4;");

            Label locLabel = new Label("📍 " + (ev.getLocationName() != null ? ev.getLocationName() : "Room"));
            locLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155; -fx-font-size: 12px;");

            Label scoreLabel = new Label(String.format("Drama: %.1f", ev.getDramaScore()));
            scoreLabel.setStyle("-fx-text-fill: #7c3aed; -fx-font-size: 11px; -fx-font-weight: bold;");

            topRow.getChildren().addAll(stepBadge, locLabel, scoreLabel);

            if (ev.hasMurderAction()) {
                Label alert = new Label("🔴 MURDER OCCURRED");
                alert.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4;");
                topRow.getChildren().add(alert);
            }
            if (ev.hasAccuseAction()) {
                Label alert = new Label("🟡 ACCUSATION");
                alert.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4;");
                topRow.getChildren().add(alert);
            }

            evBox.getChildren().add(topRow);

            if (ev.getPresentAgentNames() != null && !ev.getPresentAgentNames().isEmpty()) {
                Label agentsLabel = new Label("👥 Present: " + String.join(", ", ev.getPresentAgentNames()));
                agentsLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
                evBox.getChildren().add(agentsLabel);
            }

            if (ev.getActions() != null && !ev.getActions().isEmpty()) {
                for (Action a : ev.getActions()) {
                    String actionDesc = formatActionSummary(a);
                    Label actionLabel = new Label("  • " + actionDesc);
                    actionLabel.setStyle("-fx-text-fill: #1e293b; -fx-font-size: 12px;");
                    actionLabel.setWrapText(true);
                    evBox.getChildren().add(actionLabel);
                }
            }

            listContainer.getChildren().add(evBox);
        }

        eventsCard.getChildren().add(listContainer);
        return eventsCard;
    }

    private String formatActionSummary(Action a) {
        StringBuilder sb = new StringBuilder();
        if (a.getActorId() != null) {
            sb.append("[").append(a.getActorId()).append("] ");
        }
        sb.append(a.getActionType().name());
        if (a.getTargetAgentId() != null) {
            sb.append(" -> target: ").append(a.getTargetAgentId());
        }
        if (a.getTargetLocationId() != null) {
            sb.append(" -> loc: ").append(a.getTargetLocationId());
        }
        if (a.getDetails() != null && !a.getDetails().isBlank()) {
            sb.append(": ").append(a.getDetails());
        }
        if (a.getInteraction() != null && !a.getInteraction().isBlank()) {
            sb.append(" (").append(a.getInteraction()).append(")");
        }
        if (a.hasNewKnowledge()) {
            sb.append(" [💡 Clue: ").append(a.getNewKnowledgeFact()).append("]");
        }
        return sb.toString();
    }

    private VBox buildMetricCard(String label, String value, String colorHex) {
        VBox box = new VBox(2);
        box.setPadding(new Insets(6, 12, 6, 12));
        box.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label valLabel = new Label(value);
        valLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + colorHex + ";");

        Label titleLabel = new Label(label);
        titleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        box.getChildren().addAll(valLabel, titleLabel);
        return box;
    }

    private static String getCategoryBadgeStyle(String category) {
        if ("ESSENTIAL".equalsIgnoreCase(category)) {
            return "-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4;";
        } else if ("DISPENSABLE".equalsIgnoreCase(category)) {
            return "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4;";
        } else {
            return "-fx-background-color: #dbeafe; -fx-text-fill: #1d4ed8; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4;";
        }
    }

    public static List<Chapter> filterChapters(
            List<Chapter> chapters,
            String searchText,
            String categoryFilter,
            String cutFilter
    ) {
        if (chapters == null) {
            return Collections.emptyList();
        }
        String query = searchText != null ? searchText.trim().toLowerCase(Locale.ROOT) : "";
        return chapters.stream().filter(c -> {
            if (categoryFilter != null && !"All Categories".equalsIgnoreCase(categoryFilter)) {
                String catName = c.category() != null ? c.category().name() : "";
                if (!categoryFilter.equalsIgnoreCase(catName)) {
                    return false;
                }
            }
            if (cutFilter != null && !"All Scenes".equalsIgnoreCase(cutFilter)) {
                if ("Final Cut Only".equalsIgnoreCase(cutFilter) && !c.isSelected()) {
                    return false;
                }
                if ("Cut / Fluff Only".equalsIgnoreCase(cutFilter) && c.isSelected()) {
                    return false;
                }
            }
            if (!query.isEmpty()) {
                boolean matchNum = String.valueOf(c.sceneNumber()).contains(query);
                boolean matchLoc = c.locationName() != null && c.locationName().toLowerCase(Locale.ROOT).contains(query);
                boolean matchFocus = c.dramaticFocus() != null && c.dramaticFocus().toLowerCase(Locale.ROOT).contains(query);
                boolean matchTime = c.timeSpan() != null && c.timeSpan().toLowerCase(Locale.ROOT).contains(query);
                boolean matchText = c.text() != null && c.text().toLowerCase(Locale.ROOT).contains(query);
                return matchNum || matchLoc || matchFocus || matchTime || matchText;
            }
            return true;
        }).collect(Collectors.toList());
    }

    public static List<Chapter> getOrSynthesizeChapters(List<Chapter> existingChapters, SimulationRepository state) {
        if (existingChapters != null && !existingChapters.isEmpty()) {
            return new ArrayList<>(existingChapters);
        }
        if (state == null || state.getCurrentStep() <= 0) {
            return Collections.emptyList();
        }
        List<TimeLocationEvent> rawEvents = state.ensureOrSynthesizeTimelineEvents();
        if (rawEvents == null || rawEvents.isEmpty()) {
            return Collections.emptyList();
        }
        EditorService editorService = new EditorService();
        List<Scene> scenes = editorService.createScenes(rawEvents, state);
        List<Chapter> result = new ArrayList<>();
        for (Scene sc : scenes) {
            String timeSpan = editorService.formatSceneTimeSpan(sc, state);
            Chapter ch = new Chapter(
                    sc.getProseText(),
                    sc.isPivotal(),
                    sc.isDispensable(),
                    sc.getEndStep(),
                    sc.getSceneNumber(),
                    sc.getPrimaryLocationName(),
                    sc.getNarrativeImportanceScore(),
                    sc.getSuspenseScore(),
                    sc.getCategory(),
                    sc.getDramaticFocus(),
                    sc.getStartStep(),
                    sc.getEndStep(),
                    timeSpan,
                    sc.isSelected(),
                    sc.getDirectorNotes(),
                    sc.getOpeningHook(),
                    sc.getSceneButton(),
                    sc.getSubtext()
            );
            result.add(ch);
        }
        return result;
    }
}
