package cn.iocoder.txgy.module.cms.service.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CmsRagServiceTest {

    @Test
    void shouldExtractLawSubjectFromNaturalLanguageQuestion() {
        assertEquals("安全生产法", CmsRagService.extractQuestionSubject("安全生产法主要规定了什么？"));
        assertEquals("中华人民共和国安全生产法", CmsRagService.extractQuestionSubject("《中华人民共和国安全生产法》规定了什么？"));
        assertEquals("危险化学品经营许可", CmsRagService.extractQuestionSubject("危险化学品经营许可怎么办理？"));
        assertEquals("补贴", CmsRagService.extractQuestionSubject("补贴是什么？"));
    }
}
