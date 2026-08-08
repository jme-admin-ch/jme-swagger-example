package ch.admin.bit.jeap.jme.swagger.messages.v2;

import ch.admin.bit.jeap.security.resource.semanticAuthentication.ServletSemanticAuthorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * The current version of the messages API. It is documented as a second tag of the same API definition as
 * {@link ch.admin.bit.jeap.jme.swagger.messages.v1.InternalApiController}, the deprecated first version. In V2 the ID
 * of a message can now be any string, which is an incompatible change and therefore results in a new API version.
 * <p>
 * NOTE: To keep the example small, the messages are kept in memory in the controller itself. A real service would
 * delegate to a service and a repository instead.
 */
@SuppressWarnings("deprecation")
@Tag(name = "Messages (V2)", description = "Exchange Messages with a Server. The ID of a message can now be any string")
@RestController
@RequestMapping("/api/messages/v2")
@RequiredArgsConstructor
public class InternalApi2Controller {
    private final ServletSemanticAuthorization jeapAuthorization;

    @Getter
    private final List<Message2Dto> messages = new LinkedList<>();

    @Operation(
            summary = "Create or update a message on the server",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Message has been created"),
                    @ApiResponse(responseCode = "403", description = "The caller has no write right for the receiving business partner")}
    )
    @PutMapping("/{messageId}")
    @PreAuthorize("hasRoleForPartner('swagger','write',#incoming.receiver)")
    public ResponseEntity<Message2Dto> createOrUpdate(@PathVariable("messageId") String messageId, @RequestBody Message2Dto incoming) {
        Optional<Message2Dto> messageOpt = find(messageId);
        if (messageOpt.isPresent()) {
            Message2Dto message = messageOpt.get();
            message.setReceiver(incoming.getReceiver());
            message.setText(incoming.getText());
            return ResponseEntity.status(HttpStatus.CREATED).body(message);
        }

        //Time send is set by the server, not by the client
        incoming.setTimeSend(ZonedDateTime.now());
        //ID must be equal to the ID in the URL
        incoming.setId(messageId);
        messages.add(incoming);
        return ResponseEntity.status(HttpStatus.CREATED).body(incoming);
    }

    @Operation(
            summary = "Get all messages",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The messages the caller is allowed to read"),
                    @ApiResponse(responseCode = "403", description = "The caller has no read right")}
    )
    @GetMapping
    @PreAuthorize("hasRole('swagger','read')")
    public List<Message2Dto> readAll(@RequestParam(required = false) String receiver) {
        return messages.stream()
                .filter(m -> receiver == null || receiver.equals(m.getReceiver()))
                .filter(m -> jeapAuthorization.hasRoleForPartner("swagger", "read", m.getReceiver()))
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
    public Message2Dto readSingle(@PathVariable("messageId") String messageId) {
        return find(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No message with this ID exists"));
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
    public void delete(@PathVariable("messageId") String messageId) {
        Message2Dto message = find(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No message with this ID exists"));
        if (!jeapAuthorization.hasRoleForPartner("swagger", "write", message.getReceiver())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        messages.remove(message);
    }

    private Optional<Message2Dto> find(String messageId) {
        return messages.stream()
                .filter(m -> m.getId().equals(messageId))
                .findFirst();
    }
}
