package com.ghost616.platform.repository;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghost616.platform.entity.MessageToolCall;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
@DS("message")
public interface MessageToolCallMapper extends BaseMapper<MessageToolCall> {

    @Delete("<script>DELETE FROM message_tool_call WHERE message_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteByMessageIds(@Param("ids") List<Long> ids);
}
