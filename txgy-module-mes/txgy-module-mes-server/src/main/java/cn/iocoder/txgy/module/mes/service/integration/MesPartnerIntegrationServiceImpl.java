package cn.iocoder.txgy.module.mes.service.integration;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesPartnerUpsertReqDTO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.client.MesMdClientDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.vendor.MesMdVendorDO;
import cn.iocoder.txgy.module.mes.dal.mysql.md.client.MesMdClientMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.md.vendor.MesMdVendorMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.*;

/**
 * MES 往来企业主数据集成 Service 实现类
 *
 * ERP 是客户 / 供应商主数据的权威来源，按 ERP 往来企业编码幂等 upsert：
 * CUSTOMER 落 mes_md_client、SUPPLIER 落 mes_md_vendor。
 * 字段映射：contact→联系人一、mobile→联系人一电话、taxNo→统一社会信用代码；
 * 客户类型、客户等级等 MES 自有属性推送不覆盖。
 */
@Service
@Validated
@Slf4j
public class MesPartnerIntegrationServiceImpl implements MesPartnerIntegrationService {

    private static final String PARTNER_TYPE_CUSTOMER = "CUSTOMER";
    private static final String PARTNER_TYPE_SUPPLIER = "SUPPLIER";

    @Resource
    private MesMdClientMapper clientMapper;
    @Resource
    private MesMdVendorMapper vendorMapper;

    @Override
    public MesIntegrationAcceptedRespDTO upsertPartner(MesPartnerUpsertReqDTO reqDTO) {
        // 1. 校验推送内容
        validate(reqDTO);
        // 2. 按往来企业类型分别落库
        return switch (reqDTO.getPartnerType()) {
            case PARTNER_TYPE_CUSTOMER -> upsertClient(reqDTO);
            case PARTNER_TYPE_SUPPLIER -> upsertVendor(reqDTO);
            default -> throw exception(INTEGRATION_PARTNER_TYPE_INVALID);
        };
    }

    private MesIntegrationAcceptedRespDTO upsertClient(MesPartnerUpsertReqDTO reqDTO) {
        MesMdClientDO exist = clientMapper.selectByCode(reqDTO.getPartnerCode());
        String businessStatus;
        if (exist == null) {
            // 新建：type 等客户类型等 MES 自有属性沿用默认，后续可在 MES 维护
            clientMapper.insert(MesMdClientDO.builder()
                    .code(reqDTO.getPartnerCode())
                    .name(reqDTO.getName())
                    .contact1Name(reqDTO.getContact())
                    .contact1Telephone(reqDTO.getMobile())
                    .telephone(reqDTO.getTelephone())
                    .email(reqDTO.getEmail())
                    .creditCode(reqDTO.getTaxNo())
                    .type(0)
                    .status(resolveStatus(reqDTO.getStatus()))
                    .remark(reqDTO.getRemark())
                    .build());
            businessStatus = MesMaterialIntegrationServiceImpl.BUSINESS_STATUS_CREATED;
        } else {
            // 更新：仅覆盖 ERP 权威字段；ERP 为空的字段不覆盖 MES 已有数据
            clientMapper.updateById(MesMdClientDO.builder()
                    .id(exist.getId())
                    .name(reqDTO.getName())
                    .contact1Name(reqDTO.getContact())
                    .contact1Telephone(reqDTO.getMobile())
                    .telephone(reqDTO.getTelephone())
                    .email(reqDTO.getEmail())
                    .creditCode(reqDTO.getTaxNo())
                    .status(resolveStatus(reqDTO.getStatus()))
                    .remark(reqDTO.getRemark())
                    .build());
            businessStatus = MesMaterialIntegrationServiceImpl.BUSINESS_STATUS_UPDATED;
        }
        log.info("[upsertClient][ERP 推送客户({} - {}) 落库成功，状态({})]", reqDTO.getPartnerCode(),
                reqDTO.getName(), businessStatus);
        return buildResponse(reqDTO, businessStatus);
    }

    private MesIntegrationAcceptedRespDTO upsertVendor(MesPartnerUpsertReqDTO reqDTO) {
        MesMdVendorDO exist = vendorMapper.selectByCode(reqDTO.getPartnerCode());
        String businessStatus;
        if (exist == null) {
            // 新建：等级、评分等 MES 自有属性沿用默认，后续可在 MES 维护
            vendorMapper.insert(MesMdVendorDO.builder()
                    .code(reqDTO.getPartnerCode())
                    .name(reqDTO.getName())
                    .contact1Name(reqDTO.getContact())
                    .contact1Telephone(reqDTO.getMobile())
                    .telephone(reqDTO.getTelephone())
                    .email(reqDTO.getEmail())
                    .creditCode(reqDTO.getTaxNo())
                    .status(resolveStatus(reqDTO.getStatus()))
                    .remark(reqDTO.getRemark())
                    .build());
            businessStatus = MesMaterialIntegrationServiceImpl.BUSINESS_STATUS_CREATED;
        } else {
            vendorMapper.updateById(MesMdVendorDO.builder()
                    .id(exist.getId())
                    .name(reqDTO.getName())
                    .contact1Name(reqDTO.getContact())
                    .contact1Telephone(reqDTO.getMobile())
                    .telephone(reqDTO.getTelephone())
                    .email(reqDTO.getEmail())
                    .creditCode(reqDTO.getTaxNo())
                    .status(resolveStatus(reqDTO.getStatus()))
                    .remark(reqDTO.getRemark())
                    .build());
            businessStatus = MesMaterialIntegrationServiceImpl.BUSINESS_STATUS_UPDATED;
        }
        log.info("[upsertVendor][ERP 推送供应商({} - {}) 落库成功，状态({})]", reqDTO.getPartnerCode(),
                reqDTO.getName(), businessStatus);
        return buildResponse(reqDTO, businessStatus);
    }

    private MesIntegrationAcceptedRespDTO buildResponse(MesPartnerUpsertReqDTO reqDTO, String businessStatus) {
        return new MesIntegrationAcceptedRespDTO().setAccepted(true)
                .setRequestId(reqDTO.getRequestId())
                .setTargetCode(reqDTO.getPartnerCode())
                .setBusinessStatus(businessStatus)
                .setProcessedVersion(reqDTO.getDataVersion());
    }

    private void validate(MesPartnerUpsertReqDTO reqDTO) {
        String code = reqDTO.getPartnerCode();
        if (code == null || code.isBlank() || code.length() > 64) {
            throw exception(INTEGRATION_PARTNER_CODE_INVALID);
        }
        String name = reqDTO.getName();
        if (name == null || name.isBlank() || name.length() > 255) {
            throw exception(INTEGRATION_NAME_INVALID);
        }
        if (reqDTO.getPartnerType() == null || reqDTO.getPartnerType().isBlank()) {
            throw exception(INTEGRATION_PARTNER_TYPE_INVALID);
        }
        if (reqDTO.getDataVersion() == null || reqDTO.getDataVersion() <= 0) {
            throw exception(INTEGRATION_DATA_VERSION_INVALID);
        }
        String requestId = reqDTO.getRequestId();
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            throw exception(INTEGRATION_REQUEST_ID_INVALID);
        }
    }

    private Integer resolveStatus(String status) {
        if (MesMaterialIntegrationServiceImpl.STATUS_ENABLED.equals(status)) {
            return CommonStatusEnum.ENABLE.getStatus();
        }
        if (MesMaterialIntegrationServiceImpl.STATUS_DISABLED.equals(status)) {
            return CommonStatusEnum.DISABLE.getStatus();
        }
        throw exception(INTEGRATION_STATUS_INVALID);
    }

}
