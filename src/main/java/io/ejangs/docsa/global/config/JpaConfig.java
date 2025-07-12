package io.ejangs.docsa.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = {
        "io.ejangs.docsa.domain.branch.dao.mysql",
        "io.ejangs.docsa.domain.commit.dao.mysql",
        "io.ejangs.docsa.domain.doc.dao.mysql",
        "io.ejangs.docsa.domain.save.dao.mysql",
        "io.ejangs.docsa.domain.user.dao.mysql"
})
public class JpaConfig {
}
