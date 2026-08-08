package ch.admin.bit.jeap.jme.swagger.test;

import ch.admin.bit.jeap.jme.test.BootServiceIntegrationTestBase;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static io.restassured.RestAssured.given;
import static java.lang.Boolean.TRUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * End-to-end integration test for the swagger example. It starts the mock authorization server and three instances of
 * the example service - one per {@code jeap.swagger.status} the example demonstrates - and then verifies from the
 * outside what a developer following the README would see: an open Swagger UI, a Swagger UI behind basic
 * authentication, no Swagger UI at all, and an API that always needs an OAuth 2.0 access token.
 * <p>
 * The services are started on reserved free ports instead of the fixed ports of their {@code application-local.yml},
 * so the test does not conflict with manually started instances. Every URL the services use to refer to each other is
 * overridden accordingly.
 */
class SwaggerExampleIT extends BootServiceIntegrationTestBase {

    private static final String SERVICE_MODULE = "jme-swagger-service";
    private static final String AUTH_SERVER_MODULE = "jme-swagger-auth-scs";

    /** Client ID of the Swagger UI, see springdoc.swagger-ui.oauth.client-id in the service. */
    private static final String CLIENT_ID = "jme-swagger-tester";
    /** Default user name of the jEAP swagger starter for jeap.swagger.status: SECURED. */
    private static final String SWAGGER_UI_USER = "swagger";
    /** Password from application-secured.yml. */
    private static final String SWAGGER_UI_PASSWORD = "secret";

    private static final String ISSUER_PROPERTY = "jeap.security.oauth2.resourceserver.authorization-server.issuer";

    /** API definition an opened Swagger UI shows, see springdoc.swagger-ui.urls-primary-name in the service. */
    private static final String PRIMARY_API_GROUP = "Messaging API";
    /**
     * System property with which a build environment that is known to provide a browser demands the browser test to
     * run instead of being skipped. Set by the Jenkinsfile and by .github/workflows/build.yml.
     */
    private static final String REQUIRE_BROWSER_PROPERTY = "requireBrowser";

    private static final List<Integer> PORTS = reserveFreePorts(4);
    private static final int AUTH_SERVER_PORT = PORTS.getFirst();
    private static final int OPEN_PORT = PORTS.get(1);
    private static final int SECURED_PORT = PORTS.get(2);
    private static final int DISABLED_PORT = PORTS.get(3);

    private static final String AUTH_SERVER_BASE_URL = "http://localhost:" + AUTH_SERVER_PORT + "/jme-swagger-auth-scs";
    private static final String OPEN_BASE_URL = "http://localhost:" + OPEN_PORT + "/jme-swagger-service";
    private static final String SECURED_BASE_URL = "http://localhost:" + SECURED_PORT + "/jme-swagger-service";
    private static final String DISABLED_BASE_URL = "http://localhost:" + DISABLED_PORT + "/jme-swagger-service";

    private static final SwaggerSmokeChecks OPEN = new SwaggerSmokeChecks(OPEN_BASE_URL);
    private static final SwaggerSmokeChecks SECURED = new SwaggerSmokeChecks(SECURED_BASE_URL);
    private static final SwaggerSmokeChecks DISABLED = new SwaggerSmokeChecks(DISABLED_BASE_URL);

