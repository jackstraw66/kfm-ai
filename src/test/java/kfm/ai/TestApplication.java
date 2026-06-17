package kfm.ai;

import org.springframework.boot.SpringApplication;
import org.testcontainers.utility.TestcontainersConfiguration;

public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.from(App::main)
          .with(TestcontainersConfiguration.class)
          .run(args);
    }
}
