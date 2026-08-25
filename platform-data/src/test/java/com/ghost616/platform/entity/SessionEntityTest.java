package com.ghost616.platform.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SessionEntityTest {

    @Test
    void memoryPointSequenceNumField() throws Exception {
        Field field = Session.class.getDeclaredField("memoryPointSequenceNum");
        assertNotNull(field);
        assertEquals(Integer.class, field.getType());
        assertEquals("memory_point_sequence_num", field.getAnnotation(TableField.class).value());

        Session session = new Session();
        session.setMemoryPointSequenceNum(5);
        assertEquals(5, session.getMemoryPointSequenceNum());
    }

    @Test
    void memoryPromptField() throws Exception {
        Field field = Session.class.getDeclaredField("memoryPrompt");
        assertNotNull(field);
        assertEquals(String.class, field.getType());
        assertEquals("memory_prompt", field.getAnnotation(TableField.class).value());

        Session session = new Session();
        session.setMemoryPrompt("请记住用户的偏好");
        assertEquals("请记住用户的偏好", session.getMemoryPrompt());
    }

    @Test
    void sessionInheritsLogicDeletedFieldFromBaseEntity() throws Exception {
        Field field = BaseEntity.class.getDeclaredField("deleted");
        assertNotNull(field);
        assertEquals(Integer.class, field.getType());
        TableLogic tableLogic = field.getAnnotation(TableLogic.class);
        assertNotNull(tableLogic);
        assertEquals("0", tableLogic.value());
        assertEquals("1", tableLogic.delval());

        Session session = new Session();
        session.setDeleted(1);
        assertEquals(1, session.getDeleted());
    }

    @Test
    void sessionToolDeletedField() throws Exception {
        Field field = SessionTool.class.getDeclaredField("deleted");
        assertNotNull(field);
        assertEquals(Integer.class, field.getType());
        TableLogic tableLogic = field.getAnnotation(TableLogic.class);
        assertNotNull(tableLogic);
        assertEquals("0", tableLogic.value());
        assertEquals("1", tableLogic.delval());

        SessionTool sessionTool = new SessionTool();
        sessionTool.setDeleted(1);
        assertEquals(1, sessionTool.getDeleted());
    }

    @Test
    void sessionSkillDeletedField() throws Exception {
        Field field = SessionSkill.class.getDeclaredField("deleted");
        assertNotNull(field);
        assertEquals(Integer.class, field.getType());
        TableLogic tableLogic = field.getAnnotation(TableLogic.class);
        assertNotNull(tableLogic);
        assertEquals("0", tableLogic.value());
        assertEquals("1", tableLogic.delval());

        SessionSkill sessionSkill = new SessionSkill();
        sessionSkill.setDeleted(1);
        assertEquals(1, sessionSkill.getDeleted());
    }
}
