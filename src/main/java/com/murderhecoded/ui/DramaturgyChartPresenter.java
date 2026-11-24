package com.murderhecoded.ui;

import com.murderhecoded.domain.DramaturgyProfile;
import com.murderhecoded.domain.GlobalEvent;
import com.murderhecoded.domain.TimeLocationEvent;
import com.murderhecoded.repository.SimulationRepository;
import com.murderhecoded.service.DramaturgyDirectorService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@SuppressWarnings("PMD")
public class DramaturgyChartPresenter {

    public static BorderPane createView(LineChart<Number, Number> chart, Label headerLabel, HBox statsBox) {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(14));

        VBox topBox = new VBox(10);
        topBox.setPadding(new Insets(0, 0, 10, 0));

        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #1e293b;");
        Label subtitle = new Label("Dramatic Tension progression across time steps. Compares live scene tension with the ideal 6-Act narrative curve (0.0 – 10.0 scale).");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        statsBox.setAlignment(Pos.CENTER_LEFT);
        statsBox.setSpacing(12);

        topBox.getChildren().addAll(headerLabel, subtitle, statsBox);
        pane.setTop(topBox);

        chart.setTitle("Dramaturgy Curve");
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setCreateSymbols(true);
        VBox.setVgrow(chart, Priority.ALWAYS);
        pane.setCenter(chart);

        // Interpretation footer
        HBox footerBox = new HBox(16);
        footerBox.setPadding(new Insets(8, 0, 0, 0));
        footerBox.setAlignment(Pos.CENTER_LEFT);

        Label legendNote = new Label("💡 Pacing Guide: Dashed line shows the 6-Act target curve (0–10). Solid blue line tracks live peak room drama. Key beats: ☠️ Murder, 🔍 Discovery, ⚖️ Climax. Click any point to scrub the timeline.");
        legendNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569; -fx-font-style: italic;");
        footerBox.getChildren().add(legendNote);
        pane.setBottom(footerBox);

