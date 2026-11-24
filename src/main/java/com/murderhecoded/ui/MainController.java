package com.murderhecoded.ui;
import com.murderhecoded.AppConstants;

import com.murderhecoded.domain.*;
import com.murderhecoded.llm.LlmRole;
import com.murderhecoded.service.*;
import com.murderhecoded.repository.SimulationRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.OutputStream;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ResourceBundle;

import javafx.scene.web.WebView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.springframework.beans.factory.annotation.Value;

@Component
@SuppressWarnings("PMD")
public class MainController {
    public static final String APP_NAME = AppConstants.APP_NAME;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MainController.class);

    @Value("${simulation.default.agents:7}")
    private int defaultAgents;

    @Value("${simulation.default.places:8}")
    private int defaultPlaces;

    @Value("${simulation.default.items:5}")
    private int defaultItems;

    @Value("${simulation.default.title:Murder on the Orient Express}")
    private String defaultTitle;

    @Value("${simulation.default.description:A classic murder mystery at an isolated location.}")
    private String defaultDescription;

    @Value("${simulation.default.scenelength:Medium}")
    private String defaultSceneLength;

    @Value("${simulation.default.steps:100}")
    private int defaultSteps;

    private final SimulationService simulationService;
    private final ProseService proseService;
    private final SimulationRepository state;
    private final SimulationInitializationService initializer;
    private final OllamaLlmService ollamaLlmService;
    private final com.murderhecoded.service.ProjectService projectService;
    private final DramaturgyDirectorService dramaturgyDirectorService;

    private BorderPane mainView;
    private TabPane tabPane;
    private Tab simulationTab;
    private Tab storyTab;
    private Label statusLabel;
    private Label timeLabel;
    private Circle statusLamp;
    private FadeTransition statusLampAnimation;
    private Label storyStatsLabel;
    
    // Simulation Tab controls
    private ComboBox<String> authorVoiceCombo;
    private ComboBox<String> sceneLengthCombo;
    private Label sceneLengthLabel;
    private ComboBox<Integer> targetStepsCombo;
    private ComboBox<Integer> stepMinutesCombo;
    private Spinner<Integer> numAgentsSpinner;
    private Spinner<Integer> numPlacesSpinner;
    private Spinner<Integer> numItemsSpinner;
    private TextField storyTitleField;
    private Label storyTitleLabel;
    private TextArea storyDescArea;
    private Label storyDescLabel;
    private Label authorVoiceLabel;
    private CheckBox proseToggle;
    private CheckBox sceneDirectorToggle;
    private Button buildWorldBtn;
    private Button startBtn;
    private Button resumeBtn;
    private Button stopBtn;
    private Button generateScenesBtn;
    private Button generateProseBtn;
    private Button exportStoryBtn;
    private Label projectHeaderLabel;
    private Label projectPathBadge;
    private Thread currentInitThread;
    private Task<Boolean> currentInitTask;
    private volatile boolean cancelRequested = false;
    private volatile boolean isLoadingProject = false;
    
    // Generation & Progress Dashboard controls
    private Label worldStatusBadge;
    private Label worldDetailsLabel;
    private Label scenesStatusBadge;
    private Label scenesDetailsLabel;
    private ProgressBar simProgressBar;
    private Label simProgressLabel;
    private Label simDetailsLabel;
    private ProgressBar proseProgressBar;
    private Label proseProgressLabel;
    private Label proseDetailsLabel;
    
    // Event Log Tab controls
    private TextArea eventLogArea;
    private Button saveLogBtn;
    private Button clearLogBtn;
    
    // Story Tab controls
    private Slider timelineSlider;
    private HBox storyTimelineBox;
    private TabPane storySubTabPane;
    private WebView highlightsArea;
    private BorderPane agentCardPane;
    private BorderPane worldCardPane;
    private BorderPane globalEventsPane;
    private WebView storyArea;
    private ToggleGroup textModeGroup;
    private RadioButton rawBtn;
    private RadioButton editBtn;
    private final List<Chapter> chapters = new ArrayList<>();
    
    // Items Tab controls
    private FlowPane itemsFlowPane;
    private Label itemsHeaderLabel;
    private ComboBox<String> itemTypeFilterCombo;
    private ComboBox<String> itemLocFilterCombo;
    private TextField itemSearchField;
    
    // Person Detail Tab controls
    private TreeView<PersonDetailPresenter.PersonTreeItemData> personTreeView;
    private VBox personDetailContentBox;
    private String selectedPersonId;

    // Item Detail Tab controls
    private TreeView<ItemDetailPresenter.ItemTreeItemData> itemTreeView;
    private VBox itemDetailContentBox;
    private String selectedItemId;

    // Location Detail Tab controls
    private TreeView<LocationDetailPresenter.LocationTreeItemData> locationTreeView;
    private VBox locationDetailContentBox;
    private String selectedLocationId;

    // Dramaturgy & Events Tree controls
    private LineChart<Number, Number> dramaturgyChart;
    private Label dramaturgyHeaderLabel;
    private HBox dramaturgyStatsBox;
    private TreeView<TimelineTreePresenter.TimelineNodeData> timelineTreeView;
    private VBox timelineTreeDetailBox;

    // Scenes Tab Presenter
    private ScenesTabPresenter scenesTabPresenter;
    private final com.murderhecoded.service.EditorService editorService;

    private ResourceBundle bundle;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.boot.info.BuildProperties buildProperties;

    public void setBuildProperties(org.springframework.boot.info.BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    public String getAppVersion() {
        if (buildProperties != null && buildProperties.getVersion() != null) {
            return buildProperties.getVersion();
        }
        String pkgVersion = getClass().getPackage() != null ? getClass().getPackage().getImplementationVersion() : null;
        if (pkgVersion != null && !pkgVersion.isBlank()) {
            return pkgVersion;
        }
        return AppConstants.APP_VERSION;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MainController(SimulationService simulationService,
                          ProseService proseService,
                          SimulationRepository state,
                          SimulationInitializationService initializer,
                          OllamaLlmService ollamaLlmService,
                          com.murderhecoded.service.ProjectService projectService,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) DramaturgyDirectorService dramaturgyDirectorService,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) com.murderhecoded.service.EditorService editorService,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) com.murderhecoded.service.LlmSceneDirectorService directorService) {
        this.simulationService = simulationService;
        this.proseService = proseService;
        this.state = state;
        this.initializer = initializer;
        this.ollamaLlmService = ollamaLlmService;
        this.projectService = projectService;
        this.dramaturgyDirectorService = dramaturgyDirectorService != null ? dramaturgyDirectorService
                : (simulationService != null && simulationService.getDramaturgyDirectorService() != null
                ? simulationService.getDramaturgyDirectorService() : new DramaturgyDirectorService());

        if (editorService != null) {
            this.editorService = editorService;
        } else {
            this.editorService = new com.murderhecoded.service.EditorService();
        }
        if (this.editorService.getDirectorService() == null && directorService != null) {
            this.editorService.setDirectorService(directorService);
        }

        loadResourceBundle(Locale.ENGLISH);
        
        this.simulationService.setCallbacks(this::appendLog, this::updateUiFromState);
        this.proseService.setOnLogEvent(this::appendLog);
        this.proseService.setOnChapterGenerated(this::appendStory);
        this.initializer.setOnLogEvent(this::appendLog);
    }


    public void initializeUi() {
        initUi();
    }

    public void showStartupDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(APP_NAME + " - Startup");
        alert.setHeaderText("Welcome to " + APP_NAME + "!");
        alert.setContentText("Do you want to create a new simulation or open an existing one?");
        
        ButtonType newBtn = new ButtonType("New Project");
        ButtonType openBtn = new ButtonType("Open Project");
        alert.getButtonTypes().setAll(newBtn, openBtn);
        
        alert.showAndWait().ifPresent(type -> {
            if (type == openBtn) {
                loadNovel();
            }
        });
    }

    public BorderPane getView() {
        return mainView;
    }

    private void loadResourceBundle(Locale locale) {
        this.bundle = ResourceBundle.getBundle("messages", locale);
    }

    private void initUi() {
        mainView = new BorderPane();
        VBox topContainer = new VBox(createMenuBar(), createProjectHeaderBar());
        mainView.setTop(topContainer);
        
        tabPane = new TabPane();
        
        simulationTab = new Tab("Simulation");
        simulationTab.setContent(createSimulationTab());
        simulationTab.setClosable(false);
        
        storyTab = new Tab("Story");
        storyTab.setContent(createStoryTab());
        storyTab.setClosable(false);
        
        Tab logTab = new Tab("Event Log");
        logTab.setContent(createLogTab());
        logTab.setClosable(false);
        
        scenesTabPresenter = new ScenesTabPresenter();
        Tab scenesTab = new Tab("Scenes");
        scenesTab.setContent(scenesTabPresenter.createView(
            step -> {
                if (timelineSlider != null) {
                    timelineSlider.setValue(step);
                }
            },
            this::generateScenesForCurrentSimulation,
            this::generateProseForCurrentSimulation,
            this::regenerateProseForSingleChapter
        ));
        scenesTab.setClosable(false);

        tabPane.getTabs().addAll(simulationTab, storyTab, logTab, scenesTab);
        mainView.setCenter(tabPane);
        
        statusLabel = new Label("Ready.");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #1e293b;");

        statusLamp = new Circle(6, Color.web("#10b981"));
        statusLamp.setStroke(Color.web("#059669"));
        statusLamp.setStrokeWidth(1.5);
        Tooltip.install(statusLamp, new Tooltip("Ready / Idle"));

        statusLampAnimation = new FadeTransition(Duration.millis(500), statusLamp);
        statusLampAnimation.setFromValue(1.0);
        statusLampAnimation.setToValue(0.15);
        statusLampAnimation.setCycleCount(Animation.INDEFINITE);
        statusLampAnimation.setAutoReverse(true);

        timelineSlider = new Slider(0, 0, 0);
        timelineSlider.setBlockIncrement(1);
        timelineSlider.setMajorTickUnit(1);
        timelineSlider.setMinorTickCount(0);
        timelineSlider.setSnapToTicks(true);
        timelineSlider.setShowTickLabels(true);
        timelineSlider.setShowTickMarks(true);
        timelineSlider.setPrefWidth(220);
        timelineSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int step = newVal.intValue();
            displaySnapshot(step);
            updateTimelineMarkers();
            updateHighlights();
            if (timeLabel != null) {
                timeLabel.setText("Time: " + state.getClockTime(step));
            }
            updateDramaturgySelectedStep(step);
        });

        timeLabel = new Label("Time: 00:00");
        timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155; -fx-font-weight: bold;");

        storyStatsLabel = new Label("");
        storyStatsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");

        Region statusSpacer = new Region();
        HBox.setHgrow(statusSpacer, Priority.ALWAYS);

        HBox statusLeftBox = new HBox(8, statusLamp, statusLabel);
        statusLeftBox.setAlignment(Pos.CENTER_LEFT);

        HBox timelineBox = new HBox(8, timeLabel, timelineSlider);
        timelineBox.setAlignment(Pos.CENTER_LEFT);

        HBox statusBar = new HBox(12, statusLeftBox, timelineBox, statusSpacer, storyStatsLabel);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(6, 12, 6, 12));
        statusBar.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #cbd5e1; -fx-border-width: 1 0 0 0;");

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            boolean showTimeline = (newTab == storyTab);
            timelineBox.setVisible(showTimeline);
            timelineBox.setManaged(showTimeline);
            updateStoryStats();
        });

        timelineBox.setVisible(false);
        timelineBox.setManaged(false);
        storyStatsLabel.setVisible(false);
        storyStatsLabel.setManaged(false);

        mainView.setBottom(statusBar);
    }

    private MenuBar menuBar;
    private Menu fileMenu, configMenu, helpMenu;
    private MenuItem loadNovelItem, saveTxtItem, savePdfItem, exitItem, llmConfigItem, aboutItem;

    private MenuBar createMenuBar() {
        menuBar = new MenuBar();
        
        fileMenu = new Menu("File");
        MenuItem newNovelItem = new MenuItem("New");
        newNovelItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
        newNovelItem.setOnAction(e -> handleNewNovel());
        loadNovelItem = new MenuItem("Load Novel");
        loadNovelItem.setOnAction(e -> loadNovel());
        saveTxtItem = new MenuItem("Save as Text");
        saveTxtItem.setOnAction(e -> saveStoryAsTxt());
        savePdfItem = new MenuItem("Save as PDF");
        savePdfItem.setOnAction(e -> saveStoryAsPdf());
        SeparatorMenuItem separator = new SeparatorMenuItem();
        exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> {
            Platform.exit();
            System.exit(0);
        });
        fileMenu.getItems().addAll(newNovelItem, loadNovelItem, separator, exitItem);
        
        configMenu = new Menu("Config");
        llmConfigItem = new MenuItem("LLM Configuration");
        llmConfigItem.setOnAction(e -> showLlmConfigDialog());
        configMenu.getItems().add(llmConfigItem);
        
        helpMenu = new Menu("Help");
        aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAboutDialog());
        helpMenu.getItems().add(aboutItem);
        
        menuBar.getMenus().addAll(fileMenu, configMenu, helpMenu);
        return menuBar;
    }

    private HBox createProjectHeaderBar() {
        HBox headerBar = new HBox(12);
        headerBar.setAlignment(Pos.CENTER_LEFT);
        headerBar.setPadding(new Insets(6, 16, 6, 16));
        headerBar.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-width: 0 0 1px 0;");

        String initialTitle = getFormattedTitleWithSequence();
        projectHeaderLabel = new Label("📁 Active Project: " + (initialTitle != null ? initialTitle : "None"));
        projectHeaderLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        projectPathBadge = new Label("Not created yet");
        projectPathBadge.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569; -fx-background-color: #e2e8f0; -fx-padding: 2 8; -fx-background-radius: 4px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label modeLabel = new Label("Multi-Agent Murder Mystery Studio");
        modeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8; -fx-font-style: italic;");

        headerBar.getChildren().addAll(projectHeaderLabel, projectPathBadge, spacer, modeLabel);
        return headerBar;
    }

    private void loadNovel() {
        javafx.stage.DirectoryChooser dirChooser = new javafx.stage.DirectoryChooser();
        dirChooser.setTitle("Load Novel Project");
        java.io.File novelDir = new java.io.File("novel");
        if (novelDir.exists() && novelDir.isDirectory()) {
            dirChooser.setInitialDirectory(novelDir);
        }
        Window window = mainView != null && mainView.getScene() != null ? mainView.getScene().getWindow() : null;
        java.io.File dir = dirChooser.showDialog(window);
        if (dir != null) {
            javafx.scene.Scene fxScene = mainView != null ? mainView.getScene() : null;
            if (fxScene != null) {
                fxScene.setCursor(Cursor.WAIT);
            }
            if (mainView != null) {
                mainView.setCursor(Cursor.WAIT);
            }
            isLoadingProject = true;
            setGenerationRunning(false, "⏳ Loading novel project: " + dir.getName() + "... Please wait.");

            Thread.ofVirtual().start(() -> {
                try {
                    com.murderhecoded.service.ProjectService.ProjectData data = projectService.loadProject(dir);
                    state.restoreFrom(data.stateDocument());

                    Platform.runLater(() -> {
                        try {
                            chapters.clear();
                            chapters.addAll(data.chapters());
                            
                            int nextSeq = data.chapters().stream().mapToInt(c -> c.sceneNumber()).max().orElse(0) + 1;
                            proseService.restoreState(data.chapters().size(), data.chapters().size(), nextSeq, data.chapters());
                            
                            if (eventLogArea != null) {
                                eventLogArea.setText(data.eventLog());
                            }
                            if (data.eventLog() != null && !data.eventLog().isBlank()) {
                                state.getEventHistory().clear();
                                for (String line : data.eventLog().split("\n")) {
                                    String trimmed = line.trim();
                                    if (!trimmed.isEmpty()) {
                                        state.getEventHistory().add(trimmed);
                                    }
                                }
                            }

                            if (targetStepsCombo != null && state.getTargetSteps() > 0) {
                                if (!targetStepsCombo.getItems().contains(state.getTargetSteps())) {
                                    targetStepsCombo.getItems().add(state.getTargetSteps());
                                    targetStepsCombo.getItems().sort(Integer::compareTo);
                                }
                                targetStepsCombo.setValue(state.getTargetSteps());
                            }
                            if (stepMinutesCombo != null && state.getStepMinutes() > 0) {
                                stepMinutesCombo.setValue(state.getStepMinutes());
                            }
                            if (storyTitleField != null) {
                                String loadedTitle = state.getTitle() != null && !state.getTitle().isBlank()
                                        ? state.getTitle() : defaultTitle;
                                storyTitleField.setText(loadedTitle != null ? loadedTitle : "");
                                state.setTitle(storyTitleField.getText());
                            }
                            if (storyDescArea != null) {
                                String loadedPremise = state.getPremise() != null && !state.getPremise().isBlank()
                                        ? state.getPremise() : defaultDescription;
                                storyDescArea.setText(loadedPremise != null ? loadedPremise : "");
                                state.setPremise(storyDescArea.getText());
                            }
                            if (numAgentsSpinner != null && !state.getAgents().isEmpty()) {
                                numAgentsSpinner.getValueFactory().setValue(state.getAgents().size());
                            }
                            if (numPlacesSpinner != null && !state.getLocations().isEmpty()) {
                                numPlacesSpinner.getValueFactory().setValue(state.getLocations().size());
                            }

                            updateUiFromState();
                            displaySnapshot(state.getCurrentStep());
                            updateTimelineMarkers();
                            updateHighlights();
                            updateGlobalEventsTab();
                            updateDashboardProgress();
                            renderStory();
                            updateDramaturgyChart();
                            updateTimelineTree();
                            updateScenesTab();

                            if (buildWorldBtn != null) {
                                buildWorldBtn.setText("🔄 1. Rebuild World");
                                buildWorldBtn.setDisable(false);
                            }
                            if (state.getCurrentStep() > 0) {
                                startBtn.setVisible(false);
                                startBtn.setManaged(false);
                                resumeBtn.setVisible(true);
                                resumeBtn.setManaged(true);
                                resumeBtn.setDisable(false);
                            } else {
                                startBtn.setVisible(true);
                                startBtn.setManaged(true);
                                startBtn.setDisable(!state.isWorldGenerated());
                                startBtn.setText("▶ 2. Start Simulation");
                                resumeBtn.setVisible(false);
                                resumeBtn.setManaged(false);
                            }
                            stopBtn.setDisable(true);
                            if (generateScenesBtn != null) {
                                generateScenesBtn.setDisable(state.getCurrentStep() == 0 || proseService.isRunning());
                                generateScenesBtn.setText(chapters.isEmpty() ? "🎬 3. Generate Scenes" : "🔄 3. Regenerate Scenes");
                            }
                            if (generateProseBtn != null) {
                                generateProseBtn.setDisable(chapters.isEmpty() || proseService.isRunning());
                                boolean hasProse = chapters.stream().anyMatch(Chapter::hasProse);
                                generateProseBtn.setText(hasProse ? "🔄 4. Regenerate Prose" : "✍️ 4. Generate Prose");
                            }

                            updateExportButtonState();
                            updateActiveProjectDisplay();
                            setGenerationRunning(false, "Loaded novel: " + dir.getName());
                        } finally {
                            if (fxScene != null) {
                                fxScene.setCursor(Cursor.DEFAULT);
                            }
                            if (mainView != null) {
                                mainView.setCursor(Cursor.DEFAULT);
                            }
                            isLoadingProject = false;
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        if (fxScene != null) {
                            fxScene.setCursor(Cursor.DEFAULT);
                        }
                        if (mainView != null) {
                            mainView.setCursor(Cursor.DEFAULT);
                        }
                        isLoadingProject = false;
                        statusLabel.setText("Failed to load novel: " + ex.getMessage());
                    });
                    ex.printStackTrace();
                }
            });
        }
    }

    private void saveStoryAsTxt() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save as Text");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = chooser.showSaveDialog(mainView.getScene().getWindow());
        if (file != null) {
            try {
                boolean edited = editBtn != null && editBtn.isSelected();
                String content = buildStoryTextContent(edited);
                Files.writeString(file.toPath(), content);
                statusLabel.setText("Saved as " + file.getName());
            } catch (Exception ex) {
                statusLabel.setText("Error saving text file");
                ex.printStackTrace();
            }
        }
    }

    private void saveStoryAsPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save as PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = chooser.showSaveDialog(mainView.getScene().getWindow());
        if (file != null) {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                boolean edited = editBtn != null && editBtn.isSelected();
                writeStoryToPdf(fos, edited);
                statusLabel.setText("Saved as " + file.getName());
            } catch (Exception ex) {
                statusLabel.setText("Error saving PDF");
                ex.printStackTrace();
            }
        }
    }

    public List<Chapter> getChapters() {
        return chapters;
    }

    public String getStoryTitle() {
        String formatted = getFormattedTitleWithSequence();
        if (formatted != null && !formatted.isBlank()) {
            return formatted;
        }
        if (state != null && state.getTitle() != null && !state.getTitle().trim().isBlank()) {
            return state.getTitle().trim();
        }
        if (storyTitleField != null && storyTitleField.getText() != null && !storyTitleField.getText().trim().isBlank()) {
            return storyTitleField.getText().trim();
        }
        if (defaultTitle != null && !defaultTitle.isBlank()) {
            return defaultTitle.trim();
        }
        return "Untitled Mystery";
    }

    public String buildStoryTextContent(boolean filterEdited) {
        StringBuilder sb = new StringBuilder();
        String title = getStoryTitle();
        if (title != null && !title.isBlank()) {
            sb.append(title).append("\n\n");
        }
        if (chapters != null) {
            for (Chapter c : chapters) {
                if (filterEdited && !c.isSelected()) continue;
                sb.append(c.text()).append("\n\n***\n\n");
            }
        }
        sb.append("\n\n").append(buildStoryMetadataText());
        return sb.toString();
    }

    public void writeStoryToPdf(OutputStream out, boolean filterEdited) throws Exception {
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();
        String title = getStoryTitle();
        if (title != null && !title.isBlank()) {
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22);
            Paragraph titlePara = new Paragraph(title, titleFont);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(24f);
            document.add(titlePara);
        }
        if (chapters != null) {
            for (Chapter c : chapters) {
                if (filterEdited && !c.isSelected()) continue;
                document.add(new Paragraph(c.text()));
                document.add(new Paragraph("\n***\n"));
            }
        }
        document.newPage();
        document.add(new Paragraph(buildStoryMetadataText(), FontFactory.getFont(FontFactory.COURIER, 9f)));
        document.close();
    }

    public String getActiveNovelTitle() {
        Path p = projectService != null ? projectService.getCurrentProjectPath() : null;
        if (p == null) {
            return null;
        }
        if (state != null && state.getTitle() != null && !state.getTitle().trim().isBlank()) {
            return state.getTitle().trim();
        }
        if (storyTitleField != null && storyTitleField.getText() != null && !storyTitleField.getText().trim().isBlank()) {
            return storyTitleField.getText().trim();
        }
        if (p.getFileName() != null) {
            String dirName = p.getFileName().toString();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*?)[_-](\\d+)$").matcher(dirName);
            String raw = m.matches() ? m.group(1) : dirName;
            return raw.replace('_', ' ').replace('-', ' ').trim();
        }
        return (defaultTitle != null && !defaultTitle.isBlank()) ? defaultTitle : "Untitled Mystery";
    }

    public String getActiveSequenceNumber() {
        Path p = projectService != null ? projectService.getCurrentProjectPath() : null;
        if (p != null && p.getFileName() != null) {
            String dirName = p.getFileName().toString();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*?)[_-](\\d+)$").matcher(dirName);
            if (m.matches()) {
                return m.group(2);
            }
        }
        return null;
    }

    public String getFormattedTitleWithSequence() {
        String title = getActiveNovelTitle();
        if (title == null || title.isBlank()) {
            return null;
        }
        String seq = getActiveSequenceNumber();
        if (seq != null && !seq.isBlank()) {
            String pattern = ".*(\\b|#|_)" + java.util.regex.Pattern.quote(seq) + "$";
            if (!title.matches(pattern)) {
                return title + " #" + seq;
            }
        }
        return title;
    }

    public void updateActiveProjectDisplay() {
        Path p = projectService != null ? projectService.getCurrentProjectPath() : null;
        String formattedTitle = getFormattedTitleWithSequence();

        if (projectHeaderLabel != null) {
            if (formattedTitle != null) {
                projectHeaderLabel.setText("📁 Active Project: " + formattedTitle);
            } else {
                projectHeaderLabel.setText("📁 Active Project: None");
            }
        }
        if (projectPathBadge != null) {
            if (p != null) {
                projectPathBadge.setText("Path: " + p.toString());
                projectPathBadge.setVisible(true);
            } else {
                projectPathBadge.setText("Not created yet");
                projectPathBadge.setVisible(true);
            }
        }

        if (mainView != null && mainView.getScene() != null && mainView.getScene().getWindow() instanceof Stage stage) {
            String baseTitle = APP_NAME + " v" + getAppVersion();
            if (formattedTitle != null) {
                stage.setTitle(baseTitle + " - " + formattedTitle);
            } else {
                stage.setTitle(baseTitle);
            }
        }
    }

    private void updateExportButtonState() {
        if (exportStoryBtn != null) {
            boolean hasProse = chapters != null && !chapters.isEmpty() &&
                    chapters.stream().anyMatch(Chapter::hasProse);
            exportStoryBtn.setDisable(!hasProse);
        }
    }

    private void exportStoryToProjectDir() {
        if (chapters == null || chapters.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Prose to Export");
            alert.setHeaderText("No Chapters Available");
            alert.setContentText("Please run Phase 4 (Generate Prose) before exporting the story.");
            alert.showAndWait();
            return;
        }

        Path projectDir = projectService.getCurrentProjectPath();
        if (projectDir == null) {
            String title = state != null && state.getTitle() != null && !state.getTitle().isBlank() ? state.getTitle() : "Novel";
            projectService.createNewProject(title);
            projectDir = projectService.getCurrentProjectPath();
            updateActiveProjectDisplay();
        }

        try {
            if (!Files.exists(projectDir)) {
                Files.createDirectories(projectDir);
            }

            String baseName = state != null && state.getTitle() != null && !state.getTitle().isBlank()
                    ? state.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_")
                    : projectDir.getFileName().toString();
            if (baseName.isBlank()) {
                baseName = "novel";
            }

            boolean edited = editBtn != null && editBtn.isSelected();

            // 1. Export TXT
            Path txtPath = projectDir.resolve(baseName + ".txt");
            String txtContent = buildStoryTextContent(edited);
            Files.writeString(txtPath, txtContent);

            // 2. Export PDF
            Path pdfPath = projectDir.resolve(baseName + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(pdfPath.toFile())) {
                writeStoryToPdf(fos, edited);
            }

            statusLabel.setText("Exported novel to: " + txtPath.getFileName() + " & " + pdfPath.getFileName() + " in " + projectDir.getFileName());

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export Complete");
            alert.setHeaderText("Novel Exported Successfully");
            alert.setContentText("Saved both formats to project directory:\n• " + txtPath.toAbsolutePath() + "\n• " + pdfPath.toAbsolutePath());
            alert.showAndWait();
        } catch (Exception ex) {
            log.error("Failed to export novel to project directory", ex);
            statusLabel.setText("Error exporting novel: " + ex.getMessage());
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Export Error");
            errorAlert.setHeaderText("Failed to Export Novel");
            errorAlert.setContentText("Could not write files: " + ex.getMessage());
            errorAlert.showAndWait();
        }
    }

    private void showLlmConfigDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("LLM Configuration");

        VBox contentBox = new VBox(12);
        contentBox.setPadding(new Insets(15));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField baseUrlField = new TextField(ollamaLlmService.getBaseUrl());
        baseUrlField.setPrefWidth(220);

        Button refreshBtn = new Button("Fetch Models");
        HBox baseUrlBox = new HBox(8, baseUrlField, refreshBtn);
        baseUrlBox.setAlignment(Pos.CENTER_LEFT);

        Label modelStatusLabel = new Label();
        modelStatusLabel.setWrapText(true);
        modelStatusLabel.setMaxWidth(380);

        ComboBox<String> worldModelField = new ComboBox<>();
        ComboBox<String> agentModelField = new ComboBox<>();
        ComboBox<String> proseModelField = new ComboBox<>();

        for (ComboBox<String> cb : List.of(worldModelField, agentModelField, proseModelField)) {
            cb.setEditable(true);
            cb.setPrefWidth(300);
        }

        worldModelField.setValue(ollamaLlmService.getModel(LlmRole.WORLD));
        agentModelField.setValue(ollamaLlmService.getModel(LlmRole.AGENT));
        proseModelField.setValue(ollamaLlmService.getModel(LlmRole.PROSE));

        Runnable fetchModelsAction = () -> {
            String url = baseUrlField.getText();
            modelStatusLabel.setText("Connecting to " + url + "...");
            modelStatusLabel.setStyle("-fx-text-fill: #555555; -fx-font-size: 11px;");
            refreshBtn.setDisable(true);

            Task<List<String>> fetchTask = new Task<>() {
                @Override
                protected List<String> call() {
                    return ollamaLlmService.getAvailableModels(url);
                }
            };

            fetchTask.setOnSucceeded(evt -> {
                refreshBtn.setDisable(false);
                List<String> updatedModels = fetchTask.getValue();
                if (updatedModels != null && !updatedModels.isEmpty()) {
                    updateModelComboBox(worldModelField, updatedModels);
                    updateModelComboBox(agentModelField, updatedModels);
                    updateModelComboBox(proseModelField, updatedModels);
                    modelStatusLabel.setText("Loaded " + updatedModels.size() + " models from Ollama.");
                    modelStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 11px;");
                } else {
                    modelStatusLabel.setText("Could not fetch models from " + url + " - verify Ollama is running.");
                    modelStatusLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11px;");
                }
            });

            fetchTask.setOnFailed(evt -> {
                refreshBtn.setDisable(false);
                modelStatusLabel.setText("Failed to connect to " + url);
                modelStatusLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11px;");
            });

            Thread th = new Thread(fetchTask, "FetchModels-Thread");
            th.setDaemon(true);
            th.start();
        };

        refreshBtn.setOnAction(e -> fetchModelsAction.run());
        baseUrlField.setOnAction(e -> fetchModelsAction.run());

        // Initial fetch in background when dialog opens
        fetchModelsAction.run();

        grid.add(new Label("Base URL:"), 0, 0);
        grid.add(baseUrlBox, 1, 0);
        grid.add(modelStatusLabel, 1, 1);
        grid.add(new Label("World Model:"), 0, 2);
        grid.add(worldModelField, 1, 2);
        grid.add(new Label("Agent Model:"), 0, 3);
        grid.add(agentModelField, 1, 3);
        grid.add(new Label("Prose Model:"), 0, 4);
        grid.add(proseModelField, 1, 4);

        // Advanced Hyperparameters (collapsible)
        GridPane advancedGrid = new GridPane();
        advancedGrid.setHgap(10);
        advancedGrid.setVgap(8);
        advancedGrid.setPadding(new Insets(10));

        Slider worldTempSlider = createTempSlider(ollamaLlmService.getWorldTemperature());
        Label worldTempVal = new Label(String.format(Locale.ROOT, "%.2f", worldTempSlider.getValue()));
        worldTempSlider.valueProperty().addListener((obs, oldV, newV) -> worldTempVal.setText(String.format(Locale.ROOT, "%.2f", newV.doubleValue())));
        Spinner<Integer> worldCtxSpinner = createCtxSpinner(ollamaLlmService.getWorldNumCtx());

        Slider agentTempSlider = createTempSlider(ollamaLlmService.getAgentTemperature());
        Label agentTempVal = new Label(String.format(Locale.ROOT, "%.2f", agentTempSlider.getValue()));
        agentTempSlider.valueProperty().addListener((obs, oldV, newV) -> agentTempVal.setText(String.format(Locale.ROOT, "%.2f", newV.doubleValue())));
        Spinner<Integer> agentCtxSpinner = createCtxSpinner(ollamaLlmService.getAgentNumCtx());

        Slider proseTempSlider = createTempSlider(ollamaLlmService.getProseTemperature());
        Label proseTempVal = new Label(String.format(Locale.ROOT, "%.2f", proseTempSlider.getValue()));
        proseTempSlider.valueProperty().addListener((obs, oldV, newV) -> proseTempVal.setText(String.format(Locale.ROOT, "%.2f", newV.doubleValue())));
        Spinner<Integer> proseCtxSpinner = createCtxSpinner(ollamaLlmService.getProseNumCtx());

        advancedGrid.add(new Label("World Temp:"), 0, 0);
        advancedGrid.add(new HBox(5, worldTempSlider, worldTempVal), 1, 0);
        advancedGrid.add(new Label("World Context:"), 2, 0);
        advancedGrid.add(worldCtxSpinner, 3, 0);

        advancedGrid.add(new Label("Agent Temp:"), 0, 1);
        advancedGrid.add(new HBox(5, agentTempSlider, agentTempVal), 1, 1);
        advancedGrid.add(new Label("Agent Context:"), 2, 1);
        advancedGrid.add(agentCtxSpinner, 3, 1);

        advancedGrid.add(new Label("Prose Temp:"), 0, 2);
        advancedGrid.add(new HBox(5, proseTempSlider, proseTempVal), 1, 2);
        advancedGrid.add(new Label("Prose Context:"), 2, 2);
        advancedGrid.add(proseCtxSpinner, 3, 2);

        TitledPane advancedPane = new TitledPane("Advanced Hyperparameters (Optional)", advancedGrid);
        advancedPane.setExpanded(false);

        // Test Connection
        Button testBtn = new Button("Test Connection");
        Label testResultLabel = new Label();
        testResultLabel.setWrapText(true);
        testResultLabel.setMaxWidth(400);

        testBtn.setOnAction(e -> {
            testBtn.setDisable(true);
            testResultLabel.setText("Testing connection to all 3 models...");
            testResultLabel.setStyle("-fx-text-fill: #333333;");

            String testUrl = baseUrlField.getText();
            String testWorld = cleanModelName(getModelFieldValue(worldModelField));
            String testAgent = cleanModelName(getModelFieldValue(agentModelField));
            String testProse = cleanModelName(getModelFieldValue(proseModelField));

            Task<String> testTask = new Task<>() {
                @Override
                protected String call() {
                    return ollamaLlmService.testConnectionBatch(testUrl, testWorld, testAgent, testProse);
                }
            };

            testTask.setOnSucceeded(evt -> {
                testBtn.setDisable(false);
                String response = testTask.getValue();
                testResultLabel.setText(response);
                if (response != null && response.startsWith("Success!")) {
                    testResultLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                } else {
                    testResultLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
                }
                if (dialog.getDialogPane() != null && dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                    dialog.getDialogPane().getScene().getWindow().sizeToScene();
                }
            });

            testTask.setOnFailed(evt -> {
                testBtn.setDisable(false);
                Throwable ex = testTask.getException();
                String msg = ex != null ? ex.getMessage() : "Connection failed";
                testResultLabel.setText("Error: " + msg);
                testResultLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
                if (dialog.getDialogPane() != null && dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                    dialog.getDialogPane().getScene().getWindow().sizeToScene();
                }
            });

            Thread testThread = new Thread(testTask, "SpringAI-TestTask-Thread");
            testThread.setDaemon(true);
            testThread.start();
        });

        HBox testBox = new HBox(10, testBtn, testResultLabel);
        testBox.setAlignment(Pos.CENTER_LEFT);

        contentBox.getChildren().addAll(grid, advancedPane, testBox);

        dialog.setResizable(true);
        ScrollPane configScrollPane = new ScrollPane(contentBox);
        configScrollPane.setFitToWidth(true);
        configScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        configScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        configScrollPane.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        dialog.getDialogPane().setContent(configScrollPane);
        dialog.getDialogPane().setPrefSize(650, 700);
        dialog.getDialogPane().setMinWidth(600);
        dialog.getDialogPane().setMinHeight(480);

        advancedPane.expandedProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                if (dialog.getDialogPane() != null && dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                    dialog.getDialogPane().getScene().getWindow().sizeToScene();
                }
            });
        });
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                String selectedWorld = cleanModelName(getModelFieldValue(worldModelField));
                String selectedAgent = cleanModelName(getModelFieldValue(agentModelField));
                String selectedProse = cleanModelName(getModelFieldValue(proseModelField));

                ollamaLlmService.configure(
                        baseUrlField.getText(),
                        selectedWorld,
                        selectedAgent,
                        selectedProse,
                        worldTempSlider.getValue(),
                        agentTempSlider.getValue(),
                        proseTempSlider.getValue(),
                        worldCtxSpinner.getValue(),
                        agentCtxSpinner.getValue(),
                        proseCtxSpinner.getValue()
                );

                statusLabel.setText(String.format("Configured: World [%s] | Agent [%s] | Prose [%s]",
                        ollamaLlmService.getModel(LlmRole.WORLD),
                        ollamaLlmService.getModel(LlmRole.AGENT),
                        ollamaLlmService.getModel(LlmRole.PROSE)));
            }
        });
    }

    private void updateModelComboBox(ComboBox<String> combo, List<String> updatedModels) {
        String current = getModelFieldValue(combo);
        String currentClean = cleanModelName(current);
        combo.getItems().clear();
        combo.getItems().addAll(updatedModels);

        String match = null;
        if (currentClean != null && !currentClean.isBlank()) {
            for (String m : updatedModels) {
                if (cleanModelName(m).equalsIgnoreCase(currentClean)) {
                    match = m;
                    break;
                }
            }
        }

        if (match != null) {
            combo.setValue(match);
        } else if (current != null && !current.isBlank()
                && !current.equals("world-m") && !current.equals("agent-m") && !current.equals("prose-m")) {
            combo.setValue(current);
        } else if (!updatedModels.isEmpty()) {
            combo.setValue(updatedModels.get(0));
        }
    }

    private String getModelFieldValue(ComboBox<String> cb) {
        if (cb == null) return null;
        if (cb.isEditable() && cb.getEditor() != null) {
            String text = cb.getEditor().getText();
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return cb.getValue();
    }

    private String cleanModelName(String raw) {
        if (raw == null) return null;
        if (raw.contains(" (")) {
            return raw.substring(0, raw.indexOf(" (")).trim();
        }
        return raw.trim();
    }

    private Slider createTempSlider(double initial) {
        Slider slider = new Slider(0.0, 1.5, Math.max(0.0, Math.min(1.5, initial)));
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(false);
        slider.setMajorTickUnit(0.5);
        slider.setBlockIncrement(0.1);
        slider.setPrefWidth(120);
        return slider;
    }

    private Spinner<Integer> createCtxSpinner(int initial) {
        int safeInitial = Math.max(1024, Math.min(65536, initial));
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1024, 65536, safeInitial, 1024);
        Spinner<Integer> spinner = new Spinner<>(factory);
        spinner.setEditable(true);
        spinner.setPrefWidth(90);
        return spinner;
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText(APP_NAME + " (v" + getAppVersion() + ")");
        alert.setContentText("Multi-Agent Murder Mystery Simulation System\nVersion: " + getAppVersion() + "\nProcedural mystery generator with real-time progress dashboard.");
        alert.showAndWait();
    }

    private VBox createAgentTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        
        agentCardPane = new BorderPane();
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        FlowPane flow = new FlowPane();
        flow.setHgap(10);
        flow.setVgap(10);
        flow.setPadding(new Insets(10));
        scroll.setContent(flow);
        agentCardPane.setCenter(scroll);
        
        box.getChildren().add(agentCardPane);
        VBox.setVgrow(agentCardPane, Priority.ALWAYS);
        return box;
    }

    private VBox createWorldTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        
        worldCardPane = new BorderPane();
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        FlowPane flow = new FlowPane();
        flow.setHgap(10);
        flow.setVgap(10);
        flow.setPadding(new Insets(10));
        scroll.setContent(flow);
        worldCardPane.setCenter(scroll);
        
        box.getChildren().add(worldCardPane);
        VBox.setVgrow(worldCardPane, Priority.ALWAYS);
        return box;
    }

    private VBox createItemsTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(12));
        
        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);
        
        itemsHeaderLabel = new Label("Story Items & Clues");
        itemsHeaderLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");
        
        Label filterLabel = new Label("Type:");
        filterLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #64748b;");
        
        itemTypeFilterCombo = new ComboBox<>();
        itemTypeFilterCombo.getItems().addAll("All Types", "WEAPON", "CLUE", "VALUABLE", "NOTEBOOK", "OTHER");
        itemTypeFilterCombo.setValue("All Types");
        itemTypeFilterCombo.setOnAction(e -> updateItemsTab());
        
        Label locFilterLabel = new Label("Location:");
        locFilterLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #64748b;");
        
        itemLocFilterCombo = new ComboBox<>();
        itemLocFilterCombo.getItems().addAll("All Locations", "Carried by Characters", "In Rooms", "Hidden Only");
        itemLocFilterCombo.setValue("All Locations");
        itemLocFilterCombo.setOnAction(e -> updateItemsTab());
        
        itemSearchField = new TextField();
        itemSearchField.setPromptText("Search item or location...");
        itemSearchField.setPrefWidth(200);
        itemSearchField.textProperty().addListener((obs, oldVal, newVal) -> updateItemsTab());
        
        topRow.getChildren().addAll(itemsHeaderLabel, filterLabel, itemTypeFilterCombo, locFilterLabel, itemLocFilterCombo, itemSearchField);
        
        itemsFlowPane = new FlowPane();
        itemsFlowPane.setHgap(12);
        itemsFlowPane.setVgap(12);
        itemsFlowPane.setPadding(new Insets(10));
        
        ScrollPane scroll = new ScrollPane(itemsFlowPane);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #f8fafc;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        
        box.getChildren().addAll(topRow, scroll);
        return box;
    }

    private void updateItemsTab() {
        if (itemsFlowPane == null) return;
        int currentStep = timelineSlider != null ? (int) timelineSlider.getValue() : state.getCurrentStep();
        String typeFilter = itemTypeFilterCombo != null ? itemTypeFilterCombo.getValue() : "All Types";
        String locFilter = itemLocFilterCombo != null ? itemLocFilterCombo.getValue() : "All Locations";
        String searchText = itemSearchField != null ? itemSearchField.getText().trim() : "";
        ItemsTabPresenter.renderItems(itemsFlowPane, itemsHeaderLabel, state, currentStep, typeFilter, locFilter, searchText);
    }

    private VBox createGlobalEventsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        
        Label titleLabel = new Label("Scheduled Global Setting Events");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #2c3e50;");
        Label subtitleLabel = new Label("Estate-wide occurrences that shape the environment, gathering times, and atmosphere.");
        subtitleLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px;");
        
        globalEventsPane = new BorderPane();
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        FlowPane flow = new FlowPane();
        flow.setHgap(15);
        flow.setVgap(15);
        flow.setPadding(new Insets(10));
        scroll.setContent(flow);
        globalEventsPane.setCenter(scroll);
        
        box.getChildren().addAll(titleLabel, subtitleLabel, globalEventsPane);
        VBox.setVgrow(globalEventsPane, Priority.ALWAYS);
        return box;
    }

    private void updateGlobalEventsTab() {
        if (globalEventsPane == null || !(globalEventsPane.getCenter() instanceof ScrollPane)) return;
        ScrollPane scroll = (ScrollPane) globalEventsPane.getCenter();
        FlowPane flow = (FlowPane) scroll.getContent();
        flow.getChildren().clear();

        int currentScrubbedStep = timelineSlider != null ? (int) timelineSlider.getValue() : state.getCurrentStep();

        if (state.getGlobalEvents().isEmpty()) {
            Label emptyLabel = new Label("No Global Events scheduled for this mystery yet.");
            emptyLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-style: italic; -fx-font-size: 14px;");
            flow.getChildren().add(emptyLabel);
            return;
        }

        for (com.murderhecoded.domain.GlobalEvent ge : state.getGlobalEvents().values()) {
            VBox card = new VBox(8);
            card.setPadding(new Insets(12));
            card.setPrefWidth(320);

            boolean isActive = ge.isActiveAt(currentScrubbedStep);
            boolean isConcluded = ge.isConcludedAt(currentScrubbedStep);

            String borderColor = "#cbd5e1";
            String bgColor = "#ffffff";
            String statusText = "SCHEDULED";
            String statusBg = "#2980b9";

            if (isActive) {
                borderColor = "#27ae60";
                bgColor = "#f0fff4";
                statusText = "★ ACTIVE NOW";
                statusBg = "#27ae60";
            } else if (isConcluded) {
                borderColor = "#e2e8f0";
                bgColor = "#f8fafc";
                statusText = "CONCLUDED";
                statusBg = "#7f8c8d";
            }

            card.setStyle("-fx-border-color: " + borderColor + "; -fx-border-width: 2px; -fx-background-color: " + bgColor + "; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 5, 0, 0, 1);");

            // Header Row: Title & Status
            HBox topRow = new HBox(8);
            topRow.setAlignment(Pos.CENTER_LEFT);
            Label nameLabel = new Label(ge.getName());
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1e293b;");
            nameLabel.setWrapText(true);
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            Label badge = new Label(statusText);
            badge.setStyle("-fx-background-color: " + statusBg + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 10px;");
            topRow.getChildren().addAll(nameLabel, badge);

            // Time & Type Row
            String startTime = state.getClockTime(ge.getScheduledStep());
            String endTime = state.getClockTime(ge.getScheduledStep() + Math.max(1, ge.getDurationSteps()));
            Label timeLabel = new Label(String.format("Time: %s - %s (Steps %d - %d)", startTime, endTime, ge.getScheduledStep(), ge.getScheduledStep() + ge.getDurationSteps()));
            timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");

            Label typeLabel = new Label("Impact: " + (ge.getImpactType() != null ? ge.getImpactType() : "GATHERING"));
            typeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-background-color: #e2e8f0; -fx-padding: 2 6; -fx-background-radius: 4px;");

            HBox metaBox = new HBox(8, typeLabel);
            metaBox.setAlignment(Pos.CENTER_LEFT);

            if (ge.getTargetLocationId() != null && state.getLocations().containsKey(ge.getTargetLocationId())) {
                Label locLabel = new Label("Location: " + state.getLocations().get(ge.getTargetLocationId()).getName());
                locLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #1e88e5; -fx-background-color: #e3f2fd; -fx-padding: 2 6; -fx-background-radius: 4px;");
                metaBox.getChildren().add(locLabel);
            }

            // Description
            Label descLabel = new Label(ge.getDescription());
            descLabel.setWrapText(true);
            descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");

            // Jump to event button
            Button jumpBtn = new Button("Jump to Step " + ge.getScheduledStep());
            jumpBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8;");
            jumpBtn.setOnAction(e -> {
                if (timelineSlider != null) {
                    timelineSlider.setValue(ge.getScheduledStep());
                }
            });

            card.getChildren().addAll(topRow, timeLabel, metaBox, descLabel, jumpBtn);
            flow.getChildren().add(card);
        }
    }

    private VBox createSimulationTab() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(15));
        
        // 1. Setup Section
        VBox configSection = new VBox(10);
        configSection.setStyle("-fx-background-color: #f7f9fa; -fx-padding: 15; -fx-border-color: #dcdfe3; -fx-border-radius: 6; -fx-background-radius: 6;");
        
        Label configHeader = new Label("Story & Simulation Setup");
        configHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #2c3e50;");
        
        HBox configBox = new HBox(12);
        configBox.setAlignment(Pos.CENTER_LEFT);
        
        targetStepsCombo = new ComboBox<>();
        targetStepsCombo.getItems().addAll(20, 40, 60, 80, 100, 120, 140);
        targetStepsCombo.setValue(defaultSteps == 20 || defaultSteps == 40 || defaultSteps == 60 || defaultSteps == 80 || defaultSteps == 100 || defaultSteps == 120 || defaultSteps == 140 ? defaultSteps : 100);
        targetStepsCombo.setPrefWidth(75);
        
        stepMinutesCombo = new ComboBox<>();
        stepMinutesCombo.getItems().addAll(15, 20, 30, 60);
        stepMinutesCombo.setValue(15);
        stepMinutesCombo.setPrefWidth(70);
        
        numAgentsSpinner = createSafeIntegerSpinner(3, 15, defaultAgents, "Number of characters in the mystery (3 - 15)");
        numPlacesSpinner = createSafeIntegerSpinner(3, 15, defaultPlaces, "Number of locations to explore (3 - 15)");
        numItemsSpinner = createSafeIntegerSpinner(1, 10, defaultItems, "Number of items per location (1 - 10)");
        
        Label totalItemsBadge = new Label();
        totalItemsBadge.setStyle("-fx-background-color: #edf2f7; -fx-text-fill: #4a5568; -fx-padding: 4 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold; -fx-border-color: #cbd5e0; -fx-border-radius: 4;");
        Runnable updateTotalBadge = () -> {
            int p = getParsedOrFallback(numPlacesSpinner, defaultPlaces);
            int it = getParsedOrFallback(numItemsSpinner, defaultItems);
            totalItemsBadge.setText(String.format("Total: %d items", p * it));
        };
        updateTotalBadge.run();
        numPlacesSpinner.valueProperty().addListener((obs, o, n) -> updateTotalBadge.run());
        numItemsSpinner.valueProperty().addListener((obs, o, n) -> updateTotalBadge.run());
        numPlacesSpinner.getEditor().textProperty().addListener((obs, o, n) -> updateTotalBadge.run());
        numItemsSpinner.getEditor().textProperty().addListener((obs, o, n) -> updateTotalBadge.run());

        proseToggle = new CheckBox("Generate Prose");
        proseToggle.setSelected(true);
        
        String initialTitle = state != null && state.getTitle() != null && !state.getTitle().isBlank()
                ? state.getTitle() : (defaultTitle != null && !defaultTitle.isBlank() ? defaultTitle : "Murder on the Orient Express");
        if (state != null) {
            state.setTitle(initialTitle);
        }
        storyTitleLabel = new Label("Title:");
        storyTitleField = new TextField(initialTitle);
        storyTitleField.setPrefWidth(350);
        storyTitleField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (state != null) {
                state.setTitle(newVal != null ? newVal.trim() : "");
            }
            updateActiveProjectDisplay();
        });
        
        String initialDesc = state != null && state.getPremise() != null && !state.getPremise().isBlank()
                ? state.getPremise() : (defaultDescription != null && !defaultDescription.isBlank() ? defaultDescription : "A classic murder mystery at an isolated location.");
        if (state != null) {
            state.setPremise(initialDesc);
        }
        storyDescLabel = new Label("Premise / Description:");
        storyDescArea = new TextArea(initialDesc);
        storyDescArea.setPrefRowCount(3);
        storyDescArea.setWrapText(true); // Line wrapping for description
        storyDescArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (state != null) {
                state.setPremise(newVal != null ? newVal.trim() : "");
            }
        });
        
        authorVoiceLabel = new Label("Author Voice:");
        authorVoiceCombo = new ComboBox<>();
        authorVoiceCombo.getItems().addAll("Agatha Christie", "Arthur Conan Doyle", "Raymond Chandler", "Edgar Allan Poe", "Stephen King", "Default");
        authorVoiceCombo.setValue("Agatha Christie");
        authorVoiceCombo.setEditable(true);
        
        sceneLengthLabel = new Label("Scene Length:");
        sceneLengthCombo = new ComboBox<>();
        sceneLengthCombo.getItems().addAll("Short", "Medium", "Long");
        sceneLengthCombo.setValue(defaultSceneLength);
        
        sceneDirectorToggle = new CheckBox("🎬 LLM Scene Director");
        sceneDirectorToggle.setSelected(true);
        sceneDirectorToggle.setStyle("-fx-font-weight: bold; -fx-text-fill: #b45309;");
        
        HBox voiceBox = new HBox(12, authorVoiceLabel, authorVoiceCombo, sceneLengthLabel, sceneLengthCombo, proseToggle, sceneDirectorToggle);
        voiceBox.setAlignment(Pos.CENTER_LEFT);
        
        configBox.getChildren().addAll(
            new Label("Steps:"), targetStepsCombo,
            new Label("Step Interval:"), stepMinutesCombo, new Label("mins"),
            new Label("Characters:"), numAgentsSpinner,
            new Label("Places:"), numPlacesSpinner,
            new Label("Items / Place:"), numItemsSpinner,
            totalItemsBadge
        );
        
        HBox titleRow = new HBox(10, storyTitleLabel, storyTitleField);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        
        configSection.getChildren().addAll(configHeader, titleRow, storyDescLabel, storyDescArea, voiceBox, configBox);
        
        // 2. Action Controls
        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        
        buildWorldBtn = new Button("🌍 1. Build World");
        buildWorldBtn.setStyle("-fx-background-color: #0284c7; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 16;");
        buildWorldBtn.setOnAction(e -> buildWorld());

        startBtn = new Button("▶ 2. Start Simulation");
        startBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 16;");
        startBtn.setOnAction(e -> startSimulation());
        startBtn.setDisable(!state.isWorldGenerated());

        resumeBtn = new Button("⏯ Resume");
        resumeBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 16;");
        resumeBtn.setOnAction(e -> resumeSimulation());
        resumeBtn.setManaged(false);
        resumeBtn.setVisible(false);
        
        generateScenesBtn = new Button("🎬 3. Generate Scenes");
        generateScenesBtn.setStyle("-fx-background-color: #d97706; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 16;");
        generateScenesBtn.setOnAction(e -> generateScenesForCurrentSimulation());
        generateScenesBtn.setDisable(state.getCurrentStep() == 0);

        generateProseBtn = new Button("✍️ 4. Generate Prose");
        generateProseBtn.setStyle("-fx-background-color: #9333ea; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 16;");
        generateProseBtn.setOnAction(e -> generateProseForCurrentSimulation());
        generateProseBtn.setDisable(chapters.isEmpty());

        exportStoryBtn = new Button("💾 Export Story (TXT & PDF)");
        exportStoryBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 16;");
        exportStoryBtn.setOnAction(e -> exportStoryToProjectDir());
        updateExportButtonState();

        Region stopSpacer = new Region();
        HBox.setHgrow(stopSpacer, Priority.ALWAYS);

        stopBtn = new Button("⏹ Pause / Stop");
        stopBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 16;");
        stopBtn.setOnAction(e -> stopSimulation());
        stopBtn.setDisable(true);

        buttonBox.getChildren().addAll(
                buildWorldBtn,
                startBtn,
                resumeBtn,
                generateScenesBtn,
                generateProseBtn,
                exportStoryBtn,
                stopSpacer,
                stopBtn
        );
        
        // 3. Generation & Progress Dashboard Section
        VBox dashboardSection = new VBox(12);
        dashboardSection.setStyle("-fx-background-color: #ffffff; -fx-padding: 15; -fx-border-color: #bdc3c7; -fx-border-radius: 6; -fx-background-radius: 6;");
        VBox.setVgrow(dashboardSection, Priority.ALWAYS);
        
        Label dashHeader = new Label("Generation & Simulation Progress");
        dashHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #2c3e50;");
        
        // Panel 1: World Generation
        VBox worldCard = new VBox(5);
        worldCard.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 4;");
        Label worldTitle = new Label("1. World Generation Status");
        worldTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #34495e;");
        
        worldStatusBadge = new Label("Not Started");
        worldStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        
        worldDetailsLabel = new Label("No characters, places, or items generated yet.");
        worldDetailsLabel.setStyle("-fx-text-fill: #555;");
        worldCard.getChildren().addAll(worldTitle, worldStatusBadge, worldDetailsLabel);
        
        // Panel 2: Simulation Scenes Progress
        VBox simCard = new VBox(6);
        simCard.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 4;");
        Label simTitle = new Label("2. Simulation Scenes & Pacing");
        simTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #34495e;");
        
        simProgressBar = new ProgressBar(0.0);
        simProgressBar.setPrefWidth(500);
        simProgressBar.setPrefHeight(18);
        
        simProgressLabel = new Label("Step 0 of 144 (0.0%) - Idle");
        simProgressLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2980b9;");
        
        simDetailsLabel = new Label("Scenes generated: 0 | Remaining steps: 144 (36 hours)");
        simDetailsLabel.setStyle("-fx-text-fill: #555;");
        simCard.getChildren().addAll(simTitle, simProgressBar, simProgressLabel, simDetailsLabel);
        
        // Panel 3: Story Scenes (Editorial Curation)
        VBox scenesCard = new VBox(6);
        scenesCard.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 4;");
        Label scenesTitle = new Label("3. Story Scenes & Dramaturgy Framing");
        scenesTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #34495e;");

        scenesStatusBadge = new Label("No Scenes Curated");
        scenesStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d;");

        scenesDetailsLabel = new Label("Run simulation steps, then click '🎬 Generate Scenes' to frame story scenes.");
        scenesDetailsLabel.setStyle("-fx-text-fill: #555;");
        scenesCard.getChildren().addAll(scenesTitle, scenesStatusBadge, scenesDetailsLabel);

        // Panel 4: Prose Pipeline Progress
        VBox proseCard = new VBox(6);
        proseCard.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 4;");
        Label proseTitle = new Label("4. Prose Engine (Chapters Generated)");
        proseTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #34495e;");
        
        proseProgressBar = new ProgressBar(0.0);
        proseProgressBar.setPrefWidth(500);
        proseProgressBar.setPrefHeight(18);
        
        proseProgressLabel = new Label("Scenes with Prose: 0 (0.0%)");
        proseProgressLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #8e44ad;");
        
        proseDetailsLabel = new Label("Chapters written: 0 | Pending in queue: 0 scenes");
        proseDetailsLabel.setStyle("-fx-text-fill: #555;");
        proseCard.getChildren().addAll(proseTitle, proseProgressBar, proseProgressLabel, proseDetailsLabel);
        
        proseCard.managedProperty().bind(proseToggle.selectedProperty());
        proseCard.visibleProperty().bind(proseToggle.selectedProperty());
        
        dashboardSection.getChildren().addAll(dashHeader, worldCard, simCard, scenesCard, proseCard);
        
        box.getChildren().addAll(configSection, buttonBox, dashboardSection);
        return box;
    }

    private VBox createLogTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        
        HBox topBox = new HBox(12);
        topBox.setAlignment(Pos.CENTER_LEFT);
        
        Label logTitle = new Label("Live Simulation Event Log");
        logTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #2c3e50;");
        HBox.setHgrow(logTitle, Priority.ALWAYS);
        
        saveLogBtn = new Button("Save Log to File");
        saveLogBtn.setOnAction(e -> saveEventLog());
        
        clearLogBtn = new Button("Clear Log");
        clearLogBtn.setOnAction(e -> {
            if (eventLogArea != null) eventLogArea.clear();
        });
        
        topBox.getChildren().addAll(logTitle, saveLogBtn, clearLogBtn);
        
        eventLogArea = new TextArea();
        eventLogArea.setEditable(false);
        eventLogArea.setWrapText(true);
        eventLogArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");
        VBox.setVgrow(eventLogArea, Priority.ALWAYS);
        
        box.getChildren().addAll(topBox, eventLogArea);
        return box;
    }
    
    private BorderPane createStoryTab() {
        BorderPane pane = new BorderPane();
        
        storyTimelineBox = new HBox(5);
        storyTimelineBox.setPadding(new Insets(10));
        ScrollPane timelineScroll = new ScrollPane(storyTimelineBox);
        timelineScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        timelineScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        timelineScroll.setMinHeight(70);
        pane.setTop(timelineScroll);
        
        storySubTabPane = new TabPane();
        
        Tab highlightsTab = new Tab("Highlights");
        highlightsTab.setClosable(false);
        highlightsArea = new WebView();
        highlightsTab.setContent(highlightsArea);
        
        Tab agentsTab = new Tab("Characters");
        agentsTab.setClosable(false);
        agentsTab.setContent(createAgentTab());
        
        Tab itemsTab = new Tab("Items");
        itemsTab.setClosable(false);
        itemsTab.setContent(createItemsTab());
        
        Tab worldTab = new Tab("World");
        worldTab.setClosable(false);
        worldTab.setContent(createWorldTab());
        
        Tab personDetailTab = new Tab("Person Detail");
        personDetailTab.setClosable(false);
        personDetailTab.setContent(createPersonDetailTab());

        Tab itemDetailTab = new Tab("Item Detail");
        itemDetailTab.setClosable(false);
        itemDetailTab.setContent(createItemDetailTab());

        Tab locationDetailTab = new Tab("Location Detail");
        locationDetailTab.setClosable(false);
        locationDetailTab.setContent(createLocationDetailTab());
        
        Tab rawProseTab = new Tab("Raw Prose");
        rawProseTab.setClosable(false);
        VBox rawBox = new VBox(10);
        rawBox.setPadding(new Insets(10));
        HBox filterBox = new HBox(10);
        textModeGroup = new ToggleGroup();
        rawBtn = new RadioButton("Raw Mode");
        rawBtn.setToggleGroup(textModeGroup);
        rawBtn.setSelected(true);
        editBtn = new RadioButton("Edited Mode");
        editBtn.setToggleGroup(textModeGroup);
        textModeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> renderStory());

        Button generateProseStoryBtn = new Button("✍️ Generate Prose from Events");
        generateProseStoryBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 4 10; -fx-font-weight: bold;");
        generateProseStoryBtn.setOnAction(e -> generateProseForCurrentSimulation());

        Button exportStoryStoryBtn = new Button("💾 Export Story (TXT & PDF)");
        exportStoryStoryBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 4 10; -fx-font-weight: bold;");
        exportStoryStoryBtn.setOnAction(e -> exportStoryToProjectDir());

        filterBox.getChildren().addAll(rawBtn, editBtn, generateProseStoryBtn, exportStoryStoryBtn);
        
        storyArea = new WebView();
        VBox.setVgrow(storyArea, Priority.ALWAYS);
        rawBox.getChildren().addAll(filterBox, storyArea);
        rawProseTab.setContent(rawBox);
        
        Tab dramaturgyTab = new Tab("Dramaturgy Curve");
        dramaturgyTab.setClosable(false);
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Time Step");
        NumberAxis yAxis = new NumberAxis(0.0, 10.0, 1.0);
        yAxis.setLabel("Dramatic Tension (0 – 10)");
        yAxis.setAutoRanging(false);
        dramaturgyChart = new LineChart<>(xAxis, yAxis);
        dramaturgyHeaderLabel = new Label("Dramaturgy Tension Curve");
        dramaturgyStatsBox = new HBox();
        BorderPane dramaturgyView = DramaturgyChartPresenter.createView(dramaturgyChart, dramaturgyHeaderLabel, dramaturgyStatsBox);
        dramaturgyTab.setContent(dramaturgyView);
        dramaturgyTab.setOnSelectionChanged(e -> {
            if (dramaturgyTab.isSelected()) {
                updateDramaturgyChart();
            }
        });

        Tab eventsTreeTab = new Tab("Events Tree");
        eventsTreeTab.setClosable(false);
        timelineTreeView = new TreeView<>();
        timelineTreeDetailBox = new VBox();
        BorderPane eventsTreeView = TimelineTreePresenter.createView(timelineTreeView, timelineTreeDetailBox, state, step -> {
            if (timelineSlider != null) {
                timelineSlider.setValue(step);
            }
        });
        eventsTreeTab.setContent(eventsTreeView);

        Tab globalEventsTab = new Tab("Global Events");
        globalEventsTab.setClosable(false);
        globalEventsTab.setContent(createGlobalEventsTab());
        
        storySubTabPane.getTabs().addAll(highlightsTab, dramaturgyTab, eventsTreeTab, globalEventsTab, agentsTab, itemsTab, worldTab, personDetailTab, itemDetailTab, locationDetailTab, rawProseTab);
        pane.setCenter(storySubTabPane);
        
        return pane;
    }

    private void updateTimelineMarkers() {
        if (storyTimelineBox != null) {
            storyTimelineBox.getChildren().clear();
            int maxStep = state.getCurrentStep();
            
            java.util.Map<Integer, String> stepColors = new java.util.HashMap<>();
            // Highlight scheduled global events in light blue
            for (com.murderhecoded.domain.GlobalEvent ge : state.getGlobalEvents().values()) {
                int duration = Math.max(1, ge.getDurationSteps());
                for (int s = ge.getScheduledStep(); s < ge.getScheduledStep() + duration; s++) {
                    stepColors.put(s, "#dbeafe");
                }
            }
            
            int currentParsingStep = -1;
            for (String log : state.getEventHistory()) {
                if (log.startsWith("[Time Step: ")) {
                    int endIdx = log.indexOf(" - ");
                    if (endIdx > 12) {
                        try {
                            currentParsingStep = Integer.parseInt(log.substring(12, endIdx).trim());
                        } catch (NumberFormatException ignored) {}
                    }
                } else if (currentParsingStep >= 0) {
                    if (log.contains("executed MURDER") || log.contains(" MURDER ") || log.contains("DISCOVERED the dead body") || log.contains("Alarm: Body Discovered")) {
                        stepColors.put(currentParsingStep, "#ffcccc"); // Red for murder / discovery
                    } else if ((log.contains("executed SEARCH") && log.contains("DISCOVERED")) || log.contains("executed ACCUSE") || log.contains(" ACCUSATION")) {
                        if (!"#ffcccc".equals(stepColors.get(currentParsingStep))) {
                            stepColors.put(currentParsingStep, "#ffffcc"); // Yellow for clues / accusations
                        }
                    }
                }
            }
            
            for (int i = 0; i <= maxStep; i++) {
                Button btn = new Button(String.valueOf(i));
                btn.setMinSize(35, 35);
                btn.setPadding(new Insets(2));
                
                String color = stepColors.getOrDefault(i, "#e0e0e0");
                if (i == (int) timelineSlider.getValue()) {
                    btn.setStyle("-fx-background-color: " + color + "; -fx-border-color: #000000; -fx-border-width: 2px; -fx-font-weight: bold;");
                } else {
                    btn.setStyle("-fx-background-color: " + color + "; -fx-border-color: #888;");
                }
                
                final int step = i;
                btn.setOnAction(e -> {
                    timelineSlider.setValue(step);
                    Platform.runLater(this::updateTimelineMarkers);
                });
                storyTimelineBox.getChildren().add(btn);
            }
        }
    }

    private void updateHighlights() {
        if (highlightsArea != null) {
            StringBuilder html = new StringBuilder();
            html.append("<html><head><meta charset='UTF-8'><style>");
            html.append("body { font-family: system-ui, -apple-system, 'Segoe UI', Arial, sans-serif; font-size: 15px; padding: 20px; color: #333; line-height: 1.6; -webkit-font-smoothing: antialiased; text-rendering: optimizeLegibility; } ");
            html.append(".major-event { background-color: #eff6ff; padding: 12px 16px; margin-bottom: 14px; border-left: 6px solid #2563eb; border-radius: 6px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
            html.append(".murder { background-color: #fee2e2; padding: 12px 16px; margin-bottom: 14px; border-left: 6px solid #dc2626; border-radius: 6px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
            html.append(".clue { background-color: #fefce8; padding: 12px 16px; margin-bottom: 14px; border-left: 6px solid #eab308; border-radius: 6px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
            html.append("h2 { color: #1e293b; border-bottom: 2px solid #e2e8f0; padding-bottom: 8px; margin-top: 0; }");
            html.append("h3 { color: #334155; margin: 18px 0 10px 0; font-size: 15px; text-transform: uppercase; letter-spacing: 0.5px; }");
            html.append("</style></head><body>");
            html.append("<h2>Story Highlights & Major Events</h2>");
            
            boolean hasHighlights = false;

            // 1. Major Global & Dramaturgic Events
            if (state != null && state.getGlobalEvents() != null && !state.getGlobalEvents().isEmpty()) {
                html.append("<h3>⚡ Major Dramaturgic & Global Events</h3>");
                for (com.murderhecoded.domain.GlobalEvent ge : state.getGlobalEvents().values()) {
                    int duration = Math.max(1, ge.getDurationSteps());
                    String timeSpan = state.formatInterval(ge.getScheduledStep(), ge.getScheduledStep() + duration);
                    html.append("<div class='major-event'>");
                    html.append("<b style='color:#1e40af;'>").append(ge.getName()).append("</b> ");
                    html.append("<span style='font-size:12px; color:#64748b; margin-left:8px;'>[").append(timeSpan).append(" &bull; Step ").append(ge.getScheduledStep()).append("]</span><br/>");
                    html.append("<span style='font-size:14px; color:#334155;'>").append(ge.getDescription()).append("</span>");
                    html.append("</div>");
                    hasHighlights = true;
                }
            }

            // 2. Timeline Highlights (Murders, Discoveries, Clues, Accusations)
            List<String> keyEvents = new ArrayList<>();
            if (state != null && state.getEventHistory() != null) {
                for (String log : state.getEventHistory()) {
                    if (log.contains("executed MURDER") || log.contains(" MURDER ") || log.contains("DISCOVERED the dead body") || log.contains("Alarm: Body Discovered")) {
                        keyEvents.add("<div class='murder'>💀 <b>CLIMAX / MURDER:</b> " + log + "</div>");
                    } else if ((log.contains("executed SEARCH") && log.contains("DISCOVERED")) || log.contains("DISCOVERED the hidden item") || log.contains("executed ACCUSE") || log.contains(" ACCUSATION")) {
                        keyEvents.add("<div class='clue'>🔍 <b>DISCOVERY / CLUE:</b> " + log + "</div>");
                    }
                }
            }

            if (!keyEvents.isEmpty()) {
                html.append("<h3>🔍 Critical Turning Points & Clues</h3>");
                for (String evHtml : keyEvents) {
                    html.append(evHtml);
                    hasHighlights = true;
                }
            }
            
            if (!hasHighlights) {
                html.append("<p style='color: #888;'>No major events have occurred yet...</p>");
            }
            html.append("</body></html>");
            Platform.runLater(() -> highlightsArea.getEngine().loadContent(html.toString()));
        }
    }

    private BorderPane createPersonDetailTab() {
        BorderPane pane = new BorderPane();
        
        SplitPane splitPane = new SplitPane();
        
        // Left Column: TreeView with Categories & Characters
        VBox leftBox = new VBox(8);
        leftBox.setPadding(new Insets(10));
        leftBox.setStyle("-fx-background-color: #f8fafc;");
        
        Label treeHeader = new Label("Characters Directory");
        treeHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #334155;");
        
        personTreeView = new TreeView<>();
        personTreeView.setShowRoot(false);
        personTreeView.setStyle("-fx-font-family: 'Segoe UI', system-ui, sans-serif; -fx-font-size: 13px;");
        VBox.setVgrow(personTreeView, Priority.ALWAYS);
        
        personTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null) {
                if (!newVal.getValue().isCategory()) {
                    selectedPersonId = newVal.getValue().agentId();
                    renderPersonDetail();
                } else if (!newVal.getChildren().isEmpty()) {
                    personTreeView.getSelectionModel().select(newVal.getChildren().get(0));
                }
            }
        });
        
        leftBox.getChildren().addAll(treeHeader, personTreeView);
        
        // Right Column: Crisp Native JavaFX Details
        personDetailContentBox = new VBox(12);
        personDetailContentBox.setPadding(new Insets(16));
        
        ScrollPane rightScroll = new ScrollPane(personDetailContentBox);
        rightScroll.setFitToWidth(true);
        rightScroll.setStyle("-fx-background-color: transparent; -fx-background: #f1f5f9;");
        
        splitPane.getItems().addAll(leftBox, rightScroll);
        splitPane.setDividerPositions(0.28);
        
        pane.setCenter(splitPane);
        return pane;
    }

    private void updatePersonTreeView() {
        if (personTreeView == null) return;
        int currentStep = timelineSlider != null ? (int) timelineSlider.getValue() : state.getCurrentStep();
        selectedPersonId = PersonDetailPresenter.updatePersonTreeView(personTreeView, state, currentStep, selectedPersonId);
    }

    private void renderPersonDetail() {
        if (personDetailContentBox == null) return;
        int currentStep = timelineSlider != null ? (int) timelineSlider.getValue() : state.getCurrentStep();
        PersonDetailPresenter.renderPersonDetail(personDetailContentBox, state, currentStep, selectedPersonId);
    }

    private BorderPane createItemDetailTab() {
        BorderPane pane = new BorderPane();
        
        SplitPane splitPane = new SplitPane();
        
        // Left Column: TreeView with Categories & Items
        VBox leftBox = new VBox(8);
        leftBox.setPadding(new Insets(10));
        leftBox.setStyle("-fx-background-color: #f8fafc;");
        
        Label treeHeader = new Label("Items Directory");
        treeHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #334155;");
        
        itemTreeView = new TreeView<>();
        itemTreeView.setShowRoot(false);
        itemTreeView.setStyle("-fx-font-family: 'Segoe UI', system-ui, sans-serif; -fx-font-size: 13px;");
        VBox.setVgrow(itemTreeView, Priority.ALWAYS);
        
        itemTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null) {
                if (!newVal.getValue().isCategory()) {
                    selectedItemId = newVal.getValue().itemId();
                    renderItemDetail();
                } else if (!newVal.getChildren().isEmpty()) {
                    itemTreeView.getSelectionModel().select(newVal.getChildren().get(0));
                }
            }
        });
        
        leftBox.getChildren().addAll(treeHeader, itemTreeView);
        
        // Right Column: Crisp Native JavaFX Details
        itemDetailContentBox = new VBox(12);
        itemDetailContentBox.setPadding(new Insets(16));
        
        ScrollPane rightScroll = new ScrollPane(itemDetailContentBox);
        rightScroll.setFitToWidth(true);
        rightScroll.setStyle("-fx-background-color: transparent; -fx-background: #f1f5f9;");
        
        splitPane.getItems().addAll(leftBox, rightScroll);
        splitPane.setDividerPositions(0.28);
        
        pane.setCenter(splitPane);
        return pane;
    }

    private void updateItemTreeView() {
        if (itemTreeView == null) return;
        int currentStep = timelineSlider != null ? (int) timelineSlider.getValue() : state.getCurrentStep();
        selectedItemId = ItemDetailPresenter.updateItemTreeView(itemTreeView, state, currentStep, selectedItemId);
    }

    private void renderItemDetail() {
        if (itemDetailContentBox == null) return;
        int currentStep = timelineSlider != null ? (int) timelineSlider.getValue() : state.getCurrentStep();
        ItemDetailPresenter.renderItemDetail(itemDetailContentBox, state, currentStep, selectedItemId);
    }

    private BorderPane createLocationDetailTab() {
        BorderPane pane = new BorderPane();
        
        SplitPane splitPane = new SplitPane();
        
        // Left Column: TreeView with Locations
        VBox leftBox = new VBox(8);
        leftBox.setPadding(new Insets(10));
        leftBox.setStyle("-fx-background-color: #f8fafc;");
        
        Label treeHeader = new Label("Locations Directory");
        treeHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #334155;");
        
        locationTreeView = new TreeView<>();
        locationTreeView.setShowRoot(false);
        locationTreeView.setStyle("-fx-font-family: 'Segoe UI', system-ui, sans-serif; -fx-font-size: 13px;");
        VBox.setVgrow(locationTreeView, Priority.ALWAYS);
        
        locationTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null) {
                if (!newVal.getValue().isCategory()) {
                    selectedLocationId = newVal.getValue().locationId();
                    renderLocationDetail();
                } else if (!newVal.getChildren().isEmpty()) {
                    locationTreeView.getSelectionModel().select(newVal.getChildren().get(0));
                }
            }
        });
        
        leftBox.getChildren().addAll(treeHeader, locationTreeView);
        
        // Right Column: Crisp Native JavaFX Details
        locationDetailContentBox = new VBox(12);
        locationDetailContentBox.setPadding(new Insets(16));
        
        ScrollPane rightScroll = new ScrollPane(locationDetailContentBox);
        rightScroll.setFitToWidth(true);
        rightScroll.setStyle("-fx-background-color: transparent; -fx-background: #f1f5f9;");
        
        splitPane.getItems().addAll(leftBox, rightScroll);
        splitPane.setDividerPositions(0.28);
        
        pane.setCenter(splitPane);
        return pane;
    }

    private void updateLocationTreeView() {
        if (locationTreeView == null) return;
        int currentStep = timelineSlider != null ? (int) timelineSlider.getValue() : state.getCurrentStep();
        selectedLocationId = LocationDetailPresenter.updateLocationTreeView(locationTreeView, state, currentStep, selectedLocationId);
    }

    private void renderLocationDetail() {
        if (locationDetailContentBox == null) return;
        int currentStep = timelineSlider != null ? (int) timelineSlider.getValue() : state.getCurrentStep();
        LocationDetailPresenter.renderLocationDetail(locationDetailContentBox, state, currentStep, selectedLocationId);
    }

    private void renderStory() {
        boolean editedMode = textModeGroup != null && textModeGroup.getSelectedToggle() != null
                && ((RadioButton) textModeGroup.getSelectedToggle()).getText().startsWith("Edited");
        com.murderhecoded.service.HtmlRendererService renderer = new com.murderhecoded.service.HtmlRendererService();
        String html = renderer.renderStory(chapters, editedMode, bundle, buildStoryMetadataHtml());
        storyArea.getEngine().loadContent(html);
        updateStoryStats();
    }

    public int calculateTotalWordCount() {
        if (chapters == null) {
            return 0;
        }
        return chapters.stream()
                .mapToInt(Chapter::wordCount)
                .sum();
    }

    private void updateStoryStats() {
        if (storyStatsLabel == null) return;
        boolean onStoryTab = tabPane != null && tabPane.getSelectionModel().getSelectedItem() == storyTab;
        boolean editedMode = onStoryTab && textModeGroup != null && textModeGroup.getSelectedToggle() != null
                && ((RadioButton) textModeGroup.getSelectedToggle()).getText().startsWith("Edited");

        int wordCount = chapters.stream()
                .filter(c -> !editedMode || !c.isFluff())
                .mapToInt(Chapter::wordCount)
                .sum();
        int totalWordsAll = calculateTotalWordCount();
        int scenesCount = chapters.size();

        if (totalWordsAll > 0 || (onStoryTab && scenesCount > 0)) {
            String wordText = String.format("%,d words", wordCount);
            String sceneText = String.format("%d %s", scenesCount, scenesCount == 1 ? "scene" : "scenes");
            storyStatsLabel.setText("📖 " + wordText + " | " + sceneText);
            storyStatsLabel.setVisible(true);
            storyStatsLabel.setManaged(true);
        } else {
            storyStatsLabel.setVisible(false);
            storyStatsLabel.setManaged(false);
        }
    }

    private Spinner<Integer> createSafeIntegerSpinner(int min, int max, int initial, String tooltipText) {
        int safeInitial = Math.max(min, Math.min(max, initial));
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
            new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, safeInitial);

        Spinner<Integer> spinner = new Spinner<>(factory);
        spinner.setEditable(true);
        spinner.setPrefWidth(70);
        if (tooltipText != null && !tooltipText.isEmpty()) {
            spinner.setTooltip(new Tooltip(tooltipText));
        }

        // Restrict input to digits only
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty() || newText.matches("\\d{1,4}")) {
                return change;
            }
            return null;
        };

        TextFormatter<Integer> formatter = new TextFormatter<>(
            factory.getConverter(),
            safeInitial,
            filter
        );
        spinner.getEditor().setTextFormatter(formatter);

        // Keep factory and text formatter synchronized
        factory.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(formatter.getValue())) {
                formatter.setValue(newVal);
            }
        });

        // Commit on focus lost
        spinner.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                commitSpinnerValue(spinner, min, max);
            }
        });

        // Commit on Enter
        spinner.getEditor().setOnAction(e -> commitSpinnerValue(spinner, min, max));

        return spinner;
    }

    private void commitSpinnerValue(Spinner<Integer> spinner, int min, int max) {
        if (spinner == null || spinner.getValueFactory() == null) {
            return;
        }
        String text = spinner.getEditor().getText().trim();
        if (text.isEmpty()) {
            spinner.getValueFactory().setValue(min);
            spinner.getEditor().setText(String.valueOf(min));
            return;
        }
        try {
            int val = Integer.parseInt(text);
            int clamped = Math.max(min, Math.min(max, val));
            spinner.getValueFactory().setValue(clamped);
            spinner.getEditor().setText(String.valueOf(clamped));
        } catch (NumberFormatException e) {
            Integer current = spinner.getValue();
            int fallback = current != null ? current : min;
            spinner.getEditor().setText(String.valueOf(fallback));
        }
    }

    private int getParsedOrFallback(Spinner<Integer> spinner, int fallback) {
        if (spinner == null) {
            return fallback;
        }
        try {
            String text = spinner.getEditor().getText().trim();
            if (!text.isEmpty()) {
                return Integer.parseInt(text);
            }
        } catch (NumberFormatException ignored) {
            // fallback
        }
        return spinner.getValue() != null ? spinner.getValue() : fallback;
    }

    private void buildWorld() {
        if (simulationService.isRunning() || proseService.isRunning() || (currentInitTask != null && currentInitTask.isRunning())) {
            setGenerationRunning(false, "Please wait for current operation to finish.");
            return;
        }

        if (state.isWorldGenerated() && state.getCurrentStep() > 0) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Rebuild World (Phase 1)");
            confirm.setHeaderText("Rebuilding the world will reset the current simulation!");
            confirm.setContentText("All recorded simulation events, curated scenes, and generated prose will be cleared. Do you want to continue?");
            var res = confirm.showAndWait();
            if (res.isEmpty() || res.get() != ButtonType.OK) {
                return;
            }
            state.reset();
            chapters.clear();
            if (eventLogArea != null) {
                eventLogArea.clear();
            }
        }

        commitSpinnerValue(numAgentsSpinner, 3, 15);
        commitSpinnerValue(numPlacesSpinner, 3, 15);
        commitSpinnerValue(numItemsSpinner, 1, 10);

        int numAgents = numAgentsSpinner.getValue() != null ? numAgentsSpinner.getValue() : defaultAgents;
        int numPlaces = numPlacesSpinner.getValue() != null ? numPlacesSpinner.getValue() : defaultPlaces;
        int numItems = numItemsSpinner.getValue() != null ? numItemsSpinner.getValue() : defaultItems;

        int target = targetStepsCombo.getValue() != null ? targetStepsCombo.getValue() : 100;
        int stepMinutes = stepMinutesCombo.getValue() != null ? stepMinutesCombo.getValue() : 15;
        int chosenHours = (target * stepMinutes) / 60;
        state.setTotalHours(chosenHours);
        state.setStepMinutes(stepMinutes);
        state.setTargetSteps(target);

        String title = storyTitleField.getText().trim();
        String desc = storyDescArea.getText().trim();
        state.setTitle(title);
        state.setPremise(desc);
        projectService.createNewProject(title);
        updateActiveProjectDisplay();
        projectService.autoSave(state, chapters, getEventLogText());
        String voice = authorVoiceCombo.getValue();
        String sLen = sceneLengthCombo.getValue();

        buildWorldBtn.setDisable(true);
        startBtn.setDisable(true);
        if (resumeBtn != null) resumeBtn.setDisable(true);
        stopBtn.setDisable(false);
        if (generateScenesBtn != null) generateScenesBtn.setDisable(true);
        if (generateProseBtn != null) generateProseBtn.setDisable(true);
        if (exportStoryBtn != null) exportStoryBtn.setDisable(true);

        worldStatusBadge.setText("Generating world via LLM...");
        worldStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #e67e22;");
        int totalItems = numPlaces * numItems;
        worldDetailsLabel.setText("Generating " + numAgents + " characters, " + numPlaces + " places, and " + totalItems + " items (" + numItems + " per place)...");

        simProgressBar.setProgress(0.0);
        simProgressLabel.setText("Step 0 of " + target + " (0.0%) - World Generation");
        simDetailsLabel.setText(String.format("Scenes generated: 0 | Target steps: %d (%s total duration)", target, state.getClockTime(target)));

        setGenerationRunning(true, String.format("Phase 1: Generating world [%s]... Please wait.", ollamaLlmService.getModel(LlmRole.WORLD)));

        cancelRequested = false;
        Task<Boolean> initTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                updateMessage("Initializing world via Spring AI...");
                proseService.setLanguage("en");
                proseService.setContext(title, desc, voice, sLen);
                initializer.initializeState(state, "en", numAgents, numPlaces, numItems, title, desc, voice);
                return state.isWorldGenerated();
            }
        };

        initTask.setOnSucceeded(evt -> {
            boolean generated = Boolean.TRUE.equals(initTask.getValue());
            if (cancelRequested || !generated) {
                worldStatusBadge.setText("World not generated! Problem with LLM");
                worldStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #c0392b;");
                setGenerationRunning(false, "Error during initialization: World not generated.");
                buildWorldBtn.setDisable(false);
                startBtn.setDisable(!state.isWorldGenerated());
                stopBtn.setDisable(true);
                return;
            }

            worldStatusBadge.setText("World Generated Successfully!");
            worldStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #27ae60;");
            worldDetailsLabel.setText(String.format("%d characters, %d places, %d items (%d per place), %d global events ready.", 
                state.getAgents().size(), state.getLocations().size(), state.getItems().size(), numItems, state.getGlobalEvents().size()));

            buildWorldBtn.setText("🔄 1. Rebuild World");
            buildWorldBtn.setDisable(false);

            startBtn.setVisible(true);
            startBtn.setManaged(true);
            startBtn.setDisable(false);
            startBtn.setText("▶ 2. Start Simulation");

            if (resumeBtn != null) {
                resumeBtn.setVisible(false);
                resumeBtn.setManaged(false);
            }
            stopBtn.setDisable(true);

            timelineSlider.setMax(0);
            timelineSlider.setValue(0);
            displaySnapshot(0);
            updateTimelineMarkers();
            updateHighlights();
            updateGlobalEventsTab();
            updateDashboardProgress();

            setGenerationRunning(false, String.format("Phase 1 Complete! World built with %d characters, %d places, %d items, %d events. Ready for '▶ 2. Start Simulation'.",
                    state.getAgents().size(), state.getLocations().size(), state.getItems().size(), state.getGlobalEvents().size()));

            String logText = getEventLogText();
            projectService.autoSave(state, chapters, logText);
            updateActiveProjectDisplay();
        });

        initTask.setOnFailed(evt -> {
            if (cancelRequested) {
                log.info("Initialization cancelled.");
                return;
            }
            Throwable ex = initTask.getException();
            String errorMsg = ex != null ? ex.getMessage() : "Unknown error";
            appendLog("[FEL] Världsgenerering kraschade: " + errorMsg);
            worldStatusBadge.setText("World Generation Failed: " + errorMsg);
            worldStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #c0392b;");
            setGenerationRunning(false, "Error during initialization: " + errorMsg);
            buildWorldBtn.setDisable(false);
            startBtn.setDisable(!state.isWorldGenerated());
            stopBtn.setDisable(true);
            log.error("Simulation initialization task failed", ex);
        });

        initTask.setOnCancelled(evt -> {
            log.info("Initialization task cancelled.");
            buildWorldBtn.setDisable(false);
            startBtn.setDisable(!state.isWorldGenerated());
            stopBtn.setDisable(true);
        });

        currentInitTask = initTask;
        currentInitThread = new Thread(initTask, "SpringAI-InitTask-Thread");
        currentInitThread.setDaemon(true);
        currentInitThread.start();
    }

    private void startSimulation() {
        if (!state.isWorldGenerated()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("World Not Generated");
            alert.setHeaderText("Build World First (Phase 1)");
            alert.setContentText("You must build the world first by clicking '🌍 1. Build World' before starting the simulation.");
            alert.showAndWait();
            return;
        }

        if (simulationService.isRunning() || proseService.isRunning()) {
            setGenerationRunning(false, "Simulation or prose generation is already running.");
            return;
        }

        int target = targetStepsCombo.getValue() != null ? targetStepsCombo.getValue() : 100;
        int stepMinutes = stepMinutesCombo.getValue() != null ? stepMinutesCombo.getValue() : 15;
        int chosenHours = (target * stepMinutes) / 60;
        state.setTotalHours(chosenHours);
        state.setStepMinutes(stepMinutes);
        state.setTargetSteps(target);

        buildWorldBtn.setDisable(true);
        startBtn.setDisable(true);
        stopBtn.setDisable(false);
        if (generateScenesBtn != null) {
            generateScenesBtn.setDisable(true);
        }
        if (generateProseBtn != null) {
            generateProseBtn.setDisable(true);
        }
        if (exportStoryBtn != null) {
            exportStoryBtn.setDisable(true);
        }

        simProgressBar.setProgress((double) state.getCurrentStep() / target);
        simProgressLabel.setText(String.format("Step %d of %d (%.1f%%) - Simulating", state.getCurrentStep(), target, ((double) state.getCurrentStep() / target) * 100));

        setGenerationRunning(true, String.format("Phase 2: Simulating agents [%s] (Step %d/%d)...", ollamaLlmService.getModel(LlmRole.AGENT), state.getCurrentStep(), target));

        simulationService.startSimulation("en", target, false, false);
    }

    private void resumeSimulation() {
        int target = targetStepsCombo.getValue() != null ? targetStepsCombo.getValue() : 100;
        int stepMinutes = stepMinutesCombo.getValue() != null ? stepMinutesCombo.getValue() : 15;
        state.setStepMinutes(stepMinutes);
        state.setTotalHours((target * stepMinutes) / 60);
        
        if (target > state.getTargetSteps()) {
            state.setTargetSteps(target);
        }
        
        if (buildWorldBtn != null) {
            buildWorldBtn.setDisable(true);
        }
        resumeBtn.setDisable(true);
        stopBtn.setDisable(false);
        if (generateScenesBtn != null) {
            generateScenesBtn.setDisable(true);
        }
        if (generateProseBtn != null) {
            generateProseBtn.setDisable(true);
            generateProseBtn.setStyle("-fx-background-color: #9333ea; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 16;");
        }
        setGenerationRunning(true, String.format("Phase 2: Resuming simulation [%s] (Step %d/%d)...",
                ollamaLlmService.getModel(LlmRole.AGENT), state.getCurrentStep(), state.getTargetSteps()));
        worldStatusBadge.setText("Resumed");
        
        simulationService.startSimulation("en", state.getTargetSteps(), false, true);
    }

    private void stopSimulation() {
        stopBtn.setDisable(true);
        cancelRequested = true;

        Window owner = mainView != null && mainView.getScene() != null ? mainView.getScene().getWindow() : null;
        StopProgressDialog dialog = new StopProgressDialog(owner);
        dialog.show();

        Thread stopperThread = new Thread(() -> {
            try {
                dialog.appendLog("Stop requested by user.");
                
                // Step 0: Stop World Generation / InitThread
                dialog.setStepStatus(0, "Interrupting world generator...", false);
                if (currentInitTask != null && currentInitTask.isRunning()) {
                    currentInitTask.cancel(true);
                }
                if (currentInitThread != null && currentInitThread.isAlive()) {
                    dialog.appendLog("Interrupting initialization thread...");
                    currentInitThread.interrupt();
                    try {
                        currentInitThread.join(1500);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    dialog.appendLog("Initialization thread stopped.");
                } else {
                    dialog.appendLog("World generator is not active.");
                }
                dialog.setStepStatus(0, "Halted", true);

                // Step 1: Stop Game Master Simulation Loop
                dialog.setStepStatus(1, "Signaling agent simulation loop...", false);
                dialog.appendLog("Calling SimulationService.stopSimulation()...");
                simulationService.stopSimulation();
                boolean gmDone = simulationService.awaitTermination(2500);
                dialog.appendLog("Simulation worker stopped: " + (gmDone ? "terminated" : "timed out"));
                dialog.setStepStatus(1, gmDone ? "Halted" : "Completed", true);

                // Step 2: Stop Prose Engine & Queue
                dialog.setStepStatus(2, "Stopping Prose Engine...", false);
                dialog.appendLog("Calling ProseService.stop() and clearing pending scene events...");
                proseService.stop();
                boolean proseDone = proseService.awaitTermination(2500);
                dialog.appendLog("Prose Engine worker stopped: " + (proseDone ? "terminated" : "timed out"));
                dialog.setStepStatus(2, proseDone ? "Halted" : "Completed", true);

                // Step 3: Novel State & Project Persistence
                dialog.setStepStatus(3, "Autosaving project snapshot...", false);
                dialog.appendLog("Persisting simulation snapshots and chapters...");
                String logText = getEventLogText();
                projectService.autoSave(state, chapters, logText);
                dialog.appendLog("Project autosaved successfully.");
                dialog.setStepStatus(3, "Saved", true);

                // Step 4: Finalize
                dialog.setStepStatus(4, "Resetting dashboard and controls...", false);
                dialog.appendLog("All subsystems safely shut down.");
                dialog.setStepStatus(4, "Ready", true);

                Platform.runLater(() -> {
                    if (buildWorldBtn != null) {
                        buildWorldBtn.setDisable(false);
                    }
                    if (state.getCurrentStep() > 0) {
                        startBtn.setVisible(false);
                        startBtn.setManaged(false);
                        resumeBtn.setVisible(true);
                        resumeBtn.setManaged(true);
                        resumeBtn.setDisable(false);
                    } else {
                        startBtn.setVisible(true);
                        startBtn.setManaged(true);
                        startBtn.setDisable(!state.isWorldGenerated());
                        startBtn.setText("▶ 2. Start Simulation");
                        resumeBtn.setVisible(false);
                        resumeBtn.setManaged(false);
                    }
                    stopBtn.setDisable(true);
                    if (generateScenesBtn != null) {
                        generateScenesBtn.setDisable(state.getCurrentStep() == 0);
                        generateScenesBtn.setText(chapters.isEmpty() ? "🎬 3. Generate Scenes" : "🔄 3. Regenerate Scenes");
                    }
                    if (generateProseBtn != null) {
                        generateProseBtn.setDisable(chapters.isEmpty());
                    }
                    setGenerationRunning(false, "Simulation Stopped.");
                    worldStatusBadge.setText("Stopped");
                    worldStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
                    updateDashboardProgress();
                    dialog.markFinished();
                });

            } catch (Exception ex) {
                dialog.appendLog("Error during shutdown: " + ex.getMessage());
                Platform.runLater(() -> {
                    if (buildWorldBtn != null) {
                        buildWorldBtn.setDisable(false);
                    }
                    if (state.getCurrentStep() > 0) {
                        startBtn.setVisible(false);
                        startBtn.setManaged(false);
                        resumeBtn.setVisible(true);
                        resumeBtn.setManaged(true);
                        resumeBtn.setDisable(false);
                    } else {
                        startBtn.setVisible(true);
                        startBtn.setManaged(true);
                        startBtn.setDisable(!state.isWorldGenerated());
                        startBtn.setText("▶ 2. Start Simulation");
                        resumeBtn.setVisible(false);
                        resumeBtn.setManaged(false);
                    }
                    stopBtn.setDisable(true);
                    if (generateScenesBtn != null) {
                        generateScenesBtn.setDisable(state.getCurrentStep() == 0);
                        generateScenesBtn.setText(chapters.isEmpty() ? "🎬 3. Generate Scenes" : "🔄 3. Regenerate Scenes");
                    }
                    if (generateProseBtn != null) {
                        generateProseBtn.setDisable(chapters.isEmpty());
                    }
                    dialog.markFinished();
                });
            }
        }, "Simulation-Stopper-Thread");
        stopperThread.setDaemon(true);
        stopperThread.start();
    }

    private String getEventLogText() {
        if (state != null && state.getEventHistory() != null && !state.getEventHistory().isEmpty()) {
            return String.join("\n", state.getEventHistory());
        }
        if (eventLogArea != null) {
            if (Platform.isFxApplicationThread()) {
                try {
                    return eventLogArea.getText();
                } catch (Exception e) {
                    return "";
                }
            }
        }
        return "";
    }

    private void saveEventLog() {
        com.murderhecoded.service.FileExportService exportService = new com.murderhecoded.service.FileExportService();
        exportService.saveEventLog(mainView.getScene().getWindow(), getEventLogText());
    }

    private void appendLog(String message) {
        if (message == null || eventLogArea == null) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            safeAppendLogText(message);
        } else {
            Platform.runLater(() -> safeAppendLogText(message));
        }
    }

    private void safeAppendLogText(String message) {
        if (eventLogArea == null) {
            return;
        }
        try {
            eventLogArea.appendText(message + "\n");
        } catch (IndexOutOfBoundsException e) {
            log.warn("TextArea paragraph index out of bounds, recovering safely: {}", e.getMessage());
            try {
                String existing = (state != null && state.getEventHistory() != null && !state.getEventHistory().isEmpty())
                        ? String.join("\n", state.getEventHistory()) + "\n"
                        : eventLogArea.getText();
                eventLogArea.setText(existing + message + "\n");
                eventLogArea.positionCaret(eventLogArea.getLength());
            } catch (Exception ex) {
                log.debug("Failed TextArea fallback recovery: {}", ex.getMessage());
            }
        } catch (Exception ex) {
            log.warn("Error appending log to UI: {}", ex.getMessage());
        }
    }

    private void appendStory(Chapter chapter) {
        if (chapter == null) return;
        boolean replaced = false;
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).sceneNumber() == chapter.sceneNumber()) {
                chapters.set(i, chapter);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            chapters.add(chapter);
        }
        if (proseService != null && proseService.isRunning()) {
            setGenerationRunning(true, String.format("Phase 4: Generating prose chapters [%s]... (Scene %d/%d completed, %,d words)",
                    ollamaLlmService.getModel(LlmRole.PROSE), chapter.sceneNumber(), chapters.size(), calculateTotalWordCount()));
        }
        renderStory();
        Platform.runLater(() -> {
            updateDashboardProgress();
            updateScenesTab();
        });
    }

    private void updateDashboardProgress() {
        int target = state.getTargetSteps() > 0 ? state.getTargetSteps() : 144;
        int current = state.getCurrentStep();
        double simProgress = Math.min(1.0, (double) current / target);
        
        if (simProgressBar != null) {
            simProgressBar.setProgress(simProgress);
        }
        
        int remaining = Math.max(0, target - current);
        int totalScenes = simulationService.getTotalScenesGenerated();
        
        if (simProgressLabel != null) {
            simProgressLabel.setText(String.format("Step %d of %d (%.1f%%) - %s", 
                current, target, simProgress * 100, state.getClockTime(current)));
        }
        if (simDetailsLabel != null) {
            int stepMins = state.getStepMinutes() > 0 ? state.getStepMinutes() : 15;
            int hoursLeft = (remaining * stepMins) / 60;
            simDetailsLabel.setText(String.format("Scenes generated: %d | Remaining steps: %d (%d hours left)", 
                totalScenes, remaining, hoursLeft));
        }
        
        // Story Scenes (Dramaturgic Curation)
        int scenesCount = chapters.size();
        long inFinalCut = chapters.stream().filter(Chapter::isSelected).count();
        long withProse = chapters.stream().filter(Chapter::hasProse).count();
        if (scenesStatusBadge != null) {
            if (scenesCount == 0) {
                scenesStatusBadge.setText("No Scenes Curated");
                scenesStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
            } else {
                scenesStatusBadge.setText(String.format("%d Scenes Curated (%d in Final Cut)", scenesCount, inFinalCut));
                scenesStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #d97706;");
            }
        }
        if (scenesDetailsLabel != null) {
            if (scenesCount == 0) {
                scenesDetailsLabel.setText("Click '🎬 Generate Scenes' once simulation steps have been recorded.");
            } else {
                scenesDetailsLabel.setText(String.format("Final Cut: %d scenes | Truncated/Cut: %d | Prose Written: %d of %d", 
                    inFinalCut, scenesCount - inFinalCut, withProse, scenesCount));
            }
        }

        // Prose Engine metrics
        int pending = proseService.getPendingQueueSize();
        double proseProgress = scenesCount > 0 ? (double) withProse / scenesCount : 0.0;
        if (proseProgressBar != null) {
            proseProgressBar.setProgress(proseProgress);
        }
        if (proseProgressLabel != null) {
            proseProgressLabel.setText(String.format("Chapters with Prose: %d / %d (%.1f%%)", 
                withProse, scenesCount, proseProgress * 100));
        }
        if (proseDetailsLabel != null) {
            int totalWords = calculateTotalWordCount();
            if (totalWords > 0) {
                proseDetailsLabel.setText(String.format("Chapters written: %d (%,d words) | Pending in queue: %d scenes", 
                    withProse, totalWords, pending));
            } else {
                proseDetailsLabel.setText(String.format("Chapters written: %d | Pending in queue: %d scenes", 
                    withProse, pending));
            }
        }

        // Panel 1: World Generation Status
        if (worldStatusBadge != null && worldDetailsLabel != null) {
            boolean worldReady = state.isWorldGenerated() || (!state.getAgents().isEmpty() && !state.getLocations().isEmpty());
            if (worldReady) {
                if (simulationService.isRunning()) {
                    worldStatusBadge.setText("Simulation Running");
                    worldStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #e67e22;");
                } else if (state.isCompleted() || current >= target) {
                    worldStatusBadge.setText("World Generated & Completed");
                    worldStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #27ae60;");
                } else if (current > 0) {
                    worldStatusBadge.setText("World Generated & Paused (Step " + current + ")");
                    worldStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #27ae60;");
                } else {
                    worldStatusBadge.setText("World Generated Successfully!");
                    worldStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #27ae60;");
                }
                worldDetailsLabel.setText(String.format("%d characters, %d places, %d items, %d global events ready.", 
                    state.getAgents().size(), state.getLocations().size(), state.getItems().size(), state.getGlobalEvents().size()));
            } else {
                worldStatusBadge.setText("Not Started");
                worldStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
                worldDetailsLabel.setText("No characters, places, or items generated yet.");
            }
        }
    }

    private void updateUiFromState() {
        int current = state.getCurrentStep();
        if (!isLoadingProject) {
            projectService.autoSave(state, chapters, getEventLogText());
        }
        timelineSlider.setMax(current);
        
        int scrubbedStep = (int) timelineSlider.getValue();
        if (scrubbedStep == current - 1 || scrubbedStep >= current) {
            timelineSlider.setValue(current);
            scrubbedStep = current;
        }
        displaySnapshot(scrubbedStep);
        updateTimelineMarkers();
        updateHighlights();
        
        if (timeLabel != null) {
            timeLabel.setText("Time: " + state.getClockTime(scrubbedStep));
        }
        
        updateDashboardProgress();
        updateDramaturgyChart();
        updateTimelineTree();
        updateScenesTab();
        
        if (!simulationService.isRunning() || state.isCompleted() || current >= state.getTargetSteps()) {
            if (buildWorldBtn != null) {
                buildWorldBtn.setDisable(false);
            }
            if (resumeBtn.isVisible()) {
                resumeBtn.setDisable(false);
            } else {
                startBtn.setDisable(!state.isWorldGenerated());
            }
            stopBtn.setDisable(true);
            if (generateScenesBtn != null) {
                generateScenesBtn.setDisable(current == 0 || proseService.isRunning());
                generateScenesBtn.setText(chapters.isEmpty() ? "🎬 3. Generate Scenes" : "🔄 3. Regenerate Scenes");
            }
            if (generateProseBtn != null) {
                generateProseBtn.setDisable(chapters.isEmpty() || proseService.isRunning());
                boolean hasProse = chapters.stream().anyMatch(Chapter::hasProse);
                generateProseBtn.setText(hasProse ? "🔄 4. Regenerate Prose" : "✍️ 4. Generate Prose");
            }
            if (state.isCompleted() || current >= state.getTargetSteps()) {
                if (chapters.isEmpty()) {
                    setGenerationRunning(false, "Phase 2 Complete! Raw events recorded. Click '🎬 3. Generate Scenes' to frame novel scenes.");
                } else {
                    boolean hasProse = chapters.stream().anyMatch(Chapter::hasProse);
                    setGenerationRunning(false, String.format("Simulation Complete! %d scenes ready. Click '%s' to write novel prose.",
                            chapters.size(), hasProse ? "🔄 4. Regenerate Prose" : "✍️ 4. Generate Prose"));
                }
            }
        } else if (simulationService.isRunning()) {
            setGenerationRunning(true, String.format("Phase 2: Simulating Agents [%s] (Step %d/%d)...",
                    ollamaLlmService.getModel(LlmRole.AGENT), current, state.getTargetSteps()));
        }
    }

    private void generateScenesForCurrentSimulation() {
        if (simulationService.isRunning() || proseService.isRunning()) {
            statusLabel.setText("Please wait for the current process to finish.");
            return;
        }

        if (state.getCurrentStep() == 0) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Simulation Found");
            alert.setHeaderText("Run Simulation First");
            alert.setContentText("You need to generate or run a simulation to record events before curating scenes.");
            alert.showAndWait();
            return;
        }

        List<TimeLocationEvent> allEvents = state.ensureOrSynthesizeTimelineEvents();
        if (allEvents.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Events");
            alert.setHeaderText("No Events Recorded");
            alert.setContentText("No timeline events found to curate scenes from.");
            alert.showAndWait();
            return;
        }

        boolean hasExistingProse = chapters.stream().anyMatch(Chapter::hasProse);
        if (hasExistingProse) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Regenerate Scenes");
            confirm.setHeaderText("Regenerate Scenes from Events?");
            confirm.setContentText("Regenerating scenes will re-analyze all events and rebuild the scene structure. Previously written prose will be reset for the new scenes. Do you want to continue?");
            var result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }

        boolean useDirector = (sceneDirectorToggle == null || sceneDirectorToggle.isSelected());
        String voice = authorVoiceCombo != null ? authorVoiceCombo.getValue() : "Default";
        String title = storyTitleField != null ? storyTitleField.getText().trim() : state.getTitle();
        String premise = storyDescArea != null ? storyDescArea.getText().trim() : state.getPremise();

        setGenerationRunning(true, String.format("Phase 3: Curating scenes from %d events%s...",
                allEvents.size(), useDirector ? " via LLM Scene Director" : " via EditorService"));

        if (!useDirector) {
            List<Scene> scenes = editorService.createScenes(allEvents, state);
            applyScenesToChapters(scenes);
            setGenerationRunning(false, String.format("Phase 3 Complete: Curated %d scenes.", scenes.size()));
            return;
        }

        Task<List<Scene>> directorTask = new Task<>() {
            @Override
            protected List<Scene> call() {
                return editorService.createScenes(
                        allEvents,
                        state,
                        true,
                        voice,
                        title,
                        premise,
                        MainController.this::appendLog,
                        p -> updateProgress(p, 1.0)
                );
            }
        };

        if (simProgressBar != null) {
            simProgressBar.progressProperty().bind(directorTask.progressProperty());
        }

        directorTask.setOnSucceeded(e -> {
            if (simProgressBar != null) {
                simProgressBar.progressProperty().unbind();
                simProgressBar.setProgress(1.0);
            }
            List<Scene> scenes = directorTask.getValue();
            applyScenesToChapters(scenes);
            setGenerationRunning(false, String.format("Phase 3 Complete! Curated %d scenes with LLM Director guidance.", scenes != null ? scenes.size() : 0));
        });

        directorTask.setOnFailed(e -> {
            if (simProgressBar != null) {
                simProgressBar.progressProperty().unbind();
                simProgressBar.setProgress(0.0);
            }
            Throwable ex = directorTask.getException();
            appendLog("[FEL] LLM Scene Director misslyckades: " + (ex != null ? ex.getMessage() : "Okänt fel"));
            List<Scene> scenes = editorService.createScenes(allEvents, state);
            applyScenesToChapters(scenes);
            setGenerationRunning(false, "Fas 3 klar via algoritmisk fallback.");
        });

        Thread t = new Thread(directorTask, "llm-scene-director-task");
        t.setDaemon(true);
        t.start();
    }

    private void applyScenesToChapters(List<Scene> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            return;
        }
        chapters.clear();
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
            chapters.add(ch);
        }

        updateScenesTab();
        renderStory();
        updateDashboardProgress();

        if (generateScenesBtn != null) {
            generateScenesBtn.setText("🔄 3. Regenerate Scenes");
        }
        if (generateProseBtn != null) {
            generateProseBtn.setDisable(false);
            generateProseBtn.setText("✍️ 4. Generate Prose");
            generateProseBtn.setStyle("-fx-background-color: #9333ea; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 16;");
        }

        String logText = getEventLogText();
        projectService.autoSave(state, chapters, logText);
        updateActiveProjectDisplay();
    }

    private void generateProseForCurrentSimulation() {
        if (simulationService.isRunning() || proseService.isRunning()) {
            statusLabel.setText("Please wait for the current process to finish.");
            return;
        }

        if (state.getCurrentStep() == 0) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Simulation Found");
            alert.setHeaderText("Run Simulation First");
            alert.setContentText("You need to generate or load a simulation before generating prose.");
            alert.showAndWait();
            return;
        }

        // If scenes haven't been curated yet, curate them first!
        if (chapters.isEmpty()) {
            generateScenesForCurrentSimulation();
            if (chapters.isEmpty()) {
                return;
            }
        }

        boolean hasExistingProse = chapters.stream().anyMatch(Chapter::hasProse);
        if (hasExistingProse) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Regenerate Prose");
            confirm.setHeaderText("Regenerate Prose for All Scenes?");
            confirm.setContentText("This will rewrite prose for all " + chapters.size() + " scenes using the LLM while keeping current scene boundaries intact. Do you want to continue?");
            var result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }

        if (resumeBtn != null && resumeBtn.isVisible()) {
            resumeBtn.setDisable(true);
        } else {
            startBtn.setDisable(true);
        }
        stopBtn.setDisable(false);
        if (generateScenesBtn != null) {
            generateScenesBtn.setDisable(true);
        }
        if (generateProseBtn != null) {
            generateProseBtn.setDisable(true);
        }
        if (exportStoryBtn != null) {
            exportStoryBtn.setDisable(true);
        }

        setGenerationRunning(true, String.format("Phase 4: Generating prose chapters [%s] via LLM... (0 / %d scenes)",
                ollamaLlmService.getModel(LlmRole.PROSE), chapters.size()));

        List<Scene> scenesToGenerate = proseService.chaptersToScenes(chapters);

        String title = storyTitleField != null ? storyTitleField.getText().trim() : state.getTitle();
        String desc = storyDescArea != null ? storyDescArea.getText().trim() : state.getPremise();
        String voice = authorVoiceCombo != null ? authorVoiceCombo.getValue() : "Default";
        String sLen = sceneLengthCombo != null ? sceneLengthCombo.getValue() : "Short";

        proseService.setLanguage("en");
        proseService.setContext(title, desc, voice, sLen);

        if (proseProgressBar != null) {
            proseProgressBar.setProgress(0.0);
        }
        if (proseProgressLabel != null) {
            proseProgressLabel.setText("Generating Prose...");
        }
        if (proseDetailsLabel != null) {
            proseDetailsLabel.setText("Writing " + scenesToGenerate.size() + " chapters...");
        }

        proseService.generateProseForScenes(scenesToGenerate, () -> {
            Platform.runLater(() -> {
                if (resumeBtn != null && resumeBtn.isVisible()) {
                    resumeBtn.setDisable(false);
                } else {
                    startBtn.setDisable(false);
                }
                stopBtn.setDisable(true);
                if (generateScenesBtn != null) {
                    generateScenesBtn.setDisable(false);
                }
                if (generateProseBtn != null) {
                    generateProseBtn.setDisable(false);
                    generateProseBtn.setText("🔄 4. Regenerate Prose");
                    generateProseBtn.setStyle("-fx-background-color: #9333ea; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 16;");
                }
                int totalWords = calculateTotalWordCount();
                setGenerationRunning(false, String.format("Phase 4 Complete! %d chapters written (%,d words) [%s].",
                        chapters.size(), totalWords, ollamaLlmService.getModel(LlmRole.PROSE)));
                updateDashboardProgress();
                renderStory();
                updateScenesTab();
                updateExportButtonState();
            });

            String logText = getEventLogText();
            projectService.autoSave(state, chapters, logText);
        });
    }

    private void regenerateProseForSingleChapter(Chapter targetChapter) {
        if (simulationService.isRunning() || proseService.isRunning()) {
            statusLabel.setText("Please wait for the current process to finish.");
            return;
        }
        if (targetChapter == null) return;

        boolean isResolution = targetChapter.dramaticFocus() != null
                && (targetChapter.dramaticFocus().contains("The Grand Reveal") || targetChapter.dramaticFocus().contains("Resolution") || "Finale & Denouement".equalsIgnoreCase(targetChapter.timeSpan()));

        String title = storyTitleField != null ? storyTitleField.getText().trim() : state.getTitle();
        String desc = storyDescArea != null ? storyDescArea.getText().trim() : state.getPremise();
        String voice = authorVoiceCombo != null ? authorVoiceCombo.getValue() : "Default";
        String sLen = sceneLengthCombo != null ? sceneLengthCombo.getValue() : "Short";

        proseService.setLanguage("en");
        proseService.setContext(title, desc, voice, sLen);

        if (isResolution) {
            setGenerationRunning(true, String.format("Regenerating Grand Reveal & Resolution Chapter [%s] via LLM...", ollamaLlmService.getModel(LlmRole.PROSE)));
            appendLog("🎬 [Grand Reveal] Startar regenerering av upplösningskapitlet via LLM...");

            Thread.ofVirtual().start(() -> {
                try {
                    Chapter newChapter = proseService.generateResolutionChapter(targetChapter.sceneNumber(), targetChapter.endStep());
                    Platform.runLater(() -> {
                        appendStory(newChapter);
                        int totalWords = calculateTotalWordCount();
                        setGenerationRunning(false, String.format("Grand Reveal Chapter regenerated! (%,d words total)", totalWords));
                        String logText = getEventLogText();
                        projectService.autoSave(state, chapters, logText);
                    });
                } catch (Exception ex) {
                    log.error("Failed to regenerate resolution chapter", ex);
                    Platform.runLater(() -> {
                        appendLog("[FEL] Kunde inte regenerera upplösningskapitlet: " + ex.getMessage());
                        setGenerationRunning(false, "Fel vid regenerering av upplösningskapitlet.");
                    });
                }
            });
            return;
        }

        setGenerationRunning(true, String.format("Regenerating prose for Scene %d via LLM...", targetChapter.sceneNumber()));

        Scene scene = proseService.chapterToScene(targetChapter);

        Thread.ofVirtual().start(() -> {
            try {
                Chapter newChapter = proseService.generateProseForScene(scene);
                Platform.runLater(() -> {
                    appendStory(newChapter);
                    int totalWords = calculateTotalWordCount();
                    setGenerationRunning(false, String.format("Scene %d prose regenerated! (%,d words total)", targetChapter.sceneNumber(), totalWords));
                    String logText = getEventLogText();
                    projectService.autoSave(state, chapters, logText);
                });
            } catch (Exception ex) {
                log.error("Failed to regenerate prose for scene " + targetChapter.sceneNumber(), ex);
                Platform.runLater(() -> statusLabel.setText("Failed to regenerate prose for scene " + targetChapter.sceneNumber()));
            }
        });
    }

    private void updateDramaturgySelectedStep(int step) {
        if (dramaturgyChart == null) return;
        DramaturgyChartPresenter.updateSelectedStep(dramaturgyChart, dramaturgyHeaderLabel, dramaturgyStatsBox, state, dramaturgyDirectorService, step, s -> {
            if (timelineSlider != null) {
                timelineSlider.setValue(s);
            }
        });
    }

    private void updateDramaturgyChart() {
        if (dramaturgyChart == null) return;
        int scrubbed = timelineSlider != null ? (int) timelineSlider.getValue() : state.getCurrentStep();
        DramaturgyChartPresenter.updateChart(dramaturgyChart, dramaturgyHeaderLabel, dramaturgyStatsBox, state, dramaturgyDirectorService, scrubbed, step -> {
            if (timelineSlider != null) {
                timelineSlider.setValue(step);
            }
        });
    }

    private void updateTimelineTree() {
        if (timelineTreeView == null) return;
        TimelineTreePresenter.updateTreeView(timelineTreeView, state, "");
    }

    private void updateScenesTab() {
        if (scenesTabPresenter == null) return;
        scenesTabPresenter.updateScenes(chapters, state);
    }

    private void displaySnapshot(int timeStep) {
        SimulationStateSnapshot snap = state.getSnapshotAt(timeStep);
        if (snap == null) {
            return;
        }
        
        updateGlobalEventsTab();
        updateItemsTab();

        // Only rebuild heavy tree views and character cards if on the simulation tab
        boolean onSimTab = tabPane != null && tabPane.getSelectionModel().getSelectedItem() == simulationTab;
        if (!onSimTab) {
            return;
        }

        updatePersonTreeView();
        renderPersonDetail();
        updateItemTreeView();
        renderItemDetail();
        updateLocationTreeView();
        renderLocationDetail();
        
        // Render Agents in Character tab
        if (agentCardPane != null && agentCardPane.getCenter() instanceof ScrollPane) {
            ScrollPane scroll = (ScrollPane) agentCardPane.getCenter();
            FlowPane flow = (FlowPane) scroll.getContent();
            flow.getChildren().clear();
            
            for (AgentSnapshot a : snap.agents().values()) {
                VBox card = new VBox(5);
                card.setPadding(new Insets(10));
                
                String bgColor = a.isAlive() ? "#fcfdfd" : "#ffebee";
                String borderColor = a.isAlive() ? "#cbd5e1" : "#e53935";
                if (!a.hasArrived()) {
                    bgColor = "#f0f4f8";
                    borderColor = "#90caf9";
                } else if (a.isMurderer()) {
                    borderColor = "#e53935";
                } else if (a.isDetective()) {
                    borderColor = "#1e88e5";
                }
                card.setStyle("-fx-border-color: " + borderColor + "; -fx-border-width: 2; -fx-background-color: " + bgColor + "; -fx-pref-width: 260; -fx-background-radius: 6; -fx-border-radius: 6;");
                
                StringBuilder nameBuilder = new StringBuilder(a.name());
                if (a.isMurderer()) {
                    nameBuilder.append(" (Murderer)");
                } else if (a.isDetective()) {
                    nameBuilder.append(" (Detective)");
                }
                
                if (!a.isAlive()) {
                    nameBuilder.append(" (DEAD)");
                } else if (!a.hasArrived()) {
                    nameBuilder.append(" (En Route)");
                }
                
                Label nameLabel = new Label(nameBuilder.toString());
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14; -fx-text-fill: #1e293b;");
                nameLabel.setWrapText(true);
                card.getChildren().add(nameLabel);
                
                boolean isVictim = snap.agents().values().stream().anyMatch(other -> other.isMurderer() && a.id().equals(other.targetVictimId()));
                if (isVictim) {
                    Label victimLabel = new Label("TARGET VICTIM");
                    victimLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #b71c1c; -fx-font-size: 11;");
                    card.getChildren().add(victimLabel);
                }
                
                Label roleLabel = new Label("Role: " + (a.role() != null ? a.role() : "Guest"));
                roleLabel.setWrapText(true);
                card.getChildren().add(roleLabel);
                
                if (a.personality() != null) {
                    Label persLabel = new Label("Personality: " + a.personality());
                    persLabel.setWrapText(true);
                    card.getChildren().add(persLabel);
                }
                if (a.relationship() != null) {
                    Label relLabel = new Label("Relationship: " + a.relationship());
                    relLabel.setWrapText(true);
                    card.getChildren().add(relLabel);
                }
                
                Label goalLabel = new Label("Goal: " + a.secretGoal());
                goalLabel.setWrapText(true);
                card.getChildren().add(goalLabel);
                
                if (a.appearance() != null) {
                    Label appLabel = new Label("Appearance: " + a.appearance());
                    appLabel.setWrapText(true);
                    card.getChildren().add(appLabel);
                }
                
                if (!a.hasArrived()) {
                    Label locLabel = new Label("Status: En route (arrives upon body discovery)");
                    locLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #475569;");
                    locLabel.setWrapText(true);
                    card.getChildren().add(locLabel);
                } else {
                    String locName = state.resolveLocationName(a.currentLocationId(), timeStep);
                    Label locLabel = new Label("Location: " + locName);
                    locLabel.setWrapText(true);
                    card.getChildren().add(locLabel);
                }
                
                if (!a.knowledgeBase().isEmpty()) {
                    Label kHeader = new Label("Knowledge:");
                    kHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");
                    card.getChildren().add(kHeader);
                    for (KnowledgeSnapshot k : a.knowledgeBase()) {
                        Label kl = new Label("- " + k.information());
                        kl.setWrapText(true);
                        card.getChildren().add(kl);
                    }
                }
                
                flow.getChildren().add(card);
            }
        }
        
        // Render Places in World tab
        if (worldCardPane != null && worldCardPane.getCenter() instanceof ScrollPane) {
            ScrollPane scroll = (ScrollPane) worldCardPane.getCenter();
            FlowPane flow = (FlowPane) scroll.getContent();
            flow.getChildren().clear();

            for (LocationSnapshot p : snap.locations().values()) {
                VBox card = new VBox(5);
                card.setPadding(new Insets(10));
                card.setStyle("-fx-border-color: #888; -fx-border-width: 1; -fx-background-color: #f9f9f9; -fx-pref-width: 250;");
                
                Label nameLabel = new Label(p.name());
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
                nameLabel.setWrapText(true);
                card.getChildren().add(nameLabel);
                
                if (p.description() != null && !p.description().isEmpty()) {
                    Label descLabel = new Label(p.description());
                    descLabel.setWrapText(true);
                    descLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #555; -fx-font-size: 11;");
                    card.getChildren().add(descLabel);
                }
                
                card.getChildren().add(new Label("Characters:"));
                for (AgentSnapshot a : state.getPresentAgentsInLocation(p.id(), timeStep)) {
                    if (!a.hasArrived()) continue;
                    Label al = new Label("- " + a.name() + (a.isAlive() ? "" : " (DEAD)"));
                    al.setWrapText(true);
                    if (!a.isAlive()) al.setStyle("-fx-text-fill: red;");
                    card.getChildren().add(al);
                }
                
                card.getChildren().add(new Label("Items:"));
                for (ItemSnapshot i : state.getItemsInLocation(p.id(), timeStep)) {
                    Label il = new Label("- " + i.name() + (i.hidden() ? " (Hidden)" : ""));
                    il.setWrapText(true);
                    card.getChildren().add(il);
                }

                if (p.traces() != null && !p.traces().isEmpty()) {
                    card.getChildren().add(new Label("Traces:"));
                    for (EnvironmentTraceSnapshot trace : p.traces()) {
                        Label tl = new Label("- " + trace.description());
                        tl.setWrapText(true);
                        tl.setStyle("-fx-text-fill: #993300;");
                        card.getChildren().add(tl);
                    }
                }
                
                flow.getChildren().add(card);
            }
            
            // Items carried by agents
            VBox carriedCard = new VBox(5);
            carriedCard.setPadding(new Insets(10));
            carriedCard.setStyle("-fx-border-color: blue; -fx-border-width: 1; -fx-background-color: #e6f2ff; -fx-pref-width: 250;");
            Label carriedLabel = new Label("Carried Items:");
            carriedLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
            carriedCard.getChildren().add(carriedLabel);
            for (ItemSnapshot i : snap.items().values()) {
                if (snap.agents().containsKey(i.currentLocationId())) {
                    AgentSnapshot a = snap.agents().get(i.currentLocationId());
                    if (!a.hasArrived()) continue;
                    Label itemLabel = new Label("- " + i.name() + " (" + a.name() + ")");
                    itemLabel.setWrapText(true);
                    carriedCard.getChildren().add(itemLabel);
                }
            }
            flow.getChildren().add(carriedCard);
        }
    }

    private void setGenerationRunning(boolean running, String message) {
        Platform.runLater(() -> {
            if (statusLabel != null && message != null) {
                statusLabel.setText(message);
            }
            if (statusLamp != null) {
                if (running) {
                    statusLamp.setFill(Color.web("#f59e0b")); // Glowing amber
                    statusLamp.setStroke(Color.web("#d97706"));
                    Tooltip.install(statusLamp, new Tooltip("Generation in progress..."));
                    if (statusLampAnimation != null) {
                        statusLampAnimation.play();
                    }
                } else {
                    if (statusLampAnimation != null) {
                        statusLampAnimation.stop();
                    }
                    statusLamp.setOpacity(1.0);
                    statusLamp.setFill(Color.web("#10b981")); // Steady green (ready)
                    statusLamp.setStroke(Color.web("#059669"));
                    Tooltip.install(statusLamp, new Tooltip("Ready / Idle"));
                }
            }
        });
    }

    private void handleNewNovel() {
        if (simulationService.isRunning() || proseService.isRunning()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Simulation Running");
            alert.setHeaderText("A simulation or prose generation is currently active.");
            alert.setContentText("Do you want to stop the current process and start a new project?");
            var res = alert.showAndWait();
            if (res.isEmpty() || res.get() != ButtonType.OK) {
                return;
            }
            stopSimulation();
        }

        state.reset();
        chapters.clear();
        projectService.setCurrentProjectPath(null);
        if (eventLogArea != null) {
            eventLogArea.clear();
        }

        String initialTitle = defaultTitle != null && !defaultTitle.isBlank() ? defaultTitle : "Murder on the Orient Express";
        if (storyTitleField != null) {
            storyTitleField.setText(initialTitle);
        }
        state.setTitle(initialTitle);

        String initialDesc = defaultDescription != null && !defaultDescription.isBlank() ? defaultDescription : "A classic murder mystery at an isolated location.";
        if (storyDescArea != null) {
            storyDescArea.setText(initialDesc);
        }
        state.setPremise(initialDesc);
        if (numAgentsSpinner != null) {
            numAgentsSpinner.getValueFactory().setValue(defaultAgents > 0 ? defaultAgents : 6);
        }
        if (numPlacesSpinner != null) {
            numPlacesSpinner.getValueFactory().setValue(defaultPlaces > 0 ? defaultPlaces : 4);
        }
        if (numItemsSpinner != null) {
            numItemsSpinner.getValueFactory().setValue(defaultItems > 0 ? defaultItems : 2);
        }
        if (targetStepsCombo != null) {
            targetStepsCombo.setValue(100);
        }
        if (stepMinutesCombo != null) {
            stepMinutesCombo.setValue(15);
        }

        if (buildWorldBtn != null) {
            buildWorldBtn.setText("🌍 1. Build World");
            buildWorldBtn.setDisable(false);
        }
        startBtn.setText("▶ 2. Start Simulation");
        startBtn.setVisible(true);
        startBtn.setManaged(true);
        startBtn.setDisable(true);
        resumeBtn.setVisible(false);
        resumeBtn.setManaged(false);
        stopBtn.setDisable(true);
        if (generateScenesBtn != null) {
            generateScenesBtn.setDisable(true);
            generateScenesBtn.setText("🎬 3. Generate Scenes");
        }
        if (generateProseBtn != null) {
            generateProseBtn.setDisable(true);
            generateProseBtn.setText("✍️ 4. Generate Prose");
        }

        worldStatusBadge.setText("Not Started");
        worldStatusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        worldDetailsLabel.setText("Configure mystery parameters and click '🌍 1. Build World' (Phase 1).");
        simProgressBar.setProgress(0.0);
        updateActiveProjectDisplay();
        updateExportButtonState();
        simProgressLabel.setText("Step 0 of 0 (0.0%)");
        simDetailsLabel.setText("Scenes generated: 0");
        proseProgressBar.setProgress(0.0);
        proseProgressLabel.setText("Scenes with Prose: 0 (0.0%)");
        proseDetailsLabel.setText("Chapters written: 0");

        timelineSlider.setMin(0);
        timelineSlider.setMax(0);
        timelineSlider.setValue(0);

        updateScenesTab();
        renderStory();
        updateHighlights();
        updateGlobalEventsTab();
        displaySnapshot(0);

        tabPane.getSelectionModel().select(simulationTab);
        setGenerationRunning(false, "Ready. Click '🌍 1. Build World' to begin Phase 1.");
    }

    private String buildStoryMetadataHtml() {
        String title = state != null && state.getTitle() != null ? state.getTitle() : "Untitled Mystery";
        String premise = state != null && state.getPremise() != null ? state.getPremise() : "None";
        int hours = state != null ? state.getTotalHours() : 36;
        int targetSteps = state != null ? state.getTargetSteps() : 144;
        int stepMin = state != null && state.getStepMinutes() > 0 ? state.getStepMinutes() : 15;
        int currentStep = state != null ? state.getCurrentStep() : 0;
        int numAgents = state != null ? state.getAgents().size() : 0;
        int numPlaces = state != null ? state.getLocations().size() : 0;
        int numItems = state != null ? state.getItems().size() : 0;
        int numEvents = state != null ? state.getGlobalEvents().size() : 0;

        String voice = authorVoiceCombo != null && authorVoiceCombo.getValue() != null ? authorVoiceCombo.getValue() : "Classic Mystery";
        String sLen = sceneLengthCombo != null && sceneLengthCombo.getValue() != null ? sceneLengthCombo.getValue() : "Medium";

        String worldModel = ollamaLlmService != null ? ollamaLlmService.getWorldModel() : "default";
        String agentModel = ollamaLlmService != null ? ollamaLlmService.getAgentModel() : "default";
        String proseModel = ollamaLlmService != null ? ollamaLlmService.getProseModel() : "default";
        String baseUrl = ollamaLlmService != null ? ollamaLlmService.getBaseUrl() : "http://localhost:11434";

        double worldTemp = ollamaLlmService != null ? ollamaLlmService.getWorldTemperature() : 0.7;
        double agentTemp = ollamaLlmService != null ? ollamaLlmService.getAgentTemperature() : 0.7;
        double proseTemp = ollamaLlmService != null ? ollamaLlmService.getProseTemperature() : 0.8;

        int worldCtx = ollamaLlmService != null ? ollamaLlmService.getWorldNumCtx() : 8192;
        int agentCtx = ollamaLlmService != null ? ollamaLlmService.getAgentNumCtx() : 4096;
        int proseCtx = ollamaLlmService != null ? ollamaLlmService.getProseNumCtx() : 8192;

        long finalCutCount = chapters != null ? chapters.stream().filter(Chapter::isSelected).count() : 0;
        long cutCount = (chapters != null ? chapters.size() : 0) - finalCutCount;
        int totalWords = calculateTotalWordCount();
        int finalCutWords = chapters != null ? chapters.stream().filter(Chapter::isSelected).mapToInt(Chapter::wordCount).sum() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='margin-top: 40px; padding: 20px; background-color: #f8fafc; border: 1px solid #cbd5e1; border-radius: 8px; font-family: system-ui, sans-serif;'>");
        sb.append("<h3 style='margin-top: 0; color: #1e293b; border-bottom: 2px solid #cbd5e1; padding-bottom: 8px;'>📋 Novel Generation Parameters & Setup</h3>");
        sb.append("<table style='width: 100%; border-collapse: collapse; font-size: 14px;'>");
        sb.append("<tr><td style='padding: 6px 8px; font-weight: bold; width: 220px; color: #475569;'>Application:</td><td style='color: #0f172a;'><b>").append(AppConstants.APP_NAME).append(" (v").append(getAppVersion()).append(")</b></td></tr>");
        sb.append("<tr><td style='padding: 6px 8px; font-weight: bold; width: 220px; color: #475569;'>Title:</td><td style='color: #0f172a;'><b>").append(title).append("</b></td></tr>");
        sb.append("<tr><td style='padding: 6px 8px; font-weight: bold; color: #475569;'>Premise:</td><td style='color: #334155;'>").append(premise).append("</td></tr>");
        sb.append("<tr><td style='padding: 6px 8px; font-weight: bold; color: #475569;'>Word Count:</td><td style='color: #334155;'>").append(String.format("%,d words", totalWords)).append(cutCount > 0 ? String.format(" (%,d words in Final Cut)", finalCutWords) : "").append("</td></tr>");
        sb.append("<tr><td style='padding: 6px 8px; font-weight: bold; color: #475569;'>Simulation Duration:</td><td style='color: #334155;'>").append(targetSteps).append(" target steps (").append(stepMin).append(" min/step, total duration: ").append(state != null ? state.getClockTime(targetSteps) : hours + " hours").append(", completed ").append(currentStep).append(" steps)</td></tr>");
        sb.append("<tr><td style='padding: 6px 8px; font-weight: bold; color: #475569;'>Cast & Environment:</td><td style='color: #334155;'>").append(numAgents).append(" Characters, ").append(numPlaces).append(" Locations, ").append(numItems).append(" Items, ").append(numEvents).append(" Major Global Events</td></tr>");
        sb.append("<tr><td style='padding: 6px 8px; font-weight: bold; color: #475569;'>Author Voice & Style:</td><td style='color: #334155;'>").append(voice).append(" (Scene Length: ").append(sLen).append(")</td></tr>");
        sb.append("<tr><td style='padding: 6px 8px; font-weight: bold; color: #475569;'>Ollama Server Base URL:</td><td style='color: #334155;'><code>").append(baseUrl).append("</code></td></tr>");
        sb.append("<tr><td style='padding: 6px 8px; font-weight: bold; color: #475569;'>World Generation Model:</td><td style='color: #334155;'><code>").append(worldModel).append("</code> (Temp: ").append(worldTemp).append(", Context: ").append(worldCtx).append(")</td></tr>");
        sb.append("<tr><td style='padding: 6px 8px; font-weight: bold; color: #475569;'>Agent Simulation Model:</td><td style='color: #334155;'><code>").append(agentModel).append("</code> (Temp: ").append(agentTemp).append(", Context: ").append(agentCtx).append(")</td></tr>");
        sb.append("<tr><td style='padding: 6px 8px; font-weight: bold; color: #475569;'>Prose Generation Model:</td><td style='color: #334155;'><code>").append(proseModel).append("</code> (Temp: ").append(proseTemp).append(", Context: ").append(proseCtx).append(")</td></tr>");
        sb.append("<tr><td style='padding: 6px 8px; font-weight: bold; color: #475569;'>Editorial Curation:</td><td style='color: #334155;'>").append(chapters != null ? chapters.size() : 0).append(" Curated Scenes total (").append(finalCutCount).append(" Final Cut Chapters, ").append(cutCount).append(" Cut / Atmospheric Summaries, ").append(String.format("%,d words", totalWords)).append(")</td></tr>");
        sb.append("</table>");
        sb.append("</div>");
        return sb.toString();
    }

    public String buildStoryMetadataText() {
        String title = getStoryTitle();
        String premise = state != null && state.getPremise() != null ? state.getPremise() : "None";
        int hours = state != null ? state.getTotalHours() : 36;
        int targetSteps = state != null ? state.getTargetSteps() : 144;
        int stepMin = state != null && state.getStepMinutes() > 0 ? state.getStepMinutes() : 15;
        int currentStep = state != null ? state.getCurrentStep() : 0;
        int numAgents = state != null ? state.getAgents().size() : 0;
        int numPlaces = state != null ? state.getLocations().size() : 0;
        int numItems = state != null ? state.getItems().size() : 0;
        int numEvents = state != null ? state.getGlobalEvents().size() : 0;

        String voice = authorVoiceCombo != null && authorVoiceCombo.getValue() != null ? authorVoiceCombo.getValue() : "Classic Mystery";
        String sLen = sceneLengthCombo != null && sceneLengthCombo.getValue() != null ? sceneLengthCombo.getValue() : "Medium";

        String worldModel = ollamaLlmService != null ? ollamaLlmService.getWorldModel() : "default";
        String agentModel = ollamaLlmService != null ? ollamaLlmService.getAgentModel() : "default";
        String proseModel = ollamaLlmService != null ? ollamaLlmService.getProseModel() : "default";
        String baseUrl = ollamaLlmService != null ? ollamaLlmService.getBaseUrl() : "http://localhost:11434";

        double worldTemp = ollamaLlmService != null ? ollamaLlmService.getWorldTemperature() : 0.7;
        double agentTemp = ollamaLlmService != null ? ollamaLlmService.getAgentTemperature() : 0.7;
        double proseTemp = ollamaLlmService != null ? ollamaLlmService.getProseTemperature() : 0.8;

        int worldCtx = ollamaLlmService != null ? ollamaLlmService.getWorldNumCtx() : 8192;
        int agentCtx = ollamaLlmService != null ? ollamaLlmService.getAgentNumCtx() : 4096;
        int proseCtx = ollamaLlmService != null ? ollamaLlmService.getProseNumCtx() : 8192;

        long finalCutCount = chapters != null ? chapters.stream().filter(Chapter::isSelected).count() : 0;
        long cutCount = (chapters != null ? chapters.size() : 0) - finalCutCount;
        int totalWords = calculateTotalWordCount();
        int finalCutWords = chapters != null ? chapters.stream().filter(Chapter::isSelected).mapToInt(Chapter::wordCount).sum() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\n");
        sb.append("NOVEL GENERATION PARAMETERS & SETUP\n");
        sb.append("================================================================================\n");
        sb.append("Application: ").append(AppConstants.APP_NAME).append("\n");
        sb.append("Program Version: ").append(getAppVersion()).append("\n");
        sb.append("Title: ").append(title).append("\n");
        sb.append("Premise: ").append(premise).append("\n");
        sb.append("Word Count: ").append(String.format("%,d words", totalWords));
        if (cutCount > 0) {
            sb.append(String.format(" (%,d words in Final Cut)", finalCutWords));
        }
        sb.append("\n");
        sb.append("Simulation Duration: ").append(targetSteps).append(" target steps (").append(stepMin).append(" min/step, total duration: ").append(state != null ? state.getClockTime(targetSteps) : hours + " hours").append(", completed ").append(currentStep).append(" steps)\n");
        sb.append("Cast & Environment: ").append(numAgents).append(" Characters, ").append(numPlaces).append(" Locations, ").append(numItems).append(" Items, ").append(numEvents).append(" Major Global Events\n");
        sb.append("Author Voice: ").append(voice).append(" | Scene Length: ").append(sLen).append("\n");
        sb.append("Ollama Base URL: ").append(baseUrl).append("\n\n");
        sb.append("LLM Models Used:\n");
        sb.append("- World Generation: ").append(worldModel).append(" (Temp: ").append(worldTemp).append(", Context: ").append(worldCtx).append(")\n");
        sb.append("- Agent Simulation: ").append(agentModel).append(" (Temp: ").append(agentTemp).append(", Context: ").append(agentCtx).append(")\n");
        sb.append("- Prose Engine:     ").append(proseModel).append(" (Temp: ").append(proseTemp).append(", Context: ").append(proseCtx).append(")\n\n");
        sb.append("Editorial Curation:\n");
        sb.append("- Total Scenes Curated: ").append(chapters != null ? chapters.size() : 0).append("\n");
        sb.append("- Final Cut Chapters:   ").append(finalCutCount).append("\n");
        sb.append("- Cut / Fluff Chapters: ").append(cutCount).append("\n");
        sb.append("- Total Word Count:     ").append(String.format("%,d words", totalWords)).append("\n");
        if (cutCount > 0) {
            sb.append("- Final Cut Word Count: ").append(String.format("%,d words", finalCutWords)).append("\n");
        }
        sb.append("================================================================================\n");
        return sb.toString();
    }
}
