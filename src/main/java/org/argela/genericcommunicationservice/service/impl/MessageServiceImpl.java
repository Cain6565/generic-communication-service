package org.argela.genericcommunicationservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.argela.genericcommunicationservice.dto.RestSendDto;
import org.argela.genericcommunicationservice.dto.RabbitSendDto;
import org.argela.genericcommunicationservice.dto.WebSocketSendDto;
import org.argela.genericcommunicationservice.entity.MessageEntity;
import org.argela.genericcommunicationservice.enums.MessageStatus;
import org.argela.genericcommunicationservice.enums.ProtocolType;
import org.argela.genericcommunicationservice.repository.MessageRepository;
import org.argela.genericcommunicationservice.service.MessageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;

    // ✅ REST MESAJ SAKLAMA
    @Override
    public MessageEntity saveRestMessage(RestSendDto dto, MessageStatus status) {
        MessageEntity entity = new MessageEntity();

        // Headers'dan method ve URL'i extract et
        String method = dto.getHeaders() != null ?
                dto.getHeaders().getOrDefault("method", "POST") : "POST";
        String url = extractUrlFromHeaders(dto.getHeaders());

        // HTTP-benzeri kanonik forma map'le
        entity.setMethod(method.toUpperCase());
        entity.setUrl(url);
        entity.setVersion("HTTP/1.1");
        entity.setHeaders(dto.getHeaders());
        entity.setBody(dto.getBody());

        // Meta bilgiler
        entity.setSender(extractSenderFromHeaders(dto.getHeaders()));
        entity.setGroupId(extractGroupIdFromHeaders(dto.getHeaders()));
        entity.setProtocol(ProtocolType.REST);
        entity.setStatus(status);
        entity.setTimestamp(Instant.now());

        return messageRepository.save(entity);
    }

    // ✅ RABBITMQ MESAJ SAKLAMA
    @Override
    public MessageEntity saveRabbitMessage(RabbitSendDto dto, MessageStatus status) {
        MessageEntity entity = new MessageEntity();

        // RabbitMQ mesajını HTTP-benzeri kanonik forma map'le
        entity.setMethod("PUBLISH");
        entity.setUrl(String.format("rabbitmq://%s/%s", dto.getBroker(), dto.getQueue()));
        entity.setVersion("AMQP/0.9.1");

        // Headers - RabbitMQ meta bilgilerini koy
        Map<String, String> headers = new HashMap<>();
        headers.put("broker", dto.getBroker());
        headers.put("queue", dto.getQueue());
        if (dto.getExchange() != null && !dto.getExchange().trim().isEmpty()) {
            headers.put("exchange", dto.getExchange());
        }
        if (dto.getRoutingKey() != null && !dto.getRoutingKey().trim().isEmpty()) {
            headers.put("routing-key", dto.getRoutingKey());
        }
        entity.setHeaders(headers);
        entity.setBody(dto.getPayload());

        // Meta bilgiler
        entity.setSender(dto.getSender());
        entity.setGroupId(dto.getGroupId());
        entity.setProtocol(ProtocolType.RABBITMQ);
        entity.setStatus(status);
        entity.setTimestamp(Instant.now());

        return messageRepository.save(entity);
    }

    // ✅ WEBSOCKET MESAJ SAKLAMA
    @Override
    public MessageEntity saveWebSocketMessage(WebSocketSendDto dto, MessageStatus status) {
        MessageEntity entity = new MessageEntity();

        // WebSocket mesajını HTTP-benzeri kanonik forma map'le
        entity.setMethod("SEND");
        entity.setUrl(String.format("websocket://%s%s", dto.getWebsocket(), dto.getDestination()));
        entity.setVersion("STOMP/1.2");

        // Headers - WebSocket meta bilgilerini koy
        Map<String, String> headers = new HashMap<>();
        headers.put("websocket", dto.getWebsocket());  // broker → websocket
        headers.put("destination", dto.getDestination());
        if (dto.getMessageType() != null && !dto.getMessageType().trim().isEmpty()) {
            headers.put("message-type", dto.getMessageType());
        }
        // DTO'dan gelen headers'ı da ekle
        if (dto.getHeaders() != null) {
            headers.putAll(dto.getHeaders());
        }
        entity.setHeaders(headers);
        entity.setBody(dto.getPayload());

        // Meta bilgiler
        entity.setSender(dto.getSender());
        entity.setGroupId(dto.getGroupId());
        entity.setProtocol(ProtocolType.WEBSOCKET);
        entity.setStatus(status);
        entity.setTimestamp(Instant.now());

        return messageRepository.save(entity);
    }

    // ✅ ENTITY GÜNCELLEME
    @Override
    public MessageEntity updateMessage(MessageEntity entity) {
        return messageRepository.save(entity);
    }

    @Override
    public MessageEntity updateMessageStatus(Long id, MessageStatus status) {
        MessageEntity entity = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mesaj bulunamadı: " + id));
        entity.setStatus(status);
        return messageRepository.save(entity);
    }

    // ✅ TEMEL LİSTELEME METHODLARI
    @Override
    public Page<MessageEntity> getAllMessages(Pageable pageable) {
        return messageRepository.findAll(pageable);
    }

    @Override
    public Page<MessageEntity> getMessagesByProtocol(String protocol, Pageable pageable) {
        try {
            ProtocolType protocolType = ProtocolType.valueOf(protocol.toUpperCase());
            return messageRepository.findByProtocol(protocolType, pageable);
        } catch (IllegalArgumentException e) {
            // Geçersiz protocol ise boş sayfa döndür
            return Page.empty(pageable);
        }
    }

    // ✅ İSTATİSTİKLER
    @Override
    public Map<String, Object> getMessageStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMessages", messageRepository.count());
        stats.put("byProtocol", messageRepository.countMessagesByProtocol());
        stats.put("byStatus", messageRepository.countMessagesByStatus());
        return stats;
    }

    // ✅ YÖNETİM
    @Override
    public void deleteAllMessages() {
        messageRepository.deleteAll();
    }

    // 🔧 HELPER METHODLAR (Basitleştirilmiş)
    private String extractUrlFromHeaders(Map<String, String> headers) {
        if (headers == null) return null;
        return headers.get("url");
    }

    private String extractSenderFromHeaders(Map<String, String> headers) {
        if (headers == null) return null;
        return headers.get("sender");
    }

    private String extractGroupIdFromHeaders(Map<String, String> headers) {
        if (headers == null) return null;
        String groupId = headers.get("group-id");
        return groupId != null ? groupId : headers.get("groupId");
    }
}