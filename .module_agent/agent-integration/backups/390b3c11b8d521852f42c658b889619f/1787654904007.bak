package com.ghost616.agentinteg.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.ToolType;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import com.ghost616.agentbase.service.agent.invoker.CustomToolInvoker;
import com.ghost616.agentbase.service.agent.invoker.SubSessionCallback;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class SubSessionCallbackTool extends CustomToolInvoker {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    public static final String TOOL_NAME = "callback_sub_session";

    private final SubSessionCallback callback;

    public SubSessionCallbackTool(ToolConfigDTO toolConfig, SubSessionCallback callback) {
        super(toolConfig);
        this.callback = callback;
    }

    public static ToolConfigDTO createToolConfig() {
        return ToolConfigDTO.builder()
                .id(null)
                .toolType(ToolType.CUSTOM)
                .name(TOOL_NAME)
                .description("创建或复用子会话并通过回调执行用户消息，支持指定工具和技能")
                .parameterSchema("""
                        {
                          "type": "object",
                          "properties": {
                            "sessionName": { "type": "string", "description": "子会话名称；若该名称的子会话已存在则复用其会话发送消息，否则新建子会话" },
                            "description": { "type": "string", "description": "子会话描述" },
                            "toolNames": { "type": "array", "items": { "type": "string" }, "description": "要分配给子会话的工具名称列表" },
                            "skillNames": { "type": "array", "items": { "type": "string" }, "description": "要分配给子会话的技能名称列表" },
                            "thinking": { "type": "boolean", "description": "是否启用思考模式" },
                            "userMessage": { "type": "string", "description": "发送给子会话的用户消息" }
                          },
                          "required": ["sessionName", "userMessage"]
                        }""")
                .build();
    }

    @Override
    public String execute(AgentExecutionContext ctx, String arguments) {
        try {
            JsonNode root = JSON_MAPPER.readTree(arguments);
            String sessionName = root.get("sessionName").asText();
            String userMessage = root.get("userMessage").asText();

            Boolean thinking = root.has("thinking") && !root.get("thinking").isNull()
                    ? root.get("thinking").asBoolean() : null;

            String childSessionId = findExistingChildSessionId(ctx, sessionName);
            if (childSessionId == null) {
                String description = root.has("description") && !root.get("description").isNull()
                        ? root.get("description").asText() : null;

                List<String> toolIds = resolveToolIds(ctx, root.get("toolNames"));
                List<String> skillIds = resolveSkillIds(ctx, root.get("skillNames"));

                childSessionId = ctx.createChildSession(sessionName, description, ctx.getModelId(), toolIds, skillIds, null);
            }

            Message message = callback.execute(ctx, childSessionId, userMessage, thinking);

            return message.getContent();
        } catch (Exception e) {
            log.error("callback_sub_session 执行失败", e);
            try {
                return JSON_MAPPER.writeValueAsString(Map.of("status", "error", "errMsg", e.getMessage()));
            } catch (Exception inner) {
                return "{\"status\":\"error\",\"errMsg\":\"" + inner.getMessage() + "\"}";
            }
        }
    }

    private String findExistingChildSessionId(AgentExecutionContext ctx, String sessionName) {
        List<AgentExecutionContext.ChildSession> childSessions = ctx.getChildSessions();
        if (childSessions == null || childSessions.isEmpty()) {
            return null;
        }
        return childSessions.stream()
                .filter(child -> sessionName.equals(child.sessionName()))
                .map(AgentExecutionContext.ChildSession::sessionId)
                .findFirst()
                .orElse(null);
    }

    private List<String> resolveToolIds(AgentExecutionContext ctx, JsonNode toolNamesNode) {
        if (toolNamesNode == null || toolNamesNode.isNull() || !toolNamesNode.isArray()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (JsonNode node : toolNamesNode) {
            names.add(node.asText());
        }
        if (names.isEmpty()) {
            return null;
        }
        return ctx.getTools().stream()
                .filter(t -> names.contains(t.getName()))
                .map(ToolConfigDTO::getId)
                .distinct()
                .toList();
    }

    private List<String> resolveSkillIds(AgentExecutionContext ctx, JsonNode skillNamesNode) {
        if (skillNamesNode == null || skillNamesNode.isNull() || !skillNamesNode.isArray()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (JsonNode node : skillNamesNode) {
            names.add(node.asText());
        }
        if (names.isEmpty()) {
            return null;
        }
        return ctx.getSkills().stream()
                .filter(s -> names.contains(s.getName()))
                .map(SkillConfigDTO::getId)
                .distinct()
                .toList();
    }
}
