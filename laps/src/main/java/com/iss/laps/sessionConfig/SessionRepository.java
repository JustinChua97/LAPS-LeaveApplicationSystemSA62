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
        sessions.put(session.getId(), new SessionInfo(session, Instant.now())); //sessionInfo object is created to allow more expansion of more inputs in the future like user-details etc.
        log.info("Session {} registered at {}", session.getId(), Instant.now());
    }

    public void invalidateExpiredSessions() {
        Instant now = Instant.now();
        sessions.values().removeIf(info -> {
            boolean expired = now.isAfter(info.getCreated().plusSeconds(60)); // 1 min for testing.
            if (expired) {
                info.invalidate();
            }
            return expired;
        });
    }

    public void removeSession(String sessionId) {
        SessionInfo info = sessions.remove(sessionId);
        if (info != null) {
            info.invalidate();
            log.info("Session {} removed manually at {}", sessionId, Instant.now());
        }
    }

    @Slf4j
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
                log.info("Session {} invalidated due to timeout at {}", session.getId(), Instant.now());
            } catch (IllegalStateException e) {
                log.debug("Session {} already invalidated by container", session.getId());
            }
        }
    }
}
