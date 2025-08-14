package org.argela.genericcommunicationservice.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mesajın geldiği/gittiği haberleşme protokolü")
public enum ProtocolType {

    @Schema(description = "HTTP/REST tabanlı senkron haberleşme")
    REST,

    @Schema(description = "RabbitMQ ile asenkron haberleşme")
    RABBITMQ,

    @Schema(description = "WebSocket ile gerçek zamanlı haberleşme")
    WEBSOCKET
}
