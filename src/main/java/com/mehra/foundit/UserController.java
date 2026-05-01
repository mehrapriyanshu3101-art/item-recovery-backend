package com.mehra.foundit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JavaMailSender mailSender;

    // Reads spring.mail.username from application.properties automatically
    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * 1. REGISTRATION (Used by Android App)
     * Creates a user and returns a unique 8-character token for the QR code.
     */
    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody User user) {
        String token = UUID.randomUUID().toString().substring(0, 8);
        user.setUniqueToken(token);
        userRepository.save(user);
        return ResponseEntity.ok(token);
    }

    /**
     * 2. FINDER LOOKUP (Used by Website)
     * Returns the owner's name to the finder so they know who they found the item for.
     */
    @GetMapping("/find/{token}")
    public ResponseEntity<User> findByToken(@PathVariable String token) {
        return userRepository.findByUniqueToken(token)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 3. DYNAMIC EMAIL DISPATCH (Used by Website)
     * Receives the token and message from the finder, looks up the owner,
     * and sends the email directly to that owner's registered email address.
     */
    @PostMapping("/send-email")
    public ResponseEntity<String> sendRecoveryEmail(@RequestBody Map<String, String> payload) {
        String token         = payload.get("id");
        String finderMessage = payload.get("message");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body("Missing token in request.");
        }
        if (finderMessage == null || finderMessage.isBlank()) {
            return ResponseEntity.badRequest().body("Message cannot be empty.");
        }

        return userRepository.findByUniqueToken(token).map(owner -> {
            if (owner.getEmail() == null || owner.getEmail().isBlank()) {
                return ResponseEntity.status(500).body("Owner email is not configured.");
            }
            try {
                SimpleMailMessage email = new SimpleMailMessage();
                email.setFrom(senderEmail);          // required by Gmail SMTP
                email.setTo(owner.getEmail());
                email.setSubject("Item Found! A message from the finder");
                email.setText(
                        "Hello " + owner.getName() + ",\n\n" +
                                "Good news! Someone found your lost item and sent you a message:\n\n" +
                                "\"" + finderMessage + "\"\n\n" +
                                "Please reply to this message if the finder included their contact info."
                );

                mailSender.send(email);
                return ResponseEntity.ok("Email sent successfully to " + owner.getName());

            } catch (Exception e) {
                return ResponseEntity.status(500)
                        .body(buildMailErrorMessage(e));
            }
        }).orElse(ResponseEntity.status(404).body("Token not recognized."));
    }

    private String buildMailErrorMessage(Exception e) {
        String rootMessage = findRootCauseMessage(e).toLowerCase();

        if (e instanceof MailAuthenticationException || rootMessage.contains("auth") || rootMessage.contains("username and password not accepted")) {
            return "Email auth failed: MAIL_USERNAME / MAIL_PASSWORD is invalid, revoked, or not an App Password.";
        }
        if (rootMessage.contains("535-5.7.8")) {
            return "Gmail rejected login (535-5.7.8). Use a valid Gmail App Password (16 chars, no spaces).";
        }
        if (rootMessage.contains("timed out") || rootMessage.contains("timeout") || rootMessage.contains("could not connect to smtp host")) {
            return "SMTP connection timed out. Render could not reach Gmail SMTP or the connection is blocked.";
        }
        if (rootMessage.contains("from address failed") || rootMessage.contains("sender address rejected")) {
            return "Sender rejected by SMTP. MAIL_USERNAME must match the Gmail account used to generate the App Password.";
        }
        if (rootMessage.contains("recipient address rejected") || rootMessage.contains("invalid addresses")) {
            return "Recipient email was rejected by SMTP. Check owner's email format in database.";
        }
        if (e instanceof MailSendException) {
            return "SMTP send failed. Check Gmail account security settings and SMTP access on the server.";
        }
        return "Email send failed: " + sanitize(rootMessage);
    }

    private String findRootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        if (current.getMessage() != null && !current.getMessage().isBlank()) {
            return current.getMessage();
        }
        return throwable.getClass().getSimpleName();
    }

    private String sanitize(String message) {
        return message
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
    }
}
