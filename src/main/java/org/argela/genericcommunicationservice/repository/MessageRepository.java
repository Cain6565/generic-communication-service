package org.argela.genericcommunicationservice.repository;

import org.argela.genericcommunicationservice.entity.MessageEntity;
import org.argela.genericcommunicationservice.enums.MessageStatus;
import org.argela.genericcommunicationservice.enums.ProtocolType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    // ✅ TEMEL FINDER'LAR (Kalacak)
    Page<MessageEntity> findByProtocol(ProtocolType protocol, Pageable pageable);

    // ✅ İSTATİSTİK SORULARI (Kalacak)
    @Query("SELECT m.protocol AS protocol, COUNT(m) AS count FROM MessageEntity m GROUP BY m.protocol")
    List<Map<String, Object>> countMessagesByProtocol();

    @Query("SELECT m.status AS status, COUNT(m) AS count FROM MessageEntity m GROUP BY m.status")
    List<Map<String, Object>> countMessagesByStatus();
}