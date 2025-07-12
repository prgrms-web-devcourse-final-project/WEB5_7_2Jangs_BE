package io.ejangs.docsa.domain.user.dao.mysql;

import io.ejangs.docsa.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);
}
