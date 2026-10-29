package com.claseya.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Natural key of a conversation: one row per student<->teacher pair (RACE-01).
 *
 * <p>The row is inserted with {@code INSERT ... ON CONFLICT DO NOTHING} so the database,
 * not a read-then-write check, decides which concurrent request owns the conversation.
 */
@Entity
@Table(name = "conversation_pairs")
public class ConversationPair {

    @EmbeddedId
    private Key id = new Key();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    public Key getId() {
        return id;
    }

    public void setId(Key id) {
        this.id = id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "student_user_id", nullable = false)
        private UUID studentUserId;

        @Column(name = "teacher_user_id", nullable = false)
        private UUID teacherUserId;

        public Key() {
        }

        public Key(UUID studentUserId, UUID teacherUserId) {
            this.studentUserId = studentUserId;
            this.teacherUserId = teacherUserId;
        }

        public UUID getStudentUserId() {
            return studentUserId;
        }

        public void setStudentUserId(UUID studentUserId) {
            this.studentUserId = studentUserId;
        }

        public UUID getTeacherUserId() {
            return teacherUserId;
        }

        public void setTeacherUserId(UUID teacherUserId) {
            this.teacherUserId = teacherUserId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(studentUserId, key.studentUserId)
                    && Objects.equals(teacherUserId, key.teacherUserId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(studentUserId, teacherUserId);
        }
    }
}
