package kfm.ai.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.ollama.OllamaContainer;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    public OllamaContainer ollamaContainer() {
        return new OllamaContainer("ollama/ollama:0.5.4");
    }

    @Bean
    public DynamicPropertyRegistrar dynamicPropertyRegistrar(OllamaContainer ollamaContainer) {
        return registry -> {
            registry.add("spring.ai.ollama.base-url", ollamaContainer::getEndpoint);
        };
    }
}
