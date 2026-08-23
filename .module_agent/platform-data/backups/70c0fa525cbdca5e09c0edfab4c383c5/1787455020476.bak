CREATE TABLE IF NOT EXISTS message (
    id           BIGINT PRIMARY KEY,
    user_id      BIGINT DEFAULT 1,
    session_id   BIGINT,
    role         VARCHAR(20),
    content      MEDIUMTEXT,
    reasoning    MEDIUMTEXT,
    sequence_num INT,
    tool_call_id VARCHAR(100),
    token_usage  TEXT,
    rollback     TINYINT NOT NULL DEFAULT 0,
    conversation_id VARCHAR(50),
    create_time  TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_message_session_id ON message(session_id);
CREATE INDEX IF NOT EXISTS idx_message_tool_call_id ON message(tool_call_id);

CREATE TABLE IF NOT EXISTS agent_log (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT DEFAULT 1,
    session_id      BIGINT,
    conversation_id VARCHAR(50),
    log_type        VARCHAR(64),
    log_level       VARCHAR(32),
    log_data        TEXT,
    session_variables      TEXT,
    conversation_variables TEXT,
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    deleted         INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_agent_log_session_id ON agent_log(session_id);
CREATE INDEX IF NOT EXISTS idx_agent_log_conversation_id ON agent_log(conversation_id);

CREATE TABLE IF NOT EXISTS message_tool_call (
    id                  BIGINT PRIMARY KEY,
    message_id          BIGINT,
    tool_call_id        VARCHAR(100),
    tool_call_name      VARCHAR(100),
    tool_call_arguments TEXT,
    type                VARCHAR(32) DEFAULT 'function',
    web_search_call     TEXT,
    custom_tool_call    TEXT
);
CREATE INDEX IF NOT EXISTS idx_message_tool_call_message_id ON message_tool_call(message_id);
CREATE INDEX IF NOT EXISTS idx_message_tool_call_tool_call_id ON message_tool_call(tool_call_id);