        return pane;
    }

    public static void updateChart(LineChart<Number, Number> chart,
                                   Label headerLabel,
                                   HBox statsBox,
                                   SimulationRepository state,
                                   Consumer<Integer> onJumpToStep) {
        updateChart(chart, headerLabel, statsBox, state, null, state != null ? state.getCurrentStep() : 0, onJumpToStep);
    }

    public static void updateChart(LineChart<Number, Number> chart,
                                   Label headerLabel,
                                   HBox statsBox,
                                   SimulationRepository state,
                                   DramaturgyDirectorService directorService,
                                   Consumer<Integer> onJumpToStep) {
        updateChart(chart, headerLabel, statsBox, state, directorService, state != null ? state.getCurrentStep() : 0, onJumpToStep);
    }

    public static void updateChart(LineChart<Number, Number> chart,
                                   Label headerLabel,
                                   HBox statsBox,
                                   SimulationRepository state,
                                   DramaturgyDirectorService directorService,
                                   int selectedStep,
                                   Consumer<Integer> onJumpToStep) {
        if (chart == null || state == null) {
            return;
        }

        DramaturgyDirectorService director = directorService != null ? directorService : new DramaturgyDirectorService();
        DramaturgyProfile profile = director.resolveProfile(state);

        int currentStep = state.getCurrentStep();
        int targetSteps = state.getTargetSteps() > 0 ? state.getTargetSteps() : Math.max(20, currentStep);
        int maxHorizon = Math.max(targetSteps, currentStep);
        int activeStep = Math.max(0, Math.min(selectedStep, currentStep));

        if (headerLabel != null) {
            if (activeStep == currentStep) {
                headerLabel.setText(String.format("Dramaturgy Tension Curve (Steps 1 – %d)", Math.max(1, currentStep)));
            } else {
                headerLabel.setText(String.format("Dramaturgy Tension Curve (Viewing Step %d of %d)", activeStep, Math.max(1, currentStep)));
            }
        }

        chart.getData().clear();

        // 1. Ideal Target Curve (0.0 - 10.0 scale from DramaturgyDirectorService)
        XYChart.Series<Number, Number> targetSeries = new XYChart.Series<>();
        targetSeries.setName("Ideal Curve (" + profile.name() + ")");
        targetSeries.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.setStyle("-fx-stroke: #94a3b8; -fx-stroke-dash-array: 6 6; -fx-stroke-width: 2px;");
            }
        });

        for (int s = 1; s <= maxHorizon; s++) {
            double targetScore = director.calculateTargetTension(s, targetSteps, profile);
            DramaturgyDirectorService.TensionTrend trend = director.determineTensionTrend(s, targetSteps, profile);
            XYChart.Data<Number, Number> targetData = new XYChart.Data<>(s, targetScore);
            final int stepIndex = s;
            final double finalScore = targetScore;
            final DramaturgyDirectorService.TensionTrend finalTrend = trend;

            targetData.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    Tooltip tip = new Tooltip(String.format(Locale.ROOT, "Step %d | Ideal Target: %.1f/10.0 (%s)", stepIndex, finalScore, finalTrend.name()));
                    Tooltip.install(newNode, tip);
                    newNode.setStyle("-fx-background-color: #94a3b8, white; -fx-background-insets: 0, 1; -fx-background-radius: 3px; -fx-padding: 2.5px;");
                }
            });
            targetSeries.getData().add(targetData);
        }

        // 2. Actual Story Drama (Peak Room Suspense per step, 0.0 - 10.0 scale)
        XYChart.Series<Number, Number> actualSeries = new XYChart.Series<>();
        actualSeries.setName("Actual Story Tension (Peak Room)");
        actualSeries.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.setStyle("-fx-stroke: #2563eb; -fx-stroke-width: 2.5px;");
            }
        });

        for (int s = 1; s <= currentStep; s++) {
            List<TimeLocationEvent> events = state.getEventsForStep(s);
            double stepPeak = 0.0;
            String peakRoomName = "None";
            boolean hasMurder = false;
            boolean hasAccuse = false;
            boolean killerPresent = false;

            if (events != null && !events.isEmpty()) {
                for (TimeLocationEvent ev : events) {
                    double suspense = ev.getSuspenseScore();
                    if (suspense > stepPeak) {
                        stepPeak = suspense;
                        peakRoomName = ev.getLocationName() != null ? ev.getLocationName() : "Room";
                    }
                    if (ev.hasMurderAction()) {
                        hasMurder = true;
                    }
                    if (ev.hasAccuseAction()) {
                        hasAccuse = true;
                    }
                    if (ev.isMurdererPresent()) {
                        killerPresent = true;
                    }
                }
            }

            boolean hasDiscovery = false;
            if (director.isBodyDiscovered() && s == director.getDiscoveryStep()) {
                hasDiscovery = true;
            }
            if (!hasDiscovery && state.getGlobalEvents() != null) {
                for (GlobalEvent ge : state.getGlobalEvents().values()) {
                    if (ge.getScheduledStep() == s && ("ALARM".equalsIgnoreCase(ge.getImpactType()) ||
                            (ge.getName() != null && ge.getName().toLowerCase(Locale.ROOT).contains("discovery")))) {
                        hasDiscovery = true;
                        break;
                    }
                }
            }

            XYChart.Data<Number, Number> actualData = new XYChart.Data<>(s, stepPeak);
            final int stepIndex = s;
            final double finalPeak = stepPeak;
            final String finalRoom = peakRoomName;
            final boolean fMurder = hasMurder;
            final boolean fDiscovery = hasDiscovery;
            final boolean fAccuse = hasAccuse;
            final boolean fKiller = killerPresent;
            final String clockTime = state.getClockTime(s);

            actualData.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    attachDataInteractions(newNode, stepIndex, clockTime, finalPeak, finalRoom, fMurder, fDiscovery, fAccuse, fKiller, onJumpToStep);
                    if (stepIndex == activeStep) {
                        newNode.setEffect(new DropShadow(10, Color.web("#2563eb")));
                        newNode.setScaleX(1.35);
                        newNode.setScaleY(1.35);
                    }
                }
            });

            actualSeries.getData().add(actualData);
        }

        chart.getData().add(targetSeries);
        chart.getData().add(actualSeries);

        // Sync legend symbol colors with actual curves
        Platform.runLater(() -> {
            for (Node item : chart.lookupAll(".chart-legend-item")) {
                if (item instanceof Label l) {
                    Node sym = l.getGraphic();
                    if (sym != null) {
                        if (l.getText() != null && l.getText().contains("Ideal")) {
                            sym.setStyle("-fx-background-color: #94a3b8;");
                        } else if (l.getText() != null && l.getText().contains("Actual")) {
                            sym.setStyle("-fx-background-color: #2563eb;");
                        }
                    }
                }
            }
        });

        // 3. Render Stat Cards for the selected time step
        renderStatCards(statsBox, state, director, profile, activeStep, targetSteps);
    }

    public static void updateSelectedStep(LineChart<Number, Number> chart,
                                          Label headerLabel,
                                          HBox statsBox,
                                          SimulationRepository state,
                                          DramaturgyDirectorService directorService,
                                          int selectedStep,
                                          Consumer<Integer> onJumpToStep) {
        if (chart == null || state == null) {
            return;
        }

        // If chart is empty or not yet fully populated, run full update
        if (chart.getData().size() < 2) {
            updateChart(chart, headerLabel, statsBox, state, directorService, selectedStep, onJumpToStep);
            return;
        }

        DramaturgyDirectorService director = directorService != null ? directorService : new DramaturgyDirectorService();
        DramaturgyProfile profile = director.resolveProfile(state);
        int currentStep = state.getCurrentStep();
        int targetSteps = state.getTargetSteps() > 0 ? state.getTargetSteps() : Math.max(20, currentStep);
        int step = Math.max(0, Math.min(selectedStep, currentStep));

        if (headerLabel != null) {
            if (step == currentStep) {
                headerLabel.setText(String.format("Dramaturgy Tension Curve (Steps 1 – %d)", Math.max(1, currentStep)));
            } else {
                headerLabel.setText(String.format("Dramaturgy Tension Curve (Viewing Step %d of %d)", step, Math.max(1, currentStep)));
            }
        }

        // Highlight active point on the actual curve (Series index 1)
        if (chart.getData().size() >= 2) {
            XYChart.Series<Number, Number> actualSeries = chart.getData().get(1);
            for (XYChart.Data<Number, Number> data : actualSeries.getData()) {
                Node node = data.getNode();
                if (node != null) {
                    if (data.getXValue().intValue() == step) {
                        node.setEffect(new DropShadow(10, Color.web("#2563eb")));
                        node.setScaleX(1.35);
                        node.setScaleY(1.35);
                    } else {
                        node.setEffect(null);
                        node.setScaleX(1.0);
                        node.setScaleY(1.0);
                    }
                }
            }
        }

        renderStatCards(statsBox, state, director, profile, step, targetSteps);
    }

    private static void renderStatCards(HBox statsBox,
                                        SimulationRepository state,
                                        DramaturgyDirectorService director,
                                        DramaturgyProfile profile,
                                        int step,
                                        int targetSteps) {
        if (statsBox == null) return;
        statsBox.getChildren().clear();

        // 1. Resolve live room drama at selected step
        double stepPeak = 0.0;
        String peakRoomName = "None";
        if (step > 0) {
            List<TimeLocationEvent> events = state.getEventsForStep(step);
            if (events != null && !events.isEmpty()) {
                for (TimeLocationEvent ev : events) {
                    if (ev.getSuspenseScore() > stepPeak) {
                        stepPeak = ev.getSuspenseScore();
                        peakRoomName = ev.getLocationName() != null ? ev.getLocationName() : "Room";
                    }
                }
            }
        }

        // Ideal tension & trend for selected step
        double targetScore = director.calculateTargetTension(step, targetSteps, profile);
        DramaturgyDirectorService.TensionTrend trend = director.determineTensionTrend(step, targetSteps, profile);

        // Narrative stage & phase for selected step
        DramaturgyDirectorService.StoryAct act = director.determineActForStep(state, step);
        DramaturgyDirectorService.ActPhase phase = director.determineActPhaseForStep(state, step);
        double actProgress = director.determineActProgressForStep(state, step);

        // Overall peak across the whole mystery
        double overallPeakTension = 0.0;
        int overallPeakStep = state.getCurrentStep() > 0 ? 1 : 0;
        String overallPeakRoom = "None";

        for (int s = 1; s <= state.getCurrentStep(); s++) {
            List<TimeLocationEvent> events = state.getEventsForStep(s);
            if (events != null) {
                for (TimeLocationEvent ev : events) {
                    if (ev.getSuspenseScore() > overallPeakTension) {
                        overallPeakTension = ev.getSuspenseScore();
                        overallPeakStep = s;
                        overallPeakRoom = ev.getLocationName() != null ? ev.getLocationName() : "Room";
                    }
                }
            }
        }

        String card1Title = step == state.getCurrentStep() ? "Current Step Drama" : String.format("Step %d Drama", step);
        String card1Sub = peakRoomName.equals("None")
                ? String.format(Locale.ROOT, "Target: %.1f (%s)", targetScore, trend.name())
                : String.format(Locale.ROOT, "Target: %.1f (%s) in %s", targetScore, trend.name(), peakRoomName);

        // Card 1: Tension at selected step vs Ideal Target
        statsBox.getChildren().add(createStatCard(
                card1Title,
                String.format(Locale.ROOT, "%.1f / 10.0", stepPeak),
                card1Sub,
                "#2563eb", "#eff6ff"));

        // Card 2: Narrative Stage & Phase at selected step from DramaturgyDirectorService
        String card2Title = step == state.getCurrentStep() ? "Narrative Stage" : String.format("Stage at Step %d", step);
        statsBox.getChildren().add(createStatCard(
                card2Title,
                act.getDescription(),
                String.format(Locale.ROOT, "Phase: %s (%.0f%% through Act)", phase.getDisplayName(), actProgress * 100),
                "#7c3aed", "#f5f3ff"));

        // Card 3: Overall Peak Story Tension
        statsBox.getChildren().add(createStatCard(
                "Peak Story Tension",
                String.format(Locale.ROOT, "%.1f / 10.0 (Step %d)", overallPeakTension, overallPeakStep),
                String.format(Locale.ROOT, "Location: %s", overallPeakRoom),
                "#dc2626", "#fef2f2"));
    }

    private static void attachDataInteractions(Node node,
                                               int step,
                                               String clockTime,
                                               double peak,
                                               String peakRoom,
                                               boolean hasMurder,
                                               boolean hasDiscovery,
                                               boolean hasAccuse,
                                               boolean killerPresent,
                                               Consumer<Integer> onJumpToStep) {
        StringBuilder tipText = new StringBuilder();
        tipText.append(String.format(Locale.ROOT, "Step %d | %s%n", step, clockTime));
        tipText.append(String.format(Locale.ROOT, "Peak Room Suspense: %.1f/10.0 in %s", peak, peakRoom));

        if (hasMurder) {
            tipText.append("\n☠️ LETHAL STRIKE / MURDER OCCURRED!");
            node.setStyle("-fx-background-color: #dc2626, white; -fx-background-insets: 0, 2; -fx-background-radius: 9px; -fx-padding: 7px; -fx-cursor: hand;");
        } else if (hasDiscovery) {
            tipText.append("\n🔍 BODY DISCOVERED / ALARM RAISED!");
            node.setStyle("-fx-background-color: #f59e0b, white; -fx-background-insets: 0, 2; -fx-background-radius: 8px; -fx-padding: 6px; -fx-cursor: hand;");
        } else if (hasAccuse) {
            tipText.append("\n⚖️ GRAND CLIMAX / ACCUSATION!");
            node.setStyle("-fx-background-color: #7c3aed, white; -fx-background-insets: 0, 2; -fx-background-radius: 8px; -fx-padding: 6px; -fx-cursor: hand;");
        } else if (killerPresent) {
            tipText.append("\n🔪 Killer Present in Step");
            node.setStyle("-fx-background-color: #ea580c, white; -fx-background-insets: 0, 1; -fx-background-radius: 7px; -fx-padding: 5px; -fx-cursor: hand;");
        } else {
            node.setStyle("-fx-background-color: #2563eb, white; -fx-background-insets: 0, 1; -fx-background-radius: 5px; -fx-padding: 4px; -fx-cursor: hand;");
        }

        Tooltip tip = new Tooltip(tipText.toString());
        Tooltip.install(node, tip);

        if (onJumpToStep != null) {
            node.setOnMouseClicked(e -> onJumpToStep.accept(step));
        }
    }

    private static VBox createStatCard(String title, String mainValue, String subText, String textTheme, String bgTheme) {
        VBox card = new VBox(3);
        card.setPadding(new Insets(8, 14, 8, 14));
        card.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; -fx-background-radius: 6px; -fx-border-radius: 6px;", bgTheme, textTheme));

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #64748b;");

        Label valLabel = new Label(mainValue);
        valLabel.setStyle(String.format("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: %s;", textTheme));

        Label subLabel = new Label(subText);
        subLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        card.getChildren().addAll(titleLabel, valLabel, subLabel);
        return card;
    }
}
