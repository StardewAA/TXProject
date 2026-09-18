package cn.iocoder.txgy.module.mes.service.tm.toolrepair;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolrepair.vo.MesTmToolRepairLineSaveReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolrepair.vo.MesTmToolRepairPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolrepair.vo.MesTmToolRepairSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolrepair.MesTmToolRepairDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolrepair.MesTmToolRepairLineDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * MES 工具维修单 Service 接口
 */
public interface MesTmToolRepairService {

    Long createToolRepair(@Valid MesTmToolRepairSaveReqVO createReqVO);
    void updateToolRepair(@Valid MesTmToolRepairSaveReqVO updateReqVO);
    void deleteToolRepair(Long id);
    MesTmToolRepairDO getToolRepair(Long id);
    PageResult<MesTmToolRepairDO> getToolRepairPage(MesTmToolRepairPageReqVO pageReqVO);
    void confirmToolRepair(Long id);
    void finishToolRepair(Long id);

    // 行
    Long createToolRepairLine(@Valid MesTmToolRepairLineSaveReqVO createReqVO);
    void updateToolRepairLine(@Valid MesTmToolRepairLineSaveReqVO updateReqVO);
    void deleteToolRepairLine(Long id);
    List<MesTmToolRepairLineDO> getToolRepairLineListByRepairId(Long repairId);
}
