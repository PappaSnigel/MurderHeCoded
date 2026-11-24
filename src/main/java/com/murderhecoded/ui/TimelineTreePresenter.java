package com.murderhecoded.ui;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.GlobalEvent;
import com.murderhecoded.domain.TimeLocationEvent;
import com.murderhecoded.repository.SimulationRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@SuppressWarnings("PMD")
public class TimelineTreePresenter {

    public enum NodeType { ROOT, STEP, EVENT, GLOBAL_EVENT }

    public record TimelineNodeData(
            String label,
            int step,
            TimeLocationEvent event,
            GlobalEvent globalEvent,
            NodeType type
    ) {
        @Override
        public String toString() {
            return label;
        }
    }

    public static BorderPane createView(TreeView<TimelineNodeData> treeView,
                                        VBox detailContentBox,
                                        SimulationRepository state,
                                        Consumer<Integer> onJumpToStep) {
        BorderPane pane = new BorderPane();

        SplitPane splitPane = new SplitPane();

        // Left Pane: Search Filter + TreeView
        VBox leftBox = new VBox(8);
        leftBox.setPadding(new Insets(10));
        leftBox.setStyle("-fx-background-color: #f8fafc;");

        Label treeHeader = new Label("Mystery Timeline & Events");
        treeHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");

        TextField searchField = new TextField();
        searchField.setPromptText("Filter scenes, locations, characters...");
        searchField.setStyle("-fx-font-size: 12px;");
        searchField.textProperty().addListener((obs, oldVal, newVal) ->
                updateTreeView(treeView, state, newVal));

        treeView.setShowRoot(false);
        treeView.setStyle("-fx-font-family: 'Segoe UI', system-ui, sans-serif; -fx-font-size: 12px;");
        VBox.setVgrow(treeView, Priority.ALWAYS);

        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null) {
                renderDetail(detailContentBox, newVal.getValue(), state, onJumpToStep);
            }
        });

        leftBox.getChildren().addAll(treeHeader, searchField, treeView);

        // Right Pane: Detail Card
        detailContentBox.setPadding(new Insets(16));
        detailContentBox.setSpacing(14);

        ScrollPane rightScroll = new ScrollPane(detailContentBox);
        rightScroll.setFitToWidth(true);
        rightScroll.setStyle("-fx-background-color: transparent; -fx-background: #f1f5f9;");

        splitPane.getItems().addAll(leftBox, rightScroll);
        splitPane.setDividerPositions(0.36);

        pane.setCenter(splitPane);
        return pane;
    }

    public static void updateTreeView(TreeView<TimelineNodeData> treeView,
                                      SimulationRepository state,
                                      String filterText) {
        if (treeView == null || state == null) {
            return;
        }

        TreeItem<TimelineNodeData> root = new TreeItem<>(new TimelineNodeData("Root", 0, null, null, NodeType.ROOT));
        root.setExpanded(true);

        int currentStep = state.getCurrentStep();
        String query = filterText != null ? filterText.trim().toLowerCase(Locale.ROOT) : "";

        TreeItem<TimelineNodeData> nodeToSelect = null;

        for (int s = 1; s <= currentStep; s++) {
            List<TimeLocationEvent> events = state.getEventsForStep(s);
            List<GlobalEvent> globalEvents = state.getActiveGlobalEvents(s);

            double dramaSum = 0.0;
            boolean hasMurder = false;
            boolean hasAccuse = false;
            int sceneCount = events != null ? events.size() : 0;

            if (events != null) {
                for (TimeLocationEvent ev : events) {
                    dramaSum += ev.getSuspenseScore();
                    if (ev.hasMurderAction()) hasMurder = true;
                    if (ev.hasAccuseAction()) hasAccuse = true;
                }
            }

            StringBuilder stepLabel = new StringBuilder();
            stepLabel.append(String.format(Locale.ROOT, "Step %d (%s) - %d Scenes", s, state.getClockTime(s), sceneCount));
            if (hasMurder) {
                stepLabel.append(" ☠️ [MURDER]");
            } else if (hasAccuse) {
                stepLabel.append(" ⚖️ [ACCUSATION]");
            }
            stepLabel.append(String.format(Locale.ROOT, " | Drama: %.1f", dramaSum));

            TreeItem<TimelineNodeData> stepItem = new TreeItem<>(new TimelineNodeData(stepLabel.toString(), s, null, null, NodeType.STEP));
            stepItem.setExpanded(s == currentStep || hasMurder || hasAccuse);

            boolean stepMatches = query.isEmpty() || stepLabel.toString().toLowerCase(Locale.ROOT).contains(query);
            boolean anyChildMatches = false;

            // Add Global Events
            for (GlobalEvent ge : globalEvents) {
                String geLabel = "🌐 World Event: " + ge.getName();
                boolean geMatches = query.isEmpty() || geLabel.toLowerCase(Locale.ROOT).contains(query) || ge.getDescription().toLowerCase(Locale.ROOT).contains(query);
                if (geMatches) {
                    anyChildMatches = true;
                    stepItem.getChildren().add(new TreeItem<>(new TimelineNodeData(geLabel, s, null, ge, NodeType.GLOBAL_EVENT)));
                }
            }

            // Add TimeLocationEvents
            if (events != null) {
                for (TimeLocationEvent ev : events) {
                    StringBuilder evLabel = new StringBuilder();
                    String loc = ev.getLocationName() != null ? ev.getLocationName() : "Room";
                    evLabel.append("📍 ").append(loc);
                    evLabel.append(String.format(Locale.ROOT, " [Suspense: %.1f/10]", ev.getSuspenseScore()));

                    if (ev.hasMurderAction()) {
                        evLabel.append(" ☠️ [MURDER]");
                    } else if (ev.hasAccuseAction()) {
                        evLabel.append(" ⚖️ [ACCUSE]");
                    } else if (ev.isMurdererPresent()) {
                        evLabel.append(" 🔪 [Killer Present]");
                    }

                    boolean evMatches = query.isEmpty()
                            || evLabel.toString().toLowerCase(Locale.ROOT).contains(query)
                            || (ev.getPresentAgentNames() != null && ev.getPresentAgentNames().stream().anyMatch(n -> n.toLowerCase(Locale.ROOT).contains(query)))
                            || (ev.getActions() != null && ev.getActions().stream().anyMatch(a -> a.getDetails() != null && a.getDetails().toLowerCase(Locale.ROOT).contains(query)));

                    if (evMatches) {
                        anyChildMatches = true;
                        TreeItem<TimelineNodeData> evItem = new TreeItem<>(new TimelineNodeData(evLabel.toString(), s, ev, null, NodeType.EVENT));
                        stepItem.getChildren().add(evItem);
                        if (nodeToSelect == null && (s == currentStep || hasMurder)) {
                            nodeToSelect = evItem;
                        }
                    }
                }
            }

            if (stepMatches || anyChildMatches) {
                root.getChildren().add(stepItem);
                if (nodeToSelect == null) {
                    nodeToSelect = stepItem;
                }
            }
        }

        treeView.setRoot(root);
        if (nodeToSelect != null) {
            treeView.getSelectionModel().select(nodeToSelect);
        }
    }

    public static void renderDetail(VBox detailContentBox,
                                    TimelineNodeData data,
                                    SimulationRepository state,
                                    Consumer<Integer> onJumpToStep) {
        if (detailContentBox == null || data == null) {
            return;
        }

        detailContentBox.getChildren().clear();

        if (data.type() == NodeType.EVENT && data.event() != null) {
            renderEventDetail(detailContentBox, data.event(), data.step(), state, onJumpToStep);
        } else if (data.type() == NodeType.STEP) {
            renderStepDetail(detailContentBox, data.step(), state, onJumpToStep);
        } else if (data.type() == NodeType.GLOBAL_EVENT && data.globalEvent() != null) {
            renderGlobalEventDetail(detailContentBox, data.globalEvent(), data.step(), state, onJumpToStep);
        }
    }

    private static void renderEventDetail(VBox box,
                                          TimeLocationEvent ev,
                                          int step,
                                          SimulationRepository state,
                                          Consumer<Integer> onJumpToStep) {
        // 1. Header Card
        VBox headerCard = new VBox(6);
        headerCard.setPadding(new Insets(12));
        headerCard.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 8px; -fx-border-color: #cbd5e1; -fx-border-radius: 8px;");

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label locName = new Label("📍 " + (ev.getLocationName() != null ? ev.getLocationName() : "Room Scene"));
        locName.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #0f172a;");
        HBox.setHgrow(locName, Priority.ALWAYS);

        double suspense = ev.getSuspenseScore();
        String suspenseColor = suspense >= 7.5 ? "#dc2626" : (suspense >= 4.5 ? "#d97706" : "#2563eb");
        String suspenseBg = suspense >= 7.5 ? "#fef2f2" : (suspense >= 4.5 ? "#fffbeb" : "#eff6ff");
        Label suspenseBadge = new Label(String.format(Locale.ROOT, "Suspense: %.1f / 10.0", suspense));
        suspenseBadge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 12px; -fx-border-color: %s; -fx-border-radius: 12px;", suspenseBg, suspenseColor, suspenseColor));

        titleRow.getChildren().addAll(locName, suspenseBadge);

        String clockTime = state != null ? state.getClockTime(step) : "Step " + step;
        Label timeLabel = new Label(String.format("Time: %s (Step %d, Sequence #%d)", clockTime, step, ev.getSequenceNumber()));
        timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");

        headerCard.getChildren().addAll(titleRow, timeLabel);
        box.getChildren().add(headerCard);

        // 2. Murderer Pressure & Dramaturgy Directive Banner (if murderer present)
        if (ev.isMurdererPresent()) {
            VBox killerCard = new VBox(6);
            killerCard.setPadding(new Insets(12));
            killerCard.setStyle("-fx-background-color: #fff1f2; -fx-border-color: #e11d48; -fx-border-width: 2px; -fx-background-radius: 8px; -fx-border-radius: 8px;");

            Label killerTitle = new Label("🔪 DRAMATURGIC MURDERER PRESSURE REPORT");
            killerTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #9f1239;");

            Label killerDirective = new Label(ev.getMurdererPressureInfo() != null && !ev.getMurdererPressureInfo().isBlank()
                    ? ev.getMurdererPressureInfo()
                    : "The murderer is present in this room under live dramaturgic tension (" + String.format(Locale.ROOT, "%.1f", suspense) + "/10.0).");
            killerDirective.setStyle("-fx-font-size: 12px; -fx-text-fill: #881337; -fx-font-weight: bold;");
            killerDirective.setWrapText(true);

            killerCard.getChildren().addAll(killerTitle, killerDirective);
            box.getChildren().add(killerCard);
        }

        // 3. Characters Present
        VBox charCard = new VBox(6);
        charCard.setPadding(new Insets(10));
        charCard.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 8px; -fx-border-color: #e2e8f0; -fx-border-radius: 8px;");

        Label charHeader = new Label("Characters Present:");
        charHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #334155;");

        FlowPane charFlow = new FlowPane(6, 6);
        List<String> presentNames = ev.getPresentAgentNames();
        if (presentNames != null && !presentNames.isEmpty()) {
            for (String name : presentNames) {
                Label badge = new Label(name);
                badge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #1e293b; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-border-color: #cbd5e1; -fx-border-radius: 4px; -fx-font-size: 11px;");
                charFlow.getChildren().add(badge);
            }
        } else {
            Label empty = new Label("No characters directly present.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 11px;");
            charFlow.getChildren().add(empty);
        }
        charCard.getChildren().addAll(charHeader, charFlow);
        box.getChildren().add(charCard);

        // 4. Actions Taken
        VBox actionsCard = new VBox(8);
        actionsCard.setPadding(new Insets(10));
        actionsCard.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 8px; -fx-border-color: #e2e8f0; -fx-border-radius: 8px;");

        Label actionsHeader = new Label("Actions Executed (" + (ev.getActions() != null ? ev.getActions().size() : 0) + "):");
        actionsHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
        actionsCard.getChildren().add(actionsHeader);

        if (ev.getActions() != null && !ev.getActions().isEmpty()) {
            for (Action act : ev.getActions()) {
                VBox actRow = new VBox(3);
                actRow.setPadding(new Insets(6, 8, 6, 8));
                actRow.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 6px; -fx-border-color: #e2e8f0; -fx-border-radius: 6px;");

                HBox actTop = new HBox(8);
                actTop.setAlignment(Pos.CENTER_LEFT);

                String actorName = act.getActorId();
                if (state != null && state.getAgents().containsKey(actorName)) {
                    actorName = state.getAgents().get(actorName).getName();
                }

                Label actorLabel = new Label(actorName);
                actorLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #0f172a;");

                String typeStyle = switch (act.getActionType()) {
                    case MURDER -> "-fx-background-color: #dc2626; -fx-text-fill: white;";
                    case ACCUSE -> "-fx-background-color: #7c3aed; -fx-text-fill: white;";
                    case SEARCH -> "-fx-background-color: #0284c7; -fx-text-fill: white;";
                    case MANIPULATE_ITEM -> "-fx-background-color: #ea580c; -fx-text-fill: white;";
                    case TALK -> "-fx-background-color: #16a34a; -fx-text-fill: white;";
                    default -> "-fx-background-color: #64748b; -fx-text-fill: white;";
                };
                Label typeBadge = new Label(act.getActionType() != null ? act.getActionType().name() : "ACT");
                typeBadge.setStyle(typeStyle + " -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 4px;");

                actTop.getChildren().addAll(actorLabel, typeBadge);

                Label detailsLabel = new Label(act.getDetails() != null ? act.getDetails() : "No details.");
                detailsLabel.setWrapText(true);
                detailsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");

                actRow.getChildren().addAll(actTop, detailsLabel);

                if (act.getNewKnowledgeFact() != null && !act.getNewKnowledgeFact().isBlank() && !"None".equalsIgnoreCase(act.getNewKnowledgeFact())) {
                    Label fact = new Label("💡 Learned Fact: " + act.getNewKnowledgeFact());
                    fact.setStyle("-fx-font-size: 11px; -fx-text-fill: #0369a1; -fx-font-style: italic;");
                    actRow.getChildren().add(fact);
                }

                actionsCard.getChildren().add(actRow);
            }
        } else {
            Label emptyAct = new Label("No actions executed in this interval.");
            emptyAct.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-font-size: 12px;");
            actionsCard.getChildren().add(emptyAct);
        }
        box.getChildren().add(actionsCard);

        // 5. Knowledge & Clues Discovered
        if (ev.getKnowledgeGained() != null && !ev.getKnowledgeGained().isEmpty()) {
            VBox kCard = new VBox(6);
            kCard.setPadding(new Insets(10));
            kCard.setStyle("-fx-background-color: #f0fdf4; -fx-background-radius: 8px; -fx-border-color: #86efac; -fx-border-radius: 8px;");

            Label kHeader = new Label("🧠 Knowledge Gained in Scene:");
            kHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #166534;");
            kCard.getChildren().add(kHeader);

            for (String k : ev.getKnowledgeGained()) {
                Label kLabel = new Label("• " + k);
                kLabel.setWrapText(true);
                kLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #14532d;");
                kCard.getChildren().add(kLabel);
            }
            box.getChildren().add(kCard);
        }

        // Jump to Step Button
        if (onJumpToStep != null) {
            Button jumpBtn = new Button("⏩ Scrub Timeline to Step " + step);
            jumpBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 14; -fx-background-radius: 6px;");
            jumpBtn.setOnAction(e -> onJumpToStep.accept(step));
            box.getChildren().add(jumpBtn);
        }
    }

    private static void renderStepDetail(VBox box,
                                         int step,
                                         SimulationRepository state,
                                         Consumer<Integer> onJumpToStep) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 8px; -fx-border-color: #cbd5e1; -fx-border-radius: 8px;");

        String timeStr = state != null ? state.getClockTime(step) : "Step " + step;
        Label title = new Label(String.format("Step %d Overview (%s)", step, timeStr));
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #1e293b;");

        List<TimeLocationEvent> events = state != null ? state.getEventsForStep(step) : null;
        double dramaSum = 0.0;
        int sceneCount = events != null ? events.size() : 0;
        if (events != null) {
            dramaSum = events.stream().mapToDouble(TimeLocationEvent::getSuspenseScore).sum();
        }

        Label dramaLabel = new Label(String.format(Locale.ROOT, "Total Dramatic Tension Sum: %.1f | Scenes Taking Place: %d", dramaSum, sceneCount));
        dramaLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569; -fx-font-weight: bold;");

        card.getChildren().addAll(title, dramaLabel);

        if (events != null && !events.isEmpty()) {
            Label listHeader = new Label("Scenes in this step:");
            listHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #334155;");
            card.getChildren().add(listHeader);

            for (TimeLocationEvent ev : events) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(4, 8, 4, 8));
                row.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 4px;");

                Label loc = new Label("📍 " + ev.getLocationName());
                loc.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a;");
                HBox.setHgrow(loc, Priority.ALWAYS);

                Label tension = new Label(String.format(Locale.ROOT, "Suspense: %.1f/10", ev.getSuspenseScore()));
                tension.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

                row.getChildren().addAll(loc, tension);
                card.getChildren().add(row);
            }
        }

        if (onJumpToStep != null) {
            Button jumpBtn = new Button("⏩ Scrub Timeline to Step " + step);
            jumpBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 14; -fx-background-radius: 6px;");
            jumpBtn.setOnAction(e -> onJumpToStep.accept(step));
            card.getChildren().add(jumpBtn);
        }

        box.getChildren().add(card);
    }

    private static void renderGlobalEventDetail(VBox box,
                                               GlobalEvent ge,
                                               int step,
                                               SimulationRepository state,
                                               Consumer<Integer> onJumpToStep) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: #eff6ff; -fx-background-radius: 8px; -fx-border-color: #93c5fd; -fx-border-radius: 8px;");

        Label title = new Label("🌐 " + ge.getName());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #1e3a8a;");

        Label type = new Label("Impact Type: " + ge.getImpactType());
        type.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 2 6; -fx-background-radius: 4px; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label timing = new Label(String.format("Scheduled Step: %d | Duration: %d steps", ge.getScheduledStep(), ge.getDurationSteps()));
        timing.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");

        Label desc = new Label(ge.getDescription());
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");

        card.getChildren().addAll(title, type, timing, desc);
        box.getChildren().add(card);
    }
}
