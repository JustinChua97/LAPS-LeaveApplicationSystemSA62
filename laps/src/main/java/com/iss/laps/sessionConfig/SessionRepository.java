package com.iss.laps.sessionConfig;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Slf4j
public class SessionRepository {

    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public void registerSession(HttpSession session) {
        sessions.put(session.getId(), new SessionInfo(session, Instant.now()));
        log.info("Session {} registered at {}", session.getId(), Instant.now());
    }

    public void invalidateExpiredSessions() {
        Instant now = Instant.now();
        sessions.values().removeIf(info -> {
            boolean expired = now.isAfter(info.getCreated().plusSeconds(60)); // 1 min for testing
            if (expired) {
                info.invalidate();
            }
            return expired;
        });
    }

    public void removeSession(HttpSession session) {
        session.invalidate(); 
        sessions.remove(session.getId());
        log.info("Session {} invalidated and removed at {}", session.getId(), Instant.now());
    }

        //SLF4J is not static, causing errors in Sessioninfo class.
    private static class SessionInfo {
        private final HttpSession session;
        private final Instant created;

        SessionInfo(HttpSession session, Instant created) {
            this.session = session;
            this.created = created;
        }

        Instant getCreated() {
            return created;
        }

        void invalidate() {
            try {
                session.invalidate();
                
            } catch (IllegalStateException e) {
                
            }
        }
    }
}
