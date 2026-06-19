package kfm.ai.ingest;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MySQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestContainerConfig {

    @Bean
    public MySQLContainer<?> mysqlContainer() {
        MySQLContainer<?> container = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("kfm_test")
                .withUsername("test")
                .withPassword("test")
                .withCommand("--character-set-server=latin1", "--collation-server=latin1_general_ci");
        container.start();
        return container;
    }

    @Bean
    public DynamicPropertyRegistrar mysqlPropertyRegistrar(MySQLContainer<?> container) {
        return registry -> {
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            registry.add("spring.ai.ollama.base-url", () -> "http://localhost:11434");
        };
    }
}
