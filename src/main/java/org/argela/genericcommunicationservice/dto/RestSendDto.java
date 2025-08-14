package org.argela.genericcommunicationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * REST protokolü için HTTP mesaj formatı.
 * Gerçek HTTP mesaj yapısına uygun olarak sadece headers ve body içerir.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "HTTP mesaj formatı - headers ve body")
public class RestSendDto {

    @Schema(
            description = "HTTP başlıkları. URL bilgisi burada olmalı (örn: 'url' veya 'target-url' key'i ile)",
            example = """
                {
                  "url": "http://localhost:8081/api/webhook",
                  "method": "POST",
                  "Content-Type": "application/json",
                  "X-Trace-Id": "abc123",
                  "Authorization": "Bearer token"
                }
                """,
            required = true
    )
    @NotNull(message = "Headers boş olamaz")
    private Map<String, String> headers;

    @Schema(
            description = "HTTP mesaj gövdesi (JSON string formatında)",
            example = """
                {
                  "message": "Hello World",
                  "timestamp": "2024-01-01T12:00:00Z",
                  "data": {
                    "userId": 123,
                    "action": "notify"
                  }
                }
                """
    )
    private String body;
}