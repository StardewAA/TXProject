package cn.iocoder.txgy.module.mes.service.integration;

import cn.iocoder.txgy.module.mes.api.integration.dto.MesIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesWorkOrderCreateReqDTO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.client.MesMdClientDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.item.MesMdItemDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.txgy.module.mes.dal.mysql.md.client.MesMdClientMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.md.item.MesMdItemMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.workorder.MesProWorkOrderMapper;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderSourceTypeEnum;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderStatusEnum;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderTypeEnum;
import cn.iocoder.txgy.module.mes.service.md.autocode.MesMdAutoCodeRecordService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.*;

/**
 * MES 生产工单集成 Service 实现类
 *
 * ERP 销售订单按行项推送，MES 幂等创建来源为 ERP 的生产工单：
 * 幂等键 = 来源单号 + 产品物料，重复推送返回 EXISTED；
 * 工单编码走 PRO_WORK_ORDER_CODE 自动规则，规则异常时降级为确定性编码；
 * 落库状态为草稿（PREPARE），排产、报工、完工仍由 MES 既有流程推进。
 */
@Service
@Validated
@Slf4j
public class MesWorkOrderIntegrationServiceImpl implements MesWorkOrderIntegrationService {

    public static final String BUSINESS_STATUS_CREATED = "CREATED";
    public static final String BUSINESS_STATUS_EXISTED = "EXISTED";

    private static final String AUTO_CODE_RULE_WORK_ORDER = "PRO_WORK_ORDER_CODE";
    private static final String STATUS_ENABLED_CODE = "ENABLED";

    @Resource
    private MesProWorkOrderMapper workOrderMapper;
    @Resource
    private MesMdItemMapper itemMapper;
    @Resource
    private MesMdClientMapper clientMapper;
    @Resource
    private MesMdAutoCodeRecordService autoCodeRecordService;

    @Override
    public MesIntegrationAcceptedRespDTO createWorkOrder(MesWorkOrderCreateReqDTO reqDTO) {
        // 1. 校验推送内容
        validate(reqDTO);
        // 2. 解析 MES 物料（主数据须先行同步；缺失即报错，避免落出无产品的工单）
        MesMdItemDO item = itemMapper.selectByCode(reqDTO.getItemCode());
        if (item == null) {
            throw exception(INTEGRATION_WORK_ORDER_ITEM_NOT_FOUND, reqDTO.getItemCode());
        }
        // 3. 幂等：同一来源单号 + 同一产品只建一次工单（重试/重复推送不重复建单）
        MesProWorkOrderDO existed = workOrderMapper.selectBySourceAndProduct(
                reqDTO.getOrderSourceCode(), item.getId());
        if (existed != null) {
            log.info("[createWorkOrder][ERP 单({}) 物料({}) 已存在工单({})，跳过建单]",
                    reqDTO.getOrderSourceCode(), reqDTO.getItemCode(), existed.getCode());
            return buildResponse(reqDTO, existed.getCode(), BUSINESS_STATUS_EXISTED);
        }
        // 4. 组装并落库
        MesProWorkOrderDO workOrder = MesProWorkOrderDO.builder()
                .code(generateWorkOrderCode(reqDTO))
                .name(buildWorkOrderName(reqDTO))
                .type(MesProWorkOrderTypeEnum.SELF.getType())
                .orderSourceType(MesProWorkOrderSourceTypeEnum.ERP.getType())
                .orderSourceCode(reqDTO.getOrderSourceCode())
                .productId(item.getId())
                .quantity(reqDTO.getQuantity())
                .quantityProduced(BigDecimal.ZERO)
                .quantityScheduled(BigDecimal.ZERO)
                .clientId(resolveClientId(reqDTO.getCustomerCode()))
                .requestDate(reqDTO.getRequestDate())
                .parentId(MesProWorkOrderDO.PARENT_ID_NULL)
                .status(MesProWorkOrderStatusEnum.PREPARE.getStatus())
                .remark(reqDTO.getRemark())
                .build();
        workOrderMapper.insert(workOrder);
        log.info("[createWorkOrder][ERP 单({}) 物料({}) 建单成功，工单编码({})]",
                reqDTO.getOrderSourceCode(), reqDTO.getItemCode(), workOrder.getCode());
        return buildResponse(reqDTO, workOrder.getCode(), BUSINESS_STATUS_CREATED);
    }

    /**
     * 生成工单编码：优先走 MES 自动编码规则，规则未配置或生成失败时降级为确定性编码
     */
    private String generateWorkOrderCode(MesWorkOrderCreateReqDTO reqDTO) {
        try {
            return autoCodeRecordService.generateAutoCode(AUTO_CODE_RULE_WORK_ORDER);
        } catch (Exception ex) {
            log.warn("[generateWorkOrderCode][自动编码规则({}) 不可用，降级为确定性编码：{}]",
                    AUTO_CODE_RULE_WORK_ORDER, ex.getMessage());
            return "ERP-" + reqDTO.getOrderSourceCode() + "-" + reqDTO.getItemCode();
        }
    }

    private String buildWorkOrderName(MesWorkOrderCreateReqDTO reqDTO) {
        String name = reqDTO.getOrderSourceCode() + "-" + reqDTO.getProductName();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private Long resolveClientId(String customerCode) {
        if (customerCode == null || customerCode.isBlank()) {
            return null;
        }
        MesMdClientDO client = clientMapper.selectByCode(customerCode);
        return client == null ? null : client.getId();
    }

    private MesIntegrationAcceptedRespDTO buildResponse(MesWorkOrderCreateReqDTO reqDTO, String workOrderCode,
                                                        String businessStatus) {
        return new MesIntegrationAcceptedRespDTO().setAccepted(true)
                .setRequestId(reqDTO.getRequestId())
                .setTargetCode(workOrderCode)
                .setBusinessStatus(businessStatus)
                .setProcessedVersion(reqDTO.getDataVersion());
    }

    private void validate(MesWorkOrderCreateReqDTO reqDTO) {
        String sourceCode = reqDTO.getOrderSourceCode();
        if (sourceCode == null || sourceCode.isBlank() || sourceCode.length() > 64) {
            throw exception(INTEGRATION_WORK_ORDER_SOURCE_CODE_INVALID);
        }
        String itemCode = reqDTO.getItemCode();
        if (itemCode == null || itemCode.isBlank() || itemCode.length() > 64) {
            throw exception(INTEGRATION_MATERIAL_CODE_INVALID);
        }
        String productName = reqDTO.getProductName();
        if (productName == null || productName.isBlank() || productName.length() > 255) {
            throw exception(INTEGRATION_NAME_INVALID);
        }
        if (reqDTO.getQuantity() == null || reqDTO.getQuantity().signum() <= 0) {
            throw exception(INTEGRATION_WORK_ORDER_QUANTITY_INVALID);
        }
        if (reqDTO.getRequestDate() == null) {
            throw exception(INTEGRATION_WORK_ORDER_REQUEST_DATE_INVALID);
        }
        if (reqDTO.getDataVersion() == null || reqDTO.getDataVersion() <= 0) {
            throw exception(INTEGRATION_DATA_VERSION_INVALID);
        }
        String requestId = reqDTO.getRequestId();
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            throw exception(INTEGRATION_REQUEST_ID_INVALID);
        }
    }

}
