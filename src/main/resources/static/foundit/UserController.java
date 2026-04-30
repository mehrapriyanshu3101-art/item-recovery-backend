package com.mehra.foundit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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

        return userRepository.findByUniqueToken(token).map(owner -> {
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
                        .body("Error sending email: " + e.getMessage());
            }
        }).orElse(ResponseEntity.status(404).body("Token not recognized."));
    }
}