    @BeforeAll
    static void startServices() throws Exception {
        // The mock authorization server only accepts redirect URIs it has registered. Its registered URI is built
        // from service-url, which therefore has to point at the service instance whose Swagger UI logs in here -
        // otherwise the authorization request is rejected with an invalid redirect URI error.
        startService(AUTH_SERVER_MODULE, AUTH_SERVER_BASE_URL, Map.of(
                "server.port", String.valueOf(AUTH_SERVER_PORT),
                "service-url", OPEN_BASE_URL + "/"));

        // jeap.swagger.status: OPEN, as configured in application-local.yml. The JWK set URI is derived from the
        // issuer, so overriding the issuer is enough to point the service at the mock authorization server.
        startService(SERVICE_MODULE, OPEN_BASE_URL, Map.of(
                "server.port", String.valueOf(OPEN_PORT),
                ISSUER_PROPERTY, AUTH_SERVER_BASE_URL));

        // jeap.swagger.status: SECURED, as configured in application-secured.yml. That profile has to win over
        // application-local.yml, which sets the status to OPEN, so it has to be activated *after* the local profile.
        // spring.profiles.include would prepend it instead, and the maven plugin passes the local profile as the
        // command line argument --spring.profiles.active, which no system property can override. Adding the profile
        // to a group of the local profile expands it to "local,secured", exactly as on the command line.
        startService(SERVICE_MODULE, SECURED_BASE_URL, Map.of(
                "server.port", String.valueOf(SECURED_PORT),
                ISSUER_PROPERTY, AUTH_SERVER_BASE_URL,
                "spring.profiles.group.local", "secured"));

        // jeap.swagger.status: DISABLED, the default of the jEAP swagger starter. A system property is enough here,
        // it takes precedence over the value in application-local.yml.
        startService(SERVICE_MODULE, DISABLED_BASE_URL, Map.of(
                "server.port", String.valueOf(DISABLED_PORT),
                ISSUER_PROPERTY, AUTH_SERVER_BASE_URL,
                "jeap.swagger.status", "DISABLED"));

        // The services are up, but the authorization server publishes its discovery document only once it is fully
        // started. Everything that logs in below depends on it, so the same wait the deployment smoke tests of the
        // companion projects use is applied here as well.
        SwaggerSmokeChecks.waitUntilAuthorizationServerReady(AUTH_SERVER_BASE_URL);
    }

    @Test
    @DisplayName("With status OPEN, anyone can read the API documentation of the service")
    void openStatusServesSwaggerUiAndOpenApiDocumentsWithoutCredentials() {
        OPEN.checkSwaggerUiAccessible();
        OPEN.checkApiDocs("Messaging API", "External Messaging API");
    }

    @Test
    @DisplayName("The Authorize button of the Swagger UI is wired to the authorization server as a public client")
    void swaggerUiIsConfiguredForTheAuthorizationCodeFlowWithPkce() {
        // Without a matching redirect URL the login would end in an invalid redirect URI error, and without PKCE the
        // authorization server would reject the public client of the Swagger UI.
        OPEN.checkSwaggerConfig();
        OPEN.checkSwaggerInitializerJs(CLIENT_ID);
    }

    @Test
    @DisplayName("An opened Swagger UI shows the messaging API, not the alphabetically first definition")
    void swaggerUiPreselectsTheMessagingApi() {
        // Without springdoc.swagger-ui.urls-primary-name, Swagger UI would show the first of the alphabetically
        // sorted definitions - "Actuator" - and not the API this example and its README are about.
        String primaryName = given().baseUri(OPEN_BASE_URL)
                .when().get("/api-docs/swagger-config")
                .then().statusCode(200)
                .extract().jsonPath().getString("'urls.primaryName'");
        assertEquals(PRIMARY_API_GROUP, primaryName);
    }

    @Test
    @DisplayName("Each API group documents its own API, with its own identity and the OAuth 2.0 requirement")
    void apiGroupsDocumentWhatTheyAreConfiguredToDocument() {
        // Group boundaries, titles, versions and the OIDC security requirement. These hold on every environment, so
        // the after-deployment smoke tests of the companion projects run exactly the same check.
        OPEN.checkOpenApiContract();
    }

