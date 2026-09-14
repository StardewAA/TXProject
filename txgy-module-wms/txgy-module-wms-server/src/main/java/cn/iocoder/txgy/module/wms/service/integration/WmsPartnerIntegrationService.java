package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsPartnerUpsertReqDTO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.merchant.WmsMerchantDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.merchant.WmsMerchantMapper;
import cn.iocoder.txgy.module.wms.enums.md.WmsMerchantTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.*;
import static cn.iocoder.txgy.module.wms.enums.integration.WmsIntegrationConstants.*;

@Service
public class WmsPartnerIntegrationService {

    private static final String PARTNER_CUSTOMER = "CUSTOMER";
    private static final String PARTNER_SUPPLIER = "SUPPLIER";
    private static final String PARTNER_BOTH = "BOTH";

    @Resource
    private WmsMerchantMapper merchantMapper;

    @Transactional(rollbackFor = Exception.class)
    public WmsIntegrationAcceptedRespDTO upsert(WmsPartnerUpsertReqDTO request) {
        int incomingType = toMerchantType(request.getPartnerType());
        int status = toStatus(request.getStatus());
        WmsMerchantDO merchant = merchantMapper.selectByCode(request.getPartnerCode());
        if (merchant == null) {
            merchant = buildMerchant(request, incomingType, status);
            merchantMapper.insert(merchant);
        } else {
            validateVersion(merchant, request);
            WmsMerchantDO updateObj = buildUpdate(merchant, request, incomingType, status);
            merchantMapper.updateById(updateObj);
        }
        return new WmsIntegrationAcceptedRespDTO().setAccepted(true).setRequestId(request.getRequestId())
                .setTargetOrderNo(request.getPartnerCode()).setBusinessStatus("SYNCED")
                .setProcessedVersion(request.getDataVersion());
    }

    private WmsMerchantDO buildMerchant(WmsPartnerUpsertReqDTO request, int type, int status) {
        Long customerVersion = type != WmsMerchantTypeEnum.SUPPLIER.getType() ? request.getDataVersion() : 0L;
        Long supplierVersion = type != WmsMerchantTypeEnum.CUSTOMER.getType() ? request.getDataVersion() : 0L;
        return fillPublicFields(new WmsMerchantDO(), request, status).setCode(request.getPartnerCode()).setType(type)
                .setSourceVersion(request.getDataVersion()).setCustomerSourceVersion(customerVersion)
                .setSupplierSourceVersion(supplierVersion);
    }

    private WmsMerchantDO buildUpdate(WmsMerchantDO current, WmsPartnerUpsertReqDTO request,
                                      int incomingType, int status) {
        long customerVersion = value(current.getCustomerSourceVersion());
        long supplierVersion = value(current.getSupplierSourceVersion());
        if (incomingType == WmsMerchantTypeEnum.CUSTOMER.getType()) customerVersion = request.getDataVersion();
        if (incomingType == WmsMerchantTypeEnum.SUPPLIER.getType()) supplierVersion = request.getDataVersion();
        if (incomingType == WmsMerchantTypeEnum.CUSTOMER_SUPPLIER.getType()) {
            customerVersion = request.getDataVersion();
            supplierVersion = request.getDataVersion();
        }
        return fillPublicFields(new WmsMerchantDO().setId(current.getId()), request, status)
                .setType(mergeType(current.getType(), incomingType))
                .setCustomerSourceVersion(customerVersion).setSupplierSourceVersion(supplierVersion)
                .setSourceVersion(Math.max(customerVersion, supplierVersion));
    }

    private WmsMerchantDO fillPublicFields(WmsMerchantDO merchant, WmsPartnerUpsertReqDTO request, int status) {
        return merchant.setName(request.getName()).setContact(request.getContact()).setMobile(request.getMobile())
                .setTelephone(request.getTelephone()).setEmail(request.getEmail()).setAddress(request.getAddress())
                .setTaxNo(request.getTaxNo()).setBankName(request.getBankName()).setBankAccount(request.getBankAccount())
                .setRemark(request.getRemark()).setStatus(status);
    }

    private void validateVersion(WmsMerchantDO merchant, WmsPartnerUpsertReqDTO request) {
        long currentVersion = PARTNER_CUSTOMER.equals(request.getPartnerType())
                ? value(merchant.getCustomerSourceVersion())
                : PARTNER_SUPPLIER.equals(request.getPartnerType())
                ? value(merchant.getSupplierSourceVersion())
                : Math.max(value(merchant.getCustomerSourceVersion()), value(merchant.getSupplierSourceVersion()));
        if (request.getDataVersion() < currentVersion) throw exception(SYNC_VERSION_STALE);
    }

    private int mergeType(Integer current, int incoming) {
        return Objects.equals(current, incoming) ? incoming : WmsMerchantTypeEnum.CUSTOMER_SUPPLIER.getType();
    }

    private int toMerchantType(String type) {
        if (PARTNER_CUSTOMER.equals(type)) return WmsMerchantTypeEnum.CUSTOMER.getType();
        if (PARTNER_SUPPLIER.equals(type)) return WmsMerchantTypeEnum.SUPPLIER.getType();
        if (PARTNER_BOTH.equals(type)) return WmsMerchantTypeEnum.CUSTOMER_SUPPLIER.getType();
        throw exception(PARTNER_TYPE_INVALID);
    }

    private int toStatus(String status) {
        if (STATUS_ENABLED.equals(status)) return CommonStatusEnum.ENABLE.getStatus();
        if (STATUS_DISABLED.equals(status)) return CommonStatusEnum.DISABLE.getStatus();
        throw exception(INTEGRATION_STATUS_INVALID);
    }

    private long value(Long version) {
        return version == null ? 0L : version;
    }
}
