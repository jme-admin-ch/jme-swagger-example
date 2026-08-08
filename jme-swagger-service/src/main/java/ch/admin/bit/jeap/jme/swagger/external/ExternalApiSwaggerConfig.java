package ch.admin.bit.jeap.jme.swagger.external;

import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Definition of the API this service offers to external consumers (external API).
 * <p>
 * APIs are declared as a bean of type {@link GroupedOpenApi}, each with its own group name. See
 * {@link ch.admin.bit.jeap.jme.swagger.messages.InternalApiSwaggerConfig} for the internal counterpart.
 * <p>
 * Parts of the shared {@code @OpenAPIDefinition} do not fit an external audience. They must therefore be replaced with
 * an OpenAPI customizer for the group, as shown below for the contact information, the link to the external documentation
 * and the version of the document.
 */
@Configuration
public class ExternalApiSwaggerConfig {

    /** Version of the external API, independent of the version of the internal one. */
    private static final String EXTERNAL_API_VERSION = "1.0.0";

    @Bean
    GroupedOpenApi externalApi() {
        return GroupedOpenApi.builder()
                .group("External Messaging API")
                .pathsToMatch("/api/**")
                .packagesToScan(this.getClass().getPackageName())

                // Some parts of the shared OpenAPI definition must be overwritten for this external API
                .addOpenApiCustomizer(this::changeContactInfo)
                .addOpenApiCustomizer(this::changeVersion)
                .build();
    }

    private void changeContactInfo(OpenAPI openAPI) {
        // Title and version together identify the API a document describes, so an API of its own needs its own title.
        openAPI.getInfo().setTitle("JME Swagger Example - External Messaging API");
        openAPI.getInfo().getContact().setEmail("info@bit.admin.ch");
        openAPI.getInfo().getContact().setName("Federal Office of Information Technology, Systems and Telecommunication FOITT");
        openAPI.getInfo().getContact().setUrl("https://www.bit.admin.ch");
        openAPI.getExternalDocs().setDescription("jEAP on GitHub");
        openAPI.getExternalDocs().setUrl("https://github.com/jeap-admin-ch");
    }

    /**
     * The version of the shared definition is the one of the internal messages API, which is at 2.0.0 (see
     * {@link ch.admin.bit.jeap.jme.swagger.SwaggerConfig}). The external API is a different API with a different,
     * (usually much slower) lifecycle: it is still at its first version, and it stays there when the internal API is
     * versioned up again. Each API group must publish the version of the API it documents.
     */
    private void changeVersion(OpenAPI openAPI) {
        openAPI.getInfo().setVersion(EXTERNAL_API_VERSION);
    }
}
