package com.complaintplatform.backend.repository;

import com.complaintplatform.backend.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByComplaintIdOrderByCreatedAtAsc(Long complaintId);
    List<ChatMessage> findByComplaintIdAndChannelOrderByCreatedAtAsc(Long complaintId, String channel);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM chat_messages", nativeQuery = true)
    void truncateTable();

    @Modifying
    @Transactional
    @Query(value = "ALTER TABLE chat_messages MODIFY COLUMN message VARCHAR(4000) NULL", nativeQuery = true)
    void fixMessageColumn();

    @Modifying
    @Transactional
    @Query(value = "ALTER TABLE chat_messages MODIFY COLUMN text VARCHAR(4000) NOT NULL", nativeQuery = true)
    void fixTextColumn();
}
