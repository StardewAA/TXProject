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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

    @Resource
    private CrmAfterSaleVisitPlanMapper visitPlanMapper;
    @Resource
    private CrmAfterSaleVisitTaskMapper visitTaskMapper;
    @Resource
    private CrmAfterSaleWarrantyService warrantyService;

    @Override
    public Long createVisitPlan(CrmAfterSaleVisitPlanSaveReqVO createReqVO, Long userId) {
        // 1. 校验质保档案存在，并获取默认起止日期
        CrmAfterSaleWarrantyDO warranty = warrantyService.validateWarranty(createReqVO.getWarrantyId());
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

        // 2. 插入回访计划
        CrmAfterSaleVisitPlanDO visitPlan = BeanUtils.toBean(createReqVO, CrmAfterSaleVisitPlanDO.class);
        visitPlanMapper.insert(visitPlan);
        return visitPlan.getId();
    }

    @Override
    public void updateVisitPlan(CrmAfterSaleVisitPlanSaveReqVO updateReqVO) {
        // 1. 校验存在
        CrmAfterSaleVisitPlanDO oldPlan = validateVisitPlan(updateReqVO.getId());
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
        // 1. 校验计划启用
        CrmAfterSaleVisitPlanDO plan = validateVisitPlan(planId);
        if (!Objects.equals(plan.getStatus(), CrmAfterSaleVisitPlanStatusEnum.ENABLE.getStatus())) {
            throw exception(AFTER_SALE_VISIT_PLAN_NOT_ENABLE);
        }
        CrmAfterSaleWarrantyDO warranty = warrantyService.validateWarranty(plan.getWarrantyId());

        // 2. 计算需要生成的任务日期（断点续排：跳过 lastTaskDate 之前与已存在的）
        LocalDate today = LocalDate.now();
        Set<LocalDate> existsDates = new HashSet<>(visitTaskMapper.selectListByPlanId(planId).stream()
                .map(CrmAfterSaleVisitTaskDO::getTaskDate).toList());
        List<LocalDate> taskDates = buildTaskDates(plan, warranty, today);
        taskDates.removeIf(existsDates::contains);

        // 3. 生成任务
        Long assigneeUserId = resolveAssigneeUserId(plan, warranty, userId);
        String remark = buildTaskRemark(plan);
        LocalDate lastDate = plan.getLastTaskDate();
        for (LocalDate taskDate : taskDates) {
            CrmAfterSaleVisitTaskDO task = CrmAfterSaleVisitTaskDO.builder()
                    .planId(planId).warrantyId(warranty.getId())
                    .customerId(warranty.getCustomerId()).contractId(warranty.getContractId())
                    .assigneeUserId(assigneeUserId).taskDate(taskDate)
                    .status(CrmAfterSaleVisitTaskStatusEnum.PENDING.getStatus())
                    .remark(remark).build();
            visitTaskMapper.insert(task);
            if (lastDate == null || taskDate.isAfter(lastDate)) {
                lastDate = taskDate;
            }
        }
        // 4. 更新断点日期
        if (lastDate != null && !lastDate.equals(plan.getLastTaskDate())) {
            CrmAfterSaleVisitPlanDO updateObj = new CrmAfterSaleVisitPlanDO();
            updateObj.setId(planId);
            updateObj.setLastTaskDate(lastDate);
            visitPlanMapper.updateById(updateObj);
        }
        return taskDates.size();
    }

    /**
     * 计算需要生成的任务日期：
     * 1. 周期型（季度/半年/年）：从开始日期按周期递推，取 开始日 ~ min(今天, 结束日) 之间的周期点；
     * 2. 到期前重点回访：仅生成 质保到期日 - N 天 这一个点。
     */
    private List<LocalDate> buildTaskDates(CrmAfterSaleVisitPlanDO plan, CrmAfterSaleWarrantyDO warranty, LocalDate today) {
        List<LocalDate> dates = new ArrayList<>();
        if (Objects.equals(plan.getCycleType(), CrmAfterSaleVisitCycleEnum.BEFORE_EXPIRE.getType())) {
            LocalDate taskDate = warranty.getWarrantyEndDate().minusDays(plan.getCycleValue());
            if (isInRange(taskDate, plan, today)) {
                dates.add(taskDate);
            }
            return dates;
        }
        // 周期型：起始点取 lastTaskDate 之后，保证断点续排
        LocalDate start = plan.getStartDate();
        LocalDate end = plan.getEndDate() != null && plan.getEndDate().isBefore(today) ? plan.getEndDate() : today;
        LocalDate cursor = start;
        int guard = 0;
        while (!cursor.isAfter(end) && guard++ < 500) {
            if (isInRange(cursor, plan, today)) {
                dates.add(cursor);
            }
            cursor = plusCycle(cursor, plan.getCycleType(), plan.getCycleValue());
        }
        return dates;
    }

    private boolean isInRange(LocalDate date, CrmAfterSaleVisitPlanDO plan, LocalDate today) {
        if (date.isAfter(today)) {
            return false;
        }
        if (plan.getStartDate() != null && date.isBefore(plan.getStartDate())) {
            return false;
        }
        if (plan.getEndDate() != null && date.isAfter(plan.getEndDate())) {
            return false;
        }
        if (plan.getLastTaskDate() != null && !date.isAfter(plan.getLastTaskDate())) {
            return false; // 已经生成过
        }
        return true;
    }

    private LocalDate plusCycle(LocalDate date, Integer cycleType, Integer cycleValue) {
        return switch (cycleType) {
            case 1 -> date.plusMonths(cycleValue * 3L); // 每季度
            case 2 -> date.plusMonths(cycleValue * 6L); // 每半年
            case 3 -> date.plusYears(cycleValue);       // 每年
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
