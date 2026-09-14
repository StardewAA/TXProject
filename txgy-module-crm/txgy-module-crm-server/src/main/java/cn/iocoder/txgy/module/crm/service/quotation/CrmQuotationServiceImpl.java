package cn.iocoder.txgy.module.crm.service.quotation;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.common.util.object.ObjectUtils;
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.txgy.module.crm.controller.admin.contract.vo.contract.CrmContractSaveReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation.CrmQuotationPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.customer.vo.customer.CrmCustomerSaveReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation.CrmQuotationSaveReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation.CrmQuotationTransferReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.quotation.CrmQuotationDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.quotation.CrmQuotationProductDO;
import cn.iocoder.txgy.module.crm.dal.mysql.quotation.CrmQuotationMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.quotation.CrmQuotationProductMapper;
import cn.iocoder.txgy.module.crm.dal.redis.no.CrmNoRedisDAO;
import cn.iocoder.txgy.module.crm.enums.common.CrmAuditStatusEnum;
import cn.iocoder.txgy.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.txgy.module.crm.enums.permission.CrmPermissionLevelEnum;
import cn.iocoder.txgy.module.crm.framework.permission.core.annotations.CrmPermission;
import cn.iocoder.txgy.module.crm.service.business.CrmBusinessService;
import cn.iocoder.txgy.module.crm.service.contract.CrmContractService;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerService;
import cn.iocoder.txgy.module.crm.service.permission.CrmPermissionService;
import cn.iocoder.txgy.module.crm.service.permission.bo.CrmPermissionCreateReqBO;
import cn.iocoder.txgy.module.crm.service.permission.bo.CrmPermissionTransferReqBO;
import cn.iocoder.txgy.module.crm.service.product.CrmProductService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.service.impl.DiffParseFunction;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.*;
import static cn.iocoder.txgy.module.crm.enums.LogRecordConstants.*;
import static cn.iocoder.txgy.module.crm.util.CrmAuditStatusUtils.convertBpmResultToAuditStatus;

/**
 * CRM 报价单 Service 实现类
 *
 * @author 防腐保温项目组
 */
@Service
@Validated
@Slf4j
public class CrmQuotationServiceImpl implements CrmQuotationService {

    /**
     * BPM 报价单审批流程标识
     */
    public static final String BPM_PROCESS_DEFINITION_KEY = "crm-quotation-audit";

    @Resource
    private CrmQuotationMapper quotationMapper;
    @Resource
    private CrmQuotationProductMapper quotationProductMapper;

    @Resource
    private CrmNoRedisDAO noRedisDAO;

