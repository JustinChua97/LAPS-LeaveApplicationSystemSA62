package com.iss.laps.service;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.iss.laps.exception.MessageNotSentException;
import com.iss.laps.model.LeaveApplication;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderEmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username:noreply@laps.iss.edu.sg}")
    private String fromEmail;

    @Value("${app.base-url:localhost}")
    private String appHost;

    @Async
    public void sendManagerReminder(LeaveApplication application, long daysUntilStart) {
        try {
            String recipient = application.getEmployee().getManager() != null
                    ? application.getEmployee().getManager().getEmail()
                    : null;

            if (recipient == null) {
                log.warn("No manager found for employee {}", application.getEmployee().getName());
                return;
            }

            String subject = buildSubject(application, daysUntilStart);
            String body = buildBody(application, daysUntilStart);

            sendEmail(recipient, subject, body);
        } catch (MessageNotSentException e) {
            log.warn("Failed to send reminder email", e);
            throw e;
        } catch  (Exception e) {
            log.error("Failed to send reminder email", e);
            throw new MessageNotSentException("Failed to send reminder email: " + e.getMessage(), e);
        }
    }

    private String buildSubject(LeaveApplication application, long daysUntilStart) {
        return "Reminder: Pending Leave Application for " 
                + application.getEmployee().getName() 
                + " (" + daysUntilStart + " days left)";
    }

    private String buildBody(LeaveApplication application, long daysUntilStart) {
        Context context = new Context();
        context.setVariable("employee", application.getEmployee().getName());
        context.setVariable("leaveType", application.getLeaveType().getName());
        context.setVariable("startDate", application.getStartDate());
        context.setVariable("endDate", application.getEndDate());
        context.setVariable("reason", application.getReason());
        context.setVariable("daysUntilStart", daysUntilStart);
        context.setVariable("appHost", appHost);

        // NEW dedicated template for reminders
        return templateEngine.process("emails/leave-reminder", context);
    }

    private void sendEmail(String to, String subject, String html) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
        log.info("Reminder email sent to {}: {}", to, subject);
    }
}


