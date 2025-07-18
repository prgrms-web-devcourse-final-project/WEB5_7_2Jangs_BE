package io.ejangs.docsa.global.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = {
        "io.ejangs.docsa.domain.branch.dao.mysql",
        "io.ejangs.docsa.domain.commit.dao.mysql",
        "io.ejangs.docsa.domain.doc.dao.mysql",
        "io.ejangs.docsa.domain.save.dao.mysql",
        "io.ejangs.docsa.domain.user.dao.mysql",
        "io.ejangs.docsa.global.mongoDeleteSystem.dao.mysql"
})
public class JpaConfig {

    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
