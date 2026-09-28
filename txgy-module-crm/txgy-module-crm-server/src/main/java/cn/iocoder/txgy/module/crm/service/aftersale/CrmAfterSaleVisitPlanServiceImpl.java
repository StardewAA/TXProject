package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitplan.CrmAfterSaleVisitPlanPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitplan.CrmAfterSaleVisitPlanSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitPlanDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitTaskDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleVisitPlanMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleVisitTaskMapper;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSalePlanTypeEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitCycleEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitPlanStatusEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitTaskStatusEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.*;

/**
 * 售后回访计划 Service 实现类
 *
 * @author txgycloud
 */
@Service
@Validated
@Slf4j
public class CrmAfterSaleVisitPlanServiceImpl implements CrmAfterSaleVisitPlanService {

    /** 周期型任务的周期标识前缀：C1、C2...（第 N 个周期） */
    private static final String CYCLE_KEY_PREFIX = "C";
    /** 到期前重点回访任务的周期标识（单次，仅对应一个周期） */
    private static final String CYCLE_KEY_EXPIRE = "EXPIRE";

    @Resource
    private CrmAfterSaleVisitPlanMapper visitPlanMapper;
    @Resource
    private CrmAfterSaleVisitTaskMapper visitTaskMapper;
    @Resource
    private CrmAfterSaleWarrantyService warrantyService;
    @Resource
    private CrmAfterSaleConfigService configService;

    @Override
    public Long createVisitPlan(CrmAfterSaleVisitPlanSaveReqVO createReqVO, Long userId) {
        // 1. 校验质保档案存在，并获取默认起止日期
        CrmAfterSaleWarrantyDO warranty = warrantyService.validateWarranty(createReqVO.getWarrantyId());
        // 1.0 规则 1：一份质保档案最多绑定一条回访计划（数据库唯一约束兜底，防并发）
        if (visitPlanMapper.selectByWarrantyId(createReqVO.getWarrantyId()) != null) {
            throw exception(AFTER_SALE_VISIT_PLAN_WARRANTY_EXISTS);
        }
        // 1.1 补齐默认值
        if (createReqVO.getAssigneeType() == null) {
            createReqVO.setAssigneeType(1); // 默认售后人员
        }
        if (createReqVO.getVisitType() == null) {
            createReqVO.setVisitType(CrmAfterSaleVisitTypeEnum.PHONE.getType());
        }
        if (createReqVO.getStatus() == null) {
            createReqVO.setStatus(CrmAfterSaleVisitPlanStatusEnum.ENABLE.getStatus());
        }
        if (createReqVO.getStartDate() == null) {
            // 手动计划默认今天生效；自动计划默认从验收日期起
            createReqVO.setStartDate(Objects.equals(createReqVO.getPlanType(), CrmAfterSalePlanTypeEnum.AUTO.getType())
                    ? warranty.getProjectAcceptDate() : LocalDate.now());
        }
        if (createReqVO.getEndDate() == null) {
            createReqVO.setEndDate(warranty.getWarrantyEndDate());
        }
        // 1.2 校验生效区间
        if (createReqVO.getEndDate().isBefore(createReqVO.getStartDate())) {
            throw exception(AFTER_SALE_VISIT_PLAN_DATE_RANGE_INVALID);
        }
        // 1.3 校验开始日期不得早于关联质保档案的完工日期
        validateStartDateNotBeforeAcceptDate(createReqVO.getStartDate(), warranty.getProjectAcceptDate());

        // 2. 插入回访计划
        CrmAfterSaleVisitPlanDO visitPlan = BeanUtils.toBean(createReqVO, CrmAfterSaleVisitPlanDO.class);
        visitPlanMapper.insert(visitPlan);
        return visitPlan.getId();
    }

