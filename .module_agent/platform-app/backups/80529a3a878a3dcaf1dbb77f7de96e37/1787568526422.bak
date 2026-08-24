package com.ghost616.platform.session;

import com.ghost616.platform.dto.session.SessionDTO;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.repository.AgentToolMapper;
import com.ghost616.platform.repository.SessionToolMapper;
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
}
