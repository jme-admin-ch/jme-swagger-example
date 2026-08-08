package ch.admin.bit.jeap.jme.swagger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

/**
 * Entry point of the example service. On startup it logs the URL of the Swagger UI. That URL opens with the internal
 * "Messaging API" definition selected, because of {@code springdoc.swagger-ui.urls-primary-name} in application.yml,
 * see {@link ch.admin.bit.jeap.jme.swagger.messages.InternalApiSwaggerConfig}.
 */
@SpringBootApplication
@Slf4j
public class Application {
    static void main(String[] args) {

        Environment env = SpringApplication.run(Application.class, args).getEnvironment();

        log.info("""
                        
                        ----------------------------------------------------------
                        \t\
                        {} is running!\s
                        \t
                        \tSwaggerUI: \thttp://localhost:{}{}/swagger-ui.html
                        \t\
                        Profile(s): \t{}
                        ----------------------------------------------------------""",
                env.getProperty("spring.application.name"),
                env.getProperty("server.port"),
                env.getProperty("server.servlet.context-path"),
                env.getActiveProfiles());
    }
}
