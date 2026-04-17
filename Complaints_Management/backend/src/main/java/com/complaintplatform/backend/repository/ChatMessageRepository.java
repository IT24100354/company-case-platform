package com.complaintplatform.backend.repository;

import com.complaintplatform.backend.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByComplaintIdOrderByCreatedAtAsc(Long complaintId);

    @Query("SELECT m FROM ChatMessage m WHERE m.complaintId = 0 AND " +
           "((m.senderId = :u1 AND m.recipientId = :u2) OR (m.senderId = :u2 AND m.recipientId = :u1)) " +
           "ORDER BY m.createdAt ASC")
    List<ChatMessage> findPrivateMessages(@Param("u1") Long u1, @Param("u2") Long u2);

    @Query("SELECT m FROM ChatMessage m WHERE m.recipientId = :userId AND m.read = false")
    List<ChatMessage> findUnreadForUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.complaintId = :id AND m.read = false AND m.senderId != :userId")
    long countUnreadByComplaintAndNotSender(@Param("id") Long id, @Param("userId") Long userId);
    
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.complaintId = 0 AND m.recipientId = :userId AND m.read = false")
    long countUnreadPrivateForUser(@Param("userId") Long userId);

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
