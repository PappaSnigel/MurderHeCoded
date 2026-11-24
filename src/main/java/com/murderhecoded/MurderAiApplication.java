package com.murderhecoded;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@SuppressWarnings("PMD.UseUtilityClass")
public class MurderAiApplication {

    public static final String APP_NAME = AppConstants.APP_NAME;

    protected MurderAiApplication() {
        // Protected constructor for framework instantiation
    }

    public static void main(String[] args) {
        Application.launch(JavaFxApplication.class, args);
    }
}
