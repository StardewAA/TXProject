package cn.iocoder.txgy.module.mes.service.wms.integration;

import cn.iocoder.txgy.module.mes.dal.dataobject.wms.integration.MesWmsIntegrationTaskDO;
import cn.iocoder.txgy.module.mes.enums.wms.MesWmsIntegrationOrderTypeEnum;

import java.util.Map;

/**
 * WMS 集成任务终态处理器
 *
 * 框架在任务到达终态（成功/失败/拒绝/取消）时，按 orderType 分发给对应业务的处理器，
 * 由业务实现回写自身单据状态（如领料单 → 已出库/回退草稿）。
 *
 * 处理器异常不应影响框架状态落库（框架侧已 catch 并记录）。
 *
 * @author OPENLAB BS
 */
public interface MesWmsIntegrationResultHandler {

    /** 本处理器支持的业务类型 */
    MesWmsIntegrationOrderTypeEnum supportedOrderType();

    /**
     * 任务到达终态时回调（在租户上下文中执行）
     *
     * @param task        集成任务（status 已是终态，含 resultPayload）
     * @param wmsStatus   WMS 处理状态（SUCCESS/EXECUTION_FAILED/REJECTED/CANCELLED）
     * @param resultData  WMS 结果 data 节点（含 items 明细与数量），可能为 null
     */
    void onTerminal(MesWmsIntegrationTaskDO task, String wmsStatus, Map<String, Object> resultData);

}
