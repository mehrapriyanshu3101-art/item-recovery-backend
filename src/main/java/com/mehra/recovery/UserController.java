package com.mehra.recovery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*") // Allows all for now to avoid connection issues
public class UserController {

    @Autowired
    private UserRepository userRepository;

    // 1. REGISTRATION
    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody User user) {
        String token = UUID.randomUUID().toString().substring(0, 8);
        user.setUniqueToken(token);
        userRepository.save(user);
        return ResponseEntity.ok(token);
    }

    // 2. LOGIN (The missing piece)
    @PostMapping("/login")
    public ResponseEntity<User> loginUser(@RequestBody User loginDetails) {
        return userRepository.findByEmail(loginDetails.getEmail())
                .filter(user -> user.getPassword().equals(loginDetails.getPassword()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(401).build());
    }

    // 3. FINDER LOOKUP
    @GetMapping("/find/{token}")
    public ResponseEntity<User> findByToken(@PathVariable String token) {
        return userRepository.findByUniqueToken(token)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}