package com.attendiq.web;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(FirebaseAuth.class)
public class FirebaseIdTokenVerifier {
    private final FirebaseAuth firebaseAuth;

    public FirebaseIdTokenVerifier(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    public FirebaseToken verify(String idToken) throws FirebaseAuthException {
        return firebaseAuth.verifyIdToken(idToken, true);
    }
}
