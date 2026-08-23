package com.ghost616.platform.repository;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.ghost616.platform.entity.Message;

import java.util.List;


@Mapper
@DS("message")
public interface MessageMapper extends BaseMapper<Message> {

    @Update("UPDATE message SET rollback=1 WHERE session_id = #{sessionId} AND sequence_num >= #{sequenceNum}")
    int rollbackBySessionIdAndGeSequenceNum(Long sessionId, Integer sequenceNum);

    @Select("SELECT * FROM message WHERE conversation_id = #{conversationId} AND rollback = 0 ORDER BY create_time ASC")
    List<Message> selectByConversationId(@Param("conversationId") String conversationId);

    @Select("SELECT COUNT(*) FROM message WHERE session_id = #{sessionId} AND role = 'user' AND rollback = 0")
    Long countUserMessages(@Param("sessionId") Long sessionId);

    @Select("SELECT sequence_num FROM message WHERE session_id = #{sessionId} AND role = 'user' AND rollback = 0 ORDER BY sequence_num ASC LIMIT 1 OFFSET #{n}")
    Integer findNthUserSequenceNum(@Param("sessionId") Long sessionId, @Param("n") int n);
}
