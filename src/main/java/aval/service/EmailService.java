package aval.service;

import java.io.File;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/**
 * Sends reconciliation reports via SMTP email.
 */
public class EmailService {

    //-------------- Attributes ----------------------//
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromAddress;

    //-------------- Methods ----------------------//
    public EmailService(
        String host,
        int port,
        String username,
        String password,
        String fromAddress
    ) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
    }

    /**
     * Sends the reconciliation report to the specified email address.
     */
    public void sendReport(String toEmail, String clientName, File reportFile)
        throws MessagingException, java.io.IOException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));

        Session session = Session.getInstance(
            props,
            new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            }
        );

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress));
        message.setRecipients(
            Message.RecipientType.TO,
            InternetAddress.parse(toEmail)
        );
        message.setSubject("AVAL AIRE — Reconciliation Report: " + clientName);

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("Please find the reconciliation report attached.");

        MimeBodyPart filePart = new MimeBodyPart();
        filePart.attachFile(reportFile);

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(textPart);
        multipart.addBodyPart(filePart);

        message.setContent(multipart);
        Transport.send(message);
    }
}
