package org.argela.genericcommunicationservice.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mesajın mevcut durumu")
public enum MessageStatus {

    @Schema(description = "Mesaj alındı fakat henüz işlenmedi")
    RECEIVED,

    @Schema(description = "Mesaj kuyrukta bekliyor")
    QUEUED,

    @Schema(description = "Mesaj başarıyla teslim edildi")
    DELIVERED,

    @Schema(description = "Mesaj iletiminde hata oluştu")
    FAILED,

    @Schema(description = "Mesaj yeniden gönderilmeye çalışılıyor")
    RETRYING
}
