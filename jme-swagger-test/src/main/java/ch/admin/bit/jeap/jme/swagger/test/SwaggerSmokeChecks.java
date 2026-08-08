package ch.admin.bit.jeap.jme.swagger.test;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.awaitility.core.ConditionTimeoutException;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Checks that verify a running instance of jme-swagger-service from the outside, through its HTTP interface only.
 * <p>
 * The checks live in {@code src/main/java} and are published as part of the {@code jme-swagger-test} artifact.
 * A failed check throws an {@link AssertionError}, a check that cannot be carried out at all throws an
 * {@link IllegalStateException}.
 * <p>
 * All checks are relative to the base URL of one service instance, including its context path, for example
 * {@code http://localhost:8080/jme-swagger-service}.
 *
 * @see <a href="https://rest-assured.io">REST Assured</a>
 */
@SuppressWarnings("SameParameterValue")
public final class SwaggerSmokeChecks {

    /**
     * Path of an endpoint of the documented API. It is protected with OAuth 2.0 in every {@code jeap.swagger.status},
     * which is what the API checks below rely on.
     */
    private static final String PROTECTED_API_PATH = "/api/messages/v2";

    /** The two APIs of the example, as named by its {@code GroupedOpenApi} beans. */
    public static final String INTERNAL_API_GROUP = "Messaging API";
    public static final String EXTERNAL_API_GROUP = "External Messaging API";

    private static final String INTERNAL_API_TITLE = "JME Swagger Example";
    private static final String INTERNAL_API_VERSION = "2.0.0";
    private static final String EXTERNAL_API_TITLE = "JME Swagger Example - External Messaging API";
    private static final String EXTERNAL_API_VERSION = "1.0.0";

    /** One representative path per API, enough to tell the two groups apart. */
    private static final String V1_API_PATH = "/api/messages";
    private static final String V2_API_PATH = "/api/messages/v2";
    private static final String EXTERNAL_API_PATH = "/api/messages/mine";

    /** Name of the security scheme the jEAP swagger starter registers for the authorization server of the service. */
    private static final String OIDC_SECURITY_SCHEME = "OIDC";

    private static final String SWAGGER_UI_PATH = "/swagger-ui/index.html";
    private static final String SWAGGER_INITIALIZER_PATH = "/swagger-ui/swagger-initializer.js";
    private static final String OAUTH2_REDIRECT_PATH = "/swagger-ui/oauth2-redirect.html";
    private static final String API_DOCS_PATH = "/api-docs";
    private static final String SWAGGER_CONFIG_PATH = "/api-docs/swagger-config";

    /** OpenID Connect discovery document of an authorization server, relative to its base URL. */
    private static final String OPENID_CONFIGURATION_PATH = "/.well-known/openid-configuration";
    /** How long {@link #waitUntilAuthorizationServerReady(String, Duration)} waits unless told otherwise. */
    private static final Duration DEFAULT_READINESS_TIMEOUT = Duration.ofMinutes(2);
    /** How long to wait between two attempts to reach the authorization server. */
    private static final Duration READINESS_POLL_INTERVAL = Duration.ofSeconds(2);

    /** Login form of the jEAP OAuth mock server, relative to its base URL. */
    private static final String MOCK_SERVER_LOGIN_PATH = "/openIdMockServerLogin";
    /** The user the login form of the mock server preselects, and the fixed password it accepts for everyone. */
    private static final String MOCK_SERVER_USER = "user";
    private static final String MOCK_SERVER_PASSWORD = "secret";

    private final String baseUrl;

    /**
     * Creates the checks for one instance of the service.
     *
     * @param baseUrl base URL of the service instance to check, including its context path, for example
     *                {@code http://localhost:8080/jme-swagger-service}
     */
    public SwaggerSmokeChecks(String baseUrl) {
        this.baseUrl = withoutTrailingSlash(baseUrl);
    }

    /**
     * Waits until the given authorization server serves its OpenID Connect discovery document, waiting at most
     * DEFAULT_READINESS_TIMEOUT minutes.
     *
     * @param authorizationServerBaseUrl base URL of the authorization server, including its context path
     * @see #waitUntilAuthorizationServerReady(String, Duration)
     */
    public static void waitUntilAuthorizationServerReady(String authorizationServerBaseUrl) {
        waitUntilAuthorizationServerReady(authorizationServerBaseUrl, DEFAULT_READINESS_TIMEOUT);
    }

    /**
     * Waits until the given authorization server serves its OpenID Connect discovery document, by polling
     * {@code /.well-known/openid-configuration} until it answers with 200 and an {@code issuer}.
     * <p>
     * A smoke test that logs in has to be able to rely on the authorization server, but the authorization server is
     * usually deployed as a service of its own: after a deployment it can still be rolling out while the smoke test of
     * the service it issues tokens for already runs. Waiting for it turns that race into a wait instead of a failed
     * deployment verification.
     *
     * @param authorizationServerBaseUrl base URL of the authorization server, including its context path, for example
     *                                   {@code http://localhost:8081/jme-swagger-auth-scs}
     * @param timeout                    how long to wait; the first attempt is made immediately
     * @throws IllegalStateException if the authorization server is still not ready when the timeout has passed
     */
    public static void waitUntilAuthorizationServerReady(String authorizationServerBaseUrl, Duration timeout) {
        String discoveryUrl = withoutTrailingSlash(authorizationServerBaseUrl) + OPENID_CONFIGURATION_PATH;
        // Awaitility polls on a thread of its own, so the reason of the last attempt is handed back through a
        // reference: it turns the timeout into a message that says why the server was not ready.
        AtomicReference<String> lastAttempt = new AtomicReference<>("did not answer");
        try {
            await().atMost(timeout)
                    .pollDelay(Duration.ZERO)
                    .pollInterval(READINESS_POLL_INTERVAL)
                    .until(() -> servesDiscoveryDocument(discoveryUrl, lastAttempt));
        } catch (ConditionTimeoutException e) {
            throw new IllegalStateException("The authorization server did not become ready within " + timeout + ": "
                    + discoveryUrl + " " + lastAttempt.get(), e);
        }
    }

    /**
     * One attempt to read the discovery document, recording in {@code lastAttempt} why it did not answer as expected.
     */
    private static boolean servesDiscoveryDocument(String discoveryUrl, AtomicReference<String> lastAttempt) {
        try {
            Response response = given().when().get(discoveryUrl);
            if (response.getStatusCode() == 200 && response.jsonPath().getString("issuer") != null) {
                return true;
            }
            lastAttempt.set("answered with status " + response.getStatusCode());
        } catch (Exception e) {
            // A server that is not up yet refuses the connection, and REST Assured lets that checked
            // java.net.ConnectException through - which is the normal case here, not an error.
            lastAttempt.set("was not reachable: " + e);
        }
        return false;
    }

    /**
     * Checks that the Swagger UI is served, which is the case for {@code jeap.swagger.status} OPEN and, with valid
     * credentials, SECURED.
     */
    public void checkSwaggerUiAccessible() {
        given().baseUri(baseUrl)
                .when().get(SWAGGER_UI_PATH)
                .then().statusCode(200)
                .body(containsString("swagger-ui"));
    }

    /**
     * Checks that the generated OpenAPI documents are served: the default document plus one document per
     * {@code GroupedOpenApi} bean of the service.
     * <p>
     * Besides being served, every document has to carry the two fields the OpenAPI specification requires of it: the
     * version of the specification it follows ({@code openapi}) and the version of the document itself
     * ({@code info.version}). The latter is easily lost, because springdoc emits a document without it instead of
     * failing.
     *
     * @param apiGroups names of the API groups that have to be documented, for example {@code "Messaging API"}
     */
    public void checkApiDocs(String... apiGroups) {
        given().baseUri(baseUrl)
                .when().get(API_DOCS_PATH)
                .then().statusCode(200)
                .body("openapi", notNullValue())
                .body("info.version", notNullValue());

        for (String apiGroup : apiGroups) {
            given().baseUri(baseUrl)
                    .when().get(API_DOCS_PATH + "/{group}", apiGroup)
                    .then().statusCode(200)
                    .body("openapi", notNullValue())
                    .body("info.version", notNullValue());
        }
    }

    /**
     * Checks what the annotations and the {@code GroupedOpenApi} beans of this example are supposed to produce: that
     * each API group documents the API it is meant to document, and that the operations are marked as protected.
     * <p>
     * These are the assertions that hold on every environment, which is why they are part of the published checks and
     * are used by the after-deployment smoke tests as well. Details that can only change together with the source
     * code - which fields of a DTO are required or read-only, which operations are deprecated - are verified once in
     * the build of this example instead, see its integration tests.
     * <p>
     * Deliberately not asserted: everything that is springdoc's business rather than this example's configuration.
     */
    public void checkOpenApiContract() {
        // The internal API documents both versions of the messages API and nothing of the external one, ...
        Map<String, Object> internal = apiDocument(INTERNAL_API_GROUP);
        checkIdentity(internal, INTERNAL_API_GROUP, INTERNAL_API_TITLE, INTERNAL_API_VERSION);
        checkPaths(internal, INTERNAL_API_GROUP, List.of(V1_API_PATH, V2_API_PATH), List.of(EXTERNAL_API_PATH));
        checkOidcSecurityRequirement(internal, INTERNAL_API_GROUP);

        // ... while the external API documents only the reduced interface for external consumers. Both are selected
        // by package in the GroupedOpenApi beans, so a controller in the wrong package would show up here.
        Map<String, Object> external = apiDocument(EXTERNAL_API_GROUP);
        checkIdentity(external, EXTERNAL_API_GROUP, EXTERNAL_API_TITLE, EXTERNAL_API_VERSION);
        checkPaths(external, EXTERNAL_API_GROUP, List.of(EXTERNAL_API_PATH), List.of(V1_API_PATH, V2_API_PATH));
        checkOidcSecurityRequirement(external, EXTERNAL_API_GROUP);
    }

    /**
     * Title and version identify the API a document describes. Both are set by this example: the title in the shared
     * {@code @OpenAPIDefinition}, the version there and, for the external API, in the customizer of its group.
     */
    private static void checkIdentity(Map<String, Object> document, String apiGroup, String title, String version) {
        Map<?, ?> info = (Map<?, ?>) document.get("info");
        if (!title.equals(info.get("title")) || !version.equals(info.get("version"))) {
            throw new AssertionError("Expected API group '" + apiGroup + "' to be documented as '" + title
                    + "' version " + version + " but it was '" + info.get("title") + "' version " + info.get("version"));
        }
    }

    private static void checkPaths(Map<String, Object> document, String apiGroup,
                                   List<String> expected, List<String> notExpected) {
        Map<?, ?> paths = (Map<?, ?>) document.get("paths");
        for (String path : expected) {
            if (!paths.containsKey(path)) {
                throw new AssertionError("Expected API group '" + apiGroup + "' to document " + path
                        + " but it documents " + paths.keySet());
            }
        }
        for (String path : notExpected) {
            if (paths.containsKey(path)) {
                throw new AssertionError("Expected API group '" + apiGroup + "' not to document " + path
                        + " but it documents " + paths.keySet());
            }
        }
    }

    /**
     * The starter registers the {@code OIDC} security scheme as soon as an authorization server is configured, and the
     * example requires it for all operations. Without both, the Swagger UI shows no "Authorize" button and sends no
     * access token, even though the API keeps rejecting requests without one.
     */
    private static void checkOidcSecurityRequirement(Map<String, Object> document, String apiGroup) {
        Map<?, ?> components = (Map<?, ?>) document.get("components");
        Map<?, ?> schemes = components == null ? Map.of() : (Map<?, ?>) components.get("securitySchemes");
        Map<?, ?> oidc = (Map<?, ?>) schemes.get(OIDC_SECURITY_SCHEME);
        if (oidc == null || !"openIdConnect".equals(oidc.get("type"))) {
            throw new AssertionError("Expected API group '" + apiGroup + "' to declare the security scheme '"
                    + OIDC_SECURITY_SCHEME + "' of type openIdConnect but the schemes were " + schemes.keySet());
        }

        List<?> security = (List<?>) document.get("security");
        boolean required = security != null && security.stream()
                .anyMatch(requirement -> ((Map<?, ?>) requirement).containsKey(OIDC_SECURITY_SCHEME));
        if (!required) {
            throw new AssertionError("Expected API group '" + apiGroup + "' to require the security scheme '"
                    + OIDC_SECURITY_SCHEME + "' for all operations but its security requirement was " + security);
        }
    }

    /**
     * The OpenAPI document of an API group, as the Swagger UI and any other consumer reads it.
     *
     * @param apiGroup name of the group, for example {@code "Messaging API"}
     */
    public Map<String, Object> apiDocument(String apiGroup) {
        return given().baseUri(baseUrl)
                .when().get(API_DOCS_PATH + "/{group}", apiGroup)
                .then().statusCode(200)
                .extract().jsonPath().getMap("$");
    }

    /**
     * Checks the configuration document the Swagger UI loads on startup. Its {@code oauth2RedirectUrl} is the URL the
     * authorization server redirects to after a successful login, so it has to point back at the redirect endpoint of
     * this Swagger UI - if it does not, the "Authorize" button ends in an invalid redirect URI error.
     */
    public void checkSwaggerConfig() {
        String oauth2RedirectUrl = given().baseUri(baseUrl)
                .when().get(SWAGGER_CONFIG_PATH)
                .then().statusCode(200)
                .extract().path("oauth2RedirectUrl");

        if (oauth2RedirectUrl == null || !oauth2RedirectUrl.endsWith(OAUTH2_REDIRECT_PATH)) {
            throw new AssertionError("Expected oauth2RedirectUrl of " + baseUrl + " to end with " + OAUTH2_REDIRECT_PATH
                    + " but it was " + oauth2RedirectUrl);
        }
    }

    /**
     * Checks the initializer script of the Swagger UI. The OAuth 2.0 client of the UI is configured there and not in
     * the configuration document: the client ID has to be the one registered at the authorization server, and PKCE has
     * to be enabled because the UI is a public client that cannot keep a client secret.
     *
     * @param expectedClientId the client ID configured with {@code springdoc.swagger-ui.oauth.client-id}
     */
    public void checkSwaggerInitializerJs(String expectedClientId) {
        given().baseUri(baseUrl)
                .when().get(SWAGGER_INITIALIZER_PATH)
                .then().statusCode(200)
                .body(containsString("\"clientId\":\"" + expectedClientId + "\""))
                .body(containsString("\"usePkceWithAuthorizationCodeGrant\":true"));
    }

    /**
     * Checks that the documented API itself is protected: without an access token it answers 401, whatever the
     * {@code jeap.swagger.status} of the service is. Documenting an API does not expose it.
     */
    public void checkApiRequiresToken() {
        given().baseUri(baseUrl)
                .when().get(PROTECTED_API_PATH)
                .then().statusCode(401);
    }

    /**
     * Checks that the API can be called with an access token issued by the authorization server of the service, and
     * that the roles in that token are enough for a read operation.
     *
     * @param accessToken access token, for example from {@link #loginWithAuthorizationCodePkce(String, String)}
     */
    public void checkApiCanBeCalledWithToken(String accessToken) {
        given().baseUri(baseUrl)
                .header("Authorization", "Bearer " + accessToken)
                .when().get(PROTECTED_API_PATH)
                .then().statusCode(200);
    }

    /**
     * Checks {@code jeap.swagger.status: SECURED}: the Swagger UI and the OpenAPI documents answer 401 without
     * credentials and are served with the configured basic authentication credentials.
     *
     * @param username user name of the Swagger UI, {@code swagger} unless configured otherwise
     * @param password password configured with {@code jeap.swagger.secured.password}
     */
    public void checkSecuredRequiresBasicAuth(String username, String password) {
        given().baseUri(baseUrl)
                .when().get(SWAGGER_UI_PATH)
                .then().statusCode(401);
        given().baseUri(baseUrl)
                .when().get(API_DOCS_PATH)
                .then().statusCode(401);

        given().baseUri(baseUrl).auth().preemptive().basic(username, password)
                .when().get(SWAGGER_UI_PATH)
                .then().statusCode(200);
        given().baseUri(baseUrl).auth().preemptive().basic(username, password)
                .when().get(API_DOCS_PATH)
                .then().statusCode(200);
    }

    /**
     * Checks {@code jeap.swagger.status: DISABLED}, the default of the jEAP swagger starter: the Swagger UI and the
     * OpenAPI documents are denied for everyone and answer 403. There is no way to authenticate for them.
     */
    public void checkSwaggerDisabled() {
        given().baseUri(baseUrl)
                .when().get(SWAGGER_UI_PATH)
                .then().statusCode(403);
        given().baseUri(baseUrl)
                .when().get(API_DOCS_PATH)
                .then().statusCode(403);
    }

    /**
     * Runs the authorization code flow with PKCE that the Swagger UI runs when a user presses "Authorize", but as a
     * sequence of HTTP requests instead of in a browser: request an authorization code, log in on the form of the jEAP
     * OAuth mock server, follow the redirect back to the redirect URI of the Swagger UI and exchange the code for an
     * access token.
     * <p>
     * The login form is submitted with the roles it preselects, which is what a browser would send as well. Those
     * roles are configured in the mock server (see {@code oauth-mock-data} in jme-swagger-auth-scs) and end up in the
     * access token.
     * <p>
     * Should this functionality also be required by another example or service, it should be moved to a specific
     * outh mock server test support library.
     * </p>
     *
     * @param authorizationServerBaseUrl base URL of the mock authorization server, including its context path
     * @param clientId                   client ID of the Swagger UI, registered at the mock authorization server
     * @return the access token
     */
    public String loginWithAuthorizationCodePkce(String authorizationServerBaseUrl, String clientId) {
        String codeVerifier = randomCodeVerifier();
        String redirectUri = baseUrl + OAUTH2_REDIRECT_PATH;
        Map<String, String> session = new HashMap<>();

        // 1. Request an authorization code. There is no session yet, so the authorization server remembers the
        // request and redirects to its login form. Only the code challenge is sent, never the verifier.
        String authorizationUrl = authorizationServerBaseUrl + "/oauth2/authorize";
        String loginUrl = requireRedirect(get(session)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid")
                .queryParam("code_challenge", codeChallengeOf(codeVerifier))
                .queryParam("code_challenge_method", "S256")
                .when().get(authorizationUrl), authorizationUrl, session);
        if (!loginUrl.contains(MOCK_SERVER_LOGIN_PATH)) {
            throw new IllegalStateException("Expected a redirect to the login form of the mock authorization server "
                    + "but got redirected to " + loginUrl);
        }

        // 2. Load the login form to find out which roles it preselects for the demo user.
        String loginPage = getRedirectTarget(session).when().get(loginUrl)
                .then().statusCode(200)
                .extract().asString();

        // 3. Submit the login form. The mock server has CSRF protection disabled, so no token has to be read from
        // the page. The two "additional" fields are always sent, even when empty, because the mock server reads them
        // unconditionally.
        RequestSpecification login = get(session)
                .formParam("username", MOCK_SERVER_USER)
                .formParam("password", MOCK_SERVER_PASSWORD)
                .formParam("additionaluserroles", "")
                .formParam("additionalbproles", "");
        for (String userRole : preselectedValues(loginPage, "userroles")) {
            login = login.formParam("userroles", userRole);
        }
        for (String businessPartnerRole : preselectedValues(loginPage, "bproles")) {
            login = login.formParam("bproles", businessPartnerRole);
        }
        String resumeUrl = requireRedirect(login.when().post(loginUrl), loginUrl, session);

        // 4. Resume the authorization request, now with an authenticated session. The authorization server issues the
        // code and redirects to the redirect URI of the Swagger UI.
        String redirect = followRedirect(session, resumeUrl);
        String code = queryParameter(redirect, "code");
        if (code == null) {
            throw new IllegalStateException("Expected an authorization code in the redirect to " + redirect);
        }

        // 5. Exchange the code for an access token. No client secret is sent: the Swagger UI is a public client and
        // proves instead that it is the same client that started the flow, by sending the PKCE code verifier.
        return given().baseUri(authorizationServerBaseUrl)
                .formParam("grant_type", "authorization_code")
                .formParam("client_id", clientId)
                .formParam("code_verifier", codeVerifier)
                .formParam("code", code)
                .formParam("redirect_uri", redirectUri)
                .when().post("/oauth2/token")
                .then().statusCode(200)
                .extract().path("access_token");
    }

    /**
     * Values of the checkboxes of the given form field that the login page preselects. A browser submits exactly
     * these, so the scripted login above does the same.
     */
    private static List<String> preselectedValues(String loginPage, String fieldName) {
        List<String> values = new ArrayList<>();
        Matcher inputs = Pattern.compile("<input[^>]*>", Pattern.DOTALL).matcher(loginPage);
        while (inputs.find()) {
            String input = inputs.group();
            if (input.contains("name=\"" + fieldName + "\"") && input.contains("checked")) {
                Matcher value = Pattern.compile("value=\"([^\"]*)\"").matcher(input);
                if (value.find()) {
                    values.add(value.group(1));
                }
            }
        }
        return values;
    }

    /**
     * A request that keeps the session of the flow and does not follow redirects: every redirect of the authorization
     * code flow is inspected here instead.
     */
    private static RequestSpecification get(Map<String, String> session) {
        return given().redirects().follow(false).cookies(session);
    }

    /**
     * A request for a URL a redirect pointed at. Such a URL is already URL encoded, so REST Assured must not encode
     * it a second time.
     */
    private static RequestSpecification getRedirectTarget(Map<String, String> session) {
        return get(session).urlEncodingEnabled(false);
    }

    /**
     * Performs a GET that has to answer with a redirect, remembers the cookies the response sets and returns the
     * location to go to next.
     */
    private static String followRedirect(Map<String, String> session, String url) {
        return requireRedirect(getRedirectTarget(session).when().get(url), url, session);
    }

    private static String requireRedirect(Response response, String requestUrl, Map<String, String> session) {
        session.putAll(response.getCookies());
        String location = response.getHeader("Location");
        if (response.getStatusCode() / 100 != 3 || location == null) {
            throw new IllegalStateException("Expected a redirect from " + requestUrl + " but got status "
                    + response.getStatusCode());
        }
        if (location.contains("error=")) {
            throw new IllegalStateException("The authorization server rejected the request: " + location);
        }
        // A Location header may be relative, so it is resolved against the URL that was requested, as a browser does.
        return URI.create(requestUrl).resolve(location).toString();
    }

    private static String queryParameter(String url, String name) {
        Matcher matcher = Pattern.compile("[?&]" + name + "=([^&]+)").matcher(url);
        return matcher.find() ? URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8) : null;
    }

    private static String withoutTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String randomCodeVerifier() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /** The PKCE code challenge of a code verifier: its SHA-256 hash, base64url encoded without padding. */
    private static String codeChallengeOf(String codeVerifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
