package com.ghost616.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("session_id")
    private Long sessionId;

    private String role;

    private String content;

    private String reasoning;

    @TableField("sequence_num")
    private Integer sequenceNum;

    @TableField("tool_call_id")
    private String toolCallId;

    @TableField("tool_result")
    private String toolResult;

    @TableField("token_usage")
    private String tokenUsage;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("rollback")
    private Boolean rollback;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
