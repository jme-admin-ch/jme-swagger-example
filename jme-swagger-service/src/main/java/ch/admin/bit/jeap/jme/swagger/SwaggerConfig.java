package ch.admin.bit.jeap.jme.swagger;

import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Configuration;

/**
 * Every service declares exactly one {@code @OpenAPIDefinition}. It holds the documentation that is common to all APIs
 * of the service: title, description, version, contact and a link to further documentation.
 * <p>
 * The APIs themselves are not declared here. For each API a service offers, a bean of type {@link GroupedOpenApi} is
 * created and Swagger UI then offers one selectable API definition per group. This example defines two of them, see
 * {@link ch.admin.bit.jeap.jme.swagger.messages.InternalApiSwaggerConfig} and
 * {@link ch.admin.bit.jeap.jme.swagger.external.ExternalApiSwaggerConfig}.
 * <p>
 * The security requirement refers to the security scheme named "OIDC" that the jEAP swagger starter registers as soon
 * as an authorization server is configured. Declaring it on the definition marks every operation of this service as
 * protected and makes the "Authorize" button of Swagger UI acquire a token from that authorization server.
 * <p>
 * <b>About {@code version}:</b> the OpenAPI specification requires it in every document, and it means the version of
 * the API this document describes as a whole - not the version of an individual operation. The messages API is at its
 * second major version, which is why {@code 2.0.0} is declared here; that the deprecated first version is still part
 * of the same document is visible in the paths and tags of the operations ({@code /api/messages} for V1,
 * {@code /api/messages/v2} for V2). An API that is versioned on a lifecycle of its own gets its own version through
 * its group, see {@link ch.admin.bit.jeap.jme.swagger.external.ExternalApiSwaggerConfig}.
 * <p>
 * See the <a href="https://jeap-admin-ch.github.io/docs/building-blocks/spring-boot-starters/jeap-spring-boot-starters/jeap-spring-boot-swagger-starter">jEAP
 * Swagger starter documentation</a> for the jEAP specific parts and <a href="https://springdoc.org">springdoc.org</a>
 * for the springdoc reference documentation.
 */
@OpenAPIDefinition(
        info = @Info(
                title = "JME Swagger Example",
                description = "An example of how to document REST APIs of a jEAP microservice with springdoc-openapi",
                version = "2.0.0",
                contact = @Contact(
                        email = "jeap-community@bit.admin.ch",
                        name = "jEAP Community",
                        url = "https://jeap-admin-ch.github.io/"
                )
        ),
        externalDocs = @ExternalDocumentation(
                url = "https://jeap-admin-ch.github.io/docs/building-blocks/spring-boot-starters/jeap-spring-boot-starters/jeap-spring-boot-swagger-starter",
                description = "jEAP Swagger starter documentation"),
        security = {@SecurityRequirement(name = "OIDC")}
)
@Configuration
public class SwaggerConfig {
}
