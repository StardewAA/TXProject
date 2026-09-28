package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsInventoryTransactionReqDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsInventoryTransactionRespDTO;

/**
 * ERP 接收 WMS「库存事务」Service 接口（方案A：WMS 作为实物唯一数据源）
 *
 * <p>WMS 每发生一笔实物异动（入库 / 出库 / 退货 / 盘点调整），调用本服务对应的回调接口，
 * ERP 侧自动完成三件事：</p>
 * <ol>
 *     <li>按事务类型生成 ERP 单据（其它入库单 / 其它出库单），并标记来源系统（MES / MALL / ERP / MANUAL）；</li>
 *     <li>自动过账，写入库存流水并更新账面库存；</li>
 *     <li>写入 {@code erp_wms_integration_record} 集成日志，支持幂等重推与失败排查。</li>
 * </ol>
 *
 * @author OPENLAB BS
 */
public interface ErpWmsInventoryTransactionService {

    /**
     * 接收 WMS 推送的库存事务
     *
     * @param reqDTO 库存事务
     * @return 处理结果（含 ERP 生成的单据类型与单号）
     */
    ErpWmsInventoryTransactionRespDTO receiveInventoryTransaction(ErpWmsInventoryTransactionReqDTO reqDTO);

}
