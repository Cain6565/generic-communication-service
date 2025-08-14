package org.argela.genericcommunicationservice.service.rabbit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.argela.genericcommunicationservice.dto.RabbitSendDto;
import org.argela.genericcommunicationservice.enums.MessageStatus;
import org.argela.genericcommunicationservice.service.MessageService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ mesaj dinleyicisi.
 * Queue'dan gelen mesajları yakalar ve DB'ye DELIVERED olarak kaydeder.
 * Bu, publish edilenin yanında ikinci bir kayıt oluşturur.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMessageListener {

    private final MessageService messageService;

    @RabbitListener(queues = {"generic-messages-queue", "notifications", "user-notifications"},
            messageConverter = "jackson2JsonMessageConverter")
    public void onMessage(@Payload RabbitSendDto dto) {

        log.info("🎧 RabbitMQ mesajı dinlendi: broker={}, queue={}", dto.getBroker(), dto.getQueue());

        try {
            // İkinci kayıt - DELIVERED olarak (bu listener'ın yakaladığı mesaj)
            messageService.saveRabbitMessage(dto, MessageStatus.DELIVERED);

            log.info("✅ Dinlenen mesaj DB'ye DELIVERED olarak kaydedildi");

        } catch (Exception e) {
            log.error("❌ Dinlenen mesaj kaydedilirken hata: {}", e.getMessage(), e);

            // Hata durumunda FAILED olarak kaydet
            try {
                messageService.saveRabbitMessage(dto, MessageStatus.FAILED);
            } catch (Exception ex) {
                log.error("FAILED kayıt da başarısız: {}", ex.getMessage());
            }
        }
    }
}