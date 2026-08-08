package ch.admin.bit.jeap.jme.swagger.external;

import ch.admin.bit.jeap.jme.swagger.messages.v2.InternalApi2Controller;
import ch.admin.bit.jeap.jme.swagger.messages.v2.Message2Dto;
import ch.admin.bit.jeap.security.resource.semanticAuthentication.ServletSemanticAuthorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * External consumers get an API of their own, which can then for example be exposed on an API gateway, and which does
 * not have to change whenever the internal API does.
 * <p>
 * This controller is part of the external API and is therefore covered by the package scan of
 * {@link ExternalApiSwaggerConfig}.
 * <p>
 * Each controller becomes an OpenAPI tag, and each operation of the controller becomes (by default) an operation of
 * that tag. Both the tag and its operations can carry additional documentation, declared with {@code @Tag} and
 * {@code @Operation} as shown here.
 * <p>
 */
@Tag(name = "MyMessages", description = "Get my Messages")
@RestController
@RequestMapping("/api/messages/mine")
@RequiredArgsConstructor
class ExternalApiController {
    private final InternalApi2Controller internalApi2Controller;
    private final ServletSemanticAuthorization jeapAuthorization;

    @Operation(
            summary = "Get all messages for the current business partners",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The messages of the business partners of the caller"),
                    @ApiResponse(responseCode = "403", description = "The caller has no read right")}
    )
    @GetMapping
    @PreAuthorize("hasRole('swagger', 'read')")
    public List<Message2Dto> readMine() {
        return internalApi2Controller.getMessages().stream()
                .filter(m -> jeapAuthorization.hasRoleForPartner("swagger", "read", m.getReceiver()))
                .toList();
    }
}
