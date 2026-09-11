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
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitTaskStatusEnum;
import cn.iocoder.txgy.module.crm.util.CrmPermissionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

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
        if (taskId != null) {
            CrmAfterSaleVisitTaskDO task = visitTaskService.validateVisitTask(taskId);
            createReqVO.setWarrantyId(task.getWarrantyId());
            warranty = warrantyService.validateWarranty(task.getWarrantyId());
        } else {
            warranty = warrantyService.validateWarranty(createReqVO.getWarrantyId());
        }
        if (createReqVO.getVisitTime() == null) {
            createReqVO.setVisitTime(LocalDateTime.now());
        }
        // 2. 插入回访记录（永久留存，逻辑删除可恢复）
        CrmAfterSaleVisitRecordDO visitRecord = BeanUtils.toBean(createReqVO, CrmAfterSaleVisitRecordDO.class);
        visitRecord.setCustomerId(warranty.getCustomerId());
        visitRecord.setContractId(warranty.getContractId());
        visitRecordMapper.insert(visitRecord);
        // 3. 关联任务则完成任务（未完成时才回写，防止重复登记时误报）
        if (taskId != null) {
            CrmAfterSaleVisitTaskDO task = visitTaskMapper.selectById(taskId);
            if (task != null && !Objects.equals(task.getStatus(), CrmAfterSaleVisitTaskStatusEnum.FINISH.getStatus())) {
                visitTaskService.finishVisitTask(taskId, userId);
            }
        }
        // 4. 回写质保档案最近回访时间/计划下次回访时间
        CrmAfterSaleVisitRecordDO latest = visitRecordMapper.selectById(visitRecord.getId());
        syncWarrantyVisitTime(warranty.getId(), latest.getVisitTime(), createReqVO.getNextVisitDate() != null
                ? createReqVO.getNextVisitDate().atStartOfDay() : null);
        return visitRecord.getId();
    }

    @Override
    public void updateVisitRecord(CrmAfterSaleVisitRecordSaveReqVO updateReqVO, Long userId) {
        // 1. 校验存在与操作权限
        CrmAfterSaleVisitRecordDO oldRecord = validateVisitRecord(updateReqVO.getId());
        if (!isCreatorOrAdmin(oldRecord, userId)) {
            throw exception(AFTER_SALE_VISIT_RECORD_OPERATE_DENIED);
        }
        // 1.1 恢复不可修改字段旧值（任务/质保等主数据）
        updateReqVO.setTaskId(oldRecord.getTaskId()).setWarrantyId(oldRecord.getWarrantyId());
        // 2. 更新
        CrmAfterSaleVisitRecordDO updateObj = BeanUtils.toBean(updateReqVO, CrmAfterSaleVisitRecordDO.class);
        updateObj.setCustomerId(oldRecord.getCustomerId()).setContractId(oldRecord.getContractId());
        visitRecordMapper.updateById(updateObj);
        // 3. 回写质保档案最近回访时间（重算该质保下最新记录）
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
        // 3. 关联任务回退为待处理
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
