package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitrecord.CrmAfterSaleVisitRecordPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitrecord.CrmAfterSaleVisitRecordSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitRecordDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitTaskDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleVisitRecordMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleVisitTaskMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleWarrantyMapper;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitRecordStatusEnum;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitTaskStatusEnum;
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
 * 售后回访记录 Service 实现类
 *
 * @author txgycloud
 */
@Service
@Validated
public class CrmAfterSaleVisitRecordServiceImpl implements CrmAfterSaleVisitRecordService {

    @Resource
    private CrmAfterSaleVisitRecordMapper visitRecordMapper;
    @Resource
    private CrmAfterSaleVisitTaskMapper visitTaskMapper;
    @Resource
    private CrmAfterSaleWarrantyMapper warrantyMapper;
    @Resource
    private CrmAfterSaleWarrantyService warrantyService;
    @Resource
    private CrmAfterSaleVisitTaskService visitTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVisitRecord(CrmAfterSaleVisitRecordSaveReqVO createReqVO, Long userId) {
        // 1. 确定归属的质保档案、客户、合同（优先以任务带出）
        CrmAfterSaleWarrantyDO warranty;
        Long taskId = createReqVO.getTaskId();
        CrmAfterSaleVisitTaskDO visitTask = null;
        if (taskId != null) {
            visitTask = visitTaskService.validateVisitTask(taskId);
            createReqVO.setWarrantyId(visitTask.getWarrantyId());
            warranty = warrantyService.validateWarranty(visitTask.getWarrantyId());
        } else {
            warranty = warrantyService.validateWarranty(createReqVO.getWarrantyId());
        }
        if (createReqVO.getVisitTime() == null) {
            createReqVO.setVisitTime(LocalDateTime.now());
        }
        // 1.1 校验回访时间：不得晚于今天、不得早于项目完工验收日期
        validateVisitTime(createReqVO.getVisitTime(), warranty);
        // 1.2 硬时间锁：未到计划回访日期时禁止提交回访记录
        //     前置到写库之前校验，保证任何入口都无法提前完成回访任务（任务侧完成时另有兜底校验）；
        //     售后设置开启「允许提前提交回访记录」时放行
        visitTaskService.validateTaskArrived(visitTask);
        // 1.3 规则 3：一条回访任务最多对应一条回访记录（数据库唯一约束兜底，防并发）
        if (taskId != null && visitRecordMapper.selectCountByTaskId(taskId) > 0) {
            throw exception(AFTER_SALE_VISIT_RECORD_TASK_EXISTS);
        }
        // 2. 插入回访记录（永久留存，逻辑删除可恢复）
        //    手动登记/直接填写均视为「已填写」；由任务完成自动生成的记录才是「待填写」
        CrmAfterSaleVisitRecordDO visitRecord = BeanUtils.toBean(createReqVO, CrmAfterSaleVisitRecordDO.class);
        visitRecord.setCustomerId(warranty.getCustomerId());
        visitRecord.setContractId(warranty.getContractId());
        visitRecord.setStatus(CrmAfterSaleVisitRecordStatusEnum.FILLED.getStatus());
        visitRecordMapper.insert(visitRecord);
        // 3. 关联任务则完成任务（未完成时才回写，防止重复登记时误报）
        if (taskId != null && visitTask != null
                && !Objects.equals(visitTask.getStatus(), CrmAfterSaleVisitTaskStatusEnum.FINISH.getStatus())) {
            visitTaskService.finishVisitTask(taskId, userId);
        }
        // 4. 回写质保档案最近回访时间/计划下次回访时间
        CrmAfterSaleVisitRecordDO latest = visitRecordMapper.selectById(visitRecord.getId());
        syncWarrantyVisitTime(warranty.getId(), latest.getVisitTime(), createReqVO.getNextVisitDate() != null
                ? createReqVO.getNextVisitDate().atStartOfDay() : null);
        return visitRecord.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVisitRecord(CrmAfterSaleVisitRecordSaveReqVO updateReqVO, Long userId) {
        // 1. 校验存在与操作权限
        CrmAfterSaleVisitRecordDO oldRecord = validateVisitRecord(updateReqVO.getId());
        if (!isCreatorOrAdmin(oldRecord, userId)) {
            throw exception(AFTER_SALE_VISIT_RECORD_OPERATE_DENIED);
        }
        // 1.1 恢复不可修改字段旧值（任务/质保等主数据）
        updateReqVO.setTaskId(oldRecord.getTaskId()).setWarrantyId(oldRecord.getWarrantyId());
        // 1.2 校验回访时间：不得晚于今天、不得早于项目完工验收日期
        validateVisitTime(updateReqVO.getVisitTime(), warrantyService.validateWarranty(oldRecord.getWarrantyId()));
        // 1.3 硬时间锁：未到计划回访日期时禁止提交回访记录（前置校验，写库前即拦截）
        CrmAfterSaleVisitTaskDO visitTask = oldRecord.getTaskId() == null
                ? null : visitTaskMapper.selectById(oldRecord.getTaskId());
        visitTaskService.validateTaskArrived(visitTask);
        // 2. 更新
        //    待填写的记录一旦提交填写，即流转为「已填写」
        CrmAfterSaleVisitRecordDO updateObj = BeanUtils.toBean(updateReqVO, CrmAfterSaleVisitRecordDO.class);
        updateObj.setCustomerId(oldRecord.getCustomerId()).setContractId(oldRecord.getContractId());
        updateObj.setStatus(CrmAfterSaleVisitRecordStatusEnum.FILLED.getStatus());
        visitRecordMapper.updateById(updateObj);
        // 3. 状态同步：记录填写完成 → 关联回访任务置为「已完成」
        //    任务状态由「记录填写」驱动（不在任务模块提前变更）；任务侧顺带完成质保回访时间回写与下一期任务滚动
        if (visitTask != null
                && !Objects.equals(visitTask.getStatus(), CrmAfterSaleVisitTaskStatusEnum.FINISH.getStatus())) {
            visitTaskService.finishVisitTask(oldRecord.getTaskId(), userId);
        }
        // 4. 回写质保档案最近回访时间（重算该质保下最新记录）
        CrmAfterSaleVisitRecordDO latest = selectLatestRecord(oldRecord.getWarrantyId());
        syncWarrantyVisitTime(oldRecord.getWarrantyId(), latest != null ? latest.getVisitTime() : null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVisitRecord(Long id, Long userId) {
        // 1. 校验存在与操作权限
        CrmAfterSaleVisitRecordDO oldRecord = validateVisitRecord(id);
        if (!isCreatorOrAdmin(oldRecord, userId)) {
            throw exception(AFTER_SALE_VISIT_RECORD_DELETE_DENIED);
        }
        // 2. 删除
        visitRecordMapper.deleteById(id);
        // 3. 关联任务回退为「待处理」：记录删除后任务恢复可再次填写（不预生成记录）
        if (oldRecord.getTaskId() != null) {
            CrmAfterSaleVisitTaskDO task = visitTaskMapper.selectById(oldRecord.getTaskId());
            if (task != null && Objects.equals(task.getStatus(), CrmAfterSaleVisitTaskStatusEnum.FINISH.getStatus())) {
                CrmAfterSaleVisitTaskDO taskUpdate = new CrmAfterSaleVisitTaskDO();
                taskUpdate.setId(task.getId());
                taskUpdate.setStatus(CrmAfterSaleVisitTaskStatusEnum.PENDING.getStatus());
                taskUpdate.setFinishTime(null);
                visitTaskMapper.updateById(taskUpdate);
            }
        }
        // 4. 回写质保档案最近回访时间
        CrmAfterSaleVisitRecordDO latest = selectLatestRecord(oldRecord.getWarrantyId());
        syncWarrantyVisitTime(oldRecord.getWarrantyId(), latest != null ? latest.getVisitTime() : null, null);
    }

    @Override
    public CrmAfterSaleVisitRecordDO getVisitRecord(Long id) {
        return visitRecordMapper.selectById(id);
    }

    @Override
    public CrmAfterSaleVisitRecordDO getVisitRecordByTaskId(Long taskId) {
        return visitRecordMapper.selectOneByTaskId(taskId);
    }

    @Override
    public CrmAfterSaleVisitRecordDO validateVisitRecord(Long id) {
        CrmAfterSaleVisitRecordDO visitRecord = visitRecordMapper.selectById(id);
        if (visitRecord == null) {
            throw exception(AFTER_SALE_VISIT_RECORD_NOT_EXISTS);
        }
        return visitRecord;
    }

    @Override
    public PageResult<CrmAfterSaleVisitRecordDO> getVisitRecordPage(CrmAfterSaleVisitRecordPageReqVO pageReqVO) {
        return visitRecordMapper.selectPage(pageReqVO);
    }

    // ========== 私有方法 ==========

    /**
     * 校验回访时间：不得晚于今天，且不得早于项目完工验收日期
     *
     * <p>按「日期」粒度比较（允许当天任意时刻），与服务端时区保持一致；
     * 时间统一使用 LocalDateTime 比较，避免时间戳与时区转换带来的偏差。
     *
     * @param visitTime 回访时间（为空则不校验）
     * @param warranty  关联的质保档案（用于取项目完工验收日期）
     */
    private void validateVisitTime(LocalDateTime visitTime, CrmAfterSaleWarrantyDO warranty) {
        if (visitTime == null) {
            return;
        }
        // 1. 不能晚于今天
        if (visitTime.toLocalDate().isAfter(LocalDate.now())) {
            throw exception(AFTER_SALE_VISIT_RECORD_TIME_FUTURE);
        }
        // 2. 不能早于项目完工验收日期
        if (warranty != null && warranty.getProjectAcceptDate() != null
                && visitTime.toLocalDate().isBefore(warranty.getProjectAcceptDate())) {
            throw exception(AFTER_SALE_VISIT_RECORD_TIME_BEFORE_ACCEPT);
        }
    }

    /**
     * 回写质保档案最近回访时间/下次回访时间
     */
    private void syncWarrantyVisitTime(Long warrantyId, LocalDateTime lastVisitTime, LocalDateTime nextVisitTime) {
        CrmAfterSaleWarrantyDO warrantyUpdate = new CrmAfterSaleWarrantyDO();
        warrantyUpdate.setId(warrantyId);
        warrantyUpdate.setLastVisitTime(lastVisitTime);
        if (nextVisitTime != null) {
            warrantyUpdate.setNextVisitTime(nextVisitTime);
        }
        warrantyMapper.updateById(warrantyUpdate);
    }

    private CrmAfterSaleVisitRecordDO selectLatestRecord(Long warrantyId) {
        List<CrmAfterSaleVisitRecordDO> records = visitRecordMapper.selectListByWarrantyIdOrderByVisitTimeDesc(warrantyId);
        return records.isEmpty() ? null : records.get(0);
    }

    private boolean isCreatorOrAdmin(CrmAfterSaleVisitRecordDO record, Long userId) {
        if (CrmPermissionUtils.isAdminUser(userId)) {
            return true;
        }
        return StrUtil.equals(StrUtil.toString(userId), record.getCreator());
    }

}
