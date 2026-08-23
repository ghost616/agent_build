package com.ghost616.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 消息图片映射实体（message_image 表），记录消息关联的图片标识与大模型图像理解结果文本。
 */
@Data
@TableName("message_image")
public class MessageImage {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("message_id")
    private Long messageId;

    @TableField("img_id")
    private String imgId;

    @TableField("img_text")
    private String imgText;
}
