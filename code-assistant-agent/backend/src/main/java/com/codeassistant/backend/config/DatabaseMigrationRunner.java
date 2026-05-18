package com.codeassistant.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        ensureAgentResponsePayloadColumn();
        ensureConversationSessionTable();
        ensureConversationMessageTable();
        ensureUserSkillTable();
        ensureKnowledgeDocumentTable();
        ensureKnowledgeDocumentColumns();
        ensureKnowledgeChunkTable();
        ensureKnowledgeChunkColumns();
    }

    private void ensureAgentResponsePayloadColumn() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE UPPER(table_name) = 'AGENT_CONVERSATION_HISTORY'
                  AND UPPER(column_name) = 'RESPONSE_PAYLOAD'
                """,
                Integer.class
        );

        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE agent_conversation_history ADD COLUMN response_payload LONGTEXT NULL");
        }
    }

    private void ensureConversationSessionTable() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE UPPER(table_name) = 'AGENT_CONVERSATION_SESSION'
                """,
                Integer.class
        );

        if (count == null || count == 0) {
            jdbcTemplate.execute("""
                    CREATE TABLE agent_conversation_session (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NULL,
                        session_id VARCHAR(128) NOT NULL,
                        title VARCHAR(255) NULL,
                        summary LONGTEXT NULL,
                        created_at DATETIME NULL,
                        updated_at DATETIME NULL,
                        INDEX idx_agent_conversation_session_user_id (user_id),
                        INDEX idx_agent_conversation_session_session_id (session_id)
                    )
                    """);
        }
    }

    private void ensureConversationMessageTable() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE UPPER(table_name) = 'AGENT_CONVERSATION_MESSAGE'
                """,
                Integer.class
        );

        if (count == null || count == 0) {
            jdbcTemplate.execute("""
                    CREATE TABLE agent_conversation_message (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        conversation_id BIGINT NULL,
                        user_id BIGINT NULL,
                        session_id VARCHAR(128) NOT NULL,
                        role VARCHAR(32) NOT NULL,
                        turn_index INT NOT NULL,
                        content LONGTEXT NULL,
                        code_content LONGTEXT NULL,
                        task_type VARCHAR(64) NULL,
                        created_at DATETIME NULL,
                        INDEX idx_agent_conversation_message_session_id (session_id),
                        INDEX idx_agent_conversation_message_turn_index (turn_index),
                        INDEX idx_agent_conversation_message_role (role)
                    )
                    """);
        }
    }

    private void ensureUserSkillTable() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE UPPER(table_name) = 'AGENT_USER_SKILL'
                """,
                Integer.class
        );

        if (count == null || count == 0) {
            jdbcTemplate.execute("""
                    CREATE TABLE agent_user_skill (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        name VARCHAR(128) NOT NULL,
                        description VARCHAR(255) NULL,
                        content LONGTEXT NOT NULL,
                        enabled TINYINT(1) NOT NULL DEFAULT 1,
                        created_at DATETIME NULL,
                        updated_at DATETIME NULL,
                        INDEX idx_agent_user_skill_user_id (user_id),
                        INDEX idx_agent_user_skill_updated_at (updated_at)
                    )
                    """);
        }
    }

    private void ensureKnowledgeDocumentTable() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE UPPER(table_name) = 'AGENT_KNOWLEDGE_DOCUMENT'
                """,
                Integer.class
        );

        if (count == null || count == 0) {
            jdbcTemplate.execute("""
                    CREATE TABLE agent_knowledge_document (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        title VARCHAR(255) NOT NULL,
                        source_name VARCHAR(255) NULL,
                        source_type VARCHAR(64) NULL,
                        tags VARCHAR(255) NULL,
                        summary TEXT NULL,
                        aliases TEXT NULL,
                        categories TEXT NULL,
                        reference_text TEXT NULL,
                        content LONGTEXT NOT NULL,
                        total_chars INT NULL,
                        enabled TINYINT(1) NOT NULL DEFAULT 1,
                        created_at DATETIME NULL,
                        updated_at DATETIME NULL,
                        INDEX idx_agent_knowledge_document_user_id (user_id),
                        INDEX idx_agent_knowledge_document_updated_at (updated_at)
                    )
                    """);
        }
    }

    private void ensureKnowledgeDocumentColumns() {
        addColumnIfMissing("agent_knowledge_document", "summary", "TEXT NULL");
        addColumnIfMissing("agent_knowledge_document", "aliases", "TEXT NULL");
        addColumnIfMissing("agent_knowledge_document", "categories", "TEXT NULL");
        addColumnIfMissing("agent_knowledge_document", "reference_text", "TEXT NULL");
        addColumnIfMissing("agent_knowledge_document", "total_chars", "INT NULL");
    }

    private void ensureKnowledgeChunkTable() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE UPPER(table_name) = 'AGENT_KNOWLEDGE_CHUNK'
                """,
                Integer.class
        );

        if (count == null || count == 0) {
            jdbcTemplate.execute("""
                    CREATE TABLE agent_knowledge_chunk (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        document_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        chunk_index INT NOT NULL,
                        start_offset INT NOT NULL,
                        end_offset INT NOT NULL,
                        title VARCHAR(255) NULL,
                        content LONGTEXT NOT NULL,
                        keywords TEXT NULL,
                        source_name VARCHAR(255) NULL,
                        source_type VARCHAR(64) NULL,
                        tags VARCHAR(255) NULL,
                        summary TEXT NULL,
                        reference_text TEXT NULL,
                        keyword_score DOUBLE NULL,
                        length_score DOUBLE NULL,
                        final_score DOUBLE NULL,
                        created_at DATETIME NULL,
                        updated_at DATETIME NULL,
                        INDEX idx_agent_knowledge_chunk_user_id (user_id),
                        INDEX idx_agent_knowledge_chunk_document_id (document_id),
                        INDEX idx_agent_knowledge_chunk_final_score (final_score)
                    )
                    """);
        }
    }

    private void ensureKnowledgeChunkColumns() {
        addColumnIfMissing("agent_knowledge_chunk", "source_name", "VARCHAR(255) NULL");
        addColumnIfMissing("agent_knowledge_chunk", "source_type", "VARCHAR(64) NULL");
        addColumnIfMissing("agent_knowledge_chunk", "tags", "VARCHAR(255) NULL");
        addColumnIfMissing("agent_knowledge_chunk", "summary", "TEXT NULL");
        addColumnIfMissing("agent_knowledge_chunk", "reference_text", "TEXT NULL");
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE UPPER(table_name) = UPPER(?)
                  AND UPPER(column_name) = UPPER(?)
                """,
                Integer.class,
                tableName,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }
}