    @Resource
    private CrmPermissionService crmPermissionService;
    @Resource
    private CrmProductService productService;
    @Resource
    private CrmCustomerService customerService;
    @Resource
    private CrmBusinessService businessService;
    @Resource
    private CrmContractService contractService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private BpmProcessInstanceApi bpmProcessInstanceApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_QUOTATION_TYPE, subType = CRM_QUOTATION_CREATE_SUB_TYPE, bizNo = "{{#quotation.id}}",
            success = CRM_QUOTATION_CREATE_SUCCESS)
    public Long createQuotation(CrmQuotationSaveReqVO createReqVO, Long userId) {
        // 1.1 校验产品项的有效性
        List<CrmQuotationProductDO> quotationProducts = validateQuotationProducts(createReqVO.getProducts());
        // 1.2 若手动填写了客户名称且未选择客户，则自动创建客户
        resolveCustomerId(createReqVO, createReqVO.getOwnerUserId(), userId);
        // 1.3 校验关联字段
        validateRelationDataExists(createReqVO);
        // 1.4 生成序号
        String no = noRedisDAO.generate(CrmNoRedisDAO.QUOTATION_NO_PREFIX);
        if (quotationMapper.selectByNo(no) != null) {
            throw exception(QUOTATION_NO_EXISTS);
        }

        // 2.1 插入报价单
        CrmQuotationDO quotation = BeanUtils.toBean(createReqVO, CrmQuotationDO.class).setNo(no);
        calculateTotalPrice(quotation, quotationProducts);
        quotationMapper.insert(quotation);
        // 2.2 插入报价单关联产品
        if (CollUtil.isNotEmpty(quotationProducts)) {
            quotationProducts.forEach(item -> item.setQuotationId(quotation.getId()));
            quotationProductMapper.insertBatch(quotationProducts);
        }

        // 3. 创建数据权限
        crmPermissionService.createPermission(new CrmPermissionCreateReqBO().setUserId(quotation.getOwnerUserId())
                .setBizType(CrmBizTypeEnum.CRM_QUOTATION.getType()).setBizId(quotation.getId())
                .setLevel(CrmPermissionLevelEnum.OWNER.getLevel()));

        // 4. 记录操作日志上下文
        LogRecordContext.putVariable("quotation", quotation);
        return quotation.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_QUOTATION_TYPE, subType = CRM_QUOTATION_UPDATE_SUB_TYPE, bizNo = "{{#updateReqVO.id}}",
            success = CRM_QUOTATION_UPDATE_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_QUOTATION, bizId = "#updateReqVO.id", level = CrmPermissionLevelEnum.WRITE)
    public void updateQuotation(CrmQuotationSaveReqVO updateReqVO, Long userId) {
        Assert.notNull(updateReqVO.getId(), "报价编号不能为空");
        updateReqVO.setOwnerUserId(null); // 不允许更新的字段
        // 1.1 校验存在
        CrmQuotationDO oldQuotation = validateQuotationExists(updateReqVO.getId());
        // 1.2 只有草稿、审批中，可以编辑
        if (!ObjectUtils.equalsAny(oldQuotation.getAuditStatus(), CrmAuditStatusEnum.DRAFT.getStatus(),
                CrmAuditStatusEnum.PROCESS.getStatus())) {
            throw exception(QUOTATION_UPDATE_FAIL_NOT_DRAFT);
        }
        // 1.3 若手动填写了客户名称且未选择客户，则自动创建客户
        resolveCustomerId(updateReqVO, oldQuotation.getOwnerUserId(), userId);
        // 1.4 校验产品项的有效性
        List<CrmQuotationProductDO> quotationProducts = validateQuotationProducts(updateReqVO.getProducts());
        // 1.5 校验关联字段
        validateRelationDataExists(updateReqVO);

        // 2.1 更新报价单
        CrmQuotationDO updateObj = BeanUtils.toBean(updateReqVO, CrmQuotationDO.class);
        calculateTotalPrice(updateObj, quotationProducts);
        quotationMapper.updateById(updateObj);
        // 2.2 更新报价单关联产品
        updateQuotationProduct(updateReqVO.getId(), quotationProducts);

        // 3. 记录操作日志上下文
        updateReqVO.setOwnerUserId(oldQuotation.getOwnerUserId()); // 避免操作日志出现“删除负责人”的情况
        LogRecordContext.putVariable(DiffParseFunction.OLD_OBJECT, BeanUtils.toBean(oldQuotation, CrmQuotationSaveReqVO.class));
        LogRecordContext.putVariable("quotationName", oldQuotation.getName());
    }

    private void updateQuotationProduct(Long id, List<CrmQuotationProductDO> newList) {
        List<CrmQuotationProductDO> oldList = quotationProductMapper.selectListByQuotationId(id);
        List<List<CrmQuotationProductDO>> diffList = diffList(oldList, newList, // id 不同，就认为是不同的记录
                (oldVal, newVal) -> oldVal.getId().equals(newVal.getId()));
        if (CollUtil.isNotEmpty(diffList.get(0))) {
            diffList.get(0).forEach(o -> o.setQuotationId(id));
            quotationProductMapper.insertBatch(diffList.get(0));
        }
        if (CollUtil.isNotEmpty(diffList.get(1))) {
            quotationProductMapper.updateBatch(diffList.get(1));
        }
        if (CollUtil.isNotEmpty(diffList.get(2))) {
            quotationProductMapper.deleteByIds(convertSet(diffList.get(2), CrmQuotationProductDO::getId));
        }
    }

    /**
     * 解析客户编号：若未选择客户、但手动填写了客户名称，则自动创建客户
     *
     * @param reqVO       请求
     * @param ownerUserId 客户归属的负责人编号
     * @param userId      操作人编号
     */
    private void resolveCustomerId(CrmQuotationSaveReqVO reqVO, Long ownerUserId, Long userId) {
        if (reqVO.getCustomerId() == null && StrUtil.isNotBlank(reqVO.getCustomerName())) {
            Long customerId = customerService.createCustomer(
                    new CrmCustomerSaveReqVO().setName(reqVO.getCustomerName()).setOwnerUserId(ownerUserId),
                    userId);
            reqVO.setCustomerId(customerId);
        }
    }

    /**
     * 校验关联数据是否存在
     *
     * @param reqVO 请求
     */
    private void validateRelationDataExists(CrmQuotationSaveReqVO reqVO) {
        // 1. 校验客户
        if (reqVO.getCustomerId() != null) {
            customerService.validateCustomer(reqVO.getCustomerId());
        }
        // 2. 校验负责人
        if (reqVO.getOwnerUserId() != null) {
            adminUserApi.validateUser(reqVO.getOwnerUserId());
        }
        // 3. 如果有关联商机，则需要校验存在
        if (reqVO.getBusinessId() != null) {
            businessService.validateBusiness(reqVO.getBusinessId());
        }
    }

    private List<CrmQuotationProductDO> validateQuotationProducts(List<CrmQuotationSaveReqVO.Product> list) {
        // 1. 校验产品存在
        productService.validProductList(convertSet(list, CrmQuotationSaveReqVO.Product::getProductId));
        // 2. 转化为 CrmQuotationProductDO 列表
        return convertList(list, o -> BeanUtils.toBean(o, CrmQuotationProductDO.class,
                item -> item.setTotalPrice(MoneyUtils.priceMultiply(item.getQuotationPrice(), item.getCount()))));
    }

    private void calculateTotalPrice(CrmQuotationDO quotation, List<CrmQuotationProductDO> quotationProducts) {
        quotation.setTotalProductPrice(getSumValue(quotationProducts, CrmQuotationProductDO::getTotalPrice, BigDecimal::add, BigDecimal.ZERO));
        BigDecimal discountPrice = MoneyUtils.priceMultiplyPercent(quotation.getTotalProductPrice(), quotation.getDiscountPercent());
        quotation.setTotalPrice(quotation.getTotalProductPrice().subtract(discountPrice));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_QUOTATION_TYPE, subType = CRM_QUOTATION_DELETE_SUB_TYPE, bizNo = "{{#id}}",
            success = CRM_QUOTATION_DELETE_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_QUOTATION, bizId = "#id", level = CrmPermissionLevelEnum.OWNER)
    public void deleteQuotation(Long id) {
        // 1.1 校验存在
        CrmQuotationDO quotation = validateQuotationExists(id);
        // 1.2 已生成合同的报价单不允许删除
        if (quotation.getContractId() != null) {
            throw exception(QUOTATION_DELETE_FAIL_HAS_CONTRACT);
        }

        // 2.1 删除报价单关联产品
        quotationProductMapper.deleteByQuotationId(id);
        // 2.2 删除报价单
        quotationMapper.deleteById(id);
        // 2.3 删除数据权限
        crmPermissionService.deletePermission(CrmBizTypeEnum.CRM_QUOTATION.getType(), id);

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("quotationName", quotation.getName());
    }

    private CrmQuotationDO validateQuotationExists(Long id) {
        CrmQuotationDO quotation = quotationMapper.selectById(id);
        if (quotation == null) {
            throw exception(QUOTATION_NOT_EXISTS);
        }
        return quotation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_QUOTATION_TYPE, subType = CRM_QUOTATION_TRANSFER_SUB_TYPE, bizNo = "{{#reqVO.id}}",
            success = CRM_QUOTATION_TRANSFER_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_QUOTATION, bizId = "#reqVO.id", level = CrmPermissionLevelEnum.OWNER)
    public void transferQuotation(CrmQuotationTransferReqVO reqVO, Long userId) {
        // 1. 校验报价单是否存在
        CrmQuotationDO quotation = validateQuotationExists(reqVO.getId());

        // 2.1 数据权限转移
        crmPermissionService.transferPermission(new CrmPermissionTransferReqBO(userId, CrmBizTypeEnum.CRM_QUOTATION.getType(),
                reqVO.getId(), reqVO.getNewOwnerUserId(), reqVO.getOldOwnerPermissionLevel()));
        // 2.2 设置负责人
        quotationMapper.updateById(new CrmQuotationDO().setId(reqVO.getId()).setOwnerUserId(reqVO.getNewOwnerUserId()));

        // 3. 记录转移日志
        LogRecordContext.putVariable("quotation", quotation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_QUOTATION_TYPE, subType = CRM_QUOTATION_SUBMIT_SUB_TYPE, bizNo = "{{#id}}",
            success = CRM_QUOTATION_SUBMIT_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_QUOTATION, bizId = "#id", level = CrmPermissionLevelEnum.WRITE)
    public void submitQuotation(Long id, Long userId) {
        // 1. 校验报价单是否在草稿状态
        CrmQuotationDO quotation = validateQuotationExists(id);
        if (ObjUtil.notEqual(quotation.getAuditStatus(), CrmAuditStatusEnum.DRAFT.getStatus())) {
            throw exception(QUOTATION_SUBMIT_FAIL_NOT_DRAFT);
        }

        // 2. 创建报价单审批流程实例
        String processInstanceId = bpmProcessInstanceApi.createProcessInstance(userId, new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(BPM_PROCESS_DEFINITION_KEY).setBusinessKey(String.valueOf(id))).getCheckedData();

        // 3. 更新报价单工作流编号与审批状态
        quotationMapper.updateById(new CrmQuotationDO().setId(id).setProcessInstanceId(processInstanceId)
                .setAuditStatus(CrmAuditStatusEnum.PROCESS.getStatus()));

        // 4. 记录日志
        LogRecordContext.putVariable("quotationName", quotation.getName());
    }

    @Override
    public void updateQuotationAuditStatus(Long id, Integer bpmResult) {
        // 1.1 校验报价单是否存在
        CrmQuotationDO quotation = validateQuotationExists(id);
        // 1.2 只有审批中，可以更新审批结果
        if (ObjUtil.notEqual(quotation.getAuditStatus(), CrmAuditStatusEnum.PROCESS.getStatus())) {
            log.error("[updateQuotationAuditStatus][报价单({}) 不处于审批中，无法更新审批结果({})]",
                    quotation.getId(), bpmResult);
            throw exception(QUOTATION_UPDATE_AUDIT_STATUS_FAIL_NOT_PROCESS);
        }

        // 2. 更新报价单审批结果
        Integer auditStatus = convertBpmResultToAuditStatus(bpmResult);
        quotationMapper.updateById(new CrmQuotationDO().setId(id).setAuditStatus(auditStatus));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_QUOTATION_TYPE, subType = CRM_QUOTATION_CREATE_CONTRACT_SUB_TYPE, bizNo = "{{#id}}",
            success = CRM_QUOTATION_CREATE_CONTRACT_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_QUOTATION, bizId = "#id", level = CrmPermissionLevelEnum.WRITE)
    public Long createContractByQuotation(Long id, Long userId) {
        // 1. 校验报价单
        CrmQuotationDO quotation = validateQuotationExists(id);
        // 1.1 只有审批通过的报价单可以转为合同
        if (ObjUtil.notEqual(quotation.getAuditStatus(), CrmAuditStatusEnum.APPROVE.getStatus())) {
            throw exception(QUOTATION_CREATE_CONTRACT_FAIL_NOT_APPROVE);
        }
        // 1.2 校验是否已经生成合同
        if (quotation.getContractId() != null) {
            throw exception(QUOTATION_CREATE_CONTRACT_FAIL_EXISTS);
        }

        // 2. 组装合同创建请求
        CrmContractSaveReqVO contractReqVO = new CrmContractSaveReqVO()
                .setName(quotation.getName())
                .setCustomerId(quotation.getCustomerId())
                .setBusinessId(quotation.getBusinessId())
                .setOwnerUserId(quotation.getOwnerUserId())
                .setOrderDate(quotation.getOrderDate())
                .setDiscountPercent(quotation.getDiscountPercent())
                .setRemark(quotation.getRemark());
        List<CrmQuotationProductDO> quotationProducts = quotationProductMapper.selectListByQuotationId(id);
        contractReqVO.setProducts(convertList(quotationProducts, item -> new CrmContractSaveReqVO.Product(
                item.getProductId(), item.getProductPrice(), item.getQuotationPrice(), item.getCount().intValue())));

        // 3. 创建合同（合同编号：沿用报价金额等数据）
        Long contractId = contractService.createContract(contractReqVO, userId);

        // 4. 回填报价单的合同编号
        quotationMapper.updateById(new CrmQuotationDO().setId(id).setContractId(contractId));

        // 5. 记录日志
        LogRecordContext.putVariable("quotationName", quotation.getName());
        return contractId;
    }

    // ======================= 查询相关 =======================

    @Override
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_QUOTATION, bizId = "#id", level = CrmPermissionLevelEnum.READ)
    public CrmQuotationDO getQuotation(Long id) {
        return quotationMapper.selectById(id);
    }

    @Override
    public CrmQuotationDO validateQuotation(Long id) {
        return validateQuotationExists(id);
    }

    @Override
    public List<CrmQuotationDO> getQuotationList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return ListUtil.empty();
        }
        return quotationMapper.selectByIds(ids);
    }

    @Override
    public PageResult<CrmQuotationDO> getQuotationPage(CrmQuotationPageReqVO pageReqVO, Long userId) {
        return quotationMapper.selectPage(pageReqVO, userId);
    }

    @Override
    public Long getQuotationCountByCustomerId(Long customerId) {
        return quotationMapper.selectCountByCustomerId(customerId);
    }

    @Override
    public Long getQuotationCountByBusinessId(Long businessId) {
        return quotationMapper.selectCountByBusinessId(businessId);
    }

    @Override
    public List<CrmQuotationProductDO> getQuotationProductListByQuotationId(Long quotationId) {
        return quotationProductMapper.selectListByQuotationId(quotationId);
    }

}
