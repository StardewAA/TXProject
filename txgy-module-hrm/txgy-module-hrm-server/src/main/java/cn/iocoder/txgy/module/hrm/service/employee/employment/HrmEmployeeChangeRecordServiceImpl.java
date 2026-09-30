package cn.iocoder.txgy.module.hrm.service.employee.employment;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.changerecord.HrmEmployeeChangeRecordCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.changerecord.HrmEmployeeChangeRecordCancelReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeChangeRecordDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment.HrmEmployeeChangeRecordMapper;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeChangeTypeEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeChangeCancelStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeChangeSourceEnum;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.util.date.LocalDateTimeUtils.getDayBeginTime;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_CHANGE_RECORD_CANNOT_CANCEL;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_CHANGE_RECORD_NOT_EXISTS;

/**
 * HRM 员工异动记录 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmEmployeeChangeRecordServiceImpl implements HrmEmployeeChangeRecordService {

    @Resource
    private HrmEmployeeChangeRecordMapper changeRecordMapper;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public HrmEmployeeChangeRecordDO createEmployeeChangeRecord(HrmEmployeeChangeRecordCreateReqVO createReqVO) {
        boolean pendingEffect = HrmEmployeeChangeTypeEnum.PENDING_EFFECT_TYPES.contains(createReqVO.getType());
        // 转正、调岗、晋升、降级和转全职按自然日生效；再入职保留实际入职时间
        if (pendingEffect) {
            createReqVO.setEffectTime(getDayBeginTime(createReqVO.getEffectTime()));
        }
        HrmEmployeeChangeRecordDO changeRecord = BeanUtils.toBean(createReqVO, HrmEmployeeChangeRecordDO.class);
        // 撤销状态默认「正常」，便于查询时直接用 cancel_status <> 1 过滤
        if (changeRecord.getCancelStatus() == null) {
            changeRecord.setCancelStatus(HrmEmployeeChangeCancelStatusEnum.NORMAL.getStatus());
        }
        // 异动审计：未显式指定来源时按「后台办理」，经办人默认取当前登录用户
        if (changeRecord.getSource() == null) {
            changeRecord.setSource(HrmEmployeeChangeSourceEnum.BACKEND.getSource());
        }
        if (changeRecord.getOperatorUserId() == null) {
            Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
            changeRecord.setOperatorUserId(loginUserId).setOperatorName(resolveUserName(loginUserId));
        }
        // 非预约生效的再入职记录创建时即视为已生效
        if (!pendingEffect) {
            changeRecord.setAppliedTime(LocalDateTime.now());
        }
        changeRecordMapper.insert(changeRecord);
        return changeRecord;
    }

    @Override
    public HrmEmployeeChangeRecordDO getEmployeeChangeRecord(Long id) {
        return changeRecordMapper.selectById(id);
    }

    @Override
    public void cancelEmployeeChangeRecord(HrmEmployeeChangeRecordCancelReqVO reqVO) {
        HrmEmployeeChangeRecordDO changeRecord = changeRecordMapper.selectById(reqVO.getId());
        if (changeRecord == null) {
            throw exception(EMPLOYEE_CHANGE_RECORD_NOT_EXISTS);
        }
        // 只有"尚未生效"的异动可以撤销；已生效的只能用反向异动冲正，保证台账不被改写
        if (changeRecord.getAppliedTime() != null) {
            throw exception(EMPLOYEE_CHANGE_RECORD_CANNOT_CANCEL, "该异动已于 "
                    + changeRecord.getAppliedTime().toLocalDate() + " 生效");
        }
        if (HrmEmployeeChangeCancelStatusEnum.CANCELED.getStatus().equals(changeRecord.getCancelStatus())) {
            throw exception(EMPLOYEE_CHANGE_RECORD_CANNOT_CANCEL, "该异动已被撤销");
        }
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        changeRecordMapper.updateById(new HrmEmployeeChangeRecordDO().setId(reqVO.getId())
                .setCancelStatus(HrmEmployeeChangeCancelStatusEnum.CANCELED.getStatus())
                .setCancelReason(reqVO.getReason())
                .setCancelTime(LocalDateTime.now())
                .setCancelUserId(loginUserId)
                .setCancelUserName(resolveUserName(loginUserId)));
    }

    @Override
    public void updateEmployeeChangeRecordApprovalInfo(Long changeRecordId, Long approvalId,
                                                      Long approverUserId, String approverName,
                                                      LocalDateTime approveTime) {
        if (changeRecordId == null) {
            return;
        }
        changeRecordMapper.updateById(new HrmEmployeeChangeRecordDO().setId(changeRecordId)
                .setSource(HrmEmployeeChangeSourceEnum.APPROVAL.getSource())
                .setApprovalId(approvalId)
                .setApproverUserId(approverUserId)
                .setApproverName(approverName)
                .setApproveTime(approveTime));
    }

    /**
     * 解析系统用户姓名，取不到时返回 null，避免影响主流程
     */
    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            AdminUserRespDTO user = adminUserApi.getUser(userId).getCheckedData();
            return user == null ? null : user.getNickname();
        } catch (Throwable ex) {
            return null;
        }
    }

    @Override
    public List<HrmEmployeeChangeRecordDO> getEmployeeChangeRecordListByEmployeeId(Long employeeId) {
        return changeRecordMapper.selectListByEmployeeId(employeeId);
    }

    @Override
    public List<HrmEmployeeChangeRecordDO> getPendingEmployeeChangeRecordList(LocalDateTime deadlineTime) {
        return changeRecordMapper.selectListByAppliedTimeNullAndEffectTimeBeforeOrEqual(
                deadlineTime, HrmEmployeeChangeTypeEnum.PENDING_EFFECT_TYPES);
    }

    @Override
    public void updateEmployeeChangeRecordAppliedTime(Long id, LocalDateTime appliedTime) {
        changeRecordMapper.updateById(new HrmEmployeeChangeRecordDO().setId(id).setAppliedTime(appliedTime));
    }

    @Override
    public List<HrmEmployeeChangeRecordDO> getEmployeeChangeRecordListByEmployeeIdsAndEffectTimeBetween(
            Collection<Long> employeeIds, LocalDateTime[] effectTimes) {
        if (CollUtil.isEmpty(employeeIds)) {
            return Collections.emptyList();
        }
        return changeRecordMapper.selectListByEmployeeIdsAndEffectTimeBetween(
                employeeIds, effectTimes);
    }

}