    @Override
    public void updateVisitPlan(CrmAfterSaleVisitPlanSaveReqVO updateReqVO) {
        // 1. 校验存在
        CrmAfterSaleVisitPlanDO oldPlan = validateVisitPlan(updateReqVO.getId());
        // 1.0 规则 1：若更换绑定的质保档案，需确认目标档案尚未绑定其它计划
        if (updateReqVO.getWarrantyId() != null
                && !Objects.equals(oldPlan.getWarrantyId(), updateReqVO.getWarrantyId())) {
            CrmAfterSaleVisitPlanDO existsPlan = visitPlanMapper.selectByWarrantyId(updateReqVO.getWarrantyId());
            if (existsPlan != null && !Objects.equals(existsPlan.getId(), updateReqVO.getId())) {
                throw exception(AFTER_SALE_VISIT_PLAN_WARRANTY_EXISTS);
            }
        }
        // 1.1 已生成任务后，周期相关配置不可修改（防止已生成任务与计划错位）
        boolean existsTask = CollUtil.isNotEmpty(visitTaskMapper.selectListByPlanId(updateReqVO.getId()));
        if (existsTask) {
            boolean cycleChanged = !Objects.equals(oldPlan.getWarrantyId(), updateReqVO.getWarrantyId())
                    || !Objects.equals(oldPlan.getPlanType(), updateReqVO.getPlanType())
                    || !Objects.equals(oldPlan.getCycleType(), updateReqVO.getCycleType())
                    || !Objects.equals(oldPlan.getCycleValue(), updateReqVO.getCycleValue())
                    || !Objects.equals(oldPlan.getStartDate(), updateReqVO.getStartDate())
                    || !Objects.equals(oldPlan.getEndDate(), updateReqVO.getEndDate())
                    || !Objects.equals(oldPlan.getAssigneeType(), updateReqVO.getAssigneeType())
                    || !Objects.equals(oldPlan.getAssigneeUserId(), updateReqVO.getAssigneeUserId());
            if (cycleChanged) {
                throw exception(AFTER_SALE_VISIT_PLAN_UPDATE_FAIL_NOT_ENABLE);
            }
            // 恢复周期相关字段的旧值，仅允许修改名称/回访方式/状态/备注
            updateReqVO.setWarrantyId(oldPlan.getWarrantyId()).setPlanType(oldPlan.getPlanType())
                    .setCycleType(oldPlan.getCycleType()).setCycleValue(oldPlan.getCycleValue())
                    .setStartDate(oldPlan.getStartDate()).setEndDate(oldPlan.getEndDate())
                    .setAssigneeType(oldPlan.getAssigneeType()).setAssigneeUserId(oldPlan.getAssigneeUserId());
        }
        // 1.2 校验生效区间
        if (updateReqVO.getEndDate() != null && updateReqVO.getStartDate() != null
                && updateReqVO.getEndDate().isBefore(updateReqVO.getStartDate())) {
            throw exception(AFTER_SALE_VISIT_PLAN_DATE_RANGE_INVALID);
        }
        // 1.3 校验开始日期不得早于关联质保档案的完工日期
        validateStartDateNotBeforeAcceptDate(updateReqVO.getStartDate(),
                warrantyService.validateWarranty(updateReqVO.getWarrantyId()).getProjectAcceptDate());

        // 2. 更新回访计划（lastTaskDate 不在 SaveReqVO 中，null 不参与更新，不会覆盖断点日期）
        CrmAfterSaleVisitPlanDO updateObj = BeanUtils.toBean(updateReqVO, CrmAfterSaleVisitPlanDO.class);
        visitPlanMapper.updateById(updateObj);
    }

    @Override
    public void deleteVisitPlan(Long id) {
        // 1. 校验存在
        validateVisitPlan(id);
        // 2. 校验没有生成回访任务
        if (CollUtil.isNotEmpty(visitTaskMapper.selectListByPlanId(id))) {
            throw exception(AFTER_SALE_VISIT_PLAN_DELETE_FAIL_EXISTS_TASK);
        }
        // 3. 删除
        visitPlanMapper.deleteById(id);
    }

    @Override
    public CrmAfterSaleVisitPlanDO getVisitPlan(Long id) {
        return visitPlanMapper.selectById(id);
    }

    @Override
    public CrmAfterSaleVisitPlanDO validateVisitPlan(Long id) {
        CrmAfterSaleVisitPlanDO visitPlan = visitPlanMapper.selectById(id);
        if (visitPlan == null) {
            throw exception(AFTER_SALE_VISIT_PLAN_NOT_EXISTS);
        }
        return visitPlan;
    }

