CREATE TABLE IF NOT EXISTS user (
    id          BIGINT PRIMARY KEY,
    login_name  VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    user_type   INTEGER,
    password    VARCHAR(255),
    enabled     INTEGER DEFAULT 1,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    deleted     INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS model_config (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT DEFAULT 1,
    name        VARCHAR(255) NOT NULL,
    platform_type VARCHAR(64),
    api_key     VARCHAR(255),
    base_url    VARCHAR(512),
    model_name  VARCHAR(255),
    request_type VARCHAR(32),
    model_type  VARCHAR(32) DEFAULT 'LLM',
    temperature DOUBLE,
    max_tokens  INTEGER,
    status      VARCHAR(32),
    description TEXT,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    deleted     INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tool_config (
    id               BIGINT PRIMARY KEY,
    user_id          BIGINT DEFAULT 1,
    name             VARCHAR(255) NOT NULL,
    tool_type        VARCHAR(32),
    description      TEXT,
    parameter_schema TEXT,
    return_schema    TEXT,
    impl_path        VARCHAR(512),
    auth_config      TEXT,
    sub_tool_type    VARCHAR(32),
    tool_script      TEXT,
    status           VARCHAR(32),
    create_time      TIMESTAMP,
    update_time      TIMESTAMP,
    deleted          INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS session (
    id               BIGINT PRIMARY KEY,
    user_id          BIGINT DEFAULT 1,
    agent_id         BIGINT,
    model_id         BIGINT,
    title            VARCHAR(200),
    system_prompt    TEXT,
    parent_session_id BIGINT,
    is_child         TINYINT(1) DEFAULT 0,
    description      VARCHAR(500),
    thinking         TINYINT(1),
    total_token_used BIGINT,
    last_response_id VARCHAR(50),
    is_evaluation    TINYINT(1) DEFAULT 0,
    memory_point_sequence_num INTEGER,
    memory_prompt     VARCHAR(500),
    create_time      TIMESTAMP,
    update_time      TIMESTAMP,
    deleted          INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_session_agent_id ON session(agent_id);
CREATE INDEX IF NOT EXISTS idx_session_model_id ON session(model_id);

CREATE TABLE IF NOT EXISTS session_tool (
    id           BIGINT PRIMARY KEY,
    session_id   BIGINT,
    tool_id      BIGINT,
    session_auth INT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_session_tool_session_id ON session_tool(session_id);
CREATE INDEX IF NOT EXISTS idx_session_tool_tool_id ON session_tool(tool_id);

CREATE TABLE IF NOT EXISTS agent_config (
    id            BIGINT PRIMARY KEY,
    user_id       BIGINT DEFAULT 1,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    system_prompt TEXT,
    model_id      BIGINT,
    status        VARCHAR(32),
    create_time             TIMESTAMP,
    update_time             TIMESTAMP,
    recent_message_count    INTEGER DEFAULT 10,
    memory_enabled          TINYINT(1) DEFAULT 0,
    memory_group_count      INTEGER DEFAULT 30,
    vector_model_id         BIGINT,
    sub_session_open_mode   VARCHAR(32) DEFAULT 'TOOL_CALL',
    deleted                 INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agent_config_model_id ON agent_config(model_id);

CREATE TABLE IF NOT EXISTS agent_tool (
    id           BIGINT PRIMARY KEY,
    agent_id     BIGINT NOT NULL,
    tool_id      BIGINT NOT NULL,
    session_auth INT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_agent_tool_agent_id ON agent_tool(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_tool_tool_id ON agent_tool(tool_id);

CREATE TABLE IF NOT EXISTS agent_skill (
    id           BIGINT PRIMARY KEY,
    agent_id     BIGINT NOT NULL,
    skill_id     BIGINT NOT NULL,
    session_auth INT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_agent_skill_agent_id ON agent_skill(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_skill_skill_id ON agent_skill(skill_id);

CREATE TABLE IF NOT EXISTS skill_config (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT DEFAULT 1,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    prompt      TEXT,
    status      VARCHAR(32),
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    deleted     INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS skill_tool (
    id       BIGINT PRIMARY KEY,
    skill_id BIGINT NOT NULL,
    tool_id  BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_skill_tool_skill_id ON skill_tool(skill_id);
CREATE INDEX IF NOT EXISTS idx_skill_tool_tool_id ON skill_tool(tool_id);

CREATE TABLE IF NOT EXISTS session_variable (
    id             BIGINT PRIMARY KEY,
    user_id        BIGINT DEFAULT 1,
    session_id     BIGINT,
    variable_key   VARCHAR(255),
    variable_value TEXT,
    create_time    TIMESTAMP,
    update_time    TIMESTAMP,
    deleted        INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_session_variable_session_id ON session_variable(session_id);
CREATE INDEX IF NOT EXISTS idx_session_variable_key ON session_variable(session_id, variable_key);

CREATE TABLE IF NOT EXISTS session_skill (
    id           BIGINT PRIMARY KEY,
    session_id   BIGINT,
    skill_id     BIGINT,
    session_auth INT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_session_skill_session_id ON session_skill(session_id);
CREATE INDEX IF NOT EXISTS idx_session_skill_skill_id ON session_skill(skill_id);

CREATE TABLE IF NOT EXISTS agent_evaluation (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT DEFAULT 1,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    agent_id    BIGINT,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    deleted     INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS evaluation (
    id                  BIGINT PRIMARY KEY,
    user_id             BIGINT DEFAULT 1,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    benchmark_session_id BIGINT,
    execution_count     INTEGER DEFAULT 0,
    model_id            BIGINT,
    agent_eval_id       BIGINT,
    agent_id            BIGINT,
    create_time         TIMESTAMP,
    update_time         TIMESTAMP,
    deleted             INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS evaluation_result (
    id                    BIGINT PRIMARY KEY,
    user_id               BIGINT DEFAULT 1,
    evaluation_id         BIGINT,
    evaluation_session_id BIGINT,
    result                TEXT,
    execution_status      VARCHAR(32) DEFAULT 'PENDING',
    model_id              BIGINT,
    final_score           INTEGER,
    create_time           TIMESTAMP,
    update_time           TIMESTAMP,
    deleted               INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS knowledge_base (
    id               BIGINT PRIMARY KEY,
    user_id          BIGINT DEFAULT 1,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    status           VARCHAR(32),
    vector_model_id  BIGINT,
    es_index         VARCHAR(255),
    rebuilding       TINYINT(1) DEFAULT 0,
    create_time      TIMESTAMP,
    update_time      TIMESTAMP,
    deleted          INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS knowledge_file (
    id                 BIGINT PRIMARY KEY,
    user_id            BIGINT DEFAULT 1,
    file_name          VARCHAR(255),
    file_description   TEXT,
    knowledge_base_id  BIGINT,
    file_size          BIGINT,
    line_count         INTEGER,
    status             VARCHAR(32),
    publish_status     VARCHAR(32) DEFAULT 'UNPUBLISHED',
    file_content       LONGTEXT,
    create_time        TIMESTAMP,
    update_time        TIMESTAMP,
    deleted            INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_knowledge_file_knowledge_base_id ON knowledge_file(knowledge_base_id);

CREATE TABLE IF NOT EXISTS agent_knowledge_base (
    id                 BIGINT PRIMARY KEY,
    agent_id           BIGINT NOT NULL,
    knowledge_base_id  BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_agent_knowledge_base_agent_id ON agent_knowledge_base(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_knowledge_base_knowledge_base_id ON agent_knowledge_base(knowledge_base_id);