    @Test
    @DisplayName("The @Schema annotations of the DTOs reach the OpenAPI document")
    void dtoAnnotationsAreReflectedInTheSchema() {
        // What a consumer of the API sees, and what fills the request body Swagger UI prefills for "Try it out".
        // Checked on one representative DTO rather than on all of them - this verifies the configuration of this
        // example, not springdoc's annotation processing.
        Map<String, Object> document = OPEN.apiDocument(SwaggerSmokeChecks.INTERNAL_API_GROUP);
        Map<?, ?> message = (Map<?, ?>) schemas(document).get("Message2Dto");

        assertEquals(List.of("receiver", "text"), message.get("required"),
                "requiredMode = REQUIRED of the message content and its receiver");

        Map<?, ?> properties = (Map<?, ?>) message.get("properties");
        assertEquals(TRUE, property(properties, "id").get("readOnly"),
                "accessMode = READ_ONLY of the message id, which the server takes from the path");
        assertEquals(TRUE, property(properties, "timeSend").get("readOnly"),
                "accessMode = READ_ONLY of the send time, which the server sets");
        assertEquals("Hello World", property(properties, "text").get("example"),
                "the example that makes Try it out usable without typing");
    }

    @Test
    @DisplayName("The deprecated first version of the API is documented as deprecated")
    void deprecatedApiVersionIsMarkedDeprecated() {
        // @Deprecated on the controller and the DTO is what tells a consumer to migrate, and what makes the Swagger
        // UI strike the operations through. It is the visible half of the API versioning this example demonstrates.
        Map<String, Object> document = OPEN.apiDocument(SwaggerSmokeChecks.INTERNAL_API_GROUP);

        assertEquals(TRUE, operation(document, "/api/messages", "get").get("deprecated"),
                "the operations of the superseded version 1");
        assertNull(operation(document, "/api/messages/v2", "get").get("deprecated"),
                "the operations of the current version 2");
        assertEquals(TRUE, ((Map<?, ?>) schemas(document).get("MessageDto")).get("deprecated"),
                "the DTO of the superseded version 1");
    }

    private static Map<?, ?> schemas(Map<String, Object> document) {
        return (Map<?, ?>) ((Map<?, ?>) document.get("components")).get("schemas");
    }

    private static Map<?, ?> property(Map<?, ?> properties, String name) {
        return (Map<?, ?>) properties.get(name);
    }

