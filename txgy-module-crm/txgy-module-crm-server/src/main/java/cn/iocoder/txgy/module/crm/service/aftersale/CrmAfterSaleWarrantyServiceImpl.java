package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantyPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantySaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleConfigDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleVisitTaskMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleWarrantyMapper;
import cn.iocoder.txgy.module.crm.dal.redis.no.CrmNoRedisDAO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contract.CrmContractDO;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleWarrantyStatusEnum;
import cn.iocoder.txgy.module.crm.enums.common.CrmAuditStatusEnum;
import cn.iocoder.txgy.module.crm.service.contract.CrmContractService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.*;

/**
 * 售后质保档案 Service 实现类
 *
 * @author txgycloud
 */
@Service
@Validated
public class CrmAfterSaleWarrantyServiceImpl implements CrmAfterSaleWarrantyService {

    /**
     * 质保编号前缀
     */
    private static final String WARRANTY_NO_PREFIX = "WB";

    @Resource
    private CrmAfterSaleWarrantyMapper warrantyMapper;
    @Resource
    private CrmAfterSaleVisitTaskMapper visitTaskMapper;
    @Resource
    private CrmAfterSaleConfigService configService;
    @Resource
    private CrmAfterSaleMessageService messageService;
    @Resource
    private CrmContractService contractService;
    @Resource
    private CrmNoRedisDAO noRedisDAO;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createWarranty(CrmAfterSaleWarrantySaveReqVO createReqVO, Long userId) {
        createReqVO.setId(null);
        // 1.1 校验合同是否审核通过，并补齐客户编号、业务负责人
        CrmContractDO contract = contractService.validateContract(createReqVO.getContractId());
        if (!Objects.equals(contract.getAuditStatus(), CrmAuditStatusEnum.APPROVE.getStatus())) {
            throw exception(AFTER_SALE_WARRANTY_CREATE_FAIL_CONTRACT_NOT_APPROVE);
        }
        // 1.2 校验该合同是否已创建质保档案：一个合同仅允许一份质保档案（一对一关系）
        //     业务规则说明：
        //       1）质保档案为逻辑删除，档案被删除后此处自然放行，允许基于该合同重新创建；
        //       2）质保档案到期终止（已到期）仍占用该合同，不允许重复创建；
        //       3）合同审批状态被回退/取消不影响已创建的档案，但同样不允许重复创建。
        if (warrantyMapper.selectByContractId(createReqVO.getContractId()) != null) {
            throw exception(AFTER_SALE_WARRANTY_CREATE_FAIL_CONTRACT_EXISTS);
        }
        if (createReqVO.getOwnerUserId() == null) {
            createReqVO.setOwnerUserId(contract.getOwnerUserId()); // 默认取合同业务负责人
        }
        if (createReqVO.getServiceUserId() == null) {
            createReqVO.setServiceUserId(userId); // 默认当前登录人为售后责任人
        }
        if (createReqVO.getStatus() == null) {
            createReqVO.setStatus(CrmAfterSaleWarrantyStatusEnum.IN_WARRANTY.getStatus());
        }
        // 1.2 校验年限与到期日
        validateWarrantyYears(createReqVO.getWarrantyYears());
        if (createReqVO.getWarrantyEndDate() == null) { // 自动计算：验收日期 + 年限 - 1 天
            createReqVO.setWarrantyEndDate(calculateEndDate(createReqVO.getProjectAcceptDate(), createReqVO.getWarrantyYears()));
        }
        validateEndDate(createReqVO.getProjectAcceptDate(), createReqVO.getWarrantyEndDate());

        // 2. 插入质保档案
        CrmAfterSaleWarrantyDO warranty = BeanUtils.toBean(createReqVO, CrmAfterSaleWarrantyDO.class);
        warranty.setCustomerId(contract.getCustomerId()); // 从合同带出客户编号
        warranty.setNo(generateWarrantyNo());
        warrantyMapper.insert(warranty);
        return warranty.getId();
    }

