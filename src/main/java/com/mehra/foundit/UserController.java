package com.mehra.foundit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    // Resend API key from environment variable
    @Value("${RESEND_API_KEY:}")
    private String resendApiKey;

    // Verified sender identity in Resend, e.g. no-reply@yourdomain.com
    @Value("${MAIL_FROM:}")
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
                if (resendApiKey == null || resendApiKey.isBlank()) {
                    return ResponseEntity.status(500).body("Missing RESEND_API_KEY on server.");
                }
                if (senderEmail == null || senderEmail.isBlank()) {
                    return ResponseEntity.status(500).body("Missing MAIL_FROM on server.");
                }

                String mailText =
                        "Hello " + owner.getName() + ",\n\n" +
                                "Good news! Someone found your lost item and sent you a message:\n\n" +
                                "\"" + finderMessage + "\"\n\n" +
                                "Please reply to this message if the finder included their contact info.";

                sendViaResend(owner.getEmail(), "Item Found! A message from the finder", mailText);
                return ResponseEntity.ok("Email sent successfully to " + owner.getName());

            } catch (Exception e) {
                return ResponseEntity.status(500)
                        .body(buildMailErrorMessage(e));
            }
        }).orElse(ResponseEntity.status(404).body("Token not recognized."));
    }

    private String buildMailErrorMessage(Exception e) {
        String rootMessage = findRootCauseMessage(e).toLowerCase();

        if (rootMessage.contains("401") || rootMessage.contains("unauthorized")) {
            return "Resend authentication failed. RESEND_API_KEY is invalid or revoked.";
        }
        if (rootMessage.contains("403") || rootMessage.contains("forbidden")) {
            return "Resend rejected sender. MAIL_FROM must be a verified sender/domain in Resend.";
        }
        if (rootMessage.contains("422")) {
            return "Resend validation failed. Check recipient email and MAIL_FROM format.";
        }
        if (rootMessage.contains("timed out") || rootMessage.contains("timeout")) {
            return "Email API timed out. Please retry in a few seconds.";
        }
        return "Email send failed: " + sanitize(rootMessage);
    }

    private void sendViaResend(String to, String subject, String text) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(40))
                .build();

        String payload = "{"
                + "\"from\":\"" + escapeJson(senderEmail) + "\","
                + "\"to\":[\"" + escapeJson(to) + "\"],"
                + "\"subject\":\"" + escapeJson(subject) + "\","
                + "\"text\":\"" + escapeJson(text) + "\""
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .timeout(Duration.ofSeconds(40))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Resend API error " + response.statusCode() + ": " + response.body());
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
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
