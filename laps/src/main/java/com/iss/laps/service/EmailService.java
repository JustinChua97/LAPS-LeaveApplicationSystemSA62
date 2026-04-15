package com.iss.laps.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.iss.laps.model.LeaveApplication;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username:noreply@laps.iss.edu.sg}")
    private String fromEmail;

    @Value("${app.base-url:localhost}")
    private String appHost;

    public enum NotificationType {
        APPLICATION, APPROVAL, REJECTION
    }

    @Async
    public void sendLeaveApplicationNotification(LeaveApplication application, NotificationType type) {
        try {
            String recipient = resolveRecipient(type, application);
            if (recipient == null) {
                log.warn("No recipient found for notification type {}", type);
                return;
            }

            String subject = buildSubject(type, application);
            String body = buildBody(type, application);

            sendEmail(recipient, subject, body);
        } catch (Exception e) {
            log.warn("Failed to send {} notification email", type, e);
        }
    }

    private String resolveRecipient(NotificationType type, LeaveApplication application) {
        switch (type) {
            case APPLICATION:
                return application.getEmployee().getManager() != null
                        ? application.getEmployee().getManager().getEmail()
                        : null;
            case APPROVAL:
            case REJECTION:
                return application.getEmployee().getEmail();
            default:
                return null;
        }
    }

    private String buildSubject(NotificationType type, LeaveApplication application) {
        switch (type) {
            case APPLICATION:
                return "Leave Application from " + application.getEmployee().getName();
            case APPROVAL:
                return "Your Leave Application has been Approved";
            case REJECTION:
                return "Your Leave Application has been Rejected";
            default:
                return "LAPS Notification";
        }
    }

    private String buildBody(NotificationType type, LeaveApplication application) {
        Context context = new Context();
        context.setVariable("employee", application.getEmployee().getName());
        context.setVariable("manager", application.getEmployee().getManager() != null
                ? application.getEmployee().getManager().getName()
                : "Manager");
        context.setVariable("leaveType", application.getLeaveType().getName());
        context.setVariable("startDate", application.getStartDate());
        context.setVariable("endDate", application.getEndDate());
        context.setVariable("reason", application.getReason());
        context.setVariable("comment", application.getManagerComment() != null ? application.getManagerComment() : "N/A");
        context.setVariable("appHost", appHost);

        String templateName;
        switch (type) {
            case APPLICATION:
                templateName = "emails/leave-application";
                break;
            case APPROVAL:
                templateName = "emails/leave-approval";
                break;
            case REJECTION:
                templateName = "emails/leave-rejection";
                break;
            default:
                templateName = "emails/generic";
        }

        return templateEngine.process(templateName, context);
    }

    private void sendEmail(String to, String subject, String html) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
        log.info("Email sent to {}: {}", to, subject);
    }
}
