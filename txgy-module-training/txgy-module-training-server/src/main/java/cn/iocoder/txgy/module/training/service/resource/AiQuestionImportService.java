package cn.iocoder.txgy.module.training.service.resource;

import cn.iocoder.txgy.module.training.controller.admin.resource.vo.AiQuestionImportReqVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.AiQuestionParseRespVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 试题 AI 智能导入 Service（文档 → 试题）
 *
 * <p>支持 PDF / Word(doc,docx) / WPS(wps) 文档上传，提取正文后交由 AI 大模型
 * 自动识别试题（题干/选项/答案/解析/难度）；当 AI 未配置或调用失败时，
 * 自动降级为本地规则解析，保证功能可用。</p>
 *
 * @author 云培训学院课题小组
 */
public interface AiQuestionImportService {

    /**
     * 解析文档并返回识别出的试题预览（不落库）
     */
    AiQuestionParseRespVO parseDocument(MultipartFile file);

    /**
     * 确认导入：将预览试题批量落库，整批统一归入指定分类并打上指定标签
     *
     * @param reqVO 试题列表 + 所属分类 + 标签集合
     * @return 成功导入的试题数量
     */
    int importQuestions(AiQuestionImportReqVO reqVO);

}
