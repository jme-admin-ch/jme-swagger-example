# JME Swagger Example

This example shows how a jEAP microservice documents its REST APIs and offers them for interactive use in a
**Swagger UI**: [springdoc-openapi](https://springdoc.org) generates OpenAPI documents from the controllers and DTOs of
the service, and the **jEAP Swagger starter** serves them, decides who may read them and connects the *Authorize*
button of the Swagger UI to the OAuth 2.0 authorization server of the service.

The example is self-contained: it brings its own authorization server, so the personal OAuth 2.0 login in the Swagger
UI and a call to a protected endpoint can be tried out on a developer machine without any infrastructure. Everything
below can be executed as it is written, starting from a fresh clone.

The example uses the following jEAP libraries:

* [jeap-spring-boot-swagger-starter](https://github.com/jeap-admin-ch/jeap-spring-boot-starters): springdoc-openapi and
  the auto configuration behind the `jeap.swagger.*` properties. This starter is what the example is about.
* [jeap-spring-boot-security-starter](https://github.com/jeap-admin-ch/jeap-spring-boot-starters): turns the service
  into an OAuth 2.0 resource server. It is not required by the Swagger starter, it is part of the example to show that
  an OAuth 2.0 protected API can be called from the Swagger UI.
* [jeap-spring-boot-monitoring-starter](https://github.com/jeap-admin-ch/jeap-spring-boot-starters): the actuator
  endpoints, which springdoc documents as an additional API group.
* [jeap-oauth-mock-server](https://github.com/jeap-admin-ch/jeap-oauth-mock-server): the authorization server of this
  example. It issues tokens to anyone who asks and must therefore never be used outside of development and test
  environments.

## Modules

* **jme-swagger-service**: The documented example service. It offers a small messages API in two versions and in two
  API groups, an internal and an external one, and it is the service whose Swagger UI you open below.
* **jme-swagger-auth-scs**: An instance of the jEAP OAuth mock server, acting as the authorization server of the
  example. It issues the access tokens that the Swagger UI obtains when a user presses *Authorize*.
* **jme-swagger-test**: The integration tests of the example, plus the reusable HTTP checks
  ([SwaggerSmokeChecks](jme-swagger-test/src/main/java/ch/admin/bit/jeap/jme/swagger/test/SwaggerSmokeChecks.java))
  they are built from.

## Prerequisites

To use this project, ensure you have the following installed:

1. **Java Development Kit (JDK)**: Version 25.
2. **Google Chrome** (optional): Only needed for the browser-based integration test, see
   [Integration tests](#integration-tests). Everything else works without it.

**Note:** Use the provided maven wrapper to build and run the project. It compiles what it needs, so no separate build
step is required for the walkthrough below.

## Getting started

The walkthrough starts the two services on the command line. In IntelliJ IDEA the same three starts are available as
the run configurations in [.run](.run): *jme-swagger-auth-scs [local]*, *jme-swagger-service [local]* and
*jme-swagger-service [local,secured]* (the last one for [Trying the SECURED variant locally](#trying-the-secured-variant-locally)).

### 1. Start the authorization server

In a first terminal:

```shell
./mvnw --projects jme-swagger-auth-scs spring-boot:run -Dspring-boot.run.profiles=local
```

It starts on port 8081 and logs the client and the user it has been configured with:

```
Base URL: http://localhost:8081/jme-swagger-auth-scs
Adding client from configuration: jme-swagger-tester
Adding User from configuration: user (Maxine Muster)
```

Both come from [application.yml](jme-swagger-auth-scs/src/main/resources/application.yml): `jme-swagger-tester` is the
OAuth 2.0 client of the Swagger UI, and `user` is the demo user you will log in as. The user has the roles
`jme_@swagger_#read` and `jme_@swagger_#write` for the business partner `12345`, which are exactly the roles the
operations of the example service require.

### 2. Start the example service

In a second terminal:

```shell
./mvnw --projects jme-swagger-service spring-boot:run -Dspring-boot.run.profiles=local
```

It starts on port 8080 and prints the URL of its Swagger UI:

```
----------------------------------------------------------
	jme-swagger-service is running!

	SwaggerUI: 	http://localhost:8080/jme-swagger-service/swagger-ui.html
	Profile(s): 	[local]
----------------------------------------------------------
```

### 3. Open the Swagger UI

Open <http://localhost:8080/jme-swagger-service/swagger-ui.html> in a browser. The URL redirects to
`/swagger-ui/index.html`, the Swagger UI of the service.

What you see is the `Messaging API`, the internal API of the service. The selection box at the top right lets you
choose another API definition; it lists them sorted by name, and the example serves three:

| API definition           | Contents                                                                                                        |
|--------------------------|-----------------------------------------------------------------------------------------------------------------|
| `Actuator`               | The actuator endpoints, documented automatically because `springdoc.show-actuator` is `true` in the jEAP starter |
| `External Messaging API` | The reduced API for external consumers: `/api/messages/mine`                                                     |
| `Messaging API`          | The internal API of the service: `/api/messages` (deprecated V1) and `/api/messages/v2`                          |

Swagger UI shows the first definition of that list ( which would be the `Actuator` group here) unless it is told
otherwise. The example sets `springdoc.swagger-ui.urls-primary-name: Messaging API` in [application.yml](jme-swagger-service/src/main/resources/application.yml)
and that definition is then the one that is pre-selected in the Swagger UI.

That the Swagger UI is accessible at all and without login is the effect of `jeap.swagger.status: OPEN` in
[application-local.yml](jme-swagger-service/src/main/resources/application-local.yml). Without it the service would use
the default of the starter, `DISABLED`, and answer `403` here — see
[What the jEAP Swagger starter does](#what-the-jeap-swagger-starter-does).

### 4. Check that the documentation is open but the API is not

While the documentation is public (in the local profile), the documented API is not. Executing one of the API's operations
before logging in will result in a `401` error.

### 5. Log in through the Swagger UI

Press the **Authorize** button at the top right of the Swagger UI. The dialog *Available authorizations* lists one
section per authentication scheme and, for OAuth 2.0, one section per grant type the authorization server announces:

`OIDC (OAuth2, authorization_code with PKCE)` is the one to use in this example,
for details see [Why the Authorize dialog offers client_credentials](#why-the-authorize-dialog-offers-client_credentials).

In the section `OIDC (OAuth2, authorization_code with PKCE)`:

1. `client_id` is already filled in with `jme-swagger-tester` (configured with `springdoc.swagger-ui.oauth.client-id`
   in [application.yml](jme-swagger-service/src/main/resources/application.yml)).
2. Leave `client_secret` empty. The Swagger UI is a public client here: it cannot keep a secret, and it proves instead
   with PKCE that it is the same client that started the login.
3. Tick the scope `openid`.
4. Press **Authorize**.

A window with the login form of the mock authorization server opens (*Login — OpenID Connect Mock Server*). It is
already filled in for you:

* *Username*: `user`, the only user of this example, named **Maxine Muster**.
* *Business Partner Roles*: `12345:jme_@swagger_#read` and `12345:jme_@swagger_#write` are ticked. These end up in the
  access token and are what the API operations check.
* *User Roles*: `jme_@swagger_#read` and `jme_@swagger_#write` are offered as well but are not ticked. They are the
  same roles without a business partner; the operations of this example authorize per business partner, so the token
  does not need them and leaving them unticked keeps visible which of the two the example uses.
* No password has to be typed, the mock server accepts a fixed one that the form sends for you.

Press **Submit**. The window closes, the button in the section now reads *Logout*, and the padlocks of the operations
are closed. Press **Close** to leave the dialog.

### 6. Try an operation out

The Swagger UI now sends the access token with every call. `springdoc.swagger-ui.tryItOutEnabled` is `true` in the jEAP
starter, so *Try it out* is already active and the input fields are editable right away.

Create a message: open `PUT /api/messages/v2/{messageId}` under the tag *Messages (V2)*, enter `1` as `messageId` and
press **Execute**. The request body is pre-filled with the `example` values of the `@Schema` annotations of
[Message2Dto](jme-swagger-service/src/main/java/ch/admin/bit/jeap/jme/swagger/messages/v2/Message2Dto.java); the
read-only fields `id` and `timeSend` are not part of it because the server sets them:

```json
{
  "text": "Hello World",
  "receiver": "12345"
}
```

The response is `201` with the stored message:

```json
{
  "id": "1",
  "text": "Hello World",
  "receiver": "12345",
  "timeSend": "2026-08-03T21:15:11.542229288+02:00"
}
```

Read it back with `GET /api/messages/v2` — it answers `200` with a list containing the message. Selecting
*External Messaging API* in the API selection box shows the same message through the reduced external interface,
`GET /api/messages/mine`; authorizing works there in exactly the same way.

To see the other side again, press **Authorize**, then **Logout**, and execute `GET /api/messages/v2` once more: it now
answers `401`.

### 7. Stop the services

Stop both terminals with `Ctrl+C`. The example keeps its messages in memory only, so nothing is left behind.

## What the jEAP Swagger starter does

Adding a single dependency to a service is enough to get everything shown above:

```xml
<dependency>
    <groupId>ch.admin.bit.jeap</groupId>
    <artifactId>jeap-spring-boot-swagger-starter</artifactId>
</dependency>
```

The starter pulls in springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`), applies the jEAP defaults for it, adds
a security filter chain for the Swagger paths and, if the service is a jEAP resource server, registers the OpenAPI
security scheme that makes the *Authorize* button work.

No version is given in the snippet: it is managed by the jEAP Spring Boot parent this example inherits from
(`ch.admin.bit.jeap:jeap-spring-boot-parent`, see the root [pom.xml](pom.xml)), which is where a jEAP service gets a
consistent set of versions from.

The starter itself is documented on two pages of the jEAP documentation, which go beyond what is shown here:

* [Swagger starter](https://jeap-admin-ch.github.io/docs/building-blocks/spring-boot-starters/jeap-spring-boot-starters/jeap-spring-boot-swagger-starter)
  — the reference of the starter and of its `jeap.swagger.*` properties.
* [Swagger / OpenAPI](https://jeap-admin-ch.github.io/docs/building-blocks/spring-boot-starters/jeap-spring-boot-starters/jeap-spring-boot-swagger)
  — how APIs are documented and published in jEAP.

### Who may read the documentation: `jeap.swagger.status`

The property `jeap.swagger.status` decides what happens with the Swagger UI and the generated OpenAPI documents:

| Status              | Effect                                                                                                     | Typical use                                          |
|---------------------|------------------------------------------------------------------------------------------------------------|------------------------------------------------------|
| `DISABLED`          | **Default.** Denied for everyone, the service answers `403`. There is no way to authenticate.               | Production environments                              |
| `OPEN`              | Served to everyone without authentication.                                                                   | Local development, development environments          |
| `SECURED`           | Served after HTTP basic authentication with the configured Swagger credentials.                              | Environments where the documentation stays internal  |
| `CUSTOM`            | The starter does not configure any security for the Swagger paths at all — the application does it itself.    | Special cases not covered by the three above         |


### The OAuth 2.0 login of the Swagger UI

As soon as `jeap.security.oauth2.resourceserver.authorization-server.issuer` is configured, the starter registers an
OpenAPI security scheme named `OIDC` of type `openIdConnect`, pointing at the discovery document of that authorization
server. A service marks its operations as protected by referring to that scheme, which this example does once for all
operations in
[SwaggerConfig](jme-swagger-service/src/main/java/ch/admin/bit/jeap/jme/swagger/SwaggerConfig.java):

```java
@OpenAPIDefinition(
        // ...
        security = {@SecurityRequirement(name = "OIDC")}
)
```

That is what puts the *Authorize* button into the Swagger UI, the padlocks onto the operations and the access token
into the requests of *Try it out*.

## Trying the SECURED variant locally

The profile [application-secured.yml](jme-swagger-service/src/main/resources/application-secured.yml) switches the
service to `jeap.swagger.status: SECURED` and sets a password. Start the service with it, in addition to the `local`
profile (the authorization server can keep running):

```shell
./mvnw --projects jme-swagger-service spring-boot:run -Dspring-boot.run.profiles=local,secured
```

Opening <http://localhost:8080/jme-swagger-service/swagger-ui.html> now makes the browser ask for credentials. Enter the
user `swagger` (the default of `jeap.swagger.secured.username`) and the password `secret` and the Swagger UI appears,
behaving exactly as before, personal OAuth 2.0 login included.

## How this example uses springdoc-openapi

Apart from the `jeap.swagger.*` properties, this example documents its API the standard springdoc way. The interesting
parts, in the order in which springdoc uses them:

* **One OpenAPI definition per service.**
  [SwaggerConfig](jme-swagger-service/src/main/java/ch/admin/bit/jeap/jme/swagger/SwaggerConfig.java) carries the
  `@OpenAPIDefinition` with the title, description, version, contact and external documentation link that all APIs of
  the service share, plus the security requirement described above.
* **One `GroupedOpenApi` bean per API.** 
  [InternalApiSwaggerConfig](jme-swagger-service/src/main/java/ch/admin/bit/jeap/jme/swagger/messages/InternalApiSwaggerConfig.java)
  and
  [ExternalApiSwaggerConfig](jme-swagger-service/src/main/java/ch/admin/bit/jeap/jme/swagger/external/ExternalApiSwaggerConfig.java)
  each declare one, selecting their operations by package and path. The group name is what the API selection box of the
  Swagger UI shows and what appears in the document URL `/api-docs/{group}` (URL encoded — the group names of this
  example contain a space, so the document is at `/api-docs/Messaging%20API`). The external group also shows how
  `addOpenApiCustomizer(...)` overrides parts of the shared definition — here the contact, the external documentation
  link and the version, all of which differ for an external audience.
* **API versioning.** The messages API exists twice: the deprecated
  [V1](jme-swagger-service/src/main/java/ch/admin/bit/jeap/jme/swagger/messages/v1/InternalApiController.java), whose
  message IDs must be UUIDs, and the current
  [V2](jme-swagger-service/src/main/java/ch/admin/bit/jeap/jme/swagger/messages/v2/InternalApi2Controller.java), whose
  IDs can be any string. Both live in the same API group under different paths, so consumers see the old and the new
  operations side by side and can migrate at their own pace. `@Deprecated` on the controller and on the DTO carries
  over into the OpenAPI document, and the Swagger UI strikes the deprecated operations through.
* **`info.version`.** The OpenAPI specification requires a version in every document. It is the version of the API the
  document describes as a whole, not of a single operation: the shared definition declares `2.0.0`, the current major
  version of the messages API, and that the deprecated V1 is still part of the same document is visible in its paths
  and tags. The external API is a different API with a lifecycle of its own and is still at `1.0.0`, which its group
  sets with a customizer.
* **Annotations on the controllers.** `@Tag` documents the group of operations a controller contributes, `@Operation`
  its individual operations and `@ApiResponse` the responses that are not obvious from the return type, such as the
  `404` of `readSingle`.
* **Annotations on the DTOs.** `@Schema` on the fields of
  [Message2Dto](jme-swagger-service/src/main/java/ch/admin/bit/jeap/jme/swagger/messages/v2/Message2Dto.java) adds the
  description, marks fields as required (`requiredMode`) or as set by the server (`accessMode = READ_ONLY`) and gives
  an `example`. The examples are what makes *Try it out* usable without typing.

See [springdoc.org](https://springdoc.org) for the springdoc reference documentation and the jEAP documentation on
[Swagger / OpenAPI](https://jeap-admin-ch.github.io/docs/building-blocks/spring-boot-starters/jeap-spring-boot-starters/jeap-spring-boot-swagger) for the jEAP specific parts.

## Why the Authorize dialog offers client_credentials

The *Authorize* dialog shows a `client_credentials` section (and a `refresh_token` and a token exchange one) although
this example only demonstrates the personal login of a user with the authorization code flow and PKCE. Filling those
sections in leads to an error instead of a token. The reason is a chain of defaults, and it is worth understanding.

1. The jEAP Swagger starter registers the security scheme as `openIdConnect` and not as `oauth2`. Such a scheme has no
   list of flows of its own — it only points at the OpenID Connect discovery document of the authorization server.
2. The Swagger UI therefore reads the available grant types out of that discovery document and renders one section per
   entry of `grant_types_supported`.
3. An authorization server lists there what it supports in general, not what a specific client is allowed to use.
4. The client of the Swagger UI, `jme-swagger-tester`, is registered as a public client in the user context
   (`context: "USER"` in [application.yml](jme-swagger-auth-scs/src/main/resources/application.yml)), which means the
   authorization code grant and nothing else, and it has no client secret. A `client_credentials` request with this
   client is rejected by the authorization server.

This is intentional: a machine-to-machine client that logs in with `client_credentials` has no user, and the operations
of this example are authorized with the roles of a user. The alternative — overriding the security scheme of the
starter with a hand-written `oauth2` scheme that lists only the authorization code flow — would hide the other sections
but also duplicate configuration that the starter and the authorization server already provide.

## Integration tests

```shell
./mvnw verify
```

builds the project and runs
[SwaggerExampleIT](jme-swagger-test/src/test/java/ch/admin/bit/jeap/jme/swagger/test/SwaggerExampleIT.java), an
end-to-end test of everything described in this README. It starts the mock authorization server and three instances of
the example service — one per `jeap.swagger.status` the example demonstrates — as separate `mvnw spring-boot:run`
processes on reserved free ports, so it does not interfere with services you started manually. It then verifies from
the outside that

* with `OPEN`, the Swagger UI and the OpenAPI documents of both API groups are served without credentials,
* the Swagger UI is wired for the authorization code flow with PKCE (redirect URL, client id and the PKCE flag),
* the documented API answers `401` without an access token,
* an access token obtained through a scripted authorization code flow with PKCE — the same flow the *Authorize* button
  runs — is accepted by the API,
* with `SECURED`, the documentation needs the basic credentials `swagger`/`secret` while the API still needs a token,
* with `DISABLED`, the documentation answers `403` for everyone.

The nested `SwaggerUiPlaywrightIT` repeats the login in a real browser with
[Playwright](https://playwright.dev/java/): it opens the Swagger UI, presses *Authorize*, submits the login form of the
mock server and executes a protected operation with *Try it out*, expecting `200`. It drives an installed Google Chrome
through the Playwright `chrome` channel and never downloads a browser; if no system Chrome is found, the test is
skipped and the build stays green. Where a browser is part of the build environment a build can pass
`-DrequireBrowser=true` and a missing browser fails instead of skipping.

The checks themselves live in
[SwaggerSmokeChecks](jme-swagger-test/src/main/java/ch/admin/bit/jeap/jme/swagger/test/SwaggerSmokeChecks.java) in
`src/main/java`, and are published as part of the `jme-swagger-test` artifact.
[AfterDeploymentSmokeTestIT](jme-swagger-test/src/test/java/ch/admin/bit/jeap/jme/swagger/test/AfterDeploymentSmokeTestIT.java)
runs the same smoke tests on already running services.

```shell
./mvnw verify -pl jme-swagger-test -DdeployStage=local -Dit.test=AfterDeploymentSmokeTestIT
```

Without `-DdeployStage=local` that test is skipped, because there is nothing running to check.

## Note

This repository is part of the open source distribution of JME. See [github.com/jme-admin-ch/jme](https://github.com/jme-admin-ch/jme)
for more information.

## Changes

This project is versioned using [Semantic Versioning](http://semver.org/) and all changes are documented in
[CHANGELOG.md](./CHANGELOG.md) following the format defined in [Keep a Changelog](http://keepachangelog.com/).

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
