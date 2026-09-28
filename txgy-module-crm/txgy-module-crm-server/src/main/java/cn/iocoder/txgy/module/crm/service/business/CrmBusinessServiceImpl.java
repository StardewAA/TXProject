package cn.iocoder.txgy.module.crm.service.business;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.ObjUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.business.vo.business.CrmBusinessLoseReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.business.vo.business.CrmBusinessPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.business.vo.business.CrmBusinessSaveReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.business.vo.business.CrmBusinessTransferReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.business.vo.business.CrmBusinessUpdateStateReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.business.vo.business.CrmBusinessUpdateStatusReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.business.vo.business.CrmBusinessWinReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.contact.vo.CrmContactBusinessReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.statistics.vo.funnel.CrmStatisticsFunnelReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.business.CrmBusinessDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.business.CrmBusinessProductDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.business.CrmBusinessStatusDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contact.CrmContactBusinessDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contact.CrmContactDO;
import cn.iocoder.txgy.module.crm.dal.mysql.business.CrmBusinessMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.business.CrmBusinessProductMapper;
import cn.iocoder.txgy.module.crm.enums.business.CrmBusinessEndStatusEnum;
import cn.iocoder.txgy.module.crm.enums.business.CrmBusinessLostReasonEnum;
import cn.iocoder.txgy.module.crm.enums.business.CrmBusinessStageEnum;
import cn.iocoder.txgy.module.crm.enums.business.CrmBusinessStateEnum;
import cn.iocoder.txgy.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.txgy.module.crm.enums.permission.CrmPermissionLevelEnum;
import cn.iocoder.txgy.module.crm.framework.permission.core.annotations.CrmPermission;
import cn.iocoder.txgy.module.crm.service.contact.CrmContactBusinessService;
import cn.iocoder.txgy.module.crm.service.contact.CrmContactService;
import cn.iocoder.txgy.module.crm.service.contract.CrmContractService;
import cn.iocoder.txgy.module.crm.service.quotation.CrmQuotationService;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerService;
import cn.iocoder.txgy.module.crm.service.permission.CrmPermissionService;
import cn.iocoder.txgy.module.crm.service.permission.bo.CrmPermissionCreateReqBO;
import cn.iocoder.txgy.module.crm.service.permission.bo.CrmPermissionTransferReqBO;
import cn.iocoder.txgy.module.crm.service.product.CrmProductService;
import cn.iocoder.txgy.module.crm.util.CrmFollowUpTimeUtils;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.service.impl.DiffParseFunction;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.*;
import static cn.iocoder.txgy.module.crm.enums.LogRecordConstants.*;

/**
 * 商机 Service 实现类
 *
 * @author ljlleo
 */
@Service
@Validated
public class CrmBusinessServiceImpl implements CrmBusinessService {

    @Resource
    private CrmBusinessMapper businessMapper;
    @Resource
    private CrmBusinessProductMapper businessProductMapper;

