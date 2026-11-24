package com.murderhecoded.service;

import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@SuppressWarnings("PMD")
public class FileExportService {

    private static final Logger log = LoggerFactory.getLogger(FileExportService.class);

    public void saveEventLog(Window ownerWindow, String content) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Event Log");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        fileChooser.setInitialFileName("murder_mystery_log.txt");
        
        File file = fileChooser.showSaveDialog(ownerWindow);
        if (file != null) {
            try {
                Files.writeString(file.toPath(), content);
            } catch (IOException ex) {
                log.error("Failed to write event log to file: {}", file.getAbsolutePath(), ex);
            }
        }
    }
}