package beer.sisipisi.formalda.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mariadb.MariaDBContainer;

public abstract class MariaDbIntegrationTest {
    protected static final MariaDBContainer DATABASE;

    static {
        DATABASE = new MariaDBContainer("mariadb:11.4")
                .withDatabaseName("formalda_test")
                .withUsername("formalda")
                .withPassword("formalda");
        DATABASE.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
        registry.add("formalda.bootstrap-admin.email", () -> "");
        registry.add("formalda.bootstrap-admin.username", () -> "");
        registry.add("formalda.bootstrap-admin.password", () -> "");
    }
}
