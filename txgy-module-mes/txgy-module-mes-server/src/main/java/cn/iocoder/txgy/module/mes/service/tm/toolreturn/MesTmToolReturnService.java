package cn.iocoder.txgy.module.mes.service.tm.toolreturn;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolreturn.vo.MesTmToolReturnLineSaveReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolreturn.vo.MesTmToolReturnPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolreturn.vo.MesTmToolReturnSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolreturn.MesTmToolReturnDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolreturn.MesTmToolReturnLineDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * MES 工具归还单 Service 接口
 */
public interface MesTmToolReturnService {

    Long createToolReturn(@Valid MesTmToolReturnSaveReqVO createReqVO);
    void updateToolReturn(@Valid MesTmToolReturnSaveReqVO updateReqVO);
    void deleteToolReturn(Long id);
    MesTmToolReturnDO getToolReturn(Long id);
    PageResult<MesTmToolReturnDO> getToolReturnPage(MesTmToolReturnPageReqVO pageReqVO);
    void confirmToolReturn(Long id);

    // 行
    Long createToolReturnLine(@Valid MesTmToolReturnLineSaveReqVO createReqVO);
    void updateToolReturnLine(@Valid MesTmToolReturnLineSaveReqVO updateReqVO);
    void deleteToolReturnLine(Long id);
    List<MesTmToolReturnLineDO> getToolReturnLineListByReturnId(Long returnId);
}
