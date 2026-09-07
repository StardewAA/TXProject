package cn.iocoder.txgy.module.mes.service.set.pollutionledger;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutionledger.vo.MesPollutionLedgerPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutionledger.vo.MesPollutionLedgerStatusReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutionledger.MesPollutionLedgerDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutionledger.MesPollutionLedgerLogDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * MES 安全环保检测-污染/危废暂存台账 Service 接口
 *
 * 台账行由"复核=有污染"自动登记（管控服务 applyReviewEffect），本服务只负责
 * 处置流转（page 查看 + status 流转）；流转到终态(已回用/已排放/已处置)时重投影对应批次戳
 * （同批全部未闭环行闭环后才清戳；CLEARED=无污染复核自动解除，非本服务人工流转目标）。
 *
 * @author OPENLAB BS
 */
public interface MesPollutionLedgerService {

    /**
     * 处置流转：STORED(暂存)→PROCESSING(处置中)→{REUSED/已回用, DISCHARGED/已排放, DISPOSED/已处置}
     * 仅开放行(暂存/处置中)可流转；已闭环(处置终态/CLEARED)禁止 reopen。
     * 置终态时重投影批次戳：该批仍开放的行继续拦截，全部闭环才清戳放行。
     *
     * @param reqVO 流转信息
     */
    void updateLedgerStatus(@Valid MesPollutionLedgerStatusReqVO reqVO);

    /**
     * 获得污染/危废暂存台账
     *
     * @param id 编号
     * @return 台账
     */
    MesPollutionLedgerDO getLedger(Long id);

    /**
     * 获得污染/危废暂存台账分页
     *
     * @param pageReqVO 分页查询
     * @return 台账分页
     */
    PageResult<MesPollutionLedgerDO> getLedgerPage(MesPollutionLedgerPageReqVO pageReqVO);

    /**
     * 获得某条台账的完整流转链（登记→处置→闭环，时间正序；只增不改）
     *
     * @param ledgerId 台账ID
     * @return 流转历史
     */
    List<MesPollutionLedgerLogDO> getLedgerLogList(Long ledgerId);

}
