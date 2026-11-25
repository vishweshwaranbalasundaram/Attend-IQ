package com.attendiq.web;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.IOException;
import java.util.Date;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "attendiq.auth.dev-auth-bypass", havingValue = "false", matchIfMissing = true)
public class FirebaseAuthConfiguration {
    @Bean
    FirebaseApp attendIqFirebaseApp(@Value("${attendiq.firebase.project-id}") String projectId,
            @Value("${FIREBASE_AUTH_EMULATOR_HOST:}") String emulatorHost) throws IOException {
        FirebaseOptions.Builder options = FirebaseOptions.builder().setProjectId(projectId);
        if (!emulatorHost.isBlank()) {
            options.setCredentials(GoogleCredentials.create(
                    new AccessToken("owner", new Date(System.currentTimeMillis() + 3_600_000))));
        } else {
            options.setCredentials(GoogleCredentials.getApplicationDefault());
        }
        return FirebaseApp.initializeApp(options.build(), "attendiq");
    }

    @Bean
    FirebaseAuth attendIqFirebaseAuth(FirebaseApp attendIqFirebaseApp) {
        return FirebaseAuth.getInstance(attendIqFirebaseApp);
    }
}
