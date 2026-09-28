package cn.iocoder.txgy.module.training.service.resource;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionImportRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionSaveReqVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 试题 Service
 *
 * @author 云培训学院课题小组
 */
public interface QuestionService {

    PageResult<QuestionRespVO> getQuestionPage(QuestionPageReqVO reqVO);

    QuestionRespVO getQuestion(Long id);

    Long createQuestion(QuestionSaveReqVO reqVO);

    void updateQuestion(QuestionSaveReqVO reqVO);

    /**
     * 启用/停用（ENABLE / DISABLE）
     */
    void changeQuestionStatus(Long id, String action);

    /**
     * 逻辑删除（被试卷引用时禁止删除）
     */
    void deleteQuestion(Long id);

    /**
     * 下载导入模板（xlsx）
     */
    byte[] downloadImportTemplate();

    /**
     * Excel 批量导入（先逐行校验，合法行入库，错误行汇总返回）
     */
    QuestionImportRespVO importQuestions(MultipartFile file);

}
