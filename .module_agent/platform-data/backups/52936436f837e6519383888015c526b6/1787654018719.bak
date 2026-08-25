package com.ghost616.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ghost616.agentbase.enums.SessionAuthType;
import lombok.Data;

@Data
@TableName("session_skill")
public class SessionSkill {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    @TableField("skill_id")
    private Long skillId;

    @TableField("session_auth")
    private SessionAuthType sessionAuth;
}