    @Resource
    private CrmBusinessStatusService businessStatusService;
    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private CrmContractService contractService;
    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private CrmQuotationService quotationService;
    @Resource
    private CrmCustomerService customerService;
    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private CrmContactService contactService;
    @Resource
    private CrmPermissionService permissionService;
    @Resource
    private CrmContactBusinessService contactBusinessService;
    @Resource
    private CrmProductService productService;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_BUSINESS_TYPE, subType = CRM_BUSINESS_CREATE_SUB_TYPE, bizNo = "{{#business.id}}",
            success = CRM_BUSINESS_CREATE_SUCCESS)
    public Long createBusiness(CrmBusinessSaveReqVO createReqVO, Long userId) {
        // 1.1 校验产品项的有效性
        List<CrmBusinessProductDO> businessProducts = validateBusinessProducts(createReqVO.getProducts());
        // 1.2 校验关联字段
        validateRelationDataExists(createReqVO);

        // 2.1 插入商机
        CrmBusinessDO business = BeanUtils.toBean(createReqVO, CrmBusinessDO.class);
        // 商机阶段：不传默认「商机待评估」（线索转商机的默认值），并据此派生状态组与预估赢单率
        applyStage(business, createReqVO.getStage() == null ? CrmBusinessStageEnum.getDefaultStage() : createReqVO.getStage());
        calculateTotalPrice(business, businessProducts);
        businessMapper.insert(business);
        // 2.2 插入商机关联商品
        if (CollUtil.isNotEmpty(businessProducts)) {
            businessProducts.forEach(item -> item.setBusinessId(business.getId()));
            businessProductMapper.insertBatch(businessProducts);
        }

        // 3. 创建数据权限
        permissionService.createPermission(new CrmPermissionCreateReqBO().setUserId(business.getOwnerUserId())
                .setBizType(CrmBizTypeEnum.CRM_BUSINESS.getType()).setBizId(business.getId())
                .setLevel(CrmPermissionLevelEnum.OWNER.getLevel()));

        // 4. 在联系人的详情页，如果直接【新建商机】，则需要关联下
        if (createReqVO.getContactId() != null) {
            contactBusinessService.createContactBusinessList(new CrmContactBusinessReqVO().setContactId(createReqVO.getContactId())
                    .setBusinessIds(Collections.singletonList(business.getId())));
        }

        // 5. 记录操作日志上下文
        LogRecordContext.putVariable("business", business);
        return business.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_BUSINESS_TYPE, subType = CRM_BUSINESS_UPDATE_SUB_TYPE, bizNo = "{{#updateReqVO.id}}",
            success = CRM_BUSINESS_UPDATE_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_BUSINESS, bizId = "#updateReqVO.id", level = CrmPermissionLevelEnum.WRITE)
    public void updateBusiness(CrmBusinessSaveReqVO updateReqVO) {
        updateReqVO.setOwnerUserId(null).setStatusTypeId(null); // 不允许更新的字段
        // 1.1 校验存在
        CrmBusinessDO oldBusiness = validateBusinessExists(updateReqVO.getId());
        // 1.2 校验商机未关闭（关闭后关键字段只读）
        validateBusinessNotClosed(oldBusiness);
        // 1.3 校验产品项的有效性
        List<CrmBusinessProductDO> businessProducts = validateBusinessProducts(updateReqVO.getProducts());
        // 1.4 校验关联字段
        validateRelationDataExists(updateReqVO);

        // 2.1 更新商机
        CrmBusinessDO updateObj = BeanUtils.toBean(updateReqVO, CrmBusinessDO.class);
        // 阶段变更时同步派生「状态组」与「预估赢单率」；未传阶段则沿用原阶段
        applyStage(updateObj, updateReqVO.getStage() == null ? oldBusiness.getStage() : updateReqVO.getStage());
        calculateTotalPrice(updateObj, businessProducts);
        businessMapper.updateById(updateObj);
        // 2.2 更新商机关联商品
        updateBusinessProduct(updateObj.getId(), businessProducts);

        // 3. 记录操作日志上下文
        updateReqVO.setOwnerUserId(oldBusiness.getOwnerUserId()); // 避免操作日志出现“删除负责人”的情况
        LogRecordContext.putVariable(DiffParseFunction.OLD_OBJECT, BeanUtils.toBean(oldBusiness, CrmBusinessSaveReqVO.class));
        LogRecordContext.putVariable("businessName", oldBusiness.getName());
    }

    @Override
    @LogRecord(type = CRM_BUSINESS_TYPE, subType = CRM_BUSINESS_FOLLOW_UP_SUB_TYPE, bizNo = "{{#id}}",
            success = CRM_BUSINESS_FOLLOW_UP_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_BUSINESS, bizId = "#id", level = CrmPermissionLevelEnum.WRITE)
    public void updateBusinessFollowUp(Long id, LocalDateTime contactTime, LocalDateTime contactNextTime,
                                       String contactLastContent) {
        // 1. 校验存在
        CrmBusinessDO business = validateBusinessExists(id);

        // 2. 更新联系人的跟进信息（最后跟进时间取「实际跟进时间」，且不早于已有值，详见 CrmFollowUpTimeUtils）
        businessMapper.updateById(new CrmBusinessDO().setId(id).setFollowUpStatus(true).setContactNextTime(contactNextTime)
                .setContactLastTime(CrmFollowUpTimeUtils.calculateContactLastTime(business.getContactLastTime(), contactTime)));
        // 累计跟进次数 +1（SQL 自增，避免并发下相互覆盖）
        businessMapper.updateFollowUpCountById(id, 1);

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("businessName", business.getName());
    }

    @Override
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_BUSINESS, bizId = "#ids", level = CrmPermissionLevelEnum.WRITE)
    public void updateBusinessContactNextTime(Collection<Long> ids, LocalDateTime contactNextTime) {
        businessMapper.updateBatch(convertList(ids, id -> new CrmBusinessDO().setId(id).setContactNextTime(contactNextTime)));
    }

    private void updateBusinessProduct(Long id, List<CrmBusinessProductDO> newList) {
        List<CrmBusinessProductDO> oldList = businessProductMapper.selectListByBusinessId(id);
        List<List<CrmBusinessProductDO>> diffList = diffList(oldList, newList, // id 不同，就认为是不同的记录
                (oldVal, newVal) -> oldVal.getId().equals(newVal.getId()));
        if (CollUtil.isNotEmpty(diffList.get(0))) {
            diffList.get(0).forEach(o -> o.setBusinessId(id));
            businessProductMapper.insertBatch(diffList.get(0));
        }
        if (CollUtil.isNotEmpty(diffList.get(1))) {
            businessProductMapper.updateBatch(diffList.get(1));
        }
        if (CollUtil.isNotEmpty(diffList.get(2))) {
            businessProductMapper.deleteByIds(convertSet(diffList.get(2), CrmBusinessProductDO::getId));
        }
    }

    private void validateRelationDataExists(CrmBusinessSaveReqVO saveReqVO) {
        // 校验商机状态
        if (saveReqVO.getStatusTypeId() != null) {
            businessStatusService.validateBusinessStatusType(saveReqVO.getStatusTypeId());
        }
        // 校验客户
        if (saveReqVO.getCustomerId() != null) {
            customerService.validateCustomer(saveReqVO.getCustomerId());
        }
        // 校验联系人（且必须与商机属于同一客户，避免跨客户关联）
        if (saveReqVO.getContactId() != null) {
            contactService.validateContact(saveReqVO.getContactId());
            if (saveReqVO.getCustomerId() != null) {
                CrmContactDO contact = contactService.getContact(saveReqVO.getContactId());
                if (ObjUtil.notEqual(saveReqVO.getCustomerId(), contact.getCustomerId())) {
                    throw exception(BUSINESS_CONTACT_CUSTOMER_NOT_MATCH);
                }
            }
        }
        // 校验负责人
        if (saveReqVO.getOwnerUserId() != null) {
            adminUserApi.validateUser(saveReqVO.getOwnerUserId());
        }
    }

    private List<CrmBusinessProductDO> validateBusinessProducts(List<CrmBusinessSaveReqVO.BusinessProduct> list) {
        // 1. 校验产品存在
        productService.validProductList(convertSet(list, CrmBusinessSaveReqVO.BusinessProduct::getProductId));
        // 2. 转化为 CrmBusinessProductDO 列表
        return convertList(list, o -> BeanUtils.toBean(o, CrmBusinessProductDO.class,
                item -> item.setTotalPrice(MoneyUtils.priceMultiply(item.getBusinessPrice(), item.getCount()))));
    }

    private void calculateTotalPrice(CrmBusinessDO business, List<CrmBusinessProductDO> businessProducts) {
        business.setTotalProductPrice(getSumValue(businessProducts, CrmBusinessProductDO::getTotalPrice, BigDecimal::add, BigDecimal.ZERO));
        BigDecimal discountPrice = MoneyUtils.priceMultiplyPercent(business.getTotalProductPrice(), business.getDiscountPercent());
        business.setTotalPrice(business.getTotalProductPrice().subtract(discountPrice));
    }

    /**
     * 设置商机阶段，并派生出「商机状态组」与「预估赢单率」
     *
     * <p>状态组与预估赢单率都是只读派生字段，不接受外部直接传值，统一由阶段推导：
     * 商机待评估 → 未开始；其余阶段 → 进行中；赢单率取该阶段配置的 5/10/40/60/70。
     *
     * @param business 商机（写入 stage / businessState / winRate）
     * @param stage    商机阶段
     */
    private void applyStage(CrmBusinessDO business, Integer stage) {
        if (CrmBusinessStageEnum.getByStage(stage) == null) {
            throw exception(BUSINESS_STAGE_INVALID);
        }
        business.setStage(stage);
        business.setBusinessState(CrmBusinessStateEnum.getStateByStage(stage));
        business.setWinRate(CrmBusinessStageEnum.getPercentByStage(stage));
    }

    /**
     * 校验商机未关闭
     *
     * <p>已赢单 / 已丢单后，名称、客户、阶段、预计成交日期、产品清单、金额、负责人、备注等关键字段只读；
     * 跟进记录与下次联系时间仍可写。
     *
     * @param business 商机
     */
    private void validateBusinessNotClosed(CrmBusinessDO business) {
        if (CrmBusinessStateEnum.isClosed(business.getBusinessState())) {
            throw exception(BUSINESS_UPDATE_FAIL_CLOSED);
        }
    }

    /**
     * 兼容历史调用：把旧「商机状态 / 阶段」名称映射为标准化后的商机阶段
     *
     * @param statusName 旧状态名称
     * @return 商机阶段；无法映射时返回 null
     */
    private static Integer matchBusinessStageByName(String statusName) {
        if (statusName == null) {
            return null;
        }
        for (CrmBusinessStageEnum item : CrmBusinessStageEnum.values()) {
            if (statusName.contains(item.getName())) {
                return item.getStage();
            }
        }
        // 旧数据里的同义阶段名称：需求沟通 → 需求确认；方案报价 → 方案评估
        if (statusName.contains("需求")) {
            return CrmBusinessStageEnum.DEMAND.getStage();
        }
        if (statusName.contains("方案")) {
            return CrmBusinessStageEnum.SCHEME.getStage();
        }
        if (statusName.contains("谈判") || statusName.contains("赢单") || statusName.contains("输单")) {
            return CrmBusinessStageEnum.NEGOTIATION.getStage();
        }
        return null;
    }

    @Override
    @LogRecord(type = CRM_BUSINESS_TYPE, subType = CRM_BUSINESS_UPDATE_STATUS_SUB_TYPE, bizNo = "{{#reqVO.id}}",
            success = CRM_BUSINESS_UPDATE_STATUS_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_BUSINESS, bizId = "#reqVO.id", level = CrmPermissionLevelEnum.WRITE)
    public void updateBusinessStatus(CrmBusinessUpdateStatusReqVO reqVO) {
        // 1.1 校验存在
        CrmBusinessDO business = validateBusinessExists(reqVO.getId());
        // 1.2 标准化后：赢单/输单必须走「赢单」「输单」按钮，保证签约信息 / 丢单原因完整
        if (reqVO.getEndStatus() != null) {
            throw exception(BUSINESS_UPDATE_STATUS_FAIL_USE_BUTTON);
        }
        // 1.3 兼容历史调用：旧 statusId 按其名称映射为新的「商机阶段」
        CrmBusinessStatusDO status = reqVO.getStatusId() == null ? null
                : businessStatusService.getBusinessStatus(reqVO.getStatusId());
        Integer stage = matchBusinessStageByName(status == null ? null : status.getName());
        if (stage == null) {
            throw exception(BUSINESS_STAGE_INVALID);
        }
        // 1.4 校验商机未关闭
        validateBusinessNotClosed(business);

        // 2. 更新商机阶段（同步派生状态组与预估赢单率）
        CrmBusinessDO updateObj = new CrmBusinessDO().setId(reqVO.getId());
        applyStage(updateObj, stage);
        businessMapper.updateById(updateObj);

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("businessName", business.getName());
        LogRecordContext.putVariable("oldStatusName", CrmBusinessStageEnum.getNameByStage(business.getStage()));
        LogRecordContext.putVariable("newStatusName", CrmBusinessStageEnum.getNameByStage(stage));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_BUSINESS_TYPE, subType = CRM_BUSINESS_UPDATE_STAGE_SUB_TYPE, bizNo = "{{#id}}",
            success = CRM_BUSINESS_UPDATE_STAGE_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_BUSINESS, bizId = "#id", level = CrmPermissionLevelEnum.WRITE)
    public void updateBusinessStage(Long id, Integer stage) {
        // 1.1 校验存在
        CrmBusinessDO business = validateBusinessExists(id);
        // 1.2 校验商机未关闭（已赢单/已丢单后阶段只读）
        validateBusinessNotClosed(business);
        // 1.3 校验阶段合法性
        if (CrmBusinessStageEnum.getByStage(stage) == null) {
            throw exception(BUSINESS_STAGE_INVALID);
        }
        if (ObjUtil.equal(business.getStage(), stage)) {
            return;
        }

        // 2. 更新阶段，并同步派生「状态组」与「预估赢单率」
        CrmBusinessDO updateObj = new CrmBusinessDO().setId(id);
        applyStage(updateObj, stage);
        businessMapper.updateById(updateObj);

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("businessName", business.getName());
        LogRecordContext.putVariable("oldStageName", CrmBusinessStageEnum.getNameByStage(business.getStage()));
        LogRecordContext.putVariable("newStageName", CrmBusinessStageEnum.getNameByStage(stage));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_BUSINESS_TYPE, subType = CRM_BUSINESS_WIN_SUB_TYPE, bizNo = "{{#reqVO.id}}",
            success = CRM_BUSINESS_WIN_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_BUSINESS, bizId = "#reqVO.id", level = CrmPermissionLevelEnum.WRITE)
    public void winBusiness(CrmBusinessWinReqVO reqVO, Long userId) {
        // 1.1 校验存在
        CrmBusinessDO business = validateBusinessExists(reqVO.getId());
        // 1.2 校验商机未关闭
        if (CrmBusinessStateEnum.isClosed(business.getBusinessState())) {
            throw exception(BUSINESS_WIN_FAIL_CLOSED, CrmBusinessStateEnum.getNameByState(business.getBusinessState()));
        }

        // 2. 标记赢单：状态组=已赢单、预估赢单率=100、结束状态=赢单，并记录签约与关闭信息
        businessMapper.updateById(new CrmBusinessDO().setId(reqVO.getId())
                .setBusinessState(CrmBusinessStateEnum.WON.getState())
                .setWinRate(100)
                .setEndStatus(CrmBusinessEndStatusEnum.WIN.getStatus())
                .setSignDate(reqVO.getSignDate()).setSignAmount(reqVO.getSignAmount())
                .setEndRemark(reqVO.getRemark())
                .setClosedAt(LocalDateTime.now()).setClosedBy(userId));

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("businessName", business.getName());
        LogRecordContext.putVariable("signDate", LocalDateTimeUtil.formatNormal(reqVO.getSignDate()));
        LogRecordContext.putVariable("signAmount", reqVO.getSignAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_BUSINESS_TYPE, subType = CRM_BUSINESS_LOSE_SUB_TYPE, bizNo = "{{#reqVO.id}}",
            success = CRM_BUSINESS_LOSE_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_BUSINESS, bizId = "#reqVO.id", level = CrmPermissionLevelEnum.WRITE)
    public void loseBusiness(CrmBusinessLoseReqVO reqVO, Long userId) {
        // 1.1 校验存在
        CrmBusinessDO business = validateBusinessExists(reqVO.getId());
        // 1.2 校验商机未关闭
        if (CrmBusinessStateEnum.isClosed(business.getBusinessState())) {
            throw exception(BUSINESS_LOSE_FAIL_CLOSED, CrmBusinessStateEnum.getNameByState(business.getBusinessState()));
        }

        // 2. 标记丢单：状态组=已丢单、预估赢单率=0、结束状态=输单，并记录丢单原因与关闭信息
        businessMapper.updateById(new CrmBusinessDO().setId(reqVO.getId())
                .setBusinessState(CrmBusinessStateEnum.LOST.getState())
                .setWinRate(0)
                .setEndStatus(CrmBusinessEndStatusEnum.LOSE.getStatus())
                .setLostReason(reqVO.getLostReason()).setLostRemark(reqVO.getLostRemark())
                .setClosedAt(LocalDateTime.now()).setClosedBy(userId));

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("businessName", business.getName());
        LogRecordContext.putVariable("lostReasonName", CrmBusinessLostReasonEnum.getNameByReason(reqVO.getLostReason()));
        LogRecordContext.putVariable("lostRemark", reqVO.getLostRemark());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_BUSINESS_TYPE, subType = CRM_BUSINESS_UPDATE_STATE_SUB_TYPE, bizNo = "{{#reqVO.id}}",
            success = CRM_BUSINESS_UPDATE_STATE_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_BUSINESS, bizId = "#reqVO.id", level = CrmPermissionLevelEnum.OWNER)
    public void updateBusinessState(CrmBusinessUpdateStateReqVO reqVO, Long userId) {
        // 1.1 校验存在
        CrmBusinessDO business = validateBusinessExists(reqVO.getId());
        // 1.2 只允许重新开启 / 纠正为「未开始」「进行中」；关闭必须走赢单/输单按钮，保证信息完整
        if (CrmBusinessStateEnum.isClosed(reqVO.getBusinessState())) {
            throw exception(BUSINESS_UPDATE_STATE_FAIL_NOT_SUPPORT);
        }
        if (ObjUtil.equal(business.getBusinessState(), reqVO.getBusinessState())) {
            throw exception(BUSINESS_UPDATE_STATE_FAIL_SAME);
        }

        // 2. 覆写状态组：清空关闭相关信息（签约信息、丢单原因、关闭时间等），赢单率按当前阶段重算
        businessMapper.update(null, new LambdaUpdateWrapper<CrmBusinessDO>()
                .eq(CrmBusinessDO::getId, reqVO.getId())
                .set(CrmBusinessDO::getBusinessState, reqVO.getBusinessState())
                .set(CrmBusinessDO::getWinRate, CrmBusinessStageEnum.getPercentByStage(business.getStage()))
                .set(CrmBusinessDO::getEndStatus, null)
                .set(CrmBusinessDO::getEndRemark, null)
                .set(CrmBusinessDO::getSignDate, null)
                .set(CrmBusinessDO::getSignAmount, null)
                .set(CrmBusinessDO::getLostReason, null)
                .set(CrmBusinessDO::getLostRemark, null)
                .set(CrmBusinessDO::getClosedAt, null)
                .set(CrmBusinessDO::getClosedBy, null));

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("businessName", business.getName());
        LogRecordContext.putVariable("oldStateName", CrmBusinessStateEnum.getNameByState(business.getBusinessState()));
        LogRecordContext.putVariable("newStateName", CrmBusinessStateEnum.getNameByState(reqVO.getBusinessState()));
        LogRecordContext.putVariable("reason", reqVO.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_BUSINESS_TYPE, subType = CRM_BUSINESS_DELETE_SUB_TYPE, bizNo = "{{#id}}",
            success = CRM_BUSINESS_DELETE_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_BUSINESS, bizId = "#id", level = CrmPermissionLevelEnum.OWNER)
    public void deleteBusiness(Long id) {
        // 1.1 校验存在
        CrmBusinessDO business = validateBusinessExists(id);
        // 1.2 校验是否关联合同
        validateContractExists(id);
        // 1.3 校验是否关联报价单
        validateQuotationExists(id);

        // 删除商机
        businessMapper.deleteById(id);
        // 删除数据权限
        permissionService.deletePermission(CrmBizTypeEnum.CRM_BUSINESS.getType(), id);

        // 记录操作日志上下文
        LogRecordContext.putVariable("businessName", business.getName());
    }

    /**
     * 删除校验合同是关联合同
     *
     * @param businessId 商机id
     * @author lzxhqs
     */
    private void validateContractExists(Long businessId) {
        if (contractService.getContractCountByBusinessId(businessId) > 0) {
            throw exception(BUSINESS_DELETE_FAIL_CONTRACT_EXISTS);
        }
    }

    /**
     * 删除校验报价单是否关联商机
     *
     * @param businessId 商机编号
     */
    private void validateQuotationExists(Long businessId) {
        if (quotationService.getQuotationCountByBusinessId(businessId) > 0) {
            throw exception(BUSINESS_DELETE_FAIL_QUOTATION_EXISTS);
        }
    }

    private CrmBusinessDO validateBusinessExists(Long id) {
        CrmBusinessDO crmBusiness = businessMapper.selectById(id);
        if (crmBusiness == null) {
            throw exception(BUSINESS_NOT_EXISTS);
        }
        return crmBusiness;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CRM_BUSINESS_TYPE, subType = CRM_BUSINESS_TRANSFER_SUB_TYPE, bizNo = "{{#reqVO.id}}",
            success = CRM_BUSINESS_TRANSFER_SUCCESS)
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_BUSINESS, bizId = "#reqVO.id", level = CrmPermissionLevelEnum.OWNER)
    public void transferBusiness(CrmBusinessTransferReqVO reqVO, Long userId) {
        // 1 校验商机是否存在
        CrmBusinessDO business = validateBusinessExists(reqVO.getId());

        // 2.1 数据权限转移
        permissionService.transferPermission(new CrmPermissionTransferReqBO(userId, CrmBizTypeEnum.CRM_BUSINESS.getType(),
                reqVO.getId(), reqVO.getNewOwnerUserId(), reqVO.getOldOwnerPermissionLevel()));
        // 2.2 设置新的负责人
        businessMapper.updateOwnerUserIdById(reqVO.getId(), reqVO.getNewOwnerUserId());

        // 记录操作日志上下文
        LogRecordContext.putVariable("business", business);
    }

    //======================= 查询相关 =======================

    @Override
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_BUSINESS, bizId = "#id", level = CrmPermissionLevelEnum.READ)
    public CrmBusinessDO getBusiness(Long id) {
        return businessMapper.selectById(id);
    }

    @Override
    public CrmBusinessDO validateBusiness(Long id) {
        return validateBusinessExists(id);
    }

    @Override
    public List<CrmBusinessDO> getBusinessList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return ListUtil.empty();
        }
        return businessMapper.selectByIds(ids);
    }

    @Override
    public List<CrmBusinessProductDO> getBusinessProductListByBusinessId(Long businessId) {
        return businessProductMapper.selectListByBusinessId(businessId);
    }

    @Override
    public PageResult<CrmBusinessDO> getBusinessPage(CrmBusinessPageReqVO pageReqVO, Long userId) {
        return businessMapper.selectPage(pageReqVO, userId);
    }

    @Override
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CUSTOMER, bizId = "#pageReqVO.customerId", level = CrmPermissionLevelEnum.READ)
    public PageResult<CrmBusinessDO> getBusinessPageByCustomerId(CrmBusinessPageReqVO pageReqVO) {
        return businessMapper.selectPageByCustomerId(pageReqVO);
    }

    @Override
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CONTACT, bizId = "#pageReqVO.contactId", level = CrmPermissionLevelEnum.READ)
    public PageResult<CrmBusinessDO> getBusinessPageByContact(CrmBusinessPageReqVO pageReqVO) {
        // 1. 查询关联的商机编号
        List<CrmContactBusinessDO> contactBusinessList = contactBusinessService.getContactBusinessListByContactId(
                pageReqVO.getContactId());
        if (CollUtil.isEmpty(contactBusinessList)) {
            return PageResult.empty();
        }
        // 2. 查询商机分页
        return businessMapper.selectPageByContactId(pageReqVO,
                convertSet(contactBusinessList, CrmContactBusinessDO::getBusinessId));
    }

    @Override
    public Long getBusinessCountByCustomerId(Long customerId) {
        return businessMapper.selectCount(CrmBusinessDO::getCustomerId, customerId);
    }

    @Override
    public Long getBusinessCountByStatusTypeId(Long statusTypeId) {
        return businessMapper.selectCountByStatusTypeId(statusTypeId);
    }

    @Override
    public List<CrmBusinessDO> getBusinessListByCustomerIdOwnerUserId(Long customerId, Long ownerUserId) {
        return businessMapper.selectListByCustomerIdOwnerUserId(customerId, ownerUserId);
    }

    @Override
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CUSTOMER, bizId = "#customerId", level = CrmPermissionLevelEnum.READ)
    public List<CrmBusinessDO> getBusinessListByCustomerId(Long customerId) {
        return businessMapper.selectListByCustomerId(customerId);
    }

    @Override
    @CrmPermission(bizType = CrmBizTypeEnum.CRM_CONTACT, bizId = "#contactId", level = CrmPermissionLevelEnum.READ)
    public List<CrmBusinessDO> getBusinessListByContact(Long contactId) {
        // 1. 查询关联的商机编号
        List<CrmContactBusinessDO> contactBusinessList = contactBusinessService.getContactBusinessListByContactId(contactId);
        if (CollUtil.isEmpty(contactBusinessList)) {
            return ListUtil.empty();
        }
        // 2. 查询商机列表
        return businessMapper.selectByIds(convertSet(contactBusinessList, CrmContactBusinessDO::getBusinessId));
    }

    @Override
    public PageResult<CrmBusinessDO> getBusinessPageByDate(CrmStatisticsFunnelReqVO pageVO) {
        return businessMapper.selectPage(pageVO);
    }

}
