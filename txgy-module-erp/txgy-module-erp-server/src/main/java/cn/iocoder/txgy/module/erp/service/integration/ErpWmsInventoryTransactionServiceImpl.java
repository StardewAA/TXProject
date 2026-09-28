package cn.iocoder.txgy.module.erp.service.integration;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsInventoryTransactionItemDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsInventoryTransactionReqDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsInventoryTransactionRespDTO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.in.ErpStockInSaveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.out.ErpStockOutSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpStockInDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpStockOutDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import cn.iocoder.txgy.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpStockInMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpStockOutMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpWarehouseMapper;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import cn.iocoder.txgy.module.erp.enums.stock.ErpStockSourceSystemEnum;
import cn.iocoder.txgy.module.erp.enums.wms.ErpWmsTransactionBizTypeEnum;
import cn.iocoder.txgy.module.erp.enums.wms.ErpWmsTransactionTypeEnum;
import cn.iocoder.txgy.module.erp.service.stock.ErpStockInService;
import cn.iocoder.txgy.module.erp.service.stock.ErpStockOutService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

/**
 * ERP 接收 WMS「库存事务」Service 实现（方案A）
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpWmsInventoryTransactionServiceImpl implements ErpWmsInventoryTransactionService {

    /** 写入 erp_wms_integration_record 的聚合类型 */
    private static final String AGGREGATE_TYPE = "WMS_INVENTORY_TRANSACTION";
    /** erp_stock_in.remark / erp_stock_out.remark 为 varchar(255) */
    private static final int REMARK_MAX_LENGTH = 255;

    /**
     * 这些来源业务在 ERP 里本来就有自己的单据（采购入库单 / 销售出库单 / 退货单），
     * 必须走「执行结果回传」回写实发数量，不能在本接口里另建「其它出入库单」
     */
    private static final List<ErpWmsTransactionBizTypeEnum> ERP_DOCUMENT_BIZ_TYPES = List.of(
            ErpWmsTransactionBizTypeEnum.PURCHASE_IN,
            ErpWmsTransactionBizTypeEnum.PURCHASE_RETURN_OUT,
            ErpWmsTransactionBizTypeEnum.ERP_SALE_OUT,
            ErpWmsTransactionBizTypeEnum.ERP_SALE_RETURN_IN);

    @Resource
    private ErpProductMapper productMapper;
    @Resource
    private ErpWarehouseMapper warehouseMapper;
    @Resource
    private ErpStockInMapper stockInMapper;
    @Resource
    private ErpStockOutMapper stockOutMapper;
    @Resource
    private ErpStockInService stockInService;
    @Resource
    private ErpStockOutService stockOutService;
    // 注意：容器里另有 mzt 日志组件的 recordService，这里必须按 Bean 名精确注入
    @Autowired
    @Qualifier("erpWmsIntegrationRecordService")
    private ErpWmsIntegrationRecordService recordService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErpWmsInventoryTransactionRespDTO receiveInventoryTransaction(ErpWmsInventoryTransactionReqDTO reqDTO) {
        // 1. 幂等：同一个 requestId 已经处理成功过，直接返回，避免 WMS 重推造成重复建单、重复记账
        if (recordService.hasProcessedInbound(reqDTO.getRequestId())) {
            return buildResp(true, null, null,
                    "重复推送已忽略（requestId=" + reqDTO.getRequestId() + "）");
        }
        // 2. 校验事务类型
        ErpWmsTransactionTypeEnum type = ErpWmsTransactionTypeEnum.find(reqDTO.getTransactionType());
        if (type == null) {
            throw exception(WMS_INVENTORY_TRANSACTION_TYPE_INVALID, reqDTO.getTransactionType());
        }
        // 2.1 拦掉 ERP 自有单据的业务：这些在 ERP 里已经有采购入库单/销售出库单/退货单，
        //     WMS 执行完应该走「执行结果回传」回写实发数量并由 ERP 落单（销售出库单还会计入销售绩效），
        //     若在这里再建一张「其它出入库」，会重复记账、且销售绩效口径会被算错。
        ErpWmsTransactionBizTypeEnum bizType = ErpWmsTransactionBizTypeEnum.find(reqDTO.getBizType());
        if (bizType != null && ERP_DOCUMENT_BIZ_TYPES.contains(bizType)) {
            throw exception(WMS_INVENTORY_TRANSACTION_BIZ_TYPE_NOT_SUPPORTED, reqDTO.getBizType());
        }
        // 3. 解析明细，按数量方向拆成「待入库」与「待出库」
        List<ErpStockInSaveReqVO.Item> inItems = new ArrayList<>();
        List<ErpStockOutSaveReqVO.Item> outItems = new ArrayList<>();
        collectItems(reqDTO, type, inItems, outItems);
        if (inItems.isEmpty() && outItems.isEmpty()) {
            throw exception(WMS_INVENTORY_TRANSACTION_ITEM_EMPTY);
        }
        // 4. 生成 ERP 单据 + 立即过账（自动更新账面库存）
        String sourceSystem = resolveSourceSystem(reqDTO.getBizType());
        String remark = buildRemark(reqDTO);
        List<String> documentTypes = new ArrayList<>();
        List<String> documentNos = new ArrayList<>();
        if (CollUtil.isNotEmpty(inItems)) {
            documentNos.add(createAndApproveStockIn(reqDTO, inItems, sourceSystem, remark));
            documentTypes.add("STOCK_IN");
        }
        if (CollUtil.isNotEmpty(outItems)) {
            documentNos.add(createAndApproveStockOut(reqDTO, outItems, sourceSystem, remark));
            documentTypes.add("STOCK_OUT");
        }
        // 5. 集成日志：与单据同事务，保证「有成功日志必有单据」；
        //    若上次失败后 WMS 用同一 requestId 重推成功，这里会把旧记录翻成 SUCCESS
        ErpWmsInventoryTransactionRespDTO resp = buildResp(true, String.join("+", documentTypes),
                String.join(",", documentNos), "ERP 已自动生成单据并更新账面库存");
        recordService.markInboundSuccess(reqDTO.getRequestId(), AGGREGATE_TYPE, reqDTO.getTransactionNo(),
                reqDTO.getEventVersion(), reqDTO, resp);
        return resp;
    }

    // ==================== 明细解析 ====================

    /**
     * 把 WMS 库存事务明细解析成 ERP 的出入库明细
     *
     * <p>数量方向规则：入库取绝对值；出库取绝对值后反向；盘点调整按 WMS 传入的正负号（正=盘盈入库，负=盘亏出库）。</p>
     */
    private void collectItems(ErpWmsInventoryTransactionReqDTO reqDTO, ErpWmsTransactionTypeEnum type,
                              List<ErpStockInSaveReqVO.Item> inItems,
                              List<ErpStockOutSaveReqVO.Item> outItems) {
        for (ErpWmsInventoryTransactionItemDTO item : reqDTO.getItems()) {
            BigDecimal quantity = item.getQuantity();
            if (quantity == null || quantity.signum() == 0) {
                throw exception(WMS_INVENTORY_TRANSACTION_QUANTITY_INVALID, item.getLineNo());
            }
            BigDecimal signed;
            if (type == ErpWmsTransactionTypeEnum.CHECK_ADJUST) {
                signed = quantity;
            } else if (type == ErpWmsTransactionTypeEnum.INBOUND) {
                signed = quantity.abs();
            } else {
                signed = quantity.abs().negate();
            }
            Long productId = resolveProductId(item.getMaterialCode());
            String warehouseCode = StrUtil.isNotBlank(item.getWarehouseCode())
                    ? item.getWarehouseCode() : reqDTO.getWarehouseCode();
            Long warehouseId = resolveWarehouseId(warehouseCode);
            if (signed.signum() > 0) {
                inItems.add(buildInItem(productId, warehouseId, signed, item));
            } else {
                outItems.add(buildOutItem(productId, warehouseId, signed.negate(), item));
            }
        }
    }

    private ErpStockInSaveReqVO.Item buildInItem(Long productId, Long warehouseId, BigDecimal count,
                                                 ErpWmsInventoryTransactionItemDTO item) {
        ErpStockInSaveReqVO.Item result = new ErpStockInSaveReqVO.Item();
        result.setProductId(productId);
        result.setWarehouseId(warehouseId);
        result.setCount(count);
        // WMS 库存事务不带价格信息，自动建单金额记 0，避免出现 null 单价
        result.setProductPrice(BigDecimal.ZERO);
        result.setRemark(item.getRemark());
        return result;
    }

    private ErpStockOutSaveReqVO.Item buildOutItem(Long productId, Long warehouseId, BigDecimal count,
                                                   ErpWmsInventoryTransactionItemDTO item) {
        ErpStockOutSaveReqVO.Item result = new ErpStockOutSaveReqVO.Item();
        result.setProductId(productId);
        result.setWarehouseId(warehouseId);
        result.setCount(count);
        result.setProductPrice(BigDecimal.ZERO);
        result.setRemark(item.getRemark());
        return result;
    }

    private Long resolveProductId(String materialCode) {
        ErpProductDO product = productMapper.selectByMaterialCode(materialCode);
        if (product == null) {
            throw exception(WMS_INVENTORY_TRANSACTION_MATERIAL_NOT_FOUND, materialCode);
        }
        return product.getId();
    }

    private Long resolveWarehouseId(String warehouseCode) {
        ErpWarehouseDO warehouse = StrUtil.isBlank(warehouseCode) ? null
                : warehouseMapper.selectByWmsWarehouseCode(warehouseCode);
        if (warehouse == null) {
            throw exception(WMS_INVENTORY_TRANSACTION_WAREHOUSE_NOT_FOUND, StrUtil.blankToDefault(warehouseCode, "空"));
        }
        return warehouse.getId();
    }

    // ==================== 建单与过账 ====================

    private String createAndApproveStockIn(ErpWmsInventoryTransactionReqDTO reqDTO,
                                           List<ErpStockInSaveReqVO.Item> items, String sourceSystem, String remark) {
        ErpStockInSaveReqVO saveReqVO = new ErpStockInSaveReqVO();
        saveReqVO.setInTime(reqDTO.getOccurredAt());
        saveReqVO.setRemark(remark);
        saveReqVO.setItems(items);
        Long id = stockInService.createStockIn(saveReqVO);
        // 标记来源：WMS 按方案A推送生成，便于列表区分、并让库存流水归属到正确的来源系统
        stockInMapper.updateById(new ErpStockInDO().setId(id)
                .setSourceSystem(sourceSystem).setSourceRefNo(reqDTO.getTransactionNo()));
        // 自动过账：写入库存流水 + 更新账面库存
        stockInService.updateStockInStatus(id, ErpAuditStatus.APPROVE.getStatus());
        ErpStockInDO stockIn = stockInService.getStockIn(id);
        return stockIn == null ? String.valueOf(id) : stockIn.getNo();
    }

    private String createAndApproveStockOut(ErpWmsInventoryTransactionReqDTO reqDTO,
                                            List<ErpStockOutSaveReqVO.Item> items, String sourceSystem, String remark) {
        ErpStockOutSaveReqVO saveReqVO = new ErpStockOutSaveReqVO();
        saveReqVO.setOutTime(reqDTO.getOccurredAt());
        saveReqVO.setRemark(remark);
        saveReqVO.setItems(items);
        Long id = stockOutService.createStockOut(saveReqVO);
        stockOutMapper.updateById(new ErpStockOutDO().setId(id)
                .setSourceSystem(sourceSystem).setSourceRefNo(reqDTO.getTransactionNo()));
        stockOutService.updateStockOutStatus(id, ErpAuditStatus.APPROVE.getStatus());
        ErpStockOutDO stockOut = stockOutService.getStockOut(id);
        return stockOut == null ? String.valueOf(id) : stockOut.getNo();
    }

    // ==================== 来源与备注 ====================

    /**
     * 按来源业务类型决定库存流水的「来源系统」
     *
     * <p>这一步很关键：ERP 的销售绩效只统计「来源系统 = ERP 且业务类型 = 销售出库」的流水，
     * 商城出库、MES 生产入库、WMS 盘点调整都不应计入 ERP 自有销售。</p>
     */
    private String resolveSourceSystem(String bizType) {
        if (StrUtil.isBlank(bizType)) {
            return ErpStockSourceSystemEnum.MANUAL.getSystem();
        }
        ErpWmsTransactionBizTypeEnum biz = ErpWmsTransactionBizTypeEnum.find(bizType);
        if (biz == null) {
            return ErpStockSourceSystemEnum.MANUAL.getSystem();
        }
        switch (biz) {
            case MES_PRODUCE_IN:
                return ErpStockSourceSystemEnum.MES.getSystem();
            case MALL_SALE_OUT:
                return ErpStockSourceSystemEnum.MALL.getSystem();
            case CHECK_PROFIT:
            case CHECK_LOSS:
            case OTHER:
                return ErpStockSourceSystemEnum.MANUAL.getSystem();
            default:
                // ERP_SALE_OUT / ERP_SALE_RETURN_IN / PURCHASE_IN / PURCHASE_RETURN_OUT 都属于 ERP 自有业务
                return ErpStockSourceSystemEnum.ERP.getSystem();
        }
    }

    private String buildRemark(ErpWmsInventoryTransactionReqDTO reqDTO) {
        StringBuilder sb = new StringBuilder("WMS 库存事务推送｜事务号 ").append(reqDTO.getTransactionNo());
        if (StrUtil.isNotBlank(reqDTO.getBizType())) {
            sb.append("｜来源业务 ").append(ErpWmsTransactionBizTypeEnum.nameOf(reqDTO.getBizType()));
        }
        if (StrUtil.isNotBlank(reqDTO.getWmsOrderNo())) {
            sb.append("｜WMS 单号 ").append(reqDTO.getWmsOrderNo());
        }
        if (StrUtil.isNotBlank(reqDTO.getSourceOrderNo())) {
            sb.append("｜ERP 单号 ").append(reqDTO.getSourceOrderNo());
        }
        if (StrUtil.isNotBlank(reqDTO.getRemark())) {
            sb.append("｜").append(reqDTO.getRemark());
        }
        return sb.length() > REMARK_MAX_LENGTH ? sb.substring(0, REMARK_MAX_LENGTH) : sb.toString();
    }

    private ErpWmsInventoryTransactionRespDTO buildResp(Boolean accepted, String documentType,
                                                        String documentNo, String message) {
        return new ErpWmsInventoryTransactionRespDTO().setAccepted(accepted)
                .setErpDocumentType(documentType).setErpDocumentNo(documentNo).setMessage(message);
    }

}
