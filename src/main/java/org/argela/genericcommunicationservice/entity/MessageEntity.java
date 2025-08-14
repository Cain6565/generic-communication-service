package org.argela.genericcommunicationservice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;
import org.argela.genericcommunicationservice.enums.ProtocolType;
import org.argela.genericcommunicationservice.enums.MessageStatus;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "messages")
@Getter
@Setter
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String method;
    private String url;
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> headers;

    @Column(columnDefinition = "text")
    private String body;

    private String sender;
    private String groupId;

    @Enumerated(EnumType.STRING)
    private ProtocolType protocol;

    @Enumerated(EnumType.STRING)
    private MessageStatus status;

    private Instant timestamp = Instant.now();
}