package com.ghost616.agentbase.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片内容 DTO，用于支持大模型图像理解。
 * <p>
 * 图片对象数组的基础单元：imgId 仅供前端关联使用（不传给模型），
 * imgText 为图片内容文本（如 base64 编码），随消息传给模型用于图像理解。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageContent {

    /** 图片 ID（仅供前端关联使用，不传给模型） */
    private String imgId;

    /** 图片内容文本（如 base64 编码，传给模型用于图像理解） */
    private String imgText;
}