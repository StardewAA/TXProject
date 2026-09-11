package cn.iocoder.txgy.module.erp.service.purchase;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall.MallPurchaseDemandCancelReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall.MallPurchaseDemandReceiveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall.MallPurchaseDemandRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall.MallPurchaseDemandResultReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpMallPurchaseDemandDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpMallPurchaseDemandItemDO;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpMallPurchaseDemandItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpMallPurchaseDemandMapper;
import cn.iocoder.txgy.module.erp.dal.redis.no.ErpNoRedisDAO;
import cn.iocoder.txgy.module.erp.service.stock.ErpWarehouseService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.MALL_PURCHASE_DEMAND_NOT_EXISTS;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.MALL_PURCHASE_DEMAND_QUANTITY_INVALID;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.MALL_PURCHASE_DEMAND_REQUEST_CONFLICT;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.MALL_PURCHASE_DEMAND_RESULT_REASON_REQUIRED;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.MALL_PURCHASE_DEMAND_STATUS_INVALID;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.MALL_PURCHASE_DEMAND_TENANT_MISMATCH;

@Service
@Validated
public class ErpMallPurchaseDemandServiceImpl implements ErpMallPurchaseDemandService {

    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCEL_REQUESTED = "CANCEL_REQUESTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_CANCEL_REJECTED = "CANCEL_REJECTED";
    public static final String STATUS_DELAYED = "DELAYED";
    public static final String STATUS_PURCHASE_COMPLETED = "PURCHASE_COMPLETED";
    public static final String STATUS_PURCHASE_FAILED = "PURCHASE_FAILED";
    private static final Set<String> RESULT_STATUSES = Set.of(
            STATUS_DELAYED, STATUS_PURCHASE_COMPLETED, STATUS_PURCHASE_FAILED,
            STATUS_CANCELLED, STATUS_CANCEL_REJECTED);

    @Resource
    private ErpMallPurchaseDemandMapper demandMapper;
    @Resource
    private ErpMallPurchaseDemandItemMapper demandItemMapper;
    @Resource
    private ErpNoRedisDAO noRedisDAO;
    @Resource
    private ErpWarehouseService warehouseService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallPurchaseDemandRespVO receiveDemand(MallPurchaseDemandReceiveReqVO reqVO) {
        validateTenant(reqVO.getTenantId());
        String requestHash = SecureUtil.sha256(JSONUtil.toJsonStr(reqVO));

        // A retry returns the original receipt. A changed retry is never allowed to overwrite it.
        ErpMallPurchaseDemandDO existingByRequest = demandMapper.selectByRequestIdForUpdate(reqVO.getRequestId());
        if (existingByRequest != null) {
            validateSameRequest(existingByRequest, requestHash);
            return buildResponse(existingByRequest);
        }
        ErpMallPurchaseDemandDO existingByMallNo = demandMapper.selectByMallDemandNoForUpdate(reqVO.getMallDemandNo());
        if (existingByMallNo != null) {
            throw exception(MALL_PURCHASE_DEMAND_REQUEST_CONFLICT);
        }

        validateReceiveRequest(reqVO);
        ErpMallPurchaseDemandDO demand = new ErpMallPurchaseDemandDO()
                .setRequestId(reqVO.getRequestId())
                .setRequestHash(requestHash)
                .setMallDemandNo(reqVO.getMallDemandNo())
                .setOrderNo(reqVO.getOrderNo())
                .setShopOrderNo(reqVO.getShopOrderNo())
                .setEnterpriseId(reqVO.getEnterpriseId())
                .setShopId(reqVO.getShopId())
                .setWarehouseId(reqVO.getWarehouseId())
                .setErpRequestNo(noRedisDAO.generate(ErpNoRedisDAO.MALL_PURCHASE_DEMAND_NO_PREFIX))
                .setStatus(STATUS_ACCEPTED)
                .setRequiredDeliveryDate(reqVO.getRequiredDeliveryDate())
                .setContactRequirement(reqVO.getContactRequirement())
                .setTechnicalRequirement(reqVO.getTechnicalRequirement())
                .setAttachmentUrls(reqVO.getAttachmentUrls());
        demandMapper.insert(demand);

        List<ErpMallPurchaseDemandItemDO> items = reqVO.getItems().stream()
                .map(item -> new ErpMallPurchaseDemandItemDO()
                        .setDemandId(demand.getId())
                        .setDemandItemId(item.getDemandItemId())
                        .setSkuId(item.getSkuId())
                        .setErpMaterialCode(item.getErpMaterialCode())
                        .setProductName(item.getProductName())
                        .setSpecification(item.getSpecification())
                        .setUnitName(item.getUnitName())
                        .setOrderQuantity(item.getOrderQuantity())
                        .setReservedQuantity(item.getReservedQuantity())
                        .setShortageQuantity(item.getShortageQuantity()))
                .toList();
        demandItemMapper.insertBatch(items);
        return buildResponse(demand);
    }

    @Override
    public MallPurchaseDemandRespVO getDemand(String erpRequestNo) {
        return buildResponse(validateDemandExists(erpRequestNo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallPurchaseDemandRespVO requestCancel(String erpRequestNo, MallPurchaseDemandCancelReqVO reqVO) {
        ErpMallPurchaseDemandDO demand = validateDemandExists(erpRequestNo);
        if (STATUS_ACCEPTED.equals(demand.getStatus()) || STATUS_DELAYED.equals(demand.getStatus())) {
            demandMapper.updateById(new ErpMallPurchaseDemandDO().setId(demand.getId())
                    .setStatus(STATUS_CANCEL_REQUESTED).setCancelReason(reqVO.getCancelReason()));
            demand.setStatus(STATUS_CANCEL_REQUESTED);
        } else if (!STATUS_CANCEL_REQUESTED.equals(demand.getStatus())) {
            throw exception(MALL_PURCHASE_DEMAND_STATUS_INVALID, demand.getStatus(), STATUS_CANCEL_REQUESTED);
        }
        return buildResponse(demand);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallPurchaseDemandRespVO updateResult(String erpRequestNo, MallPurchaseDemandResultReqVO reqVO) {
        ErpMallPurchaseDemandDO demand = demandMapper.selectByErpRequestNoForUpdate(erpRequestNo);
        if (demand == null) {
            throw exception(MALL_PURCHASE_DEMAND_NOT_EXISTS);
        }
        validateResultStatus(reqVO.getStatus());
        validateStatusTransition(demand.getStatus(), reqVO.getStatus());
        validateResultReason(reqVO);
        ErpMallPurchaseDemandDO updateObj = new ErpMallPurchaseDemandDO().setId(demand.getId())
                .setStatus(reqVO.getStatus())
                .setErpPurchaseOrderNo(reqVO.getErpOrderNo())
                .setReasonCode(reqVO.getReasonCode())
                .setReasonMessage(reqVO.getMessage())
                .setExpectedArrivalDate(reqVO.getEstimatedArrivalDate());
        demandMapper.updateById(updateObj);
        return buildResponse(demandMapper.selectByErpRequestNo(erpRequestNo));
    }

    private void validateTenant(Long requestTenantId) {
        if (!Objects.equals(TenantContextHolder.getRequiredTenantId(), requestTenantId)) {
            throw exception(MALL_PURCHASE_DEMAND_TENANT_MISMATCH);
        }
    }

    private void validateReceiveRequest(MallPurchaseDemandReceiveReqVO reqVO) {
        warehouseService.validWarehouseList(Collections.singleton(reqVO.getWarehouseId()));
        Set<String> demandItemIds = new HashSet<>();
        for (MallPurchaseDemandReceiveReqVO.Item item : reqVO.getItems()) {
            if (!demandItemIds.add(item.getDemandItemId())) {
                throw exception(MALL_PURCHASE_DEMAND_QUANTITY_INVALID, item.getDemandItemId());
            }
            if (item.getReservedQuantity().compareTo(item.getOrderQuantity()) > 0) {
                throw exception(MALL_PURCHASE_DEMAND_QUANTITY_INVALID, item.getDemandItemId());
            }
            BigDecimal availableQuantity = item.getOrderQuantity().subtract(item.getReservedQuantity());
            if (item.getShortageQuantity().compareTo(availableQuantity) > 0) {
                throw exception(MALL_PURCHASE_DEMAND_QUANTITY_INVALID, item.getDemandItemId());
            }
        }
    }

    private ErpMallPurchaseDemandDO validateDemandExists(String erpRequestNo) {
        ErpMallPurchaseDemandDO demand = demandMapper.selectByErpRequestNo(erpRequestNo);
        if (demand == null) {
            throw exception(MALL_PURCHASE_DEMAND_NOT_EXISTS);
        }
        return demand;
    }

    private void validateStatusTransition(String currentStatus, String targetStatus) {
        if (Objects.equals(currentStatus, targetStatus)) {
            return;
        }
        boolean allowed = switch (currentStatus) {
            case STATUS_ACCEPTED -> STATUS_DELAYED.equals(targetStatus)
                    || STATUS_PURCHASE_COMPLETED.equals(targetStatus)
                    || STATUS_PURCHASE_FAILED.equals(targetStatus);
            case STATUS_DELAYED -> STATUS_PURCHASE_COMPLETED.equals(targetStatus)
                    || STATUS_PURCHASE_FAILED.equals(targetStatus);
            case STATUS_CANCEL_REQUESTED -> STATUS_CANCELLED.equals(targetStatus)
                    || STATUS_CANCEL_REJECTED.equals(targetStatus);
            case STATUS_CANCEL_REJECTED -> STATUS_DELAYED.equals(targetStatus)
                    || STATUS_PURCHASE_COMPLETED.equals(targetStatus)
                    || STATUS_PURCHASE_FAILED.equals(targetStatus);
            default -> false;
        };
        if (!allowed) {
            throw exception(MALL_PURCHASE_DEMAND_STATUS_INVALID, currentStatus, targetStatus);
        }
    }

    private void validateResultStatus(String status) {
        if (!RESULT_STATUSES.contains(status)) {
            throw exception(MALL_PURCHASE_DEMAND_STATUS_INVALID, "RESULT", status);
        }
    }

    private void validateResultReason(MallPurchaseDemandResultReqVO reqVO) {
        if ((STATUS_PURCHASE_FAILED.equals(reqVO.getStatus()) || STATUS_CANCEL_REJECTED.equals(reqVO.getStatus()))
                && StrUtil.isBlank(reqVO.getMessage())) {
            throw exception(MALL_PURCHASE_DEMAND_RESULT_REASON_REQUIRED, reqVO.getStatus());
        }
    }

    private void validateSameRequest(ErpMallPurchaseDemandDO demand, String requestHash) {
        if (!Objects.equals(demand.getRequestHash(), requestHash)) {
            throw exception(MALL_PURCHASE_DEMAND_REQUEST_CONFLICT);
        }
    }

    private MallPurchaseDemandRespVO buildResponse(ErpMallPurchaseDemandDO demand) {
        return new MallPurchaseDemandRespVO()
                .setRequestId(demand.getRequestId())
                .setMallDemandNo(demand.getMallDemandNo())
                .setAccepted(!STATUS_REJECTED.equals(demand.getStatus()))
                .setErpRequestNo(demand.getErpRequestNo())
                .setErpOrderNo(demand.getErpPurchaseOrderNo())
                .setStatus(demand.getStatus())
                .setReasonCode(demand.getReasonCode())
                .setMessage(demand.getReasonMessage())
                .setEstimatedArrivalDate(demand.getExpectedArrivalDate());
    }

}
