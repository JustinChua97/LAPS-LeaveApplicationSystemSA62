package com.iss.laps.service;

import com.iss.laps.model.LeaveApplication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@laps.iss.edu.sg}")
    private String fromEmail;

    @Value("${app.base-url:localhost}")
    private String appHost;

    @Async
    public void sendLeaveApplicationNotification(LeaveApplication application) {
        try {
            if (application.getEmployee().getManager() == null) return;

            String managerEmail = application.getEmployee().getManager().getEmail();
            String subject = "Leave Application from " + application.getEmployee().getName();
            String body = String.format(
                    "Dear %s,\n\n%s has applied for %s leave from %s to %s.\n\nReason: %s\n\n" +
                    "Please login to LAPS to approve or reject: http://%s:8080/manager/leaves\n\nRegards,\nLAPS System",
                    application.getEmployee().getManager().getName(),
                    application.getEmployee().getName(),
                    application.getLeaveType().getName(),
                    application.getStartDate(),
                    application.getEndDate(),
                    application.getReason(),
                    appHost
            );
            sendEmail(managerEmail, subject, body);
        } catch (Exception e) {
            log.warn("Failed to send leave application notification email", e);
        }
    }

    @Async
    public void sendLeaveApprovalNotification(LeaveApplication application) {
        try {
            String employeeEmail = application.getEmployee().getEmail();
            String subject = "Your Leave Application has been Approved";
            String body = String.format(
                    "Dear %s,\n\nYour %s leave application from %s to %s has been APPROVED.\n\n" +
                    "Comment: %s\n\nPlease login to view details: http://%s:8080/employee/leaves\n\nRegards,\nLAPS System",
                    application.getEmployee().getName(),
                    application.getLeaveType().getName(),
                    application.getStartDate(),
                    application.getEndDate(),
                    application.getManagerComment() != null ? application.getManagerComment() : "N/A",
                    appHost
            );
            sendEmail(employeeEmail, subject, body);
        } catch (Exception e) {
            log.warn("Failed to send approval notification email", e);
        }
    }

    @Async
    public void sendLeaveRejectionNotification(LeaveApplication application) {
        try {
            String employeeEmail = application.getEmployee().getEmail();
            String subject = "Your Leave Application has been Rejected";
            String body = String.format(
                    "Dear %s,\n\nYour %s leave application from %s to %s has been REJECTED.\n\n" +
                    "Reason: %s\n\nPlease login to view details: http://%s:8080/employee/leaves\n\nRegards,\nLAPS System",
                    application.getEmployee().getName(),
                    application.getLeaveType().getName(),
                    application.getStartDate(),
                    application.getEndDate(),
                    application.getManagerComment(),
                    appHost
            );
            sendEmail(employeeEmail, subject, body);
        } catch (Exception e) {
            log.warn("Failed to send rejection notification email", e);
        }
    }

    private void sendEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        log.info("Email sent to {}: {}", to, subject);
    }
}
