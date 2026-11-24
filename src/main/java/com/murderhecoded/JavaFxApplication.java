package com.murderhecoded;

import com.murderhecoded.ui.MainController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.InputStream;

@SuppressWarnings("PMD")
public class JavaFxApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(JavaFxApplication.class);
    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        String[] args = getParameters().getRaw().toArray(new String[0]);
        context = SpringApplication.run(MurderAiApplication.class, args);
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void start(Stage primaryStage) {
        MainController mainController = context.getBean(MainController.class);
        mainController.initializeUi();
        
        Scene scene = new Scene(mainController.getView(), 1000, 700);
        
        String formattedTitle = mainController.getFormattedTitleWithSequence();
        String baseTitle = AppConstants.APP_NAME + " v" + mainController.getAppVersion();
        primaryStage.setTitle(formattedTitle != null ? baseTitle + " - " + formattedTitle : baseTitle);
        try (InputStream iconStream = getClass().getResourceAsStream("/icon.jpg")) {
            if (iconStream != null) {
                primaryStage.getIcons().add(new javafx.scene.image.Image(iconStream));
            }
        } catch (Exception e) {
            log.warn("Could not load application icon: {}", e.getMessage());
        }
        primaryStage.setScene(scene);
        primaryStage.show();
        mainController.updateActiveProjectDisplay();
        
        mainController.showStartupDialog();
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
        Platform.exit();
    }
}