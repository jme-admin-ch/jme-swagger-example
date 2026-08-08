package ch.admin.bit.jeap.jme.swagger.messages.v1;

import ch.admin.bit.jeap.jme.swagger.messages.v2.Message2Dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * A DTO is part of the interface and is documented as well. {@code @Schema} annotations can be placed on the class and
 * on its fields to describe them, to mark them as required or read-only and to provide an example. The examples are
 * what Swagger UI pre-fills in the request body, which is what makes "Try it out" usable without typing.
 * <p>
 * NOTE: This DTO is the deprecated first version, its ID can only be a UUID. Marking it as deprecated on the class
 * carries over into the generated OpenAPI document, so a consumer sees which parts of the API it should migrate away
 * from.
 *
 * @deprecated Use {@link Message2Dto} instead, where the ID of a message can be any string.
 */
@Data
@Deprecated
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Schema(deprecated = true)
// S1133 ("remove this deprecated code someday") is off here: showing a deprecated API version next to its successor
// is what this example demonstrates, so this DTO stays deprecated for good.
@SuppressWarnings("java:S1133")
public class MessageDto {
    @Schema(description = "The message ID", accessMode = Schema.AccessMode.READ_ONLY, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The message content", example = "Hello World")
    private String text;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The SAP-ID of the receiving business partner", example = "12345")
    private String receiver;
    @Schema(description = "The time when this message was sent. Will be set by the server",
            accessMode = Schema.AccessMode.READ_ONLY,
            example = "2020-04-23T14:50:05.648291+02:00")
    private ZonedDateTime timeSend;

    static MessageDto from(Message2Dto message) {
        return MessageDto.builder()
                .id(UUID.fromString(message.getId()))
                .receiver(message.getReceiver())
                .text(message.getText())
                .timeSend(message.getTimeSend())
                .build();
    }

    static boolean isCompatible(Message2Dto message) {
        try {
            //noinspection ResultOfMethodCallIgnored
            UUID.fromString(message.getId());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    Message2Dto toV2() {
        return Message2Dto.builder()
                // The ID is read-only, so a request body does not carry one - it is set by the server from the path.
                .id(getId() == null ? null : getId().toString())
                .receiver(getReceiver())
                .text(getText())
                .timeSend(getTimeSend())
                .build();
    }
}
