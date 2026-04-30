package com.mehra.foundit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // This allows UserController to find owners via QR token
    Optional<User> findByUniqueToken(String uniqueToken);
}
