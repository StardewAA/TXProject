package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantyPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantySaveReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantyUpdateStatusReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleConfigDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleVisitPlanMapper;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class CrmAfterSaleWarrantyServiceImpl implements CrmAfterSaleWarrantyService {

    /**
     * 质保编号前缀
     */
    private static final String WARRANTY_NO_PREFIX = "WB";

    /**
     * 懒同步的最小间隔（毫秒）：30 秒内最多触发一次状态自动流转，避免高频查询频繁写库
     */
    private static final long AUTO_SYNC_INTERVAL_MS = 30_000L;

    /**
     * 上次懒同步时间戳（毫秒）；0 表示尚未同步过
     */
    private static final java.util.concurrent.atomic.AtomicLong LAST_AUTO_SYNC_AT =
            new java.util.concurrent.atomic.AtomicLong(0L);

    @Resource
    private CrmAfterSaleWarrantyMapper warrantyMapper;
    @Resource
    private CrmAfterSaleVisitTaskMapper visitTaskMapper;
    @Resource
    private CrmAfterSaleVisitPlanMapper visitPlanMapper;
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
        // 懒同步：详情页同样展示最新状态
        autoSyncStatusIfNeeded();
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
        // 0. 懒同步：读取前按需触发一次状态自动流转，保证列表展示的状态始终是最新的
        autoSyncStatusIfNeeded();
        // 待办「质保即将到期」：实时按售后设置判定，不预生成任何待办数据
        if (CrmAfterSaleWarrantyPageReqVO.EXPIRY_TYPE_ABOUT_TO_EXPIRE.equals(pageReqVO.getExpiryType())) {
            return warrantyMapper.selectPageByExpiry(pageReqVO, resolveRemindServiceUserIds(userId),
                    LocalDate.now(), resolveWarrantyWarnDays());
        }
        return warrantyMapper.selectPage(pageReqVO);
    }

    @Override
    public Long getWarrantyRemindCount(Long userId) {
        // 懒同步：待办数量同样基于最新状态
        autoSyncStatusIfNeeded();
        return warrantyMapper.selectCountByExpiry(resolveRemindServiceUserIds(userId),
                LocalDate.now(), resolveWarrantyWarnDays());
    }

    /**
     * 懒同步：在读取质保数据前按需触发一次状态自动流转（默认 30 秒内只执行一次）
     *
     * <p><b>为什么需要</b>：定时任务每天 02:00 才执行一次，用户在「当天刚过期 / 刚导入数据 /
     * 刚调整到期日」时打开页面，会看到「已过到期日但状态仍是质保中」的过期数据，
     * 需要人工点「刷新状态」才能修正 —— 这与「用户无需手动操作即可看到最新状态」相冲突。
     *
     * <p><b>做法</b>：所有质保读取入口（分页 / 详情 / 待办数量）在查询前先做一次自动流转，
     * 并用时间戳限流（{@link #AUTO_SYNC_INTERVAL_MS}），避免高频查询导致频繁写库。
     * 于是：
     * <ul>
     *   <li>用户打开列表 → 状态已经是按今天算好的最新值；</li>
     *   <li>状态变化时的关联同步（停用回访计划、取消超期未完成任务、发送到期通知）也一并完成；</li>
     *   <li>定时任务退化为「兜底 + 主动推送通知」的角色，不再是状态正确性的唯一依赖。</li>
     * </ul>
     *
     * <p><b>容错</b>：同步失败只记录日志，绝不影响正常的查询返回（读接口不能因为写失败而不可用）。
     */
    private void autoSyncStatusIfNeeded() {
        long now = System.currentTimeMillis();
        long last = LAST_AUTO_SYNC_AT.get();
        if (now - last < AUTO_SYNC_INTERVAL_MS) {
            return;
        }
        // 竞争失败（别的线程刚同步过）时直接返回，避免并发重复写
        if (!LAST_AUTO_SYNC_AT.compareAndSet(last, now)) {
            return;
        }
        try {
            int count = autoUpdateWarrantyStatus();
            if (count > 0) {
                log.info("[autoSyncStatusIfNeeded][懒同步：自动流转质保状态 {} 条]", count);
            }
        } catch (Exception ex) {
            log.error("[autoSyncStatusIfNeeded][懒同步失败，不影响查询]", ex);
        }
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

    /**
     * 质保状态自动流转（定时任务 + 手动刷新共用同一实现，保证两条链路口径完全一致）
     *
     * <p><b>变更规则</b>（today = 今天，warnDays = 售后设置「质保到期提前提醒天数」，默认 30）：
     * <ol>
     *   <li>到期日 &lt; 今天           → <b>已到期终止</b>（EXPIRED）</li>
     *   <li>到期日 ≤ 今天 + warnDays  → <b>即将到期</b>（EXPIRING）</li>
     *   <li>其余                      → <b>质保中</b>（IN_WARRANTY）</li>
     * </ol>
     * 注意：到期日当天仍算「有效」，次日起才判定为已到期。
     *
     * <p><b>触发条件</b>：每日 02:00 定时任务
     * （{@code CrmAfterSaleScheduledJob#warrantyRemindJob}）；
     * 或管理员调用 {@link #syncWarrantyStatus()} 立即触发。
     *
     * <p><b>异常处理</b>：
     * <ul>
     *   <li>单条档案处理失败只记录日志，不中断其余档案的流转；</li>
     *   <li>到期日为空的历史数据跳过，不做流转；</li>
     *   <li>「已到期终止」是单向终态，不在扫描范围内、不会被自动回退；
     *       到期日被人工延长后，需通过 {@link #updateWarrantyStatus} 手动纠正。</li>
     * </ul>
     *
     * <p><b>关联同步</b>：状态首次流转为「已到期终止」时，会一并完成
     * ① 停用关联的回访计划；② 取消「计划回访日期晚于质保到期日」的未完成回访任务；
     * ③ 向售后责任人与业务负责人发送「质保已到期」站内信。
     *
     * @return 本次状态变更的档案数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoUpdateWarrantyStatus() {
        // 1. 获得提醒天数配置
        CrmAfterSaleConfigDO config = configService.getConfig();
        int warnDays = config.getWarrantyWarnDays() != null ? config.getWarrantyWarnDays() : 30;
        LocalDate today = LocalDate.now();
        // 2. 遍历非终态（质保中 / 即将到期）的质保档案进行状态流转
        List<CrmAfterSaleWarrantyDO> warranties = warrantyMapper.selectListByStatusIn(List.of(
                CrmAfterSaleWarrantyStatusEnum.IN_WARRANTY.getStatus(),
                CrmAfterSaleWarrantyStatusEnum.EXPIRING.getStatus()));
        int count = 0;
        for (CrmAfterSaleWarrantyDO warranty : warranties) {
            try {
                Integer targetStatus = calculateTargetStatus(warranty, today, warnDays);
                if (targetStatus == null || Objects.equals(warranty.getStatus(), targetStatus)) {
                    continue; // 到期日为空，或状态无变化
                }
                CrmAfterSaleWarrantyDO updateObj = new CrmAfterSaleWarrantyDO();
                updateObj.setId(warranty.getId());
                updateObj.setStatus(targetStatus);
                warrantyMapper.updateById(updateObj);
                count++;
                // 首次进入「已到期终止」：同步关联业务 + 发送到期通知
                if (Objects.equals(targetStatus, CrmAfterSaleWarrantyStatusEnum.EXPIRED.getStatus())) {
                    handleWarrantyExpired(warranty);
                }
            } catch (Exception ex) {
                // 单条失败不影响其他档案的流转
                log.error("[autoUpdateWarrantyStatus][质保({}) 状态流转失败]", warranty.getId(), ex);
            }
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncWarrantyStatus() {
        // 与定时任务同一实现：手动触发时同样执行「状态流转 + 关联同步 + 到期通知」
        return autoUpdateWarrantyStatus();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWarrantyStatus(CrmAfterSaleWarrantyUpdateStatusReqVO reqVO, Long userId) {
        // 1.1 校验存在
        CrmAfterSaleWarrantyDO warranty = validateWarranty(reqVO.getId());
        // 1.2 校验目标状态合法
        CrmAfterSaleWarrantyStatusEnum targetStatus = CollUtil.findOne(
                CollUtil.newArrayList(CrmAfterSaleWarrantyStatusEnum.values()),
                item -> Objects.equals(item.getStatus(), reqVO.getStatus()));
        if (targetStatus == null) {
            throw exception(AFTER_SALE_WARRANTY_STATUS_INVALID);
        }
        // 1.3 状态无变化时直接返回
        if (Objects.equals(warranty.getStatus(), reqVO.getStatus())) {
            return;
        }

        // 2. 更新状态
        CrmAfterSaleWarrantyDO updateObj = new CrmAfterSaleWarrantyDO();
        updateObj.setId(reqVO.getId());
        updateObj.setStatus(reqVO.getStatus());
        warrantyMapper.updateById(updateObj);
        log.info("[updateWarrantyStatus][质保({}) 状态由 {} 手动调整为 {}，操作人({})，原因({})]",
                warranty.getNo(), CrmAfterSaleWarrantyStatusEnum.getNameByStatus(warranty.getStatus()),
                targetStatus.getName(), userId, reqVO.getReason());

        // 3. 手动置为「已到期终止」时，同样执行关联同步（停用计划 / 取消超期未完成任务 / 到期通知）
        if (Objects.equals(reqVO.getStatus(), CrmAfterSaleWarrantyStatusEnum.EXPIRED.getStatus())) {
            handleWarrantyExpired(warranty);
        }
    }

    /**
     * 计算质保档案在指定日期应处于的状态；到期日为空时返回 null（不参与流转）
     */
    private static Integer calculateTargetStatus(CrmAfterSaleWarrantyDO warranty, LocalDate today, int warnDays) {
        if (warranty.getWarrantyEndDate() == null) {
            return null;
        }
        if (warranty.getWarrantyEndDate().isBefore(today)) {
            return CrmAfterSaleWarrantyStatusEnum.EXPIRED.getStatus();
        }
        if (!warranty.getWarrantyEndDate().isAfter(today.plusDays(warnDays))) {
            return CrmAfterSaleWarrantyStatusEnum.EXPIRING.getStatus();
        }
        return CrmAfterSaleWarrantyStatusEnum.IN_WARRANTY.getStatus();
    }

    /**
     * 质保到期后的关联同步：停用回访计划 → 取消到期日之后的未完成回访任务 → 发送「质保已到期」通知
     *
     * <p>每一步都独立容错：通知失败（模板缺失 / 消息服务异常）不影响状态流转与业务数据。
     *
     * @param warranty 已流转为「已到期终止」的质保档案
     */
    private void handleWarrantyExpired(CrmAfterSaleWarrantyDO warranty) {
        // 1. 停用关联的回访计划：质保已终止，不再需要回访，也避免继续生成新任务
        int planCount = visitPlanMapper.disableByWarrantyId(warranty.getId());
        // 2. 取消「计划回访日期晚于质保到期日」的未完成任务：这类回访已无业务意义，
        //    取消后既不再出现在「任务提醒」待办中，也保留完整痕迹
        int taskCount = warranty.getWarrantyEndDate() == null ? 0
                : visitTaskMapper.cancelUnfinishedAfterDate(warranty.getId(), warranty.getWarrantyEndDate());
        // 3. 通知售后责任人与业务负责人（去重）
        Set<Long> userIds = new HashSet<>();
        if (warranty.getServiceUserId() != null) {
            userIds.add(warranty.getServiceUserId());
        }
        if (warranty.getOwnerUserId() != null) {
            userIds.add(warranty.getOwnerUserId());
        }
        userIds.forEach(targetUserId -> messageService.sendWarrantyExpiredNotice(targetUserId, warranty));
        log.info("[handleWarrantyExpired][质保({}) 到期同步完成：停用回访计划 {} 条，取消回访任务 {} 条，通知 {} 人]",
                warranty.getNo(), planCount, taskCount, userIds.size());
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
