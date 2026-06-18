package kfm.ai.parser;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Spring context smoke test verifying that {@link SetlistParser} can be created
 * as a Spring bean without requiring external dependencies (DB, Ollama).
 *
 * <p>Uses a minimal {@link AnnotationConfigApplicationContext} containing only the
 * parser class itself, since it has no constructor parameters or injected collaborators.
 * This avoids bootstrapping the full Spring Boot autoconfiguration (which would
 * require DB and Ollama) while still proving that the {@code @Component} annotation
 * and bean wiring work correctly.</p>
 *
 * <p>Equivalent to a {@code @SpringBootTest(webEnvironment = NONE, classes = {SetlistParser.class})}
 * but avoids a JUnit/Spring version compatibility issue in the current test classpath.</p>
 */
class SetlistParserContextTest {

    @Test
    void setlistParserBeanIsCreatedSuccessfully() {
        try (var context = new AnnotationConfigApplicationContext(SetlistParser.class)) {
            SetlistParser parser = context.getBean(SetlistParser.class);
            assertNotNull(parser,
                    "SetlistParser bean should be created without DB or Ollama");
            assertInstanceOf(SetlistParser.class, parser);
        }
    }
}
