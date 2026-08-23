package com.ghost616.platform.repository;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghost616.platform.entity.MessageImage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息图片映射数据访问层，继承 MyBatis-Plus BaseMapper，
 * 标注 @DS("message") 注解路由至消息数据源，与 message/agent_log/message_tool_call 同库。
 */
@Mapper
@DS("message")
public interface MessageImageMapper extends BaseMapper<MessageImage> {
}
