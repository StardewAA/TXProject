package cn.iocoder.txgy.module.erp.api.mes;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.erp.api.mes.dto.ErpProductionProgressReqDTO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleOrderDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleOrderItemDO;
import cn.iocoder.txgy.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpSaleOrderItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpSaleOrderMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

/**
 * 生产完工进度 RPC API 实现类
 *
 * 接收 MES 工单完工回传，按「销售订单号 + 物料编码」定位订单行，
 * 覆盖式写入已生产数量（幂等：重复回传同一值结果一致）。
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
@Slf4j
public class ErpProductionProgressApiImpl implements ErpProductionProgressApi {

    private static final String SOURCE_MODULE_MES = "MES";

    @Resource
    private ErpSaleOrderMapper saleOrderMapper;
    @Resource
    private ErpSaleOrderItemMapper saleOrderItemMapper;
    @Resource
    private ErpProductMapper productMapper;

    @Override
    public CommonResult<Boolean> reportProgress(ErpProductionProgressReqDTO reqDTO) {
        // 1. 校验租户与来源
        validateTenant(reqDTO.getTenantId());
        if (!SOURCE_MODULE_MES.equals(reqDTO.getSourceModule())) {
            throw exception(PRODUCTION_PROGRESS_SOURCE_INVALID);
        }
        if (reqDTO.getProducedQuantity() == null || reqDTO.getProducedQuantity().signum() < 0) {
            throw exception(PRODUCTION_PROGRESS_QUANTITY_INVALID);
        }
        // 2. 定位销售订单
        ErpSaleOrderDO saleOrder = saleOrderMapper.selectByNo(reqDTO.getSaleOrderNo());
        if (saleOrder == null) {
            throw exception(SALE_ORDER_NOT_EXISTS);
        }
        // 3. 定位物料与订单行（按物料编码匹配订单行）
        ErpProductDO product = productMapper.selectByMaterialCode(reqDTO.getMaterialCode());
        if (product == null) {
            throw exception(PRODUCTION_PROGRESS_MATERIAL_NOT_FOUND, reqDTO.getMaterialCode());
        }
        List<ErpSaleOrderItemDO> items = saleOrderItemMapper.selectListByOrderId(saleOrder.getId());
        ErpSaleOrderItemDO target = items.stream()
                .filter(item -> Objects.equals(item.getProductId(), product.getId()))
                .findFirst().orElse(null);
        if (target == null) {
            throw exception(PRODUCTION_PROGRESS_ORDER_ITEM_NOT_FOUND, reqDTO.getMaterialCode());
        }
        // 4. 覆盖式写入（覆盖写天然幂等，重复回传不会重复累加）
        saleOrderItemMapper.updateById(new ErpSaleOrderItemDO().setId(target.getId())
                .setProducedCount(reqDTO.getProducedQuantity()));
        log.info("[reportProgress][订单({}) 物料({}) 完工进度回写 {}（工单 {} 完工={}）]",
                reqDTO.getSaleOrderNo(), reqDTO.getMaterialCode(), reqDTO.getProducedQuantity(),
                reqDTO.getWorkOrderCode(), reqDTO.getFinished());
        return success(true);
    }

    private void validateTenant(Long requestTenantId) {
        if (!Objects.equals(TenantContextHolder.getRequiredTenantId(), requestTenantId)) {
            throw exception(INTEGRATION_TENANT_MISMATCH);
        }
    }

}
