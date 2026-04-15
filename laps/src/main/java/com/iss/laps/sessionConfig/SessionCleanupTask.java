package com.iss.laps.sessionConfig;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionCleanupTask {
	
    private final SessionRepository sessionRepository;
    
    @Scheduled(fixedRate = 60000) // every 1 min
    public void cleanupExpiredSessions() {
        sessionRepository.invalidateExpiredSessions();
    }
}
