package cn.iocoder.txgy.module.crm.service.aftersale;

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
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitTypeEnum;
import cn.iocoder.txgy.module.crm.util.CrmPermissionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.*;

/**
 * 售后回访任务 Service 实现类
 *
 * @author txgycloud
 */
@Service
@Validated
public class CrmAfterSaleVisitTaskServiceImpl implements CrmAfterSaleVisitTaskService {

    @Resource
    private CrmAfterSaleVisitTaskMapper visitTaskMapper;
    @Resource
    private CrmAfterSaleVisitRecordMapper visitRecordMapper;
    @Resource
    private CrmAfterSaleWarrantyMapper warrantyMapper;
    @Resource
    private CrmAfterSaleWarrantyService warrantyService;
    @Resource
    private CrmAfterSaleConfigService configService;
    @Resource
    private CrmAfterSaleMessageService messageService;

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
        visitTaskMapper.insert(visitTask);
        return visitTask.getId();
    }

    @Override
    public void updateVisitTask(CrmAfterSaleVisitTaskSaveReqVO updateReqVO, Long userId) {
        // 1. 校验存在且未完成
        CrmAfterSaleVisitTaskDO oldTask = validateVisitTask(updateReqVO.getId());
        validateTaskNotFinish(oldTask);
        // 1.1 不允许修改质保档案等业务主数据，沿用旧值
        updateReqVO.setWarrantyId(oldTask.getWarrantyId()).setPlanId(oldTask.getPlanId());
        // 2. 更新（仅执行日期、处理人、备注可编辑）
        CrmAfterSaleVisitTaskDO updateObj = BeanUtils.toBean(updateReqVO, CrmAfterSaleVisitTaskDO.class);
        updateObj.setCustomerId(oldTask.getCustomerId()).setContractId(oldTask.getContractId());
        visitTaskMapper.updateById(updateObj);
    }

    @Override
    public void deleteVisitTask(Long id, Long userId) {
        // 1. 校验存在且未完成
        CrmAfterSaleVisitTaskDO visitTask = validateVisitTask(id);
        validateTaskNotFinish(visitTask);
        // 2. 删除
        visitTaskMapper.deleteById(id);
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
        // 2. 更新任务状态
        CrmAfterSaleVisitTaskDO updateObj = new CrmAfterSaleVisitTaskDO();
        updateObj.setId(id);
        updateObj.setStatus(CrmAfterSaleVisitTaskStatusEnum.FINISH.getStatus());
        updateObj.setFinishTime(LocalDateTime.now());
        visitTaskMapper.updateById(updateObj);
        // 3. 更新质保档案最近回访时间
        CrmAfterSaleWarrantyDO warranty = warrantyMapper.selectById(visitTask.getWarrantyId());
        if (warranty != null) {
            CrmAfterSaleWarrantyDO warrantyUpdate = new CrmAfterSaleWarrantyDO();
            warrantyUpdate.setId(warranty.getId());
            warrantyUpdate.setLastVisitTime(LocalDateTime.now());
            warrantyMapper.updateById(warrantyUpdate);
        }
        // 4. 自动生成「待填写」回访记录：任务一完成即预置记录，用户可直接打开填写，无需手动新建
        //    幂等保护：该任务若已有记录（例如手动登记过），则不重复生成
        if (visitRecordMapper.selectCountByTaskId(id) == 0) {
            CrmAfterSaleVisitRecordDO visitRecord = new CrmAfterSaleVisitRecordDO();
            visitRecord.setTaskId(id);
            visitRecord.setWarrantyId(visitTask.getWarrantyId());
            visitRecord.setCustomerId(visitTask.getCustomerId());
            visitRecord.setContractId(visitTask.getContractId());
            visitRecord.setVisitType(CrmAfterSaleVisitTypeEnum.PHONE.getType()); // 默认电话，待用户修改
            visitRecord.setVisitTime(LocalDateTime.now());
            visitRecord.setContent(""); // content 为非空字段，占位空串，待用户填写
            visitRecord.setStatus(CrmAfterSaleVisitRecordStatusEnum.TO_FILL.getStatus());
            visitRecordMapper.insert(visitRecord);
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
    public PageResult<CrmAfterSaleVisitTaskDO> getVisitTaskPage(CrmAfterSaleVisitTaskPageReqVO pageReqVO) {
        return visitTaskMapper.selectPage(pageReqVO);
    }

    private void validateTaskNotFinish(CrmAfterSaleVisitTaskDO visitTask) {
        if (Objects.equals(visitTask.getStatus(), CrmAfterSaleVisitTaskStatusEnum.FINISH.getStatus())) {
            throw exception(AFTER_SALE_VISIT_TASK_UPDATE_FAIL_FINISH);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoMarkOverdueVisitTasks() {
        // 1. 计算逾期判定截止日期（超过 task_overdue_days 天仍未处理）
        CrmAfterSaleConfigDO config = configService.getConfig();
        int overdueDays = config.getTaskOverdueDays() != null ? config.getTaskOverdueDays() : 3;
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.minusDays(overdueDays);
        // 2. 查询待处理且已过期的任务并标记为已逾期
        List<CrmAfterSaleVisitTaskDO> overdueTasks = visitTaskMapper.selectListByStatusAndDateLe(
                CrmAfterSaleVisitTaskStatusEnum.PENDING.getStatus(), cutoff);
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
        int overdueDays = config.getTaskOverdueDays() != null ? config.getTaskOverdueDays() : 3;
        LocalDate today = LocalDate.now();
        // 1. 查询待处理、未提醒过、任务日期在（今天+提前天数）内的任务
        LocalDate remindEnd = today.plusDays(remindDays);
        List<CrmAfterSaleVisitTaskDO> tasks = visitTaskMapper.selectListByStatusAndRemindNotSent(
                CrmAfterSaleVisitTaskStatusEnum.PENDING.getStatus(), remindEnd);
        int count = 0;
        for (CrmAfterSaleVisitTaskDO task : tasks) {
            // 1.1 已进入逾期判定期（理应被标记逾期）的不再提醒，等待逾期流转
            if (task.getTaskDate() != null
                    && task.getTaskDate().isBefore(today.minusDays(overdueDays))) {
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
