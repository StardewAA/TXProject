package cn.iocoder.txgy.module.erp.service.sale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.order.ErpSaleOrderPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.order.ErpSaleOrderSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleOrderDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleOrderItemDO;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * ERP 销售订单 Service 接口
 *
 * @author OPENLAB BS
 */
public interface ErpSaleOrderService {

    /**
     * 创建销售订单
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSaleOrder(@Valid ErpSaleOrderSaveReqVO createReqVO);

    /**
     * 更新销售订单
     *
     * @param updateReqVO 更新信息
     */
    void updateSaleOrder(@Valid ErpSaleOrderSaveReqVO updateReqVO);

    /**
     * 反审批销售订单（审批中 / 已审批 → 草稿），并联动取消 BPM 在途流程实例
     *
     * @param id 编号
     * @param userId 操作人编号
     * @param reason 反审批原因
     */
    void reverseApproveSaleOrder(Long id, Long userId, String reason);

    /** 提交销售订单审批 */
    void submitSaleOrder(Long id, Long userId);

    /** 接收 BPM 回调并更新销售订单审批状态 */
    void updateSaleOrderAuditStatus(Long id, Integer bpmStatus);

    /**
     * 更新销售订单的出库数量
     *
     * @param id 编号
     * @param outCountMap 出库数量 Map：key 销售订单项编号；value 出库数量
     */
    void updateSaleOrderOutCount(Long id, Map<Long, BigDecimal> outCountMap);

    /**
     * 更新销售订单的退货数量
     *
     * @param orderId 编号
     * @param returnCountMap 退货数量 Map：key 销售订单项编号；value 退货数量
     */
    void updateSaleOrderReturnCount(Long orderId, Map<Long, BigDecimal> returnCountMap);

    /**
     * 删除销售订单
     *
     * @param ids 编号数组
     */
    void deleteSaleOrder(List<Long> ids);

    /**
     * 获得销售订单
     *
     * @param id 编号
     * @return 销售订单
     */
    ErpSaleOrderDO getSaleOrder(Long id);

    /**
     * 校验销售订单，已经审核通过
     *
     * @param id 编号
     * @return 销售订单
     */
    ErpSaleOrderDO validateSaleOrder(Long id);

    /**
     * 获得销售订单分页
     *
     * @param pageReqVO 分页查询
     * @return 销售订单分页
     */
    PageResult<ErpSaleOrderDO> getSaleOrderPage(ErpSaleOrderPageReqVO pageReqVO);

    // ==================== 销售订单项 ====================

    /**
     * 获得销售订单项列表
     *
     * @param orderId 销售订单编号
     * @return 销售订单项列表
     */
    List<ErpSaleOrderItemDO> getSaleOrderItemListByOrderId(Long orderId);

    /**
     * 获得销售订单项 List
     *
     * @param orderIds 销售订单编号数组
     * @return 销售订单项 List
     */
    List<ErpSaleOrderItemDO> getSaleOrderItemListByOrderIds(Collection<Long> orderIds);

}
