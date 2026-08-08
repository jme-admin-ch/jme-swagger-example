package ch.admin.bit.jeap.jme.swagger.messages.v2;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * The message of the current API version, where the ID can be any string.
 * <p>
 * Fields that the server sets are documented as read-only, so they are not part of the request body Swagger UI
 * pre-fills. Fields without an explicit required mode are optional, which is the default of {@code @Schema}.
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Message2Dto {
    @Schema(description = "The message ID, can be any string but usually a UUID.",
            accessMode = Schema.AccessMode.READ_ONLY,
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private String id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The message content", example = "Hello World")
    private String text;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The SAP-ID of the receiving business partner", example = "12345")
    private String receiver;
    @Schema(description = "The time when this message was sent. Will be set by the server",
            accessMode = Schema.AccessMode.READ_ONLY,
            example = "2020-04-23T14:50:05.648291+02:00")
    private ZonedDateTime timeSend;
}
