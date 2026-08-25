package com.ghost616.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
@TableName("message_tool_call")
public class MessageToolCall {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("message_id")
    private Long messageId;

    @TableField("tool_call_id")
    private String toolCallId;

    @TableField("tool_call_name")
    private String toolCallName;

    @TableField("tool_call_arguments")
    private String toolCallArguments;

    @TableField("type")
    private String type = "function";

    @TableField("web_search_call")
    private String webSearchCall;

    @TableField("custom_tool_call")
    private String customToolCall;
}
