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

    @Test
    void socialSubset_onlyKeepsSocialPublicFields() {
        LinkedHashMap<String, Object> canonical = NoticePayloadUtils.canonicalize(
                NoticeTypeEnum.TENDER_NOTICE, new HashMap<>());

        LinkedHashMap<String, Object> subset = NoticePayloadUtils.socialSubset(
                NoticeTypeEnum.TENDER_NOTICE, canonical);

        // 社会公开字段必须全部保留、政务公开/未标字段一个都不能出边界
        int socialCount = 0;
        for (java.lang.reflect.RecordComponent component
                : NoticeTypeEnum.TENDER_NOTICE.getDatasetClass().getRecordComponents()) {
            cn.iocoder.txgy.module.bidding.api.enums.PublicLevel level =
                    component.getAnnotation(cn.iocoder.txgy.module.bidding.api.enums.PublicLevel.class);
            if (level != null && level.value()
                    == cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum.SOCIAL) {
                socialCount++;
                assertTrue(subset.containsKey(component.getName()),
                        "社会公开字段必须保留：" + component.getName());
            } else {
                assertFalse(subset.containsKey(component.getName()),
                        "政务公开/未标字段不得出边界：" + component.getName());
            }
        }
        assertEquals(socialCount, subset.size());
        assertFalse(subset.containsKey("NOT_A_SPEC_FIELD"));
    }

    @Test
    void missingRequired_countsMatchAuthorityDoc() {
        // 权威《VO数据规范》各数据集“必填=是”数量：招标公告28 / 变更公告15 / 中标候选人公示28 / 中标结果公示22
        assertEquals(28, NoticePayloadUtils.missingRequired(NoticeTypeEnum.TENDER_NOTICE,
                NoticePayloadUtils.canonicalize(NoticeTypeEnum.TENDER_NOTICE, null)).size());
        assertEquals(15, NoticePayloadUtils.missingRequired(NoticeTypeEnum.CHANGE_NOTICE,
                NoticePayloadUtils.canonicalize(NoticeTypeEnum.CHANGE_NOTICE, null)).size());
        assertEquals(28, NoticePayloadUtils.missingRequired(NoticeTypeEnum.WIN_CANDIDATE_PUBLICITY,
                NoticePayloadUtils.canonicalize(NoticeTypeEnum.WIN_CANDIDATE_PUBLICITY, null)).size());
        assertEquals(22, NoticePayloadUtils.missingRequired(NoticeTypeEnum.WIN_RESULT_PUBLICITY,
                NoticePayloadUtils.canonicalize(NoticeTypeEnum.WIN_RESULT_PUBLICITY, null)).size());
    }
}
