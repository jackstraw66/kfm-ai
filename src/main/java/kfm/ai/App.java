package kfm.ai;

import kfm.ai.dao.impl.CustomizedSimpleJpaRepositoryImpl;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main Spring Boot application entry point.
 */
@SpringBootApplication
@EnableJpaRepositories(repositoryBaseClass = CustomizedSimpleJpaRepositoryImpl.class)
@ConfigurationPropertiesScan
@Slf4j
public class App {

    /** Launches the Spring Boot application. */
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
