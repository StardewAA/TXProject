package cn.iocoder.txgy.module.erp.service.stock;

import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.mirror.ErpStockReconciliationRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.mirror.ErpStockUnmappedRespVO;

import java.util.List;

/**
 * ERP 实物库存镜像 Service
 *
 * <p>WMS 是实物库存唯一权威，本服务把 WMS 库存同步为 ERP 侧的只读镜像，
 * 并与业务账 erp_stock 做对账。同步采用<b>旁路</b>方式，不参与 ERP 出入库主流程，
 * 因此 WMS 不可用时不会阻塞 ERP 业务。</p>
 *
 * @author OPENLAB BS
 */
public interface ErpStockMirrorService {

    /**
     * 全量同步：读取 WMS 实物库存，写入/更新 ERP 实物账镜像。
     *
     * @return 同步的「产品 × 仓库」条数
     */
    int syncMirror();

    /**
     * 库存对账：业务账（ERP 单据推演）与实物账（WMS 同步）逐项比对。
     *
     * @return 对账明细（含差额），只返回两侧至少有一侧存在的组合
     */
    List<ErpStockReconciliationRespVO> getReconciliation();

    /**
     * 映射缺失诊断：暴露「为什么同步不出来」。
     */
    ErpStockUnmappedRespVO getUnmappedDiagnostics();

}
