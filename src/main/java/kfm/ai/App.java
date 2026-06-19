package kfm.ai;

import kfm.ai.dao.impl.CustomizedSimpleJpaRepositoryImpl;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Hello world!
 */
@SpringBootApplication//(exclude = ContextRegionProviderAutoConfiguration.class)
@EnableJpaRepositories(repositoryBaseClass = CustomizedSimpleJpaRepositoryImpl.class)
@ConfigurationPropertiesScan
@Slf4j
public class App {
    public static void main(String[] args) {
//        log.info("kfm-ai::main() - enter");
        SpringApplication.run(App.class, args);
//        log.info("kfm-ai::main() - exit");
    }
}
