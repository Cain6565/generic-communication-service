package org.argela.genericcommunicationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.argela.genericcommunicationservice.entity.MessageEntity;
import org.argela.genericcommunicationservice.service.MessageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "General Messages", description = "Genel mesaj yönetimi ve istatistikler")
@RequiredArgsConstructor
public class GeneralMessageController {

    private final MessageService messageService;

    @GetMapping("/messages")
    @Operation(summary = "Tüm mesajları listele",
            description = "Protokol filtresi ile mesajları getir")
    public ResponseEntity<Page<MessageEntity>> listMessages(
            @Parameter(description = "Protokol filtresi (REST, RABBITMQ, WEBSOCKET)")
            @RequestParam(required = false) String protocol,
            @Parameter(hidden = true)
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        // Protocol filtresi varsa
        if (protocol != null && !protocol.trim().isEmpty()) {
            return ResponseEntity.ok(
                    messageService.getMessagesByProtocol(protocol.toUpperCase(), pageable)
            );
        }

        // Filtresiz - tüm mesajları listele
        return ResponseEntity.ok(messageService.getAllMessages(pageable));
    }

    @GetMapping("/messages/statistics")
    @Operation(summary = "Mesaj istatistikleri",
            description = "Protokol bazlı mesaj sayıları")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        return ResponseEntity.ok(messageService.getMessageStatistics());
    }

    @DeleteMapping("/messages")
    @Operation(summary = "Tüm mesajları sil",
            description = "DİKKAT: Bu işlem geri alınamaz!")
    public ResponseEntity<Void> deleteAllMessages() {
        messageService.deleteAllMessages();
        return ResponseEntity.noContent().build();
    }
}