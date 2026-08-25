package com.ghost616.platform.session;

import com.ghost616.platform.dto.session.SessionDTO;
import com.ghost616.platform.entity.Message;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.entity.SessionSkill;
import com.ghost616.platform.entity.SessionTool;
import com.ghost616.platform.entity.SessionVariable;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.repository.AgentToolMapper;
import com.ghost616.platform.repository.SessionSkillMapper;
import com.ghost616.platform.repository.SessionToolMapper;
import com.ghost616.platform.repository.SessionVariableMapper;
import com.ghost616.platform.service.session.SessionServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.ghost616.agentbase.service.agent.AgentContextManager;
import com.ghost616.platform.dto.session.SessionMessageDTO;
import com.ghost616.agentbase.service.agent.SessionManager;
import com.ghost616.agentbase.service.agent.invoker.ToolManager;
import com.ghost616.platform.entity.User;
import com.ghost616.platform.enums.ErrorCode;
import com.ghost616.platform.exception.BusinessException;
import com.ghost616.platform.session.UserContext;
import com.ghost616.platform.session.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    private static final Long CURRENT_USER_ID = 42L;

    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private AgentToolMapper agentToolMapper;
    @Mock
    private SessionToolMapper sessionToolMapper;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private AgentContextManager agentContextManager;
    @Mock
    private ToolManager toolManager;
    @Mock
    private com.ghost616.platform.repository.MessageMapper messageMapper;
    @Mock
    private com.ghost616.platform.service.agent.DefaultMessageDataProvider defaultMessageDataProvider;
    @Mock
    private com.ghost616.platform.service.message.MessageService messageService;
    @Mock
    private SessionVariableMapper sessionVariableMapper;
    @Mock
    private SessionSkillMapper sessionSkillMapper;

    @InjectMocks
    private SessionServiceImpl sessionService;

    private Session parentSession;
    private Session childSession1;
    private Session childSession2;
    private Session nonChildSession;
    private final Long parentId = 10L;

    @BeforeEach
    void setUp() {
        // 初始化实体表信息，使 LambdaQueryWrapper 可解析 lambda 列名（参考 AgentConfigUserIsolationTest）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Session.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SessionTool.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SessionVariable.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SessionSkill.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Message.class);

        User user = new User();
        user.setId(CURRENT_USER_ID);
        UserContext.set(new UserSession("session-test", user, System.currentTimeMillis()));

        parentSession = new Session();
        parentSession.setId(1L);
        parentSession.setTitle("parent");
        parentSession.setIsChild(false);

        childSession1 = new Session();
        childSession1.setId(2L);
        childSession1.setParentSessionId(parentId);
        childSession1.setIsChild(true);
        childSession1.setTitle("child1");
        childSession1.setDescription("first child");
        childSession1.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        childSession2 = new Session();
        childSession2.setId(3L);
        childSession2.setParentSessionId(parentId);
        childSession2.setIsChild(true);
        childSession2.setTitle("child2");
        childSession2.setDescription("second child");
        childSession2.setCreateTime(LocalDateTime.of(2026, 1, 2, 0, 0));

        nonChildSession = new Session();
        nonChildSession.setId(4L);
        nonChildSession.setParentSessionId(parentId);
        nonChildSession.setIsChild(false);
        nonChildSession.setTitle("non-child");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void listChildSessions_有子会话_返回DTO列表() {
        when(sessionMapper.selectList(any())).thenReturn(List.of(childSession1, childSession2));

        List<SessionDTO> result = sessionService.listChildSessions(parentId);

        assertEquals(2, result.size());
        assertEquals("child1", result.get(0).getTitle());
        assertEquals("first child", result.get(0).getDescription());
        assertTrue(result.get(0).getIsChild());
        assertEquals(parentId, result.get(0).getParentSessionId());

        assertEquals("child2", result.get(1).getTitle());
    }

    @Test
    void listChildSessions_无子会话_返回空列表() {
        when(sessionMapper.selectList(any())).thenReturn(List.of());

        List<SessionDTO> result = sessionService.listChildSessions(parentId);

        assertTrue(result.isEmpty());
    }

    @Test
    void listChildSessions_查询条件包含parentId和isChild() {
        when(sessionMapper.selectList(any())).thenReturn(List.of(childSession1));

        List<SessionDTO> result = sessionService.listChildSessions(parentId);

        assertEquals(1, result.size());
        verify(sessionMapper).selectList(any());
    }

    @Test
    void listChildSessions_按创建时间倒序() {
        when(sessionMapper.selectList(any())).thenReturn(List.of(childSession2, childSession1));

        List<SessionDTO> result = sessionService.listChildSessions(parentId);

        assertEquals(2, result.size());
        assertEquals("child2", result.get(0).getTitle());
        assertEquals("child1", result.get(1).getTitle());
    }

    @Test
    void listChildSessions_非子会话不返回() {
        when(sessionMapper.selectList(any())).thenReturn(List.of());

        List<SessionDTO> result = sessionService.listChildSessions(parentId);

        assertTrue(result.isEmpty());
    }

    @Test
    void toDTO_映射新字段() {
        Session entity = new Session();
        entity.setId(100L);
        entity.setAgentId(200L);
        entity.setModelId(300L);
        entity.setTitle("test-title");
        entity.setSystemPrompt("test-prompt");
        entity.setParentSessionId(50L);
        entity.setIsChild(true);
        entity.setDescription("test-description");
        entity.setTotalTokenUsed(888L);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);

        when(sessionMapper.selectList(any())).thenReturn(List.of(entity));

        List<SessionDTO> result = sessionService.listChildSessions(50L);

        assertEquals(1, result.size());
        SessionDTO dto = result.get(0);
        assertEquals(100L, dto.getId());
        assertEquals(200L, dto.getAgentId());
        assertEquals(300L, dto.getModelId());
        assertEquals("test-title", dto.getTitle());
        assertEquals("test-prompt", dto.getSystemPrompt());
        assertEquals(50L, dto.getParentSessionId());
        assertTrue(dto.getIsChild());
        assertEquals("test-description", dto.getDescription());
        assertEquals(888L, dto.getTotalTokenUsed());
        assertEquals(now, dto.getCreateTime());
        assertEquals(now, dto.getUpdateTime());
    }

    @Test
    void toDTO_parentSessionId为null_不报错() {
        Session entity = new Session();
        entity.setId(1L);
        entity.setIsChild(false);
        entity.setTitle("no-parent");

        when(sessionMapper.selectList(any())).thenReturn(List.of(entity));

        List<SessionDTO> result = sessionService.listChildSessions(99L);

        assertEquals(1, result.size());
        assertNull(result.get(0).getParentSessionId());
    }

    @Test
    void toDTO_description为null_不报错() {
        Session entity = new Session();
        entity.setId(1L);
        entity.setIsChild(true);
        entity.setParentSessionId(5L);
        entity.setTitle("null-desc");

        when(sessionMapper.selectList(any())).thenReturn(List.of(entity));

        List<SessionDTO> result = sessionService.listChildSessions(5L);

        assertEquals(1, result.size());
        assertNull(result.get(0).getDescription());
    }

    @Test
    void listChildSessions_parentId为null_仍执行查询() {
        sessionService.listChildSessions(null);

        verify(sessionMapper).selectList(any());
    }

    @Test
    void toDTO_totalTokenUsed为null_不报错() {
        Session entity = new Session();
        entity.setId(1L);
        entity.setIsChild(false);
        entity.setTitle("no-tokens");

        when(sessionMapper.selectList(any())).thenReturn(List.of(entity));

        List<SessionDTO> result = sessionService.listChildSessions(99L);

        assertEquals(1, result.size());
        assertNull(result.get(0).getTotalTokenUsed());
    }

    @Test
    void toDTO_totalTokenUsed有值_映射正确() {
        Session entity = new Session();
        entity.setId(1L);
        entity.setIsChild(false);
        entity.setTitle("with-tokens");
        entity.setTotalTokenUsed(12345L);

        when(sessionMapper.selectList(any())).thenReturn(List.of(entity));

        List<SessionDTO> result = sessionService.listChildSessions(99L);

        assertEquals(1, result.size());
        assertEquals(12345L, result.get(0).getTotalTokenUsed());
    }

    @Test
    void toDTO_totalTokenUsed为0_映射正确() {
        Session entity = new Session();
        entity.setId(1L);
        entity.setIsChild(false);
        entity.setTitle("zero-tokens");
        entity.setTotalTokenUsed(0L);

        when(sessionMapper.selectList(any())).thenReturn(List.of(entity));

        List<SessionDTO> result = sessionService.listChildSessions(99L);

        assertEquals(1, result.size());
        assertEquals(0L, result.get(0).getTotalTokenUsed());
    }

    // ========== listSessions isEvaluation 过滤 ==========

    @Test
    void listSessions_过滤isEvaluation为false() {
        Session normalSession = new Session();
        normalSession.setId(1L);
        normalSession.setTitle("normal");
        normalSession.setIsEvaluation(false);
        normalSession.setIsChild(false);

        when(sessionMapper.selectList(any())).thenReturn(List.of(normalSession));

        List<SessionDTO> result = sessionService.listSessions(null);

        assertEquals(1, result.size());
        assertFalse(result.get(0).getIsEvaluation());
    }

    @Test
    void listSessions_当agentId不为空_添加agentId条件() {
        Session session = new Session();
        session.setId(1L);
        session.setAgentId(100L);
        session.setIsEvaluation(false);
        session.setIsChild(false);
        session.setTitle("agent-specific");

        when(sessionMapper.selectList(any())).thenReturn(List.of(session));

        List<SessionDTO> result = sessionService.listSessions(100L);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getAgentId());
    }

    @Test
    void listSessions_查询条件包含当前用户ID过滤() {
        when(sessionMapper.selectList(any())).thenReturn(List.of());

        sessionService.listSessions(null);

        ArgumentCaptor<LambdaQueryWrapper<Session>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sessionMapper).selectList(captor.capture());
        LambdaQueryWrapper<Session> wrapper = captor.getValue();
        // 渲染 SQL 片段以填充 paramNameValuePairs（值与参数映射在渲染时生成）
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(CURRENT_USER_ID),
                "查询条件应包含当前用户ID: " + wrapper.getParamNameValuePairs());
    }

    @Test
    void listSessions_未登录_抛USER_NOT_LOGIN() {
        UserContext.clear();

        BusinessException ex = assertThrows(BusinessException.class, () -> sessionService.listSessions(null));
        assertEquals(ErrorCode.USER_NOT_LOGIN, ex.getErrorCode());
        verify(sessionMapper, never()).selectList(any());
    }

    @Test
    void createSession_填充当前用户ID() {
        doAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            s.setId(999L);
            return null;
        }).when(sessionMapper).insert(any(Session.class));
        when(agentToolMapper.selectList(any())).thenReturn(List.of());

        SessionDTO dto = sessionService.createSession(100L, 300L, "test-title");

        assertEquals(999L, dto.getId());
        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionMapper).insert(captor.capture());
        assertEquals(CURRENT_USER_ID, captor.getValue().getUserId());
        assertEquals(100L, captor.getValue().getAgentId());
        assertEquals(300L, captor.getValue().getModelId());
        assertEquals("test-title", captor.getValue().getTitle());
    }

    @Test
    void createSession_未登录_抛USER_NOT_LOGIN() {
        UserContext.clear();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> sessionService.createSession(100L, 300L, "test-title"));
        assertEquals(ErrorCode.USER_NOT_LOGIN, ex.getErrorCode());
        verify(sessionMapper, never()).insert(any(Session.class));
    }

    @Test
    void listSessions_无匹配会话_返回空列表() {
        when(sessionMapper.selectList(any())).thenReturn(List.of());

        List<SessionDTO> result = sessionService.listSessions(100L);

        assertTrue(result.isEmpty());
    }

    @Test
    void listSessions_查询条件包含isEvaluation等于false() {
        when(sessionMapper.selectList(any())).thenReturn(List.of());

        sessionService.listSessions(100L);

        verify(sessionMapper).selectList(any());
    }

    @Test
    void listSessions_按创建时间倒序() {
        Session s1 = new Session();
        s1.setId(1L);
        s1.setTitle("older");
        s1.setIsEvaluation(false);
        s1.setIsChild(false);
        s1.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        Session s2 = new Session();
        s2.setId(2L);
        s2.setTitle("newer");
        s2.setIsEvaluation(false);
        s2.setIsChild(false);
        s2.setCreateTime(LocalDateTime.of(2026, 1, 2, 0, 0));

        when(sessionMapper.selectList(any())).thenReturn(List.of(s2, s1));

        List<SessionDTO> result = sessionService.listSessions(null);

        assertEquals(2, result.size());
        assertEquals("newer", result.get(0).getTitle());
        assertEquals("older", result.get(1).getTitle());
    }

    // ========== listLogSessions 日志会话查询（含评估会话） ==========

    @Test
    void listLogSessions_返回所有主会话_含评估会话() {
        Session normal = new Session();
        normal.setId(1L);
        normal.setTitle("normal");
        normal.setIsEvaluation(false);
        normal.setIsChild(false);

        Session eval = new Session();
        eval.setId(2L);
        eval.setTitle("eval");
        eval.setIsEvaluation(true);
        eval.setIsChild(false);

        when(sessionMapper.selectList(any())).thenReturn(List.of(normal, eval));

        List<SessionDTO> result = sessionService.listLogSessions();

        assertEquals(2, result.size());
        assertFalse(result.get(0).getIsEvaluation());
        assertTrue(result.get(1).getIsEvaluation());
    }

    @Test
    void listLogSessions_不过滤isEvaluation但过滤子会话() {
        when(sessionMapper.selectList(any())).thenReturn(List.of());

        sessionService.listLogSessions();

        ArgumentCaptor<LambdaQueryWrapper<Session>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sessionMapper).selectList(captor.capture());
        LambdaQueryWrapper<Session> wrapper = captor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        assertFalse(sqlSegment.contains("is_evaluation"), "不应包含 is_evaluation 条件: " + sqlSegment);
        assertTrue(sqlSegment.contains("is_child"), "应包含 is_child 条件: " + sqlSegment);
    }

    @Test
    void listLogSessions_查询条件包含当前用户ID过滤() {
        when(sessionMapper.selectList(any())).thenReturn(List.of());

        sessionService.listLogSessions();

        ArgumentCaptor<LambdaQueryWrapper<Session>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sessionMapper).selectList(captor.capture());
        LambdaQueryWrapper<Session> wrapper = captor.getValue();
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(CURRENT_USER_ID),
                "查询条件应包含当前用户ID: " + wrapper.getParamNameValuePairs());
    }

    @Test
    void listLogSessions_按创建时间倒序() {
        Session s1 = new Session();
        s1.setId(1L);
        s1.setTitle("older");
        s1.setIsEvaluation(false);
        s1.setIsChild(false);
        s1.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        Session s2 = new Session();
        s2.setId(2L);
        s2.setTitle("newer");
        s2.setIsEvaluation(true);
        s2.setIsChild(false);
        s2.setCreateTime(LocalDateTime.of(2026, 1, 2, 0, 0));

        when(sessionMapper.selectList(any())).thenReturn(List.of(s2, s1));

        List<SessionDTO> result = sessionService.listLogSessions();

        assertEquals(2, result.size());
        assertEquals("newer", result.get(0).getTitle());
        assertEquals("older", result.get(1).getTitle());
    }

    @Test
    void listLogSessions_DTO映射isChild和parentSessionId字段() {
        Session main = new Session();
        main.setId(10L);
        main.setTitle("主会话");
        main.setIsChild(false);
        main.setParentSessionId(null);
        main.setIsEvaluation(false);

        when(sessionMapper.selectList(any())).thenReturn(List.of(main));

        List<SessionDTO> result = sessionService.listLogSessions();

        assertEquals(1, result.size());
        assertEquals(Boolean.FALSE, result.get(0).getIsChild());
        assertNull(result.get(0).getParentSessionId());
        assertEquals(10L, result.get(0).getId());
    }

    @Test
    void listLogSessions_未登录_抛USER_NOT_LOGIN() {
        UserContext.clear();

        BusinessException ex = assertThrows(BusinessException.class, () -> sessionService.listLogSessions());
        assertEquals(ErrorCode.USER_NOT_LOGIN, ex.getErrorCode());
        verify(sessionMapper, never()).selectList(any());
    }

    // ========== getMessages ==========

    @Test
    void getMessages_会话存在_调用MessageService并转换为DTO() {
        Session entity = new Session();
        entity.setId(100L);
        entity.setIsChild(false);
        entity.setTitle("test");
        when(sessionMapper.selectById(100L)).thenReturn(entity);

        com.ghost616.platform.entity.Message msg = new com.ghost616.platform.entity.Message();
        msg.setId(1L);
        msg.setSessionId(100L);
        msg.setRole("user");
        msg.setContent("hello");
        msg.setSequenceNum(1);
        when(messageService.getAllMessages(100L)).thenReturn(List.of(msg));

        SessionMessageDTO dto = SessionMessageDTO.builder()
                .id("1")
                .sessionId("100")
                .role("user")
                .content("hello")
                .sequenceNum(1)
                .toolCalls(List.of())
                .rollback(false)
                .build();
        when(defaultMessageDataProvider.toSessionMessageDTOs(List.of(msg))).thenReturn(List.of(dto));

        List<SessionMessageDTO> result = sessionService.getMessages(100L);

        assertEquals(1, result.size());
        assertEquals("hello", result.get(0).getContent());
        verify(messageService).getAllMessages(100L);
        verify(defaultMessageDataProvider).toSessionMessageDTOs(List.of(msg));
        verify(sessionManager, never()).getMessages(any());
    }

    @Test
    void getMessages_会话不存在_抛SESSION_NOT_FOUND() {
        when(sessionMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> sessionService.getMessages(999L));
        verify(messageService, never()).getAllMessages(any());
    }

    @Test
    void getMessages_userInput为null_返回全部消息() {
        Session entity = new Session();
        entity.setId(100L);
        entity.setIsChild(false);
        entity.setTitle("test");
        when(sessionMapper.selectById(100L)).thenReturn(entity);

        com.ghost616.platform.entity.Message msg1 = new com.ghost616.platform.entity.Message();
        msg1.setId(1L);
        msg1.setSessionId(100L);
        msg1.setRole("user");
        msg1.setContent("real");
        msg1.setSequenceNum(1);
        msg1.setUserInput(Boolean.TRUE);
        com.ghost616.platform.entity.Message msg2 = new com.ghost616.platform.entity.Message();
        msg2.setId(2L);
        msg2.setSessionId(100L);
        msg2.setRole("user");
        msg2.setContent("passed");
        msg2.setSequenceNum(2);
        msg2.setUserInput(Boolean.FALSE);
        when(messageService.getAllMessages(100L)).thenReturn(List.of(msg1, msg2));
        when(defaultMessageDataProvider.toSessionMessageDTOs(List.of(msg1, msg2))).thenReturn(List.of());

        sessionService.getMessages(100L, null);

        verify(messageService).getAllMessages(100L);
        verify(defaultMessageDataProvider).toSessionMessageDTOs(List.of(msg1, msg2));
    }

    @Test
    void getMessages_userInput为true_仅返回user真实输入消息() {
        Session entity = new Session();
        entity.setId(100L);
        entity.setIsChild(false);
        entity.setTitle("test");
        when(sessionMapper.selectById(100L)).thenReturn(entity);

        com.ghost616.platform.entity.Message msg1 = new com.ghost616.platform.entity.Message();
        msg1.setId(1L);
        msg1.setSessionId(100L);
        msg1.setRole("user");
        msg1.setContent("real");
        msg1.setSequenceNum(1);
        msg1.setUserInput(Boolean.TRUE);
        com.ghost616.platform.entity.Message msg2 = new com.ghost616.platform.entity.Message();
        msg2.setId(2L);
        msg2.setSessionId(100L);
        msg2.setRole("user");
        msg2.setContent("passed");
        msg2.setSequenceNum(2);
        msg2.setUserInput(Boolean.FALSE);
        com.ghost616.platform.entity.Message msg3 = new com.ghost616.platform.entity.Message();
        msg3.setId(3L);
        msg3.setSessionId(100L);
        msg3.setRole("assistant");
        msg3.setContent("answer");
        msg3.setSequenceNum(3);
        msg3.setUserInput(Boolean.FALSE);
        when(messageService.getAllMessages(100L)).thenReturn(List.of(msg1, msg2, msg3));

        sessionService.getMessages(100L, Boolean.TRUE);

        verify(messageService).getAllMessages(100L);
        verify(defaultMessageDataProvider).toSessionMessageDTOs(List.of(msg1));
    }

    // ========== getMessagesByConversationId ==========

    @Test
    void getMessagesByConversationId_有消息_返回DTO列表() {
        com.ghost616.platform.entity.Message msg = new com.ghost616.platform.entity.Message();
        msg.setId(1L);
        msg.setConversationId("conv-1");
        msg.setRole("user");
        msg.setContent("hello");
        msg.setSequenceNum(1);
        when(messageMapper.selectByConversationId("conv-1")).thenReturn(List.of(msg));

        SessionMessageDTO dto = SessionMessageDTO.builder()
                .id("1")
                .role("user")
                .content("hello")
                .sequenceNum(1)
                .toolCalls(List.of())
                .rollback(false)
                .conversationId("conv-1")
                .build();
        when(defaultMessageDataProvider.toSessionMessageDTOs(List.of(msg))).thenReturn(List.of(dto));

        List<SessionMessageDTO> result = sessionService.getMessagesByConversationId("conv-1");

        assertEquals(1, result.size());
        assertEquals("hello", result.get(0).getContent());
        verify(messageMapper).selectByConversationId("conv-1");
        verify(defaultMessageDataProvider).toSessionMessageDTOs(List.of(msg));
    }

    @Test
    void getMessagesByConversationId_无消息_返回空列表() {
        when(messageMapper.selectByConversationId("conv-empty")).thenReturn(List.of());
        when(defaultMessageDataProvider.toSessionMessageDTOs(List.of())).thenReturn(List.of());

        List<SessionMessageDTO> result = sessionService.getMessagesByConversationId("conv-empty");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ========== toDTO isEvaluation 映射 ==========

    @Test
    void toDTO_isEvaluation为true_映射正确() {
        Session entity = new Session();
        entity.setId(1L);
        entity.setIsChild(false);
        entity.setTitle("eval-true");
        entity.setIsEvaluation(true);

        when(sessionMapper.selectList(any())).thenReturn(List.of(entity));

        List<SessionDTO> result = sessionService.listSessions(1L);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsEvaluation());
    }

    @Test
    void toDTO_isEvaluation为false_映射正确() {
        Session entity = new Session();
        entity.setId(1L);
        entity.setIsChild(false);
        entity.setTitle("eval-false");
        entity.setIsEvaluation(false);

        when(sessionMapper.selectList(any())).thenReturn(List.of(entity));

        List<SessionDTO> result = sessionService.listSessions(1L);

        assertEquals(1, result.size());
        assertFalse(result.get(0).getIsEvaluation());
    }

    @Test
    void toDTO_isEvaluation为null_不报错() {
        Session entity = new Session();
        entity.setId(1L);
        entity.setIsChild(false);
        entity.setTitle("eval-null");

        when(sessionMapper.selectList(any())).thenReturn(List.of(entity));

        List<SessionDTO> result = sessionService.listSessions(1L);

        assertEquals(1, result.size());
        assertNull(result.get(0).getIsEvaluation());
    }

    // ========== deleteSession 级联假删 ==========

    @Test
    void deleteSession_会话不存在_抛SESSION_NOT_FOUND() {
        when(sessionMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> sessionService.deleteSession(999L));
        assertEquals(ErrorCode.SESSION_NOT_FOUND, ex.getErrorCode());
        verify(sessionMapper, never()).deleteById(any());
        verify(sessionToolMapper, never()).delete(any());
        verify(sessionVariableMapper, never()).delete(any());
        verify(sessionSkillMapper, never()).delete(any());
        verify(messageMapper, never()).delete(any());
    }

    @Test
    void deleteSession_级联假删_各关联数据与缓存清理() {
        Session entity = new Session();
        entity.setId(100L);
        entity.setIsChild(false);
        entity.setTitle("to-delete");
        when(sessionMapper.selectById(100L)).thenReturn(entity);

        sessionService.deleteSession(100L);

        // 会话工具/变量/技能关联假删（@TableLogic delete → UPDATE deleted=1）
        verify(sessionToolMapper).delete(any());
        verify(sessionVariableMapper).delete(any());
        verify(sessionSkillMapper).delete(any());
        // 消息假删（message 数据源）
        verify(messageMapper).delete(any());
        // 会话假删（@TableLogic deleteById → UPDATE session SET deleted=1）
        verify(sessionMapper).deleteById(100L);
        // 上下文与工具缓存清理保留
        verify(agentContextManager).remove("100");
        verify(toolManager).clearSessionCache("100");
    }

    @Test
    void deleteSession_各假删条件按会话ID过滤() {
        Session entity = new Session();
        entity.setId(100L);
        when(sessionMapper.selectById(100L)).thenReturn(entity);

        sessionService.deleteSession(100L);

        // session_tool 删除条件按 sessionId 过滤
        ArgumentCaptor<LambdaQueryWrapper<SessionTool>> toolCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sessionToolMapper).delete(toolCaptor.capture());
        LambdaQueryWrapper<SessionTool> toolWrapper = toolCaptor.getValue();
        toolWrapper.getSqlSegment();
        assertTrue(toolWrapper.getParamNameValuePairs().containsValue(100L),
                "session_tool 应过滤 sessionId: " + toolWrapper.getParamNameValuePairs());

        // session_variable 删除条件按 sessionId 过滤
        ArgumentCaptor<LambdaQueryWrapper<SessionVariable>> varCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sessionVariableMapper).delete(varCaptor.capture());
        LambdaQueryWrapper<SessionVariable> varWrapper = varCaptor.getValue();
        varWrapper.getSqlSegment();
        assertTrue(varWrapper.getParamNameValuePairs().containsValue(100L),
                "session_variable 应过滤 sessionId: " + varWrapper.getParamNameValuePairs());

        // session_skill 删除条件按 sessionId 过滤
        ArgumentCaptor<LambdaQueryWrapper<SessionSkill>> skillCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sessionSkillMapper).delete(skillCaptor.capture());
        LambdaQueryWrapper<SessionSkill> skillWrapper = skillCaptor.getValue();
        skillWrapper.getSqlSegment();
        assertTrue(skillWrapper.getParamNameValuePairs().containsValue(100L),
                "session_skill 应过滤 sessionId: " + skillWrapper.getParamNameValuePairs());

        // message 删除条件按 sessionId 过滤
        ArgumentCaptor<LambdaQueryWrapper<Message>> msgCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(messageMapper).delete(msgCaptor.capture());
        LambdaQueryWrapper<Message> msgWrapper = msgCaptor.getValue();
        msgWrapper.getSqlSegment();
        assertTrue(msgWrapper.getParamNameValuePairs().containsValue(100L),
                "message 应过滤 sessionId: " + msgWrapper.getParamNameValuePairs());
    }

    @Test
    void deleteSession_不递归删除子孙会话() {
        Session entity = new Session();
        entity.setId(100L);
        entity.setIsChild(false);
        when(sessionMapper.selectById(100L)).thenReturn(entity);

        sessionService.deleteSession(100L);

        // 仅对当前会话执行一次假删，不按 parentSessionId 查询/删除子孙会话
        verify(sessionMapper).deleteById(100L);
        verify(sessionMapper, never()).selectList(any());
    }
}
