package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visittask.CrmAfterSaleVisitTaskPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visittask.CrmAfterSaleVisitTaskSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleConfigDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitRecordDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitTaskDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleVisitRecordMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleVisitTaskMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleWarrantyMapper;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitRecordStatusEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitTaskStatusEnum;
import cn.iocoder.txgy.module.crm.util.CrmPermissionUtils;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.*;

/**
 * 售后回访任务 Service 实现类
 *
 * @author txgycloud
 */
@Service
@Validated
@Slf4j
public class CrmAfterSaleVisitTaskServiceImpl implements CrmAfterSaleVisitTaskService {

    @Resource
    private CrmAfterSaleVisitTaskMapper visitTaskMapper;
    @Resource
    private CrmAfterSaleVisitRecordMapper visitRecordMapper;
    @Resource
    private CrmAfterSaleWarrantyMapper warrantyMapper;
    @Resource
    private CrmAfterSaleVisitPlanService visitPlanService;
    @Resource
    private CrmAfterSaleWarrantyService warrantyService;
    @Resource
    private CrmAfterSaleConfigService configService;
    @Resource
    private CrmAfterSaleMessageService messageService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVisitTask(CrmAfterSaleVisitTaskSaveReqVO createReqVO, Long userId) {
        // 1. 校验质保档案，并带出客户、合同
        CrmAfterSaleWarrantyDO warranty = warrantyService.validateWarranty(createReqVO.getWarrantyId());
        // 1.1 处理人默认取质保售后责任人
        if (createReqVO.getAssigneeUserId() == null) {
            createReqVO.setAssigneeUserId(warranty.getServiceUserId() != null
                    ? warranty.getServiceUserId() : warranty.getOwnerUserId());
        }
        // 2. 插入任务（planId=0 表示手动创建）
        CrmAfterSaleVisitTaskDO visitTask = BeanUtils.toBean(createReqVO, CrmAfterSaleVisitTaskDO.class);
        visitTask.setPlanId(0L);
        visitTask.setCustomerId(warranty.getCustomerId());
        visitTask.setContractId(warranty.getContractId());
        visitTask.setStatus(CrmAfterSaleVisitTaskStatusEnum.PENDING.getStatus());
        // 窗口期截止日期 = 计划回访日期 + 逾期判定天数
        visitTask.setWindowEndDate(calcWindowEndDate(visitTask.getTaskDate()));
        // 业务编号：按质保档案独立递增（取该档案已使用的最大业务编号 +1）
        visitTask.setBizNo(generateBizNo(warranty.getId()));
        visitTaskMapper.insert(visitTask);
        // 说明：任务创建时仅落任务数据，不预生成回访记录；
        //       用户在任务列表点击「填写回访记录」后现场填写，保存成功才新增记录并完成任务
        return visitTask.getId();
    }

    /**
     * 生成质保档案下的下一个业务编号：该档案已使用的最大业务编号 + 1
     *
     * <p>编号不回收、不复用：查询包含已逻辑删除的任务，任务删除/取消/完成后其编号仍被占用；
     * 不同质保档案之间编号互相独立。
     */
    private Integer generateBizNo(Long warrantyId) {
        Integer maxBizNo = visitTaskMapper.selectMaxBizNoByWarrantyId(warrantyId);
        return (maxBizNo == null ? 0 : maxBizNo) + 1;
    }

    @Override
    public void updateVisitTask(CrmAfterSaleVisitTaskSaveReqVO updateReqVO, Long userId) {
        // 1. 校验存在且未完成
        CrmAfterSaleVisitTaskDO oldTask = validateVisitTask(updateReqVO.getId());
        validateTaskNotFinish(oldTask);
        // 1.1 不允许修改质保档案等业务主数据，沿用旧值
        updateReqVO.setWarrantyId(oldTask.getWarrantyId()).setPlanId(oldTask.getPlanId());
        // 2. 更新（仅执行日期、处理人、备注可编辑；计划日期变更时同步重算窗口期）
        CrmAfterSaleVisitTaskDO updateObj = BeanUtils.toBean(updateReqVO, CrmAfterSaleVisitTaskDO.class);
        updateObj.setCustomerId(oldTask.getCustomerId()).setContractId(oldTask.getContractId());
        if (updateObj.getTaskDate() != null) {
            updateObj.setWindowEndDate(calcWindowEndDate(updateObj.getTaskDate()));
        }
        visitTaskMapper.updateById(updateObj);
    }

