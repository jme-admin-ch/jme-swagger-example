package ch.admin.bit.jeap.jme.swagger.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;

/**
 * Smoke test against an already running example, in contrast to {@link SwaggerExampleIT} which starts the services
 * itself.
 * <p>
 * Start jme-swagger-auth-scs and jme-swagger-service as described in the README, then run
 * {@code ./mvnw verify -pl jme-swagger-test -DdeployStage=local -Dit.test=AfterDeploymentSmokeTestIT}. Without the
 * system property {@code deployStage} the test is skipped, because there is nothing running to check.
 */
@EnabledIfSystemProperty(named = "deployStage", matches = "local")
class AfterDeploymentSmokeTestIT {

    private static final String SERVICE_BASE_URL =
            System.getProperty("serviceBaseUrl", "http://localhost:8080/jme-swagger-service");
    private static final String AUTHORIZATION_SERVER_BASE_URL =
            System.getProperty("authorizationServerBaseUrl", "http://localhost:8081/jme-swagger-auth-scs");
    private static final String CLIENT_ID = "jme-swagger-tester";

    @Test
    @DisplayName("The deployed example serves its API documentation and accepts tokens from its Swagger UI login")
    void swaggerUiIsOpenAndApiAcceptsTokenFromSwaggerUiLogin() {
        SwaggerSmokeChecks checks = new SwaggerSmokeChecks(SERVICE_BASE_URL);

        // Fail if the authorization server is not up (e.g. the developer forgot to start it).
        SwaggerSmokeChecks.waitUntilAuthorizationServerReady(AUTHORIZATION_SERVER_BASE_URL, Duration.ofSeconds(5));

        checks.checkSwaggerUiAccessible();
        checks.checkApiDocs("Messaging API", "External Messaging API");
        checks.checkOpenApiContract();
        checks.checkSwaggerConfig();
        checks.checkSwaggerInitializerJs(CLIENT_ID);
        checks.checkApiRequiresToken();

        String accessToken = checks.loginWithAuthorizationCodePkce(AUTHORIZATION_SERVER_BASE_URL, CLIENT_ID);
        checks.checkApiCanBeCalledWithToken(accessToken);
    }
}
