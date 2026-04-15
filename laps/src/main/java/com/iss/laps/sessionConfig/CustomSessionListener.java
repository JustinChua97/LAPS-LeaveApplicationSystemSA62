package com.iss.laps.sessionConfig;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import org.springframework.stereotype.Component;

@Component
public class CustomSessionListener implements HttpSessionListener {
	private final SessionRepository sessionRepository;
    @Override
    public void sessionCreated(HttpSessionEvent session) {
        sessionRepository.registerSession(session.getSession());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent session) {
        sessionRepository.removeSession(session.getSession());
    }
}

