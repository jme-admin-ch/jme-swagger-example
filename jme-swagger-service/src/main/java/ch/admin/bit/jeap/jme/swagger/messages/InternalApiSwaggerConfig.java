package ch.admin.bit.jeap.jme.swagger.messages;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Definition of the internal API, the API this service offers to other applications of the same system.
 * <p>
 * Every API of a service is declared as a bean of type {@link GroupedOpenApi}. The group name is what Swagger UI shows
 * in its API selection box and it is also part of the URL of the generated OpenAPI document
 * (<i>/api-docs/{group}</i>). Which controllers and operations belong to a group is selected with
 * {@code packagesToScan(...)}, {@code packagesToExclude(...)}, {@code pathsToMatch(...)} and
 * {@code pathsToExclude(...)}; the documentation of a group can be adjusted with
 * {@code addOpenApiCustomizer(...)} as shown in
 * {@link ch.admin.bit.jeap.jme.swagger.external.ExternalApiSwaggerConfig}.
 * <p>
 * This group scans this package and therefore contains both versions of the messages API, see
 * {@link ch.admin.bit.jeap.jme.swagger.messages.v1.InternalApiController} and
 * {@link ch.admin.bit.jeap.jme.swagger.messages.v2.InternalApi2Controller}.
 */
@SuppressWarnings("deprecation")
@Configuration
public class InternalApiSwaggerConfig {

    @Bean
    GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("Messaging API")
                .pathsToMatch("/api/**")
                .packagesToScan(this.getClass().getPackageName())
                .build();
    }
}