    @Override
    public void updateWarranty(CrmAfterSaleWarrantySaveReqVO updateReqVO) {
        // 1. 校验存在
        CrmAfterSaleWarrantyDO oldWarranty = validateWarranty(updateReqVO.getId());
        // 1.1 不允许修改来源合同、客户，沿用旧值
        updateReqVO.setContractId(oldWarranty.getContractId()).setCustomerId(oldWarranty.getCustomerId());
        // 1.2 校验年限与到期日
        validateWarrantyYears(updateReqVO.getWarrantyYears());
        if (updateReqVO.getWarrantyEndDate() == null) {
            updateReqVO.setWarrantyEndDate(calculateEndDate(updateReqVO.getProjectAcceptDate(), updateReqVO.getWarrantyYears()));
        }
        validateEndDate(updateReqVO.getProjectAcceptDate(), updateReqVO.getWarrantyEndDate());
        // 1.3 恢复不允许修改字段的旧值
        updateReqVO.setStatus(oldWarranty.getStatus()).setOwnerUserId(oldWarranty.getOwnerUserId())
                .setServiceUserId(oldWarranty.getServiceUserId());

        // 2. 更新质保档案
        CrmAfterSaleWarrantyDO updateObj = BeanUtils.toBean(updateReqVO, CrmAfterSaleWarrantyDO.class);
        warrantyMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWarranty(Long id) {
        // 1. 校验存在
        validateWarranty(id);
        // 2. 校验没有关联回访任务
        if (CollUtil.isNotEmpty(visitTaskMapper.selectListByWarrantyIds(List.of(id)))) {
            throw exception(AFTER_SALE_WARRANTY_DELETE_FAIL_EXISTS_TASK);
        }
        // 3. 删除
        warrantyMapper.deleteById(id);
    }

    @Override
    public CrmAfterSaleWarrantyDO getWarranty(Long id) {
        return warrantyMapper.selectById(id);
    }

    @Override
    public CrmAfterSaleWarrantyDO validateWarranty(Long id) {
        CrmAfterSaleWarrantyDO warranty = warrantyMapper.selectById(id);
        if (warranty == null) {
            throw exception(AFTER_SALE_WARRANTY_NOT_EXISTS);
        }
        return warranty;
    }

    @Override
    public PageResult<CrmAfterSaleWarrantyDO> getWarrantyPage(CrmAfterSaleWarrantyPageReqVO pageReqVO, Long userId) {
        // 待办「质保即将到期」：实时按售后设置判定，不预生成任何待办数据
        if (CrmAfterSaleWarrantyPageReqVO.EXPIRY_TYPE_ABOUT_TO_EXPIRE.equals(pageReqVO.getExpiryType())) {
            return warrantyMapper.selectPageByExpiry(pageReqVO, resolveRemindServiceUserIds(userId),
                    LocalDate.now(), resolveWarrantyWarnDays());
        }
        return warrantyMapper.selectPage(pageReqVO);
    }

    @Override
    public Long getWarrantyRemindCount(Long userId) {
        return warrantyMapper.selectCountByExpiry(resolveRemindServiceUserIds(userId),
                LocalDate.now(), resolveWarrantyWarnDays());
    }

    /**
     * 解析待办归属范围：本人 + 下属（与待办现有的「归属过滤」口径一致）
     *
     * <p>说明：售后质保档案不在 CRM 的 crm_permission 数据权限体系内，
     * 因此沿用质保档案的「售后责任人」(serviceUserId) 作为归属人；
     * 未设置售后责任人的档案没有明确归属人，不进入待办。
     */
    private Set<Long> resolveRemindServiceUserIds(Long userId) {
        Set<Long> userIds = new HashSet<>();
        if (userId == null) {
            return userIds;
        }
        userIds.add(userId);
        List<AdminUserRespDTO> subordinates = adminUserApi.getUserListBySubordinate(userId).getCheckedData();
        if (CollUtil.isNotEmpty(subordinates)) {
            userIds.addAll(convertSet(subordinates, AdminUserRespDTO::getId));
        }
        return userIds;
    }

    /**
     * 读取售后设置中的「质保到期提前提醒天数」，未配置时按售后设置默认值 30 天兜底
     */
    private Integer resolveWarrantyWarnDays() {
        Integer warnDays = configService.getConfig().getWarrantyWarnDays();
        return warnDays == null || warnDays < 0 ? 30 : warnDays;
    }

    @Override
    public List<CrmAfterSaleWarrantyDO> getWarrantyList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return List.of();
        }
        return warrantyMapper.selectListByIds(ids);
    }

    @Override
    public List<CrmAfterSaleWarrantyDO> getWarrantyListByContractIds(Collection<Long> contractIds) {
        return warrantyMapper.selectListByContractIds(contractIds);
    }

    private void validateWarrantyYears(Integer years) {
        if (years == null || years < 1 || years > 5) {
            throw exception(AFTER_SALE_WARRANTY_YEARS_INVALID);
        }
    }

    private void validateEndDate(LocalDate acceptDate, LocalDate endDate) {
        if (endDate.isBefore(acceptDate)) {
            throw exception(AFTER_SALE_WARRANTY_END_DATE_INVALID);
        }
    }

    private LocalDate calculateEndDate(LocalDate acceptDate, Integer years) {
        return acceptDate.plusYears(years).minusDays(1);
    }

    /**
     * 生成质保编号：WB + yyyyMMdd + 6 位自增；若冲突则重试
     */
    private String generateWarrantyNo() {
        for (int i = 0; i < 5; i++) {
            String no = noRedisDAO.generate(WARRANTY_NO_PREFIX);
            if (warrantyMapper.selectByNo(no) == null) {
                return no;
            }
        }
        throw exception(AFTER_SALE_WARRANTY_NO_EXISTS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoUpdateWarrantyStatus() {
        // 1. 获得提醒天数配置
        CrmAfterSaleConfigDO config = configService.getConfig();
        int warnDays = config.getWarrantyWarnDays() != null ? config.getWarrantyWarnDays() : 30;
        LocalDate today = LocalDate.now();
        // 2. 遍历非终态（质保中/即将到期）的质保档案进行状态流转
        List<CrmAfterSaleWarrantyDO> warranties = warrantyMapper.selectListByStatusIn(List.of(
                CrmAfterSaleWarrantyStatusEnum.IN_WARRANTY.getStatus(),
                CrmAfterSaleWarrantyStatusEnum.EXPIRING.getStatus()));
        int count = 0;
        for (CrmAfterSaleWarrantyDO warranty : warranties) {
            if (warranty.getWarrantyEndDate() == null) {
                continue;
            }
            Integer targetStatus;
            if (warranty.getWarrantyEndDate().isBefore(today)) {
                targetStatus = CrmAfterSaleWarrantyStatusEnum.EXPIRED.getStatus();
            } else if (!warranty.getWarrantyEndDate().isAfter(today.plusDays(warnDays))) {
                targetStatus = CrmAfterSaleWarrantyStatusEnum.EXPIRING.getStatus();
            } else {
                targetStatus = CrmAfterSaleWarrantyStatusEnum.IN_WARRANTY.getStatus();
            }
            if (!Objects.equals(warranty.getStatus(), targetStatus)) {
                CrmAfterSaleWarrantyDO updateObj = new CrmAfterSaleWarrantyDO();
                updateObj.setId(warranty.getId());
                updateObj.setStatus(targetStatus);
                warrantyMapper.updateById(updateObj);
                count++;
            }
        }
        return count;
    }

    @Override
    public int sendWarrantyExpireRemind() {
        // 1. 计算提醒截止日（今天 + 提前提醒天数）
        CrmAfterSaleConfigDO config = configService.getConfig();
        int warnDays = config.getWarrantyWarnDays() != null ? config.getWarrantyWarnDays() : 30;
        LocalDate today = LocalDate.now();
        LocalDate remindEnd = today.plusDays(warnDays);
        // 2. 查询质保中/即将到期、且到期日不晚于提醒截止日的质保档案
        List<CrmAfterSaleWarrantyDO> warranties = warrantyMapper.selectListByStatusInAndEndDateLe(List.of(
                CrmAfterSaleWarrantyStatusEnum.IN_WARRANTY.getStatus(),
                CrmAfterSaleWarrantyStatusEnum.EXPIRING.getStatus()), remindEnd);
        int count = 0;
        for (CrmAfterSaleWarrantyDO warranty : warranties) {
            // 2.1 已到期或已发送过提醒的跳过
            if (warranty.getWarrantyEndDate() == null || warranty.getWarrantyEndDate().isBefore(today)
                    || warranty.getRemindSentDate() != null) {
                continue;
            }
            // 2.2 通知售后责任人 + 业务负责人（去重）
            Set<Long> userIds = new HashSet<>();
            if (warranty.getServiceUserId() != null) {
                userIds.add(warranty.getServiceUserId());
            }
            if (warranty.getOwnerUserId() != null) {
                userIds.add(warranty.getOwnerUserId());
            }
            userIds.forEach(userId -> messageService.sendWarrantyExpireRemind(userId, warranty));
            // 2.3 回写提醒发送日期（防重复推送）
            CrmAfterSaleWarrantyDO updateObj = new CrmAfterSaleWarrantyDO();
            updateObj.setId(warranty.getId());
            updateObj.setRemindSentDate(today);
            warrantyMapper.updateById(updateObj);
            count++;
        }
        return count;
    }

}
