package cn.iocoder.txgy.module.bidding.service.notice;

import cn.iocoder.txgy.module.bidding.api.vo.TenderNoticeVO;
import cn.iocoder.txgy.module.bidding.enums.NoticeTypeEnum;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公告载荷规整（TDD）：权威字段"一个不能少、一个不能多"。
 */
class NoticePayloadUtilsTest {

    @Test
    void canonicalize_completesAllAuthoritativeFieldsInOrder() throws Exception {
        // 只提交了两个字段，规整后必须补齐 40 字段全集（缺失补 null）
        Map<String, Object> raw = new HashMap<>();
        raw.put("UNIFIED_DEAL_CODE", "E001");
        raw.put("NOTICE_NAME", "某项目招标公告");

        LinkedHashMap<String, Object> result = NoticePayloadUtils.canonicalize(
                NoticeTypeEnum.TENDER_NOTICE, raw);

        RecordComponent[] components = TenderNoticeVO.class.getRecordComponents();
        assertEquals(components.length, result.size(), "字段数必须等于权威数据集字段全集");
        int i = 0;
        for (RecordComponent component : components) {
            String name = component.getName();
            assertTrue(result.containsKey(name), "缺少权威字段：" + name);
            // 顺序必须与数据集声明一致（供接口/字典/导出对齐）
            Object expected = raw.containsKey(name) ? raw.get(name) : null;
            assertEquals(expected, result.get(name), "字段取值不符：" + name);
            assertEquals(component.getName(), new java.util.ArrayList<>(result.keySet()).get(i), "顺序错位：" + name);
            i++;
        }
    }

    @Test
    void canonicalize_dropsUnknownFields() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("NOTICE_NAME", "招标公告");
        raw.put("NOT_A_SPEC_FIELD", "随手加的字段");

        LinkedHashMap<String, Object> result = NoticePayloadUtils.canonicalize(
                NoticeTypeEnum.TENDER_NOTICE, raw);

        assertFalse(result.containsKey("NOT_A_SPEC_FIELD"), "非权威字段必须剔除");
    }

    @Test
    void canonicalize_rejectsUnknownType() {
        assertThrows(IllegalArgumentException.class,
                () -> NoticePayloadUtils.canonicalize(null, new HashMap<>()));
    }

    @Test
    void canonicalize_acceptsEmptyRaw() throws Exception {
        LinkedHashMap<String, Object> result = NoticePayloadUtils.canonicalize(
                NoticeTypeEnum.TENDER_NOTICE, null);
        assertNotNull(result);
        assertEquals(TenderNoticeVO.class.getRecordComponents().length, result.size());
    }
}
