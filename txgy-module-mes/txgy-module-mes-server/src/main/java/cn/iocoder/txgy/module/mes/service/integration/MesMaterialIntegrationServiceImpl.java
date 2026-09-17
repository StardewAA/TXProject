package cn.iocoder.txgy.module.mes.service.integration;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesMaterialUpsertReqDTO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.item.MesMdItemDO;
import cn.iocoder.txgy.module.mes.dal.mysql.md.item.MesMdItemMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.*;

/**
 * MES 物料主数据集成 Service 实现类
 *
 * ERP 是物料主数据的权威来源，按 ERP 物料编码幂等 upsert 到 mes_md_item：
 * 新建时分类、计量单位、库存与批次标记等生产属性使用 MES 默认值（0 / 表默认），
 * 更新时仅覆盖 ERP 权威字段（名称、规格、状态、备注），不触碰 MES 生产属性。
 */
@Service
@Validated
@Slf4j
public class MesMaterialIntegrationServiceImpl implements MesMaterialIntegrationService {

    public static final String BUSINESS_STATUS_CREATED = "CREATED";
    public static final String BUSINESS_STATUS_UPDATED = "UPDATED";
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    @Resource
    private MesMdItemMapper itemMapper;

    @Override
    public MesIntegrationAcceptedRespDTO upsertMaterial(MesMaterialUpsertReqDTO reqDTO) {
        // 1. 校验推送内容
        validate(reqDTO);
        // 2. 按 ERP 权威物料编码查询既有物料
        MesMdItemDO exist = itemMapper.selectByCode(reqDTO.getMaterialCode());
        String businessStatus;
        if (exist == null) {
            // 3.1 新建：生产属性（分类/单位/库存/批次标记）不设置，沿用 MES 表默认值
            itemMapper.insert(MesMdItemDO.builder()
                    .code(reqDTO.getMaterialCode())
                    .name(reqDTO.getName())
                    .specification(reqDTO.getSpecification())
                    .unitMeasureId(0L)
                    .itemTypeId(0L)
                    .status(resolveStatus(reqDTO.getStatus()))
                    .remark(reqDTO.getRemark())
                    .build());
            businessStatus = BUSINESS_STATUS_CREATED;
        } else {
            // 3.2 更新：仅覆盖 ERP 权威字段，不触碰 MES 生产属性（安全库存/批次/高值等）
            itemMapper.updateById(MesMdItemDO.builder()
                    .id(exist.getId())
                    .name(reqDTO.getName())
                    .specification(reqDTO.getSpecification())
                    .status(resolveStatus(reqDTO.getStatus()))
                    .remark(reqDTO.getRemark())
                    .build());
            businessStatus = BUSINESS_STATUS_UPDATED;
        }
        log.info("[upsertMaterial][ERP 推送物料({} - {}) 落库成功，状态({})]", reqDTO.getMaterialCode(),
                reqDTO.getName(), businessStatus);
        return new MesIntegrationAcceptedRespDTO().setAccepted(true)
                .setRequestId(reqDTO.getRequestId())
                .setTargetCode(reqDTO.getMaterialCode())
                .setBusinessStatus(businessStatus)
                .setProcessedVersion(reqDTO.getDataVersion());
    }

    private void validate(MesMaterialUpsertReqDTO reqDTO) {
        String code = reqDTO.getMaterialCode();
        if (code == null || code.isBlank() || code.length() > 64) {
            throw exception(INTEGRATION_MATERIAL_CODE_INVALID);
        }
        String name = reqDTO.getName();
        if (name == null || name.isBlank() || name.length() > 255) {
            throw exception(INTEGRATION_NAME_INVALID);
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
        if (STATUS_ENABLED.equals(status)) {
            return CommonStatusEnum.ENABLE.getStatus();
        }
        if (STATUS_DISABLED.equals(status)) {
            return CommonStatusEnum.DISABLE.getStatus();
        }
        throw exception(INTEGRATION_STATUS_INVALID);
    }

}