    @SuppressWarnings("SameParameterValue")
    private static Map<?, ?> operation(Map<String, Object> document, String path, String method) {
        return (Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) document.get("paths")).get(path)).get(method);
    }

    @Test
    @DisplayName("Documenting the API does not expose it: calls without an access token are rejected")
    void documentedApiRejectsCallsWithoutAccessToken() {
        OPEN.checkApiRequiresToken();
    }

    @Test
    @DisplayName("A token obtained the way the Authorize button obtains it is accepted by the API")
    void apiAcceptsAnAccessTokenFromTheAuthorizationCodeFlowWithPkce() {
        // This is the scripted equivalent of pressing "Authorize" and logging in as the demo user: the roles the
        // login form preselects end up in the token, and they are the roles the API operations require.
        String accessToken = OPEN.loginWithAuthorizationCodePkce(AUTH_SERVER_BASE_URL, CLIENT_ID);
        OPEN.checkApiCanBeCalledWithToken(accessToken);
    }

    @Test
    @DisplayName("With status SECURED, the API documentation is behind basic authentication")
    void securedStatusProtectsSwaggerUiWithBasicAuth() {
        SECURED.checkSecuredRequiresBasicAuth(SWAGGER_UI_USER, SWAGGER_UI_PASSWORD);
        // SECURED protects the documentation only. The API keeps its OAuth 2.0 protection, it is not reachable with
        // the basic authentication credentials of the Swagger UI.
        SECURED.checkApiRequiresToken();
    }

    @Test
    @DisplayName("With status DISABLED, there is no API documentation at all")
    void disabledStatusHidesSwaggerUiFromEveryone() {
        DISABLED.checkSwaggerDisabled();
    }

    /**
     * The same OAuth 2.0 login as
     * {@link SwaggerExampleIT#apiAcceptsAnAccessTokenFromTheAuthorizationCodeFlowWithPkce()}, but driven through a
     * real browser: only a browser proves that a user can actually press "Authorize", log in, and then call a
     * protected operation with "Try it out".
     * <p>
     * The test uses the system installation of Google Chrome through the Playwright "chrome" channel instead of
     * downloading a browser. Without such an installation there is nothing to drive, and the test is skipped.
     */
    @Nested
    class SwaggerUiPlaywrightIT {

        @Test
        @DisplayName("A user can authorize in the Swagger UI and call a protected operation with Try it out")
        void authorizeInTheSwaggerUiAndCallAProtectedOperation() {
            try (Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                    .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")))) {
                Browser browser = launchSystemChrome(playwright);
                Page page = browser.newPage();
                // Exactly the URL the README tells the reader to open, without any query parameter: it redirects to
                // /swagger-ui/index.html and has to come up with the messaging API selected. If the preselection
                // broke, the operation this test executes further down would not be on the page.
                page.navigate(OPEN_BASE_URL + "/swagger-ui.html");

                // Press "Authorize". The dialog offers one section per grant type the authorization server
                // advertises, and the authorization code flow with PKCE is the one this example is configured for.
                page.click("button.authorize");
                Locator authorizeSection = page.locator("div.auth-container")
                        .filter(new Locator.FilterOptions().setHasText("authorization_code with PKCE"));
                assertThat(authorizeSection.locator("#client_id_authorization_code")).hasValue(CLIENT_ID);

                // Authorizing opens the login page of the mock authorization server in a popup. Submitting it sends
                // the browser back to the redirect endpoint of the Swagger UI, which hands the token to the UI and
                // closes the popup.
                Page login = page.waitForPopup(() -> authorizeSection.locator("button.authorize").click());
                login.waitForSelector("#submit-button");
                login.click("#submit-button");
                page.waitForCondition(login::isClosed);
                assertThat(authorizeSection.locator("button.authorize")).hasText("Logout");
                authorizeSection.locator("button.btn-done").click();

                // Call a protected operation with the token the UI just acquired. Its padlock is closed now, and
                // "Execute" answers 200 instead of the 401 an unauthorized call would get.
                Locator readAllMessages = page.locator(
                        "div.opblock:has(span.opblock-summary-path[data-path='/api/messages/v2'])");
                readAllMessages.locator("button.opblock-summary-control").click();
                assertThat(readAllMessages.locator("button.authorization__btn svg")).hasClass("locked");
                Locator tryItOut = readAllMessages.locator("button.try-out__btn");
                if (!"Cancel".equals(tryItOut.textContent().trim())) {
                    // "Try it out" is already active in this example (springdoc.swagger-ui.try-it-out-enabled)
                    tryItOut.click();
                }
                readAllMessages.locator("button.execute").click();
                assertThat(readAllMessages.locator("table.live-responses-table tbody td.response-col_status"))
                        .hasText("200");

                browser.close();
            }
        }

        /**
         * Launches the system installation of Google Chrome. Not every developer machine has one, so by default the
         * test is skipped when there is none. Where a browser is part of the build environment - the Jenkins build
         * image and the GitHub Actions runner, both of which pass {@code -DrequireBrowser=true} - a missing browser
         * has to fail the build instead: it would silently remove the only proof that the Swagger UI can be used.
         */
        private Browser launchSystemChrome(Playwright playwright) {
            try {
                return playwright.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome"));
            } catch (PlaywrightException e) {
                if (Boolean.getBoolean(REQUIRE_BROWSER_PROPERTY)) {
                    throw new IllegalStateException("No system installation of Google Chrome found, although "
                            + REQUIRE_BROWSER_PROPERTY + " is set", e);
                }
                return Assumptions.abort(
                        "Skipping the browser test, no system installation of Google Chrome found: " + e.getMessage());
            }
        }
    }
}
