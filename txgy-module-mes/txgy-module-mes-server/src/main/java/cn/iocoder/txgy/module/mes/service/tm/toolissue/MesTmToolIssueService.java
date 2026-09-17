package cn.iocoder.txgy.module.mes.service.tm.toolissue;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolissue.vo.MesTmToolIssueLineSaveReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolissue.vo.MesTmToolIssuePageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolissue.vo.MesTmToolIssueSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolissue.MesTmToolIssueDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolissue.MesTmToolIssueLineDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * MES 工具领用单 Service 接口
 */
public interface MesTmToolIssueService {

    Long createToolIssue(@Valid MesTmToolIssueSaveReqVO createReqVO);
    void updateToolIssue(@Valid MesTmToolIssueSaveReqVO updateReqVO);
    void deleteToolIssue(Long id);
    MesTmToolIssueDO getToolIssue(Long id);
    PageResult<MesTmToolIssueDO> getToolIssuePage(MesTmToolIssuePageReqVO pageReqVO);
    void confirmToolIssue(Long id);

    // 行
    Long createToolIssueLine(@Valid MesTmToolIssueLineSaveReqVO createReqVO);
    void updateToolIssueLine(@Valid MesTmToolIssueLineSaveReqVO updateReqVO);
    void deleteToolIssueLine(Long id);
    List<MesTmToolIssueLineDO> getToolIssueLineListByIssueId(Long issueId);
}
