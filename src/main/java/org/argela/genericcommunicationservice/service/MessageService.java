package org.argela.genericcommunicationservice.service;

import org.argela.genericcommunicationservice.dto.RestSendDto;
import org.argela.genericcommunicationservice.dto.RabbitSendDto;
import org.argela.genericcommunicationservice.dto.WebSocketSendDto;
import org.argela.genericcommunicationservice.entity.MessageEntity;
import org.argela.genericcommunicationservice.enums.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

/**
 * Generic Message Service - Basit mesaj yönetimi
 * Tüm protokoller için mesaj saklama ve listeleme işlemleri
 */
public interface MessageService {

    // ✅ MESAJ SAKLAMA (3 protokol için)
    MessageEntity saveRestMessage(RestSendDto dto, MessageStatus status);
    MessageEntity saveRabbitMessage(RabbitSendDto dto, MessageStatus status);
    MessageEntity saveWebSocketMessage(WebSocketSendDto dto, MessageStatus status);

    // ✅ ENTITY GÜNCELLEME
    MessageEntity updateMessage(MessageEntity entity);
    MessageEntity updateMessageStatus(Long id, MessageStatus status);

    // ✅ TEMEL LİSTELEME
    Page<MessageEntity> getAllMessages(Pageable pageable);
    Page<MessageEntity> getMessagesByProtocol(String protocol, Pageable pageable);

    // ✅ İSTATİSTİKLER
    Map<String, Object> getMessageStatistics();

    // ✅ YÖNETİM
    void deleteAllMessages();
}