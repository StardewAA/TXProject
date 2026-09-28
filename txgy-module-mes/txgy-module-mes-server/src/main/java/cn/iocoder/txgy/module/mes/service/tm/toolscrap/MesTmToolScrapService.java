package cn.iocoder.txgy.module.mes.service.tm.toolscrap;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolscrap.vo.MesTmToolScrapLineSaveReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolscrap.vo.MesTmToolScrapPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolscrap.vo.MesTmToolScrapSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolscrap.MesTmToolScrapDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolscrap.MesTmToolScrapLineDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * MES 工具报废单 Service 接口
 */
public interface MesTmToolScrapService {

    Long createToolScrap(@Valid MesTmToolScrapSaveReqVO createReqVO);
    void updateToolScrap(@Valid MesTmToolScrapSaveReqVO updateReqVO);
    void deleteToolScrap(Long id);
    MesTmToolScrapDO getToolScrap(Long id);
    PageResult<MesTmToolScrapDO> getToolScrapPage(MesTmToolScrapPageReqVO pageReqVO);
    void confirmToolScrap(Long id);

    // 行
    Long createToolScrapLine(@Valid MesTmToolScrapLineSaveReqVO createReqVO);
    void updateToolScrapLine(@Valid MesTmToolScrapLineSaveReqVO updateReqVO);
    void deleteToolScrapLine(Long id);
    List<MesTmToolScrapLineDO> getToolScrapLineListByScrapId(Long scrapId);
}
