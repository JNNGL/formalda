package beer.sisipisi.formalda.service;

import java.util.Locale;

import beer.sisipisi.formalda.config.FormaldaProperties;
import beer.sisipisi.formalda.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class VerificationMailService {
    private final JavaMailSender mailSender;
    private final MessageSource messages;
    private final FormaldaProperties properties;
    private final String fromAddress;

    public VerificationMailService(
            JavaMailSender mailSender,
            MessageSource messages,
            FormaldaProperties properties,
            @Value("${formalda.mail-from}") String fromAddress) {
        this.mailSender = mailSender;
        this.messages = messages;
        this.properties = properties;
        this.fromAddress = fromAddress;
    }

    public void send(User user, String code, Locale locale) {
        String subject = messages.getMessage(
                "mail.verification.subject", null, "Formalda: verification code", locale);
        String body = messages.getMessage(
                "mail.verification.body",
                new Object[] {user.getUsername(), code, properties.baseUrl()},
                "Hello, " + user.getUsername() + ". Your Formalda verification code: " + code,
                locale);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(user.getEmail());
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