    @Override
    public void deleteVisitTask(Long id, Long userId) {
        // 1. 校验存在且未完成
        CrmAfterSaleVisitTaskDO visitTask = validateVisitTask(id);
        validateTaskNotFinish(visitTask);
        // 2. 删除任务，并连带删除其关联的回访记录（保持任务与记录一一对应，避免残留孤儿记录）
        visitRecordMapper.deleteByTaskId(id);
        visitTaskMapper.deleteById(id);
        // 3. 业务编号重排：保证该质保档案下的编号始终从 1 开始严格连续递增（删除后不跳号）
        visitTaskMapper.resequenceBizNoByWarrantyId(visitTask.getWarrantyId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishVisitTask(Long id, Long userId) {
        // 1. 校验存在
        CrmAfterSaleVisitTaskDO visitTask = validateVisitTask(id);
        // 1.1 已完成的任务不允许重复完成
        if (Objects.equals(visitTask.getStatus(), CrmAfterSaleVisitTaskStatusEnum.FINISH.getStatus())) {
            throw exception(AFTER_SALE_VISIT_TASK_FINISH_FAIL_STATUS);
        }
        // 1.2 校验处理人（管理员可代操作）
        if (!Objects.equals(visitTask.getAssigneeUserId(), userId) && !CrmPermissionUtils.isAdminUser(userId)) {
            throw exception(AFTER_SALE_VISIT_TASK_OPERATE_DENIED);
        }
        // 1.3 校验：必须先完成关联回访记录的填写，才允许将任务置为「已完成」
        //     状态由「记录填写」驱动，不允许在任务模块提前变更
        CrmAfterSaleVisitRecordDO filledRecord = visitRecordMapper.selectOneByTaskId(id);
        if (filledRecord == null
                || !Objects.equals(filledRecord.getStatus(), CrmAfterSaleVisitRecordStatusEnum.FILLED.getStatus())) {
            throw exception(AFTER_SALE_VISIT_TASK_FINISH_FAIL_RECORD_NOT_FILLED);
        }
        // 1.4 硬时间锁：未到计划回访日期时禁止提交回访记录（前端按钮置灰，此处后端兜底拦截）；
        //     售后设置开启「允许提前提交回访记录」时放行；窗口期内、已逾期的任务同样放行
        validateTaskArrived(visitTask);
        // 2. 更新任务状态
        CrmAfterSaleVisitTaskDO updateObj = new CrmAfterSaleVisitTaskDO();
        updateObj.setId(id);
        updateObj.setStatus(CrmAfterSaleVisitTaskStatusEnum.FINISH.getStatus());
        LocalDateTime finishTime = LocalDateTime.now();
        updateObj.setFinishTime(finishTime);
        visitTaskMapper.updateById(updateObj);
        // 3. 更新质保档案最近回访时间
        CrmAfterSaleWarrantyDO warranty = warrantyMapper.selectById(visitTask.getWarrantyId());
        if (warranty != null) {
            CrmAfterSaleWarrantyDO warrantyUpdate = new CrmAfterSaleWarrantyDO();
            warrantyUpdate.setId(warranty.getId());
            warrantyUpdate.setLastVisitTime(finishTime);
            warrantyMapper.updateById(warrantyUpdate);
        }
        // 4. 记录已在「创建任务」时自动生成、并由用户填写完成，此处不再生成；
        //    滚动生成下一期回访任务：下一期计划回访日期 = 本期实际完成时间 + 计划周期；
        //    下一期窗口期截止日期 = 下一期计划回访日期 + 逾期判定天数；
        //    若下一期日期晚于质保到期日则自动停止；生成失败不影响本期任务的完成状态
        try {
            visitPlanService.generateNextTask(visitTask.getPlanId(), userId,
                    finishTime.toLocalDate(), visitTask.getCycleKey());
        } catch (Exception ex) {
            log.error("[finishVisitTask][任务({})完成后生成下一期回访任务失败]", id, ex);
        }
    }

    @Override
    public CrmAfterSaleVisitTaskDO getVisitTask(Long id) {
        return visitTaskMapper.selectById(id);
    }

    @Override
    public CrmAfterSaleVisitTaskDO validateVisitTask(Long id) {
        CrmAfterSaleVisitTaskDO visitTask = visitTaskMapper.selectById(id);
        if (visitTask == null) {
            throw exception(AFTER_SALE_VISIT_TASK_NOT_EXISTS);
        }
        return visitTask;
    }

    @Override
    public PageResult<CrmAfterSaleVisitTaskDO> getVisitTaskPage(CrmAfterSaleVisitTaskPageReqVO pageReqVO, Long userId) {
        // 待办「回访任务临期」：实时按售后设置判定，不预生成任何待办数据
        if (CrmAfterSaleVisitTaskPageReqVO.REMIND_TYPE_APPROACHING.equals(pageReqVO.getRemindType())) {
            return visitTaskMapper.selectPageByRemind(pageReqVO, resolveRemindAssigneeUserIds(userId),
                    LocalDate.now(), resolveTaskRemindDays());
        }
        return visitTaskMapper.selectPage(pageReqVO);
    }

    @Override
    public Long getVisitTaskRemindCount(Long userId) {
        return visitTaskMapper.selectCountByRemind(resolveRemindAssigneeUserIds(userId),
                LocalDate.now(), resolveTaskRemindDays());
    }

    /**
     * 解析待办归属范围：本人 + 下属（与待办现有的「归属过滤」口径一致）
     *
     * <p>说明：回访任务不在 CRM 的 crm_permission 数据权限体系内，
     * 因此沿用任务的「处理人」(assigneeUserId) 作为归属人。
     */
    private Set<Long> resolveRemindAssigneeUserIds(Long userId) {
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
     * 读取售后设置中的「回访任务临期提醒提前天数」，未配置时按售后设置默认值 3 天兜底
     */
    private Integer resolveTaskRemindDays() {
        Integer remindDays = configService.getConfig().getTaskRemindDays();
        return remindDays == null || remindDays < 0 ? 3 : remindDays;
    }

    @Override
    public List<CrmAfterSaleVisitTaskDO> getVisitTaskListByWarrantyId(Long warrantyId) {
        return visitTaskMapper.selectListByWarrantyId(warrantyId);
    }

    private void validateTaskNotFinish(CrmAfterSaleVisitTaskDO visitTask) {
        if (Objects.equals(visitTask.getStatus(), CrmAfterSaleVisitTaskStatusEnum.FINISH.getStatus())) {
            throw exception(AFTER_SALE_VISIT_TASK_UPDATE_FAIL_FINISH);
        }
    }

    /**
     * 计算窗口期截止日期：计划回访日期 + 回访任务逾期判定天数
     *
     * <p>逾期判定天数取自售后设置的 taskOverdueDays，为空或负数时按 0 天处理（即当天即为窗口期最后一天）；
     * 每次调用都重新读取当前配置，因此售后设置调整后对存量任务立即生效。
     *
     * @param taskDate 计划回访日期
     * @return 窗口期截止日期；计划回访日期为空时返回 null
     */
    @Override
    public LocalDate calcWindowEndDate(LocalDate taskDate) {
        if (taskDate == null) {
            return null;
        }
        Integer overdueDays = configService.getConfig().getTaskOverdueDays();
        return taskDate.plusDays(overdueDays == null || overdueDays < 0 ? 0 : overdueDays);
    }

    /**
     * 取任务的窗口期截止日期：按当前售后设置实时计算（不读 window_end_date 快照）
     */
    private LocalDate resolveWindowEndDate(CrmAfterSaleVisitTaskDO task) {
        return calcWindowEndDate(task.getTaskDate());
    }

    @Override
    public void validateTaskArrived(CrmAfterSaleVisitTaskDO task) {
        if (task == null || task.getTaskDate() == null) {
            return;
        }
        // 售后设置开启「允许提前提交回访记录」时，不受计划回访日期限制
        if (Boolean.TRUE.equals(configService.getConfig().getAllowEarlyVisit())) {
            return;
        }
        if (LocalDate.now().isBefore(task.getTaskDate())) {
            throw exception(AFTER_SALE_VISIT_TASK_NOT_ARRIVED, task.getTaskDate());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoMarkOverdueVisitTasks() {
        // 1. 逾期判定：当前日期已超过「窗口期截止日期」且仍为「待处理」的任务
        //    窗口期内（计划回访日期 ≤ 今天 ≤ 窗口期截止日期）不算逾期
        //    窗口期截止日期 = 计划回访日期 + 回访任务逾期判定天数（实时取当前配置）
        //    等价于：计划回访日期 < 今天 - 逾期判定天数
        LocalDate today = LocalDate.now();
        Integer overdueDays = configService.getConfig().getTaskOverdueDays();
        LocalDate threshold = today.minusDays(overdueDays == null || overdueDays < 0 ? 0 : overdueDays);
        // 2. 查询待处理且已超出窗口期的任务并标记为「已逾期」
        List<CrmAfterSaleVisitTaskDO> overdueTasks = visitTaskMapper.selectListByStatusAndDateLt(
                CrmAfterSaleVisitTaskStatusEnum.PENDING.getStatus(), threshold);
        int count = 0;
        for (CrmAfterSaleVisitTaskDO task : overdueTasks) {
            // 已完成的任务不处理（避免并发登记完成场景下误改）
            if (!Objects.equals(task.getStatus(), CrmAfterSaleVisitTaskStatusEnum.PENDING.getStatus())) {
                continue;
            }
            CrmAfterSaleVisitTaskDO updateObj = new CrmAfterSaleVisitTaskDO();
            updateObj.setId(task.getId());
            updateObj.setStatus(CrmAfterSaleVisitTaskStatusEnum.OVERDUE.getStatus());
            visitTaskMapper.updateById(updateObj);
            count++;
        }
        return count;
    }

    @Override
    public int remindDueVisitTasks() {
        CrmAfterSaleConfigDO config = configService.getConfig();
        int remindDays = config.getTaskRemindDays() != null ? config.getTaskRemindDays() : 3;
        LocalDate today = LocalDate.now();
        // 1. 查询待处理、未提醒过、任务日期在（今天+提前天数）内的任务
        LocalDate remindEnd = today.plusDays(remindDays);
        List<CrmAfterSaleVisitTaskDO> tasks = visitTaskMapper.selectListByStatusAndRemindNotSent(
                CrmAfterSaleVisitTaskStatusEnum.PENDING.getStatus(), remindEnd);
        int count = 0;
        for (CrmAfterSaleVisitTaskDO task : tasks) {
            // 1.1 已超出窗口期（理应被标记逾期）的不再提醒，等待逾期流转
            LocalDate windowEnd = resolveWindowEndDate(task);
            if (windowEnd != null && windowEnd.isBefore(today)) {
                continue;
            }
            // 2. 发送临期/到期提醒，并回写提醒发送日期（防重复）
            messageService.sendVisitTaskRemind(task.getAssigneeUserId(), task);
            CrmAfterSaleVisitTaskDO updateObj = new CrmAfterSaleVisitTaskDO();
            updateObj.setId(task.getId());
            updateObj.setOverdueSentDate(today);
            visitTaskMapper.updateById(updateObj);
            count++;
        }
        return count;
    }

}
