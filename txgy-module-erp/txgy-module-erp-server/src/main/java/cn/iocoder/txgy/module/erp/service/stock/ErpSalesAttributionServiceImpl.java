package cn.iocoder.txgy.module.erp.service.stock;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.attribution.ErpSalesAttributionRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.attribution.ErpSalesPerformanceSummaryRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.attribution.ErpStockSourceBreakdownRespVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpSalesAttributionRowDO;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpSalesAttributionMapper;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import cn.iocoder.txgy.module.erp.enums.common.ErpBizTypeEnum;
import cn.iocoder.txgy.module.erp.enums.stock.ErpStockRecordBizTypeEnum;
import cn.iocoder.txgy.module.erp.enums.stock.ErpStockSourceSystemEnum;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ERP 销售归因 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpSalesAttributionServiceImpl implements ErpSalesAttributionService {

    /** 回款率换算用的百分比基数 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Resource
    private ErpSalesAttributionMapper attributionMapper;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public List<ErpStockSourceBreakdownRespVO> getSourceBreakdown(Long warehouseId,
                                                                  LocalDateTime startTime, LocalDateTime endTime) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        return attributionMapper.selectSourceBreakdown(tenantId, warehouseId, startTime, endTime).stream()
                .map(row -> {
                    ErpStockRecordBizTypeEnum bizType = parseBizType(row.getBizType());
                    return new ErpStockSourceBreakdownRespVO()
                            .setSourceSystem(row.getSourceSystem())
                            .setSourceSystemName(ErpStockSourceSystemEnum.getNameBySystem(row.getSourceSystem()))
                            .setBizType(row.getBizType())
                            .setBizTypeName(bizType != null ? bizType.getName() : null)
                            .setTotalCount(row.getTotalCount())
                            .setRecordCount(row.getRecordCount())
                            .setErpSale(isErpSale(row.getSourceSystem(), row.getBizType()));
                })
                .toList();
    }

    @Override
    public List<ErpSalesAttributionRespVO> getSalesAttribution(Long userId, Long warehouseId,
                                                               LocalDateTime startTime, LocalDateTime endTime) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<ErpSalesAttributionRowDO> rows = attributionMapper.selectSalesAttribution(
                tenantId, userId, warehouseId, startTime, endTime);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量取销售员姓名，避免逐条 RPC
        Set<Long> userIds = rows.stream()
                .map(ErpSalesAttributionRowDO::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> userMap = userIds.isEmpty()
                ? Collections.emptyMap() : adminUserApi.getUserMap(userIds);
        return rows.stream()
                .map(row -> {
                    AdminUserRespDTO user = row.getUserId() != null ? userMap.get(row.getUserId()) : null;
                    return new ErpSalesAttributionRespVO()
                            .setUserId(row.getUserId())
                            .setUserName(user != null ? user.getNickname() : null)
                            .setProductCode(row.getProductCode())
                            .setProductName(row.getProductName())
                            .setOutCount(row.getOutCount())
                            .setReturnCount(row.getReturnCount())
                            .setNetCount(row.getNetCount())
                            .setOutAmount(row.getOutAmount())
                            .setReturnAmount(row.getReturnAmount())
                            .setNetAmount(row.getNetAmount());
                })
                .toList();
    }

    /**
     * 是否属于 ERP 销售：来源系统为 ERP 且业务类型为销售出库
     */
    private boolean isErpSale(String sourceSystem, Integer bizType) {
        return Objects.equals(ErpStockSourceSystemEnum.ERP.getSystem(), sourceSystem)
                && Objects.equals(ErpStockRecordBizTypeEnum.SALE_OUT.getType(), bizType);
    }

    @Override
    public List<ErpSalesPerformanceSummaryRespVO> getPerformanceSummary(Long userId, Long warehouseId,
                                                                       LocalDateTime startTime, LocalDateTime endTime) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Map<Long, ErpSalesPerformanceSummaryRespVO> summaryMap = new LinkedHashMap<>();

        // 口径一：出入库（销售员 × 产品 → 上卷到销售员）
        for (ErpSalesAttributionRowDO row : attributionMapper.selectSalesAttribution(
                tenantId, userId, warehouseId, startTime, endTime)) {
            ErpSalesPerformanceSummaryRespVO vo = getOrCreate(summaryMap, row.getUserId());
            vo.setOutCount(vo.getOutCount().add(nvl(row.getOutCount())));
            vo.setReturnCount(vo.getReturnCount().add(nvl(row.getReturnCount())));
            vo.setNetCount(vo.getNetCount().add(nvl(row.getNetCount())));
            vo.setOutAmount(vo.getOutAmount().add(nvl(row.getOutAmount())));
            vo.setReturnAmount(vo.getReturnAmount().add(nvl(row.getReturnAmount())));
            vo.setNetAmount(vo.getNetAmount().add(nvl(row.getNetAmount())));
        }

        // 口径二：名下已审批销售订单金额（应收的分母）
        attributionMapper.selectSalesOrderAmount(tenantId, userId, ErpAuditStatus.APPROVE.getStatus(), startTime, endTime)
                .forEach(row -> {
                    ErpSalesPerformanceSummaryRespVO vo = getOrCreate(summaryMap, row.getUserId());
                    vo.setOrderAmount(nvl(row.getAmount()));
                    vo.setOrderCount(row.getDocCount() == null ? 0L : row.getDocCount());
                });

        // 口径三：回款金额（经订单归因到销售员）
        attributionMapper.selectSalesReceiptAmount(tenantId, userId, ErpAuditStatus.APPROVE.getStatus(),
                        ErpBizTypeEnum.SALE_ORDER.getType(), startTime, endTime)
                .forEach(row -> {
                    ErpSalesPerformanceSummaryRespVO vo = getOrCreate(summaryMap, row.getUserId());
                    vo.setReceiptAmount(nvl(row.getAmount()));
                    vo.setReceiptCount(row.getDocCount() == null ? 0L : row.getDocCount());
                });

        if (summaryMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 派生指标：应收余额与回款率
        summaryMap.values().forEach(vo -> {
            vo.setUnreceiptedAmount(vo.getOrderAmount().subtract(vo.getReceiptAmount()));
            if (vo.getOrderAmount().signum() != 0) {
                vo.setReceiptRate(vo.getReceiptAmount().multiply(HUNDRED)
                        .divide(vo.getOrderAmount(), 2, RoundingMode.HALF_UP));
            }
        });

        // 批量补齐销售员姓名，避免逐条 RPC
        Set<Long> userIds = summaryMap.keySet().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> userMap = userIds.isEmpty()
                ? Collections.emptyMap() : adminUserApi.getUserMap(userIds);
        summaryMap.values().forEach(vo -> {
            AdminUserRespDTO user = vo.getUserId() != null ? userMap.get(vo.getUserId()) : null;
            vo.setUserName(user != null ? user.getNickname() : null);
        });

        return summaryMap.values().stream()
                .sorted(Comparator.comparing(ErpSalesPerformanceSummaryRespVO::getOrderAmount).reversed()
                        .thenComparing(ErpSalesPerformanceSummaryRespVO::getUserId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** 取或建销售员汇总行，所有金额初始化为 0，避免后续 add 时空指针 */
    private ErpSalesPerformanceSummaryRespVO getOrCreate(Map<Long, ErpSalesPerformanceSummaryRespVO> summaryMap,
                                                         Long userId) {
        return summaryMap.computeIfAbsent(userId, id -> {
            ErpSalesPerformanceSummaryRespVO vo = new ErpSalesPerformanceSummaryRespVO();
            vo.setUserId(id);
            vo.setOutCount(BigDecimal.ZERO);
            vo.setReturnCount(BigDecimal.ZERO);
            vo.setNetCount(BigDecimal.ZERO);
            vo.setOutAmount(BigDecimal.ZERO);
            vo.setReturnAmount(BigDecimal.ZERO);
            vo.setNetAmount(BigDecimal.ZERO);
            vo.setOrderAmount(BigDecimal.ZERO);
            vo.setOrderCount(0L);
            vo.setReceiptAmount(BigDecimal.ZERO);
            vo.setReceiptCount(0L);
            vo.setUnreceiptedAmount(BigDecimal.ZERO);
            return vo;
        });
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private ErpStockRecordBizTypeEnum parseBizType(Integer bizType) {
        if (bizType == null) {
            return null;
        }
        return Arrays.stream(ErpStockRecordBizTypeEnum.values())
                .filter(item -> Objects.equals(item.getType(), bizType))
                .findFirst().orElse(null);
    }

}
