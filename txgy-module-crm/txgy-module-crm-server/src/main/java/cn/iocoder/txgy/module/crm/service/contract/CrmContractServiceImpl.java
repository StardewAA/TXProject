package cn.iocoder.txgy.module.crm.service.contract;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.common.util.object.ObjectUtils;
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.txgy.module.crm.controller.admin.contract.vo.contract.CrmContractPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.contract.vo.contract.CrmContractSaveReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.contract.vo.contract.CrmContractTransferReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contract.CrmContractConfigDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contract.CrmContractDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contract.CrmContractProductDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.dal.mysql.contract.CrmContractMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.contract.CrmContractProductMapper;
import cn.iocoder.txgy.module.crm.dal.redis.no.CrmNoRedisDAO;
import cn.iocoder.txgy.module.crm.enums.common.CrmAuditStatusEnum;
import cn.iocoder.txgy.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.txgy.module.crm.enums.contract.CrmContractTemplateTypeEnum;
import cn.iocoder.txgy.module.crm.enums.permission.CrmPermissionLevelEnum;
import cn.iocoder.txgy.module.crm.framework.permission.core.annotations.CrmPermission;
import cn.iocoder.txgy.module.crm.service.business.CrmBusinessService;
import cn.iocoder.txgy.module.crm.service.contact.CrmContactService;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerService;
import cn.iocoder.txgy.module.crm.service.permission.CrmPermissionService;
import cn.iocoder.txgy.module.crm.service.permission.bo.CrmPermissionCreateReqBO;
import cn.iocoder.txgy.module.crm.service.permission.bo.CrmPermissionTransferReqBO;
import cn.iocoder.txgy.module.crm.service.product.CrmProductService;
import cn.iocoder.txgy.module.crm.service.receivable.CrmReceivableService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.service.impl.DiffParseFunction;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.*;
import static cn.iocoder.txgy.module.crm.enums.LogRecordConstants.*;
import static cn.iocoder.txgy.module.crm.util.CrmAuditStatusUtils.convertBpmResultToAuditStatus;

/**
 * CRM 合同 Service 实现类
 *
 * @author dhb52
 */
@Service
@Validated
@Slf4j
public class CrmContractServiceImpl implements CrmContractService {

    /**
     * BPM 合同审批流程标识
     */
    public static final String BPM_PROCESS_DEFINITION_KEY = "crm-contract-audit";

