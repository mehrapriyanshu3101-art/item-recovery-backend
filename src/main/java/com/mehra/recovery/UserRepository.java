package com.mehra.recovery;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUniqueToken(String uniqueToken);
    Optional<User> findByEmail(String email);
}

