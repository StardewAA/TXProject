package cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NoticeProjectFieldExtractor 提取规则测试（样本来自本地库真实公告：陕西延长石油 DYZB2025-11-G141 系列）。
 */
class NoticeProjectFieldExtractorTest {

    @Test
    void extractsCodeAndName_fromParenLabel_realTenderRow() {
        String title = "炼化公司所属各单位2026年装置大修消缺公开招标公告";
        String html = "<div class=\"notice-html-demo\"><h1>" + title + "</h1>"
                + "<p>炼化公司所属各单位 2026年装置大修/消缺公开招标招标公告（招标编号：DYZB2025-11-G141）"
                + "项目所在地区：炼化公司所属各单位一、招标条件本炼化公司所属各单位 2026年装置大修/消缺公开招标"
                + "已由审批/核准/备案机关批准，项目资金来源为生产经营成本</p></div>";
        NoticeProjectFieldExtractor.ProjectFields pf = NoticeProjectFieldExtractor.extract(title, html);
        assertEquals("DYZB2025-11-G141", pf.projectCode());
        assertTrue(pf.projectName().startsWith("炼化公司所属各单位"), pf.projectName());
        assertTrue(pf.projectName().contains("装置大修"), pf.projectName());
    }

    @Test
    void extractsName_fromParenLabel_inPublicityRow() {
        String title = "炼化公司所属各单位2026年装置大修消缺公开招标中标候选人公示";
        String content = "炼化公司所属各单位 2026 年装置大修/消缺公开招标（招标编号：DYZB2025-11-G141）评标工作已于 2025 年 12 月 22 日完成";
        NoticeProjectFieldExtractor.ProjectFields pf = NoticeProjectFieldExtractor.extract(title, content);
        assertEquals("DYZB2025-11-G141", pf.projectCode());
        assertTrue(pf.projectName().contains("装置大修"), pf.projectName());
    }

    @Test
    void extractsLabelStyle_whenBodyProvidesProjectName() {
        String content = "一、项目名称：某某产业园智能化改造建设项目" + System.lineSeparator() + "项目编号：ZB2025-0001，总投资约 8000 万元。";
        NoticeProjectFieldExtractor.ProjectFields pf = NoticeProjectFieldExtractor.extract("某某项目招标公告", content);
        assertEquals("ZB2025-0001", pf.projectCode());
        assertEquals("某某产业园智能化改造建设项目", pf.projectName());
    }

    @Test
    void extractsCode_fromBareLabel_andName_fromTitleFallback() {
        String title = "某某集团厂区道路改造工程采购公告";
        String content = "本公告依据招标编号：CG2025-0088 进行发布。";
        NoticeProjectFieldExtractor.ProjectFields pf = NoticeProjectFieldExtractor.extract(title, content);
        assertEquals("CG2025-0088", pf.projectCode());
        assertEquals("某某集团厂区道路改造工程", pf.projectName());
    }

    @Test
    void realRow_notPolluted_byNameLabelCrossSentence() {
        String title = "炼化公司所属各单位2026年装置大修消缺公开招标公告";
        String html = "<div class=\"notice-html-demo\"><h1>" + title + "</h1><p>炼化公司所属各单位 2026年装置大修/消缺公开招标招标公告（招标编号：DYZB2025-11-G141）项目所在地区：炼化公司所属各单位一、招标条件本炼化公司所属各单位 2026年装置大修/消缺公开招标已由审批/核准/备案机关批准，项目资金来源为生产经营成本，出资比例 100%，招标人为陕西延长石油（集团）有限责任公司炼化公司，本项目已具备招标条件，现招标方式为公开招标。</p></div>";
        NoticeProjectFieldExtractor.ProjectFields pf = NoticeProjectFieldExtractor.extract(title, html);
        assertEquals("DYZB2025-11-G141", pf.projectCode());
        String name = pf.projectName();
        org.junit.jupiter.api.Assertions.assertFalse(name.contains("招标公告 "), "不应出现标题与正文拼接的污染值: " + name);
        org.junit.jupiter.api.Assertions.assertFalse(name.contains(" 炼化"), "不应跨段合并: " + name);
    }

    @Test
    void returnsNulls_whenNothingRecognizable() {
        NoticeProjectFieldExtractor.ProjectFields pf =
                NoticeProjectFieldExtractor.extract("通知", "<p>无任何有效信息</p>");
        assertNull(pf.projectCode());
        assertNull(pf.projectName());
    }
}