    @Resource
    private CrmContractMapper contractMapper;
    @Resource
    private CrmContractProductMapper contractProductMapper;

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
    private CrmContactService contactService;
    @Resource
    private CrmContractConfigService contractConfigService;
    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private CrmReceivableService receivableService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private BpmProcessInstanceApi bpmProcessInstanceApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_CONTRACT_TYPE, subType = CRM_CONTRACT_CREATE_SUB_TYPE, bizNo = "{{#contract.id}}",
            success = CRM_CONTRACT_CREATE_SUCCESS)
    public Long createContract(CrmContractSaveReqVO createReqVO, Long userId) {
        // 1.1 校验产品项的有效性
        List<CrmContractProductDO> contractProducts = validateContractProducts(createReqVO.getProducts());
        // 1.2 校验关联字段
        validateRelationDataExists(createReqVO);
        // 1.3 生成序号
        String no = noRedisDAO.generate(CrmNoRedisDAO.CONTRACT_NO_PREFIX);
        if (contractMapper.selectByNo(no) != null) {
            throw exception(CONTRACT_NO_EXISTS);
        }

        // 2.1 插入合同
        CrmContractDO contract = BeanUtils.toBean(createReqVO, CrmContractDO.class).setNo(no);
        calculateTotalPrice(contract, contractProducts);
        // 2.1.1 填充合同模板默认值（我方主体取配置、对方取客户档案、条款取配置默认值）
        fillContractTemplateDefaults(contract, createReqVO.getCustomerId());
        contractMapper.insert(contract);
        // 2.2 插入合同关联商品
        if (CollUtil.isNotEmpty(contractProducts)) {
            contractProducts.forEach(item -> item.setContractId(contract.getId()));
            contractProductMapper.insertBatch(contractProducts);
        }

        // 3. 创建数据权限
        crmPermissionService.createPermission(new CrmPermissionCreateReqBO().setUserId(contract.getOwnerUserId())
                .setBizType(CrmBizTypeEnum.CRM_CONTRACT.getType()).setBizId(contract.getId())
                .setLevel(CrmPermissionLevelEnum.OWNER.getLevel()));

        // 4. 记录操作日志上下文
        LogRecordContext.putVariable("contract", contract);
        return contract.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_CONTRACT_TYPE, subType = CRM_CONTRACT_UPDATE_SUB_TYPE, bizNo = "{{#updateReqVO.id}}",
            success = CRM_CONTRACT_UPDATE_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CONTRACT, bizId = "#updateReqVO.id", level = CrmPermissionLevelEnum.WRITE)
    public void updateContract(CrmContractSaveReqVO updateReqVO) {
        Assert.notNull(updateReqVO.getId(), "合同编号不能为空");
        updateReqVO.setOwnerUserId(null); // 不允许更新的字段
        // 1.1 校验存在
        CrmContractDO oldContract = validateContractExists(updateReqVO.getId());
        // 1.2 只有草稿、审批中，可以编辑；
        if (!ObjectUtils.equalsAny(oldContract.getAuditStatus(), CrmAuditStatusEnum.DRAFT.getStatus(),
                CrmAuditStatusEnum.PROCESS.getStatus())) {
            throw exception(CONTRACT_UPDATE_FAIL_NOT_DRAFT);
        }
        // 1.3 校验产品项的有效性
        List<CrmContractProductDO> contractProducts = validateContractProducts(updateReqVO.getProducts());
        // 1.4 校验关联字段
        validateRelationDataExists(updateReqVO);

        // 2.1 更新合同
        CrmContractDO updateObj = BeanUtils.toBean(updateReqVO, CrmContractDO.class);
        calculateTotalPrice(updateObj, contractProducts);
        // 2.1.1 填充合同模板默认值
        fillContractTemplateDefaults(updateObj, updateReqVO.getCustomerId());
        contractMapper.updateById(updateObj);
        // 2.2 更新合同关联商品
        updateContractProduct(updateReqVO.getId(), contractProducts);

        // 3. 记录操作日志上下文
        updateReqVO.setOwnerUserId(oldContract.getOwnerUserId()); // 避免操作日志出现“删除负责人”的情况
        LogRecordContext.putVariable(DiffParseFunction.OLD_OBJECT, BeanUtils.toBean(oldContract, CrmContractSaveReqVO.class));
        LogRecordContext.putVariable("contractName", oldContract.getName());
    }

    private void updateContractProduct(Long id, List<CrmContractProductDO> newList) {
        List<CrmContractProductDO> oldList = contractProductMapper.selectListByContractId(id);
        List<List<CrmContractProductDO>> diffList = diffList(oldList, newList, // id 不同，就认为是不同的记录
                (oldVal, newVal) -> oldVal.getId().equals(newVal.getId()));
        if (CollUtil.isNotEmpty(diffList.get(0))) {
            diffList.get(0).forEach(o -> o.setContractId(id));
            contractProductMapper.insertBatch(diffList.get(0));
        }
        if (CollUtil.isNotEmpty(diffList.get(1))) {
            contractProductMapper.updateBatch(diffList.get(1));
        }
        if (CollUtil.isNotEmpty(diffList.get(2))) {
            contractProductMapper.deleteByIds(convertSet(diffList.get(2), CrmContractProductDO::getId));
        }
    }

    // ==================== 合同模板默认值填充 ====================

    /**
     * 填充合同模板相关字段的默认值，确保生成的合同不遗漏必填项
     *
     * 取值优先级：合同中已填写的值 > 我方主体配置 / 客户档案 > 合同配置默认值
     *
     * @param contract   合同
     * @param customerId 客户编号
     */
    private void fillContractTemplateDefaults(CrmContractDO contract, Long customerId) {
        if (contract.getTemplateType() == null) {
            contract.setTemplateType(CrmContractTemplateTypeEnum.PURCHASE.getType());
        }
        CrmContractConfigDO config = contractConfigService.getContractConfig();
        if (config != null) {
            fillClauseDefaults(contract, config);
            // 违约责任：未单独维护时，使用配置中的默认值
            if (StrUtil.isBlank(contract.getPenaltyTerms())) {
                contract.setPenaltyTerms(buildDefaultPenaltyTerms(contract.getTemplateType(), config));
            }
            // 我方主体信息：采购合同为甲方，购销合同为乙方
            fillOurParty(contract, config);
        }
        // 对方主体信息兜底：使用客户名称
        fillCustomerParty(contract, customerId);
        // 付款金额：未填写时按合同总价与比例自动计算
        calculatePayAmounts(contract);
    }

    private void fillClauseDefaults(CrmContractDO contract, CrmContractConfigDO config) {
        if (StrUtil.isBlank(contract.getQualityStandard())) {
            contract.setQualityStandard(config.getDefaultQualityStandard());
        }
        if (contract.getSampleDays() == null) {
            contract.setSampleDays(config.getDefaultSampleDays());
        }
        if (contract.getDeliveryDays() == null) {
            contract.setDeliveryDays(config.getDefaultDeliveryDays());
        }
        if (contract.getDeliveryArriveDays() == null) {
            contract.setDeliveryArriveDays(config.getDefaultDeliveryArriveDays());
        }
        if (contract.getInstallDays() == null) {
            contract.setInstallDays(config.getDefaultInstallDays());
        }
        if (contract.getFirstAcceptDays() == null) {
            contract.setFirstAcceptDays(config.getDefaultFirstAcceptDays());
        }
        if (contract.getTrialDays() == null) {
            contract.setTrialDays(config.getDefaultTrialDays());
        }
        if (contract.getFinalAcceptDays() == null) {
            contract.setFinalAcceptDays(config.getDefaultFinalAcceptDays());
        }
        if (contract.getAutoAcceptDays() == null) {
            contract.setAutoAcceptDays(config.getDefaultAutoAcceptDays());
        }
        if (contract.getMaxRepairTimes() == null) {
            contract.setMaxRepairTimes(config.getDefaultMaxRepairTimes());
        }
        if (contract.getPrepayRatio() == null) {
            contract.setPrepayRatio(config.getDefaultPrepayRatio());
        }
        if (contract.getPrepayDays() == null) {
            contract.setPrepayDays(config.getDefaultPrepayDays());
        }
        if (contract.getAcceptPayRatio() == null) {
            contract.setAcceptPayRatio(config.getDefaultAcceptPayRatio());
        }
        if (contract.getAcceptPayDays() == null) {
            contract.setAcceptPayDays(config.getDefaultAcceptPayDays());
        }
        if (contract.getDepositRatio() == null) {
            contract.setDepositRatio(config.getDefaultDepositRatio());
        }
        if (contract.getDepositMonths() == null) {
            contract.setDepositMonths(config.getDefaultDepositMonths());
        }
        if (contract.getDepositDays() == null) {
            contract.setDepositDays(config.getDefaultDepositDays());
        }
        if (contract.getWarrantyYears() == null) {
            contract.setWarrantyYears(config.getDefaultWarrantyYears());
        }
        if (contract.getResponseHours() == null) {
            contract.setResponseHours(config.getDefaultResponseHours());
        }
        if (contract.getRepairHours() == null) {
            contract.setRepairHours(config.getDefaultRepairHours());
        }
        if (contract.getRepairTimes() == null) {
            contract.setRepairTimes(config.getDefaultRepairTimes());
        }
        if (contract.getReportRepairDays() == null) {
            contract.setReportRepairDays(config.getDefaultReportRepairDays());
        }
        if (contract.getContractCopies() == null) {
            contract.setContractCopies(config.getDefaultContractCopies());
        }
        if (StrUtil.isBlank(contract.getGpcName())) {
            contract.setGpcName(config.getDefaultGpcName());
        }
    }

    private void fillOurParty(CrmContractDO contract, CrmContractConfigDO config) {
        if (StrUtil.isBlank(config.getOurCompanyName())) {
            return;
        }
        boolean isPurchase = !ObjUtil.equal(contract.getTemplateType(),
                CrmContractTemplateTypeEnum.SALES.getType());
        if (isPurchase) {
            if (StrUtil.isBlank(contract.getPartyAName())) {
                contract.setPartyAName(config.getOurCompanyName());
            }
            if (StrUtil.isBlank(contract.getPartyALegalPerson())) {
                contract.setPartyALegalPerson(config.getOurLegalPerson());
            }
            if (StrUtil.isBlank(contract.getPartyAAgent())) {
                contract.setPartyAAgent(config.getOurAgent());
            }
            if (StrUtil.isBlank(contract.getPartyAAddress())) {
                contract.setPartyAAddress(config.getOurAddress());
            }
            if (StrUtil.isBlank(contract.getPartyABankName())) {
                contract.setPartyABankName(config.getOurBankName());
            }
            if (StrUtil.isBlank(contract.getPartyABankAccount())) {
                contract.setPartyABankAccount(config.getOurBankAccount());
            }
            if (StrUtil.isBlank(contract.getPartyATel())) {
                contract.setPartyATel(config.getOurTel());
            }
            if (StrUtil.isBlank(contract.getPartyAFax())) {
                contract.setPartyAFax(config.getOurFax());
            }
        } else {
            if (StrUtil.isBlank(contract.getPartyBName())) {
                contract.setPartyBName(config.getOurCompanyName());
            }
            if (StrUtil.isBlank(contract.getPartyBLegalPerson())) {
                contract.setPartyBLegalPerson(config.getOurLegalPerson());
            }
            if (StrUtil.isBlank(contract.getPartyBAgent())) {
                contract.setPartyBAgent(config.getOurAgent());
            }
            if (StrUtil.isBlank(contract.getPartyBAddress())) {
                contract.setPartyBAddress(config.getOurAddress());
            }
            if (StrUtil.isBlank(contract.getPartyBBankName())) {
                contract.setPartyBBankName(config.getOurBankName());
            }
            if (StrUtil.isBlank(contract.getPartyBBankAccount())) {
                contract.setPartyBBankAccount(config.getOurBankAccount());
            }
            if (StrUtil.isBlank(contract.getPartyBTel())) {
                contract.setPartyBTel(config.getOurTel());
            }
            if (StrUtil.isBlank(contract.getPartyBFax())) {
                contract.setPartyBFax(config.getOurFax());
            }
        }
    }

    private void fillCustomerParty(CrmContractDO contract, Long customerId) {
        if (customerId == null) {
            return;
        }
        boolean isPurchase = !ObjUtil.equal(contract.getTemplateType(),
                CrmContractTemplateTypeEnum.SALES.getType());
        String existName = isPurchase ? contract.getPartyBName() : contract.getPartyAName();
        if (StrUtil.isNotBlank(existName)) {
            return;
        }
        CrmCustomerDO customer = customerService.getCustomer(customerId);
        if (customer == null) {
            return;
        }
        if (isPurchase) {
            contract.setPartyBName(customer.getName());
        } else {
            contract.setPartyAName(customer.getName());
        }
    }

    private String buildDefaultPenaltyTerms(Integer templateType, CrmContractConfigDO config) {
        JSONObject json = JSONUtil.createObj();
        if (ObjUtil.equal(templateType, CrmContractTemplateTypeEnum.SALES.getType())) {
            json.set("sBuyerLateRatio", config.getSBuyerLateRatio());
            json.set("sBuyerBreachRatio", config.getSBuyerBreachRatio());
            json.set("sSellerLateRatio", config.getSSellerLateRatio());
            json.set("sSellerBreachRatio", config.getSSellerBreachRatio());
        } else {
            json.set("pBuyerRejectRatio", config.getPBuyerRejectRatio());
            json.set("pBuyerOverdueWan", config.getPBuyerOverdueWan());
            json.set("pBuyerOverdueDays", config.getPBuyerOverdueDays());
            json.set("pSellerQuality", config.getPSellerQuality());
            json.set("pSellerOverdueWan", config.getPSellerOverdueWan());
            json.set("pSellerOverdueDays", config.getPSellerOverdueDays());
            json.set("pSellerCompensate", config.getPSellerCompensate());
            json.set("pSellerReplaceDays", config.getPSellerReplaceDays());
            json.set("pSellerTerminate", config.getPSellerTerminate());
            json.set("pSellerDefect", config.getPSellerDefect());
        }
        return json.toString();
    }

    private void calculatePayAmounts(CrmContractDO contract) {
        BigDecimal total = contract.getTotalPrice() == null ? BigDecimal.ZERO : contract.getTotalPrice();
        if (contract.getPrepayAmount() == null && contract.getPrepayRatio() != null) {
            contract.setPrepayAmount(calcAmountByRatio(total, contract.getPrepayRatio()));
        }
        if (contract.getAcceptPayAmount() == null && contract.getAcceptPayRatio() != null) {
            contract.setAcceptPayAmount(calcAmountByRatio(total, contract.getAcceptPayRatio()));
        }
        if (contract.getDepositAmount() == null && contract.getDepositRatio() != null) {
            contract.setDepositAmount(calcAmountByRatio(total, contract.getDepositRatio()));
        }
    }

    private static BigDecimal calcAmountByRatio(BigDecimal total, BigDecimal ratio) {
        return total.multiply(ratio).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * 校验关联数据是否存在
     *
     * @param reqVO 请求
     */
    private void validateRelationDataExists(CrmContractSaveReqVO reqVO) {
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
        // 4. 校验签约相关字段
        if (reqVO.getSignContactId() != null) {
            contactService.validateContact(reqVO.getSignContactId());
        }
        if (reqVO.getSignUserId() != null) {
            adminUserApi.validateUser(reqVO.getSignUserId());
        }
    }

    private List<CrmContractProductDO> validateContractProducts(List<CrmContractSaveReqVO.Product> list) {
        // 1. 校验产品存在
        productService.validProductList(convertSet(list, CrmContractSaveReqVO.Product::getProductId));
        // 2. 转化为 CrmContractProductDO 列表
        return convertList(list, o -> BeanUtils.toBean(o, CrmContractProductDO.class,
                item -> item.setTotalPrice(MoneyUtils.priceMultiply(item.getContractPrice(), item.getCount()))));
    }

    private void calculateTotalPrice(CrmContractDO contract, List<CrmContractProductDO> contractProducts) {
        contract.setTotalProductPrice(getSumValue(contractProducts, CrmContractProductDO::getTotalPrice, BigDecimal::add, BigDecimal.ZERO));
        BigDecimal discountPrice = MoneyUtils.priceMultiplyPercent(contract.getTotalProductPrice(), contract.getDiscountPercent());
        contract.setTotalPrice(contract.getTotalProductPrice().subtract(discountPrice));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_CONTRACT_TYPE, subType = CRM_CONTRACT_DELETE_SUB_TYPE, bizNo = "{{#id}}",
            success = CRM_CONTRACT_DELETE_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CONTRACT, bizId = "#id", level = CrmPermissionLevelEnum.OWNER)
    public void deleteContract(Long id) {
        // 1.1 校验存在
        CrmContractDO contract = validateContractExists(id);
        // 1.2 如果被 CrmReceivableDO 所使用，则不允许删除
        if (receivableService.getReceivableCountByContractId(contract.getId()) > 0) {
            throw exception(CONTRACT_DELETE_FAIL);
        }

        // 2.1 删除合同
        contractMapper.deleteById(id);
        // 2.2 删除数据权限
        crmPermissionService.deletePermission(CrmBizTypeEnum.CRM_CONTRACT.getType(), id);

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("contractName", contract.getName());
    }

    private CrmContractDO validateContractExists(Long id) {
        CrmContractDO contract = contractMapper.selectById(id);
        if (contract == null) {
            throw exception(CONTRACT_NOT_EXISTS);
        }
        return contract;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_CONTRACT_TYPE, subType = CRM_CONTRACT_TRANSFER_SUB_TYPE, bizNo = "{{#reqVO.id}}",
            success = CRM_CONTRACT_TRANSFER_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CONTRACT, bizId = "#reqVO.id", level = CrmPermissionLevelEnum.OWNER)
    public void transferContract(CrmContractTransferReqVO reqVO, Long userId) {
        // 1. 校验合同是否存在
        CrmContractDO contract = validateContractExists(reqVO.getId());

        // 2.1 数据权限转移
        crmPermissionService.transferPermission(new CrmPermissionTransferReqBO(userId, CrmBizTypeEnum.CRM_CONTRACT.getType(),
                reqVO.getId(), reqVO.getNewOwnerUserId(), reqVO.getOldOwnerPermissionLevel()));
        // 2.2 设置负责人
        contractMapper.updateById(new CrmContractDO().setId(reqVO.getId()).setOwnerUserId(reqVO.getNewOwnerUserId()));

        // 3. 记录转移日志
        LogRecordContext.putVariable("contract", contract);
    }

    @Override
    @LogRecord(type = CRM_CONTRACT_TYPE, subType = CRM_CONTRACT_FOLLOW_UP_SUB_TYPE, bizNo = "{{#id}}",
            success = CRM_CONTRACT_FOLLOW_UP_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CONTRACT, bizId = "#id", level = CrmPermissionLevelEnum.WRITE)
    public void updateContractFollowUp(Long id, LocalDateTime contactNextTime, String contactLastContent) {
        // 1. 校验存在
        CrmContractDO contract = validateContractExists(id);

        // 2. 更新联系人的跟进信息
        contractMapper.updateById(new CrmContractDO().setId(id).setContactLastTime(LocalDateTime.now()));

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("contractName", contract.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_CONTRACT_TYPE, subType = CRM_CONTRACT_SUBMIT_SUB_TYPE, bizNo = "{{#id}}",
            success = CRM_CONTRACT_SUBMIT_SUCCESS)
    public void submitContract(Long id, Long userId) {
        // 1. 校验合同是否在审批
        CrmContractDO contract = validateContractExists(id);
        if (ObjUtil.notEqual(contract.getAuditStatus(), CrmAuditStatusEnum.DRAFT.getStatus())) {
            throw exception(CONTRACT_SUBMIT_FAIL_NOT_DRAFT);
        }

        // 2. 创建合同审批流程实例
        //    注意：当「审批人与提交人为同一人」且节点配置为自动通过时，BPM 会在创建流程实例的过程中就直接
        //    审批通过，并【同步】远程回调本服务的 updateContractAuditStatus，把审批状态更新为「已通过」。
        //    因此必须等流程实例创建完成（即回调已落库）后，再回写本单据的状态，否则会把回调写入的终态覆盖掉。
        String processInstanceId = bpmProcessInstanceApi.createProcessInstance(userId, new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(BPM_PROCESS_DEFINITION_KEY).setBusinessKey(String.valueOf(id))).getCheckedData();

        // 3. 回写合同的工作流编号（仅更新该字段，不会覆盖回调写入的审批状态）
        contractMapper.updateById(new CrmContractDO().setId(id).setProcessInstanceId(processInstanceId));

        // 4. 更新审批状态为「审批中」：使用条件更新，仅在仍为「草稿」时才更新
        //    为什么用条件更新而不是先查询再判断？因为本事务的快照早于第 2 步的远程回调，
        //    普通查询会读到旧的「草稿」，从而把回调写入的「已通过」又覆盖回「审批中」。
        contractMapper.update(new CrmContractDO().setAuditStatus(CrmAuditStatusEnum.PROCESS.getStatus()),
                new LambdaUpdateWrapper<CrmContractDO>()
                        .eq(CrmContractDO::getId, id)
                        .eq(CrmContractDO::getAuditStatus, CrmAuditStatusEnum.DRAFT.getStatus()));

        // 5. 记录日志
        LogRecordContext.putVariable("contractName", contract.getName());
    }

    @Override
    public void updateContractAuditStatus(Long id, Integer bpmResult) {
        // 1.1 校验合同是否存在
        CrmContractDO contract = validateContractExists(id);
        // 1.2 仅「草稿」「审批中」允许更新审批结果：
        //     - 审批中：常规场景，即人工审批完成后回调本接口
        //     - 草稿：「审批人与提交人为同一人」自动通过时，BPM 在创建流程实例的过程中就会回调本接口，
        //             此时 submitContract 还没把状态回写为「审批中」（该事务甚至尚未提交），必须放行，
        //             否则回调会被丢弃，合同将永久停留在「审批中」
        //     - 其他：已是终态，直接忽略，保证回调的幂等性
        Integer currentAuditStatus = contract.getAuditStatus();
        if (ObjUtil.notEqual(currentAuditStatus, CrmAuditStatusEnum.PROCESS.getStatus())
                && ObjUtil.notEqual(currentAuditStatus, CrmAuditStatusEnum.DRAFT.getStatus())) {
            log.warn("[updateContractAuditStatus][contract({}) 已处于终态({})，忽略审批结果({})]",
                    contract.getId(), currentAuditStatus, bpmResult);
            return;
        }

        // 2. 更新合同审批结果
        Integer auditStatus = convertBpmResultToAuditStatus(bpmResult);
        contractMapper.updateById(new CrmContractDO().setId(id).setAuditStatus(auditStatus));
    }

    // ======================= 查询相关 =======================

    @Override
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CONTRACT, bizId = "#id", level = CrmPermissionLevelEnum.READ)
    public CrmContractDO getContract(Long id) {
        return contractMapper.selectById(id);
    }

    @Override
    public CrmContractDO validateContract(Long id) {
        return validateContractExists(id);
    }

    @Override
    public List<CrmContractDO> getContractList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return ListUtil.empty();
        }
        return contractMapper.selectByIds(ids);
    }

    @Override
    public PageResult<CrmContractDO> getContractPage(CrmContractPageReqVO pageReqVO, Long userId) {
        // 1. 即将到期，需要查询合同配置
        CrmContractConfigDO config = null;
        if (CrmContractPageReqVO.EXPIRY_TYPE_ABOUT_TO_EXPIRE.equals(pageReqVO.getExpiryType())) {
            config = contractConfigService.getContractConfig();
            if (config != null && Boolean.FALSE.equals(config.getNotifyEnabled())) {
                config = null;
            }
            if (config == null) {
                return PageResult.empty();
            }
        }
        // 2. 查询分页
        return contractMapper.selectPage(pageReqVO, userId, config);
    }

    @Override
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CUSTOMER, bizId = "#pageReqVO.customerId", level = CrmPermissionLevelEnum.READ)
    public PageResult<CrmContractDO> getContractPageByCustomerId(CrmContractPageReqVO pageReqVO) {
        return contractMapper.selectPageByCustomerId(pageReqVO);
    }

    @Override
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_BUSINESS, bizId = "#pageReqVO.businessId", level = CrmPermissionLevelEnum.READ)
    public PageResult<CrmContractDO> getContractPageByBusinessId(CrmContractPageReqVO pageReqVO) {
        return contractMapper.selectPageByBusinessId(pageReqVO);
    }

    @Override
    public Long getContractCountByContactId(Long contactId) {
        return contractMapper.selectCountByContactId(contactId);
    }

    @Override
    public Long getContractCountByCustomerId(Long customerId) {
        return contractMapper.selectCount(CrmContractDO::getCustomerId, customerId);
    }

    @Override
    public Long getContractCountByBusinessId(Long businessId) {
        return contractMapper.selectCountByBusinessId(businessId);
    }

    @Override
    public List<CrmContractProductDO> getContractProductListByContractId(Long contactId) {
        return contractProductMapper.selectListByContractId(contactId);
    }

    @Override
    public Long getAuditContractCount(Long userId) {
        return contractMapper.selectCountByAudit(userId);
    }

    @Override
    public Long getRemindContractCount(Long userId) {
        CrmContractConfigDO config = contractConfigService.getContractConfig();
        if (config == null || Boolean.FALSE.equals(config.getNotifyEnabled())) {
            return 0L;
        }
        return contractMapper.selectCountByRemind(userId, config);
    }

    @Override
    public List<CrmContractDO> getContractListByCustomerIdOwnerUserId(Long customerId, Long ownerUserId) {
        return contractMapper.selectListByCustomerIdOwnerUserId(customerId, ownerUserId);
    }

}
