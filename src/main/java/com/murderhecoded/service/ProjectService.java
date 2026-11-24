package com.murderhecoded.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.murderhecoded.domain.Chapter;
import com.murderhecoded.domain.SimulationStateDocument;
import com.murderhecoded.repository.SimulationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

@Service
@SuppressWarnings("PMD")
public class ProjectService {
    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private final ObjectMapper objectMapper;
    private Path currentProjectPath;

    public ProjectService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void createNewProject(String title) {
        String safeTitle = title.replaceAll("[^a-zA-Z0-9.-]", "_");
        if (safeTitle.isEmpty()) {
            safeTitle = "Untitled";
        }
        
        Path baseNovelDir = Paths.get("novel");
        try {
            if (!Files.exists(baseNovelDir)) {
                Files.createDirectories(baseNovelDir);
            }
            
            Path projectDir = baseNovelDir.resolve(safeTitle);
            int counter = 1;
            while (Files.exists(projectDir)) {
                projectDir = baseNovelDir.resolve(safeTitle + "_" + counter);
                counter++;
            }
            
            Files.createDirectories(projectDir);
            this.currentProjectPath = projectDir;
            log.info("Created new project directory: {}", projectDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create project directory for title: {}", safeTitle, e);
        }
    }

    public Path getCurrentProjectPath() {
        return currentProjectPath;
    }

    public void setCurrentProjectPath(Path currentProjectPath) {
        this.currentProjectPath = currentProjectPath;
    }

    public void autoSave(SimulationRepository state, List<Chapter> chapters, String eventLog) {
        if (currentProjectPath == null) {
            return;
        }

        Path tempPath = null;
        try {
            if (state != null) {
                SimulationStateDocument doc = state.toDocument();
                Path targetPath = currentProjectPath.resolve("state.json");
                tempPath = currentProjectPath.resolve("state.json.tmp");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), doc);
                safeMove(tempPath, targetPath);
            }

            // Primary scenes file reflecting full scene evaluations, time spans, and selection
            File scenesFile = currentProjectPath.resolve("scenes.json").toFile();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(scenesFile, chapters);

            // Backward-compatible mirror file
            File chaptersFile = currentProjectPath.resolve("chapters.json").toFile();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(chaptersFile, chapters);

            File logFile = currentProjectPath.resolve("eventlog.txt").toFile();
            Files.writeString(logFile.toPath(), eventLog);
            
            log.info("Autosaved project to: {}", currentProjectPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to autosave project", e);
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
            }
        }
    }

    private void safeMove(Path source, Path target) throws IOException {
        int maxAttempts = 5;
        long delayMs = 50;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (AtomicMoveNotSupportedException | java.nio.file.AccessDeniedException e) {
                try {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                } catch (java.nio.file.AccessDeniedException ex) {
                    if (attempt == maxAttempts - 1) {
                        try {
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                            Files.deleteIfExists(source);
                            return;
                        } catch (IOException copyEx) {
                            throw ex;
                        }
                    }
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while waiting to save project file", ie);
                    }
                    delayMs *= 2;
                }
            }
        }
    }

    public ProjectData loadProject(File projectDir) throws IOException {
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            throw new IllegalArgumentException("Invalid project directory: " + projectDir.getAbsolutePath());
        }
        this.currentProjectPath = projectDir.toPath();

        File stateFile = new File(projectDir, "state.json");
        SimulationStateDocument doc = null;
        if (stateFile.exists()) {
            doc = objectMapper.readValue(stateFile, SimulationStateDocument.class);
        } else {
            doc = SimulationStateDocument.empty();
        }

        File scenesFile = new File(projectDir, "scenes.json");
        File chaptersFile = new File(projectDir, "chapters.json");
        File sourceFile = scenesFile.exists() ? scenesFile : chaptersFile;

        List<Chapter> chaptersList = new java.util.ArrayList<>();
        if (sourceFile.exists()) {
            Chapter[] chaptersArray = objectMapper.readValue(sourceFile, Chapter[].class);
            chaptersList = Arrays.asList(chaptersArray);
        }

        File logFile = new File(projectDir, "eventlog.txt");
        String eventLog = "";
        if (logFile.exists()) {
            eventLog = Files.readString(logFile.toPath());
        }

        return new ProjectData(doc, chaptersList, eventLog);
    }

    public record ProjectData(
            SimulationStateDocument stateDocument,
            List<Chapter> chapters,
            String eventLog
    ) {
        public SimulationRepository state() {
            SimulationRepository repo = new SimulationRepository();
            if (stateDocument != null) {
                repo.restoreFrom(stateDocument);
            }
            return repo;
        }
    }
}
