package ch.admin.bit.jeap.jme.swagger.messages.v1;

import ch.admin.bit.jeap.jme.swagger.messages.InternalApiSwaggerConfig;
import ch.admin.bit.jeap.jme.swagger.messages.v2.InternalApi2Controller;
import ch.admin.bit.jeap.jme.swagger.messages.v2.Message2Dto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * This controller is part of the internal API and is covered by the package scan of {@link InternalApiSwaggerConfig}.
 * <p>
 * Each controller becomes an OpenAPI tag, and each operation of the controller becomes (by default) an operation of
 * that tag. Both the tag and its operations can carry additional documentation, declared with {@code @Tag} and
 * {@code @Operation} as shown here.
 * <p>
 * NOTE: This controller is the deprecated first version of the messages API: it only accepts message IDs that are
 * UUIDs. It delegates to the current version and drops the messages that cannot be represented in this version. Both
 * versions belong to the same API definition, which is how a consumer sees the old and the new operations side by side
 * and can migrate at its own pace.
 *
 * @deprecated Use {@link InternalApi2Controller} instead, where the ID of a message can be any string.
 */
@Tag(name = "Messages", description = "Exchange Messages with a Server. This version of the interface is deprecated, please use V2 instead")
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Deprecated
// S1133 ("remove this deprecated code someday") is off here: showing a deprecated API version next to its successor
// is what this example demonstrates, so this controller stays deprecated for good.
@SuppressWarnings("java:S1133")
public class InternalApiController {
    private final InternalApi2Controller proxyToNewerVersion;

    @Operation(
            summary = "Create or update a message on the server",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Message has been created"),
                    @ApiResponse(responseCode = "403", description = "The caller has no write right for the receiving business partner")}
    )
    @PutMapping("/{messageId}")
    @PreAuthorize("hasRoleForPartner('swagger','write',#incoming.receiver)")
    @Deprecated
    public ResponseEntity<MessageDto> createOrUpdate(@PathVariable("messageId") UUID messageId, @RequestBody MessageDto incoming) {
        ResponseEntity<Message2Dto> v2Response = proxyToNewerVersion.createOrUpdate(messageId.toString(), incoming.toV2());
        return ResponseEntity.status(v2Response.getStatusCode()).body(MessageDto.from(Objects.requireNonNull(v2Response.getBody())));
    }

    @Operation(
            summary = "Get all messages",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The messages of the requested business partner"),
                    @ApiResponse(responseCode = "403", description = "The caller has no read right for the requested business partner")}
    )
    @GetMapping
    @PreAuthorize("hasRoleForPartner('swagger','read',#receiver)")
    @Deprecated
    public List<MessageDto> readAll(@RequestParam String receiver) {
        return proxyToNewerVersion.readAll(receiver).stream()
                .filter(MessageDto::isCompatible)
                .map(MessageDto::from)
                .toList();
    }

    @Operation(
            summary = "Get a single message from the server",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The message"),
                    @ApiResponse(responseCode = "403", description = "The caller has no read right for the business partner of this message"),
                    @ApiResponse(responseCode = "404", description = "No message with this ID exists")}
    )
    @GetMapping("/{messageId}")
    @PreAuthorize("hasRole('swagger','read')")
    @PostAuthorize("hasRoleForPartner('swagger','read',returnObject.receiver)")
    @Deprecated
    public MessageDto readSingle(@PathVariable("messageId") UUID messageId) {
        Message2Dto message = proxyToNewerVersion.readSingle(messageId.toString());
        if (!MessageDto.isCompatible(message)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No message with this ID exists");
        }
        return MessageDto.from(message);
    }

    @Operation(
            summary = "Delete an existing message on the server",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The message has been deleted"),
                    @ApiResponse(responseCode = "403", description = "The caller has no write right for the business partner of this message"),
                    @ApiResponse(responseCode = "404", description = "No message with this ID exists")
            })
    @DeleteMapping("/{messageId}")
    @PreAuthorize("hasRole('swagger','write')")
    @Deprecated
    public void delete(@PathVariable("messageId") UUID messageId) {
        proxyToNewerVersion.delete(messageId.toString());
    }
}