    @Override
    public PageResult<CrmAfterSaleVisitPlanDO> getVisitPlanPage(CrmAfterSaleVisitPlanPageReqVO pageReqVO) {
        return visitPlanMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generateTasks(Long planId, Long userId) {
        // 计划非启用时保持抛出异常，便于前端提示；其余情况静默跳过
        CrmAfterSaleVisitPlanDO plan = validateVisitPlan(planId);
        if (!Objects.equals(plan.getStatus(), CrmAfterSaleVisitPlanStatusEnum.ENABLE.getStatus())) {
            throw exception(AFTER_SALE_VISIT_PLAN_NOT_ENABLE);
        }
        // 按周期滚动：一次仅生成「下一个周期」的任务
        return generateNextTask(planId, userId, null, null) == null ? 0 : 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateNextTask(Long planId, Long operator, LocalDate baseDate, String baseCycleKey) {
        // 1. 计划校验：存在且启用
        CrmAfterSaleVisitPlanDO plan = visitPlanMapper.selectById(planId);
        if (plan == null
                || !Objects.equals(plan.getStatus(), CrmAfterSaleVisitPlanStatusEnum.ENABLE.getStatus())) {
            return null;
        }
        CrmAfterSaleWarrantyDO warranty = warrantyService.validateWarranty(plan.getWarrantyId());
        // 生成上限（含）：质保到期日与计划结束日中较早的一个
        LocalDate limit = minDate(warranty.getWarrantyEndDate(), plan.getEndDate());

        // 2. 计算下一个周期槽位
        TaskSlot slot;
        if (Objects.equals(plan.getCycleType(), CrmAfterSaleVisitCycleEnum.BEFORE_EXPIRE.getType())) {
            // 到期前重点回访：仅初始化时生成一次（单次任务），本期完成后不再滚动
            if (baseDate != null || warranty.getWarrantyEndDate() == null) {
                return null;
            }
            slot = new TaskSlot(CYCLE_KEY_EXPIRE,
                    warranty.getWarrantyEndDate().minusDays(plan.getCycleValue()));
        } else if (baseDate == null) {
            // 初始化：首个周期取计划开始日期
            slot = new TaskSlot(nextCycleKey(planId, null), plan.getStartDate());
        } else {
            // 本期完成：下一期 = 本期任务日期 + 周期
            LocalDate nextDate = plusCycle(baseDate, plan.getCycleType(), plan.getCycleValue());
            slot = new TaskSlot(nextCycleKey(planId, baseCycleKey), nextDate);
        }

        // 3. 终止条件：下一期日期不得晚于 质保到期日 / 计划结束日
        if (limit != null && slot.taskDate().isAfter(limit)) {
            return null;
        }
        // 4. 幂等：同周期标识或同计划日期已存在任务时跳过（数据库唯一约束兜底）
        if (visitTaskMapper.selectCountByPlanIdAndCycleKey(planId, slot.cycleKey()) > 0
                || visitTaskMapper.selectCountByPlanIdAndTaskDate(planId, slot.taskDate()) > 0) {
            return null;
        }

        // 5. 生成任务
        // 业务编号：按质保档案独立递增（含已逻辑删除的任务，编号不回收/不复用）
        Integer maxBizNo = visitTaskMapper.selectMaxBizNoByWarrantyId(warranty.getId());
        CrmAfterSaleVisitTaskDO task = CrmAfterSaleVisitTaskDO.builder()
                .planId(planId).cycleKey(slot.cycleKey()).bizNo((maxBizNo == null ? 0 : maxBizNo) + 1)
                .warrantyId(warranty.getId())
                .customerId(warranty.getCustomerId()).contractId(warranty.getContractId())
                .assigneeUserId(resolveAssigneeUserId(plan, warranty, operator))
                .taskDate(slot.taskDate())
                // 窗口期截止日期 = 计划回访日期 + 逾期判定天数（超出后才判定「已逾期」）
                .windowEndDate(calcWindowEndDate(slot.taskDate()))
                .status(CrmAfterSaleVisitTaskStatusEnum.PENDING.getStatus())
                .remark(buildTaskRemark(plan)).build();
        visitTaskMapper.insert(task);

        // 6. 更新计划「最近生成任务日期」
        CrmAfterSaleVisitPlanDO updateObj = new CrmAfterSaleVisitPlanDO();
        updateObj.setId(planId);
        updateObj.setLastTaskDate(slot.taskDate());
        visitPlanMapper.updateById(updateObj);
        return task.getId();
    }

    /**
     * 生成任务所需的「周期槽位」：周期标识 + 任务日期
     *
     * @param cycleKey  周期标识（C1、C2... 或 EXPIRE）
     * @param taskDate  该周期对应的任务日期
     */
    private record TaskSlot(String cycleKey, LocalDate taskDate) {
    }

    /**
     * 解析下一个周期标识：优先基于本期标识序号 +1，否则按已有任务数量兜底（兼容历史数据）
     */
    private String nextCycleKey(Long planId, String baseCycleKey) {
        int index = 0;
        if (baseCycleKey != null && baseCycleKey.startsWith(CYCLE_KEY_PREFIX)
                && baseCycleKey.length() > CYCLE_KEY_PREFIX.length()) {
            try {
                index = Integer.parseInt(baseCycleKey.substring(CYCLE_KEY_PREFIX.length()));
            } catch (NumberFormatException ignored) {
                // 非标准标识（如历史数据 LEGACY-x）时走数量兜底
            }
        }
        if (index <= 0) {
            index = visitTaskMapper.selectListByPlanId(planId).size();
        }
        return CYCLE_KEY_PREFIX + (index + 1);
    }

    /**
     * 校验回访计划开始日期不得早于关联质保档案的完工日期
     *
     * @param startDate  计划开始日期
     * @param acceptDate 质保档案的完工日期（项目完工验收日期）
     */
    private void validateStartDateNotBeforeAcceptDate(LocalDate startDate, LocalDate acceptDate) {
        if (startDate != null && acceptDate != null && startDate.isBefore(acceptDate)) {
            throw exception(AFTER_SALE_VISIT_PLAN_START_DATE_BEFORE_ACCEPT_DATE);
        }
    }

    /**
     * 取两个日期中较早的一个（任一为空时返回另一个）
     */
    private LocalDate minDate(LocalDate first, LocalDate second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isBefore(second) ? first : second;
    }

    /**
     * 计算窗口期截止日期：计划回访日期 + 回访任务逾期判定天数
     */
    private LocalDate calcWindowEndDate(LocalDate taskDate) {
        if (taskDate == null) {
            return null;
        }
        Integer overdueDays = configService.getConfig().getTaskOverdueDays();
        return taskDate.plusDays(overdueDays == null || overdueDays < 0 ? 0 : overdueDays);
    }

    private LocalDate plusCycle(LocalDate date, Integer cycleType, Integer cycleValue) {
        return switch (cycleType) {
            case 1 -> date.plusMonths(cycleValue * 3L); // 每季度
            case 2 -> date.plusMonths(cycleValue * 6L); // 每半年
            case 3 -> date.plusYears(cycleValue);       // 每年
            case 5 -> date.plusMonths(cycleValue);      // 每月
            default -> throw exception(AFTER_SALE_VISIT_PLAN_CYCLE_INVALID);
        };
    }

    /**
     * 解析任务处理人：计划指定 > 质保档案责任人（按类型）> 操作人兜底
     */
    private Long resolveAssigneeUserId(CrmAfterSaleVisitPlanDO plan, CrmAfterSaleWarrantyDO warranty, Long operator) {
        Long assignee;
        if (plan.getAssigneeUserId() != null) {
            assignee = plan.getAssigneeUserId();
        } else if (Objects.equals(plan.getAssigneeType(), 2)) { // 业务员
            assignee = warranty.getOwnerUserId() != null ? warranty.getOwnerUserId() : warranty.getServiceUserId();
        } else {
            assignee = warranty.getServiceUserId() != null ? warranty.getServiceUserId() : warranty.getOwnerUserId();
        }
        return assignee != null ? assignee : operator;
    }

    private String buildTaskRemark(CrmAfterSaleVisitPlanDO plan) {
        return StrUtil.format("【{}】{}", plan.getName(), "质保期回访任务");
    }

    @Override
    public int autoGenerateVisitTasks() {
        LocalDate today = LocalDate.now();
        // 1. 查询启用中、仍有效期的回访计划
        List<CrmAfterSaleVisitPlanDO> visitPlans = visitPlanMapper.selectListByStatusEnabled(
                CrmAfterSaleVisitPlanStatusEnum.ENABLE.getStatus(), today);
        // 2. 逐个生成到期未生成的任务（单个计划失败不影响其它计划）
        int total = 0;
        int failCount = 0;
        for (CrmAfterSaleVisitPlanDO plan : visitPlans) {
            try {
                total += generateTasks(plan.getId(), null);
            } catch (Exception ex) {
                failCount++;
                log.error("[autoGenerateVisitTasks][回访计划({})生成回访任务失败]", plan.getId(), ex);
            }
        }
        log.info("[autoGenerateVisitTasks][本次扫描计划 {} 个，成功生成 {} 个任务，失败 {} 个计划]", visitPlans.size(), total, failCount);
        return total;
    }

}
