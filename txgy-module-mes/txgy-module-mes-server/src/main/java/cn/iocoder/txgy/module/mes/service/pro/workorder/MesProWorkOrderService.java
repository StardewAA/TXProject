package cn.iocoder.txgy.module.mes.service.pro.workorder;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.controller.admin.pro.workorder.vo.MesProWorkOrderPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.workorder.vo.MesProWorkOrderSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;

/**
 * MES 生产工单 Service 接口
 *
 * @author OPENLAB BS
 */
public interface MesProWorkOrderService {

    /**
     * 创建生产工单
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createWorkOrder(@Valid MesProWorkOrderSaveReqVO createReqVO);

    /**
     * 更新生产工单
     *
     * @param updateReqVO 更新信息
     */
    void updateWorkOrder(@Valid MesProWorkOrderSaveReqVO updateReqVO);

    /**
     * 删除生产工单
     *
     * @param id 编号
     */
    void deleteWorkOrder(Long id);

    /**
     * 校验生产工单存在
     *
     * @param id 编号
     * @return 生产工单
     */
    MesProWorkOrderDO validateWorkOrderExists(Long id);

    /**
     * 获得生产工单
     *
     * @param id 编号
     * @return 生产工单
     */
    MesProWorkOrderDO getWorkOrder(Long id);

    /**
     * 根据编码获得生产工单
     *
     * @param code 编码
     * @return 生产工单
     */
    MesProWorkOrderDO getWorkOrder(String code);

    /**
     * 获得生产工单分页
     *
     * @param pageReqVO 分页查询
     * @return 生产工单分页
     */
    PageResult<MesProWorkOrderDO> getWorkOrderPage(MesProWorkOrderPageReqVO pageReqVO);

    /**
     * 获得工单列表
     *
     * @param ids 编号数组
     * @return 工单列表
     */
    List<MesProWorkOrderDO> getWorkOrderList(Collection<Long> ids);

    /**
     * 获得工单 Map
     *
     * @param ids 编号数组
     * @return 工单 Map
     */
    default Map<Long, MesProWorkOrderDO> getWorkOrderMap(Collection<Long> ids) {
        return convertMap(getWorkOrderList(ids), MesProWorkOrderDO::getId);
    }

    /**
     * 校验工单已确认
     *
     * @param id 编号
     * @return 工单
     */
    MesProWorkOrderDO validateWorkOrderConfirmed(Long id);

    /**
     * 确认工单（草稿 → 已确认）
     *
     * @param id 编号
     */
    void confirmWorkOrder(Long id);

    /**
     * 完成工单
     *
     * @param id 编号
     */
    void finishWorkOrder(Long id);

    /**
     * 取消工单
     *
     * @param id 编号
     */
    void cancelWorkOrder(Long id);

    /**
     * 下发工单（已确认 → 已下发）
     *
     * @param id 编号
     */
    void issueWorkOrder(Long id);

    /**
     * 若工单处于已下发状态，则置为生产中（首次报工触发，幂等）
     *
     * @param id 工单编号
     */
    void markInProductionIfIssued(Long id);

    /**
     * 累加工单的已生产数量
     *
     * @param id                   工单编号
     * @param incrQuantityProduced 本次已生产数量增量
     */
    void updateProducedQuantity(Long id, BigDecimal incrQuantityProduced);

    /**
     * 根据供应商 ID 统计工单数量
     *
     * @param vendorId 供应商 ID
     * @return 数量
     */
    Long getWorkOrderCountByVendorId(Long vendorId);

    /**
     * 回写 WMS 领料出库集成结果（供 MES↔WMS 集成结果处理器调用）
     *
     * @param id                  工单编号
     * @param wmsShipmentOrderNo  WMS 领料出库单号（可能为 null）
     * @param wmsShipmentStatus   WMS 领料状态
     */
    void updateWmsShipmentResult(Long id, String wmsShipmentOrderNo, Integer wmsShipmentStatus);

    /**
     * 回写 WMS 完工入库集成结果（供 MES↔WMS 集成结果处理器调用）
     *
     * @param id                 工单编号
     * @param wmsReceiptOrderNo  WMS 完工入库单号（可能为 null）
     * @param wmsReceiptStatus   WMS 入库状态
     */
    void updateWmsReceiptResult(Long id, String wmsReceiptOrderNo, Integer wmsReceiptStatus);

    /**
     * 缺料采购入库后恢复生产工单：取消旧领料集成任务并重新向 WMS 申请领料（设计 7.2）
     *
     * @param code 工单编码
     */
    void resumeWorkOrder(String code);

}
