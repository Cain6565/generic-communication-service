package org.argela.genericcommunicationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.argela.genericcommunicationservice.dto.RestSendDto;
import org.argela.genericcommunicationservice.entity.MessageEntity;
import org.argela.genericcommunicationservice.enums.MessageStatus;
import org.argela.genericcommunicationservice.enums.ProtocolType;
import org.argela.genericcommunicationservice.service.MessageService;
import org.argela.genericcommunicationservice.service.http.HttpRelaySender;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rest")
@Tag(name = "REST Messages", description = "REST protokolü için mesaj işlemleri")
@RequiredArgsConstructor
public class RestMessageController {

    private final MessageService messageService;
    private final HttpRelaySender httpRelaySender;

    @PostMapping("/send")
    @Operation(summary = "HTTP mesajı gönder",
            description = "HTTP mesaj formatında (headers + body) mesaj alır, headers'daki URL'e çağrı yapar ve sonucu kaydeder. " +
                    "Headers'da 'url' key'i ile hedef URL belirtilmelidir.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "HTTP mesaj formatı",
                    content = @Content(
                            examples = @ExampleObject(
                                    name = "HTTP POST örneği",
                                    value = """
                               {
                                 "headers": {
                                   "url": "http://localhost:8081/api/webhook",
                                   "method": "POST",
                                   "Content-Type": "application/json",
                                   "X-Trace-Id": "abc123",
                                   "sender": "rest-client-1",
                                   "group-id": "notification-group"
                                 },
                                 "body": "{\\"message\\": \\"Hello World\\", \\"timestamp\\": \\"2024-01-01T12:00:00Z\\"}"
                               }
                               """
                            )
                    )
            ))
    public ResponseEntity<MessageEntity> send(@Valid @RequestBody RestSendDto dto) {

        // 1) İlk olarak mesajı RECEIVED statusu ile kaydet
        MessageEntity savedMessage = messageService.saveRestMessage(dto, MessageStatus.RECEIVED);

        // 2) HTTP çağrısını yap
        HttpRelaySender.HttpRelayResult result = httpRelaySender.send(dto);

        // 3) Sonuca göre status'u güncelle
        MessageStatus finalStatus = result.isDelivered() ? MessageStatus.DELIVERED : MessageStatus.FAILED;

        // 4) Mesajı güncelle
        MessageEntity updatedMessage = messageService.updateMessageStatus(savedMessage.getId(), finalStatus);

        // 5) Hata varsa body'e error detayını da ekle
        if (!result.isSuccess() && result.getErrorMessage() != null) {
            String originalBody = updatedMessage.getBody() != null ? updatedMessage.getBody() : "";
            String errorDetails = String.format("%s\n\nERROR: %s\nRESPONSE: %s",
                    originalBody, result.getErrorMessage(), result.getResponseBody());
            updatedMessage.setBody(errorDetails);
            messageService.updateMessage(updatedMessage);
        }

        return ResponseEntity.ok(updatedMessage);
    }

    @GetMapping("/messages")
    @Operation(summary = "REST mesajlarını listele",
            description = "Sadece REST protokolü ile gönderilen mesajları getirir")
    public ResponseEntity<Page<MessageEntity>> list(
            @Parameter(hidden = true)
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(messageService.getMessagesByProtocol(ProtocolType.REST.name(), pageable));
    }
}