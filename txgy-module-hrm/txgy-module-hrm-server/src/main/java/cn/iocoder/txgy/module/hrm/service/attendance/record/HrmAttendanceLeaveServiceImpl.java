package cn.iocoder.txgy.module.hrm.service.attendance.record;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.txgy.module.hrm.controller.admin.attendance.vo.leave.HrmAttendanceLeaveCancelReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.attendance.vo.leave.HrmAttendanceLeaveCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.attendance.vo.leave.HrmAttendanceLeavePageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeListReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.attendance.record.HrmAttendanceLeaveDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.attendance.record.HrmAttendanceLeaveMapper;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.ATTENDANCE_LEAVE_APPROVAL_REASON_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.ATTENDANCE_LEAVE_APPROVE_STATUS_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.ATTENDANCE_LEAVE_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.ATTENDANCE_LEAVE_STATUS_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.ATTENDANCE_LEAVE_TIME_CONFLICT;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.ATTENDANCE_LEAVE_WORKFLOW_APPROVAL;

/**
 * HRM 考勤请假 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmAttendanceLeaveServiceImpl implements HrmAttendanceLeaveService {

    public static final String LEAVE_PROCESS_KEY = "hrm_attendance_leave";

    /**
     * 人工审批标记前缀
     *
     * <p>请假申请统一走人工审批，processInstanceId 写入 {@code MANUAL-<请假单编号>}，
     * 用于把「人工审批」与历史上「工作流审批」的申请区分开。</p>
     */
    public static final String MANUAL_PROCESS_INSTANCE_PREFIX = "MANUAL-";

    @Resource
    private HrmAttendanceLeaveMapper attendanceLeaveMapper;
    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private BpmProcessInstanceApi processInstanceApi;

    @Override
    @SuppressWarnings("ExtractMethodRecommender")
    @Transactional(rollbackFor = Exception.class)
    public Long createLeave(Long userId, HrmAttendanceLeaveCreateReqVO reqVO) {
        // 1.1 校验当前用户已关联员工
        HrmEmployeeDO employee = employeeService.validateEmployeeBySelf(userId);
        return doCreateLeave(employee, reqVO, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLeaveByHr(
            cn.iocoder.txgy.module.hrm.controller.admin.attendance.vo.leave.HrmAttendanceLeaveAdminCreateReqVO reqVO) {
        // Q-004 / I-006：HR 代提/补录——员工由 HR 指定（不再依赖登录用户本人），
        // approveImmediately 为空时按「直接生效」处理（补录场景更常见）
        HrmEmployeeDO employee = employeeService.validateEmployeeExists(reqVO.getEmployeeId());
        return doCreateLeave(employee, reqVO, !Boolean.FALSE.equals(reqVO.getApproveImmediately()));
    }

    /**
     * 请假单创建的公共逻辑（员工端发起 / HR 代提共用）
     *
     * @param employee 请假员工
     * @param reqVO 请假信息
     * @param approveImmediately 是否直接落「已通过」（HR 代提补录时为 true）
     * @return 请假记录编号
     */
    private Long doCreateLeave(HrmEmployeeDO employee, HrmAttendanceLeaveCreateReqVO reqVO,
                               boolean approveImmediately) {
        // 1.2 锁定员工，避免同一员工并发创建重叠请假
        employeeService.validateEmployeeExistsForUpdate(employee.getId());
        // 1.3 校验请假时间未与审批中、已通过的申请重叠
        Long conflictCount = attendanceLeaveMapper.selectCountByEmployeeIdAndTimeRangeAndApprovalStatusIn(
                employee.getId(), new LocalDateTime[]{reqVO.getStartTime(), reqVO.getEndTime()},
                Arrays.asList(BpmProcessInstanceStatusEnum.RUNNING.getStatus(), BpmProcessInstanceStatusEnum.APPROVE.getStatus()));
        if (conflictCount > 0) {
            throw exception(ATTENDANCE_LEAVE_TIME_CONFLICT);
        }

        // 2. 创建请假记录
        Integer approvalStatus = approveImmediately ? BpmProcessInstanceStatusEnum.APPROVE.getStatus()
                : BpmProcessInstanceStatusEnum.RUNNING.getStatus();
        HrmAttendanceLeaveDO attendanceLeave = BeanUtils.toBean(reqVO, HrmAttendanceLeaveDO.class)
                .setEmployeeId(employee.getId()).setApprovalStatus(approvalStatus)
                .setApprovalTime(approveImmediately ? LocalDateTime.now() : null)
                .setApprovalReason(approveImmediately ? "HR 代提/补录请假，直接生效" : null);
        attendanceLeaveMapper.insert(attendanceLeave);

        // 3. 发起审批
        // HRM 的业务流程统一采用「手动审批」：请假申请创建后直接进入审批中，
        // 由 HR 在「考勤管理 → 请假记录」里人工点「通过 / 驳回」。
        // 这里不再依赖 Flowable 流程定义，避免尚未发布 hrm_attendance_leave 流程时
        // 员工端发起请假直接报「流程定义不存在」而无法提交。
        // 仍写入 processInstanceId，便于区分来源：MANUAL- 前缀表示人工审批。
        // 取值必须唯一（hrm_attendance_leave.process_instance_id 有唯一索引），
        // 因此带上请假单编号，否则同一员工第二次请假会撞唯一键。
        attendanceLeaveMapper.updateById(new HrmAttendanceLeaveDO().setId(attendanceLeave.getId())
                .setProcessInstanceId(MANUAL_PROCESS_INSTANCE_PREFIX + attendanceLeave.getId()));
        return attendanceLeave.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelLeave(Long userId, HrmAttendanceLeaveCancelReqVO reqVO) {
        // 1. 校验请假申请属于当前员工，且处于审批中
        HrmEmployeeDO employee = employeeService.validateEmployeeBySelf(userId);
        HrmAttendanceLeaveDO attendanceLeave = validateAttendanceLeaveExists(reqVO.getId());
        if (ObjUtil.notEqual(attendanceLeave.getEmployeeId(), employee.getId())) {
            throw exception(ATTENDANCE_LEAVE_NOT_EXISTS);
        }
        if (ObjUtil.notEqual(attendanceLeave.getApprovalStatus(), BpmProcessInstanceStatusEnum.RUNNING.getStatus())
                || StrUtil.isBlank(attendanceLeave.getProcessInstanceId())) {
            throw exception(ATTENDANCE_LEAVE_STATUS_INVALID);
        }

        // 2.1 人工审批的申请直接落终态；工作流审批的申请仍需先取消流程
        if (!isManualApproval(attendanceLeave.getProcessInstanceId())) {
            processInstanceApi.cancelProcessInstanceByStartUser(
                    userId, attendanceLeave.getProcessInstanceId(), reqVO.getReason()).checkError();
        }
        // 2.2 更新请假审批状态
        updateLeaveApprovalStatus(attendanceLeave.getId(), attendanceLeave.getProcessInstanceId(),
                BpmProcessInstanceStatusEnum.CANCEL.getStatus(), reqVO.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveLeave(Long id, Boolean approved, String reason) {
        // 1. 校验请假申请处于审批中
        HrmAttendanceLeaveDO attendanceLeave = validateAttendanceLeaveExists(id);
        if (ObjUtil.notEqual(attendanceLeave.getApprovalStatus(),
                BpmProcessInstanceStatusEnum.RUNNING.getStatus())) {
            throw exception(ATTENDANCE_LEAVE_APPROVE_STATUS_INVALID);
        }
        // 2. 工作流审批的申请不允许在此处人工终审，避免流程实例与业务状态不一致
        if (!isManualApproval(attendanceLeave.getProcessInstanceId())) {
            throw exception(ATTENDANCE_LEAVE_WORKFLOW_APPROVAL);
        }
        boolean pass = BooleanUtil.isTrue(approved);
        if (!pass && StrUtil.isBlank(reason)) {
            throw exception(ATTENDANCE_LEAVE_APPROVAL_REASON_REQUIRED);
        }

        // 3. 写入审批结果
        attendanceLeaveMapper.updateById(new HrmAttendanceLeaveDO().setId(id)
                .setApprovalStatus(pass ? BpmProcessInstanceStatusEnum.APPROVE.getStatus()
                        : BpmProcessInstanceStatusEnum.REJECT.getStatus())
                .setApprovalTime(LocalDateTime.now())
                .setApprovalReason(StrUtil.blankToDefault(reason, "人工审批通过")));
    }

    /** 是否为人工审批（非工作流）产生的请假申请 */
    private boolean isManualApproval(String processInstanceId) {
        return StrUtil.startWith(processInstanceId, MANUAL_PROCESS_INSTANCE_PREFIX);
    }

    @Override
    public List<HrmAttendanceLeaveDO> getMyAttendanceLeaveList(Long userId) {
        return attendanceLeaveMapper.selectListByEmployeeId(employeeService.validateEmployeeBySelf(userId).getId());
    }

    @Override
    public HrmAttendanceLeaveDO getAttendanceLeave(Long id) {
        return attendanceLeaveMapper.selectById(id);
    }

    @Override
    public PageResult<HrmAttendanceLeaveDO> getAttendanceLeavePage(HrmAttendanceLeavePageReqVO reqVO) {
        // 1. 解析员工筛选条件
        Collection<Long> employeeIds = resolveEmployeeIds(reqVO.getEmployeeId(), reqVO.getDeptIds(),
                reqVO.getEmployeeKeyword());
        if (employeeIds != null && CollUtil.isEmpty(employeeIds)) {
            return PageResult.empty();
        }
        reqVO.setEmployeeIds(employeeIds);

        // 2. 查询请假分页
        return attendanceLeaveMapper.selectPage(reqVO);
    }

    @Override
    public List<HrmAttendanceLeaveDO> getAttendanceLeaveList(HrmAttendanceLeavePageReqVO reqVO) {
        // 1. 解析员工筛选条件
        Collection<Long> employeeIds = resolveEmployeeIds(reqVO.getEmployeeId(), reqVO.getDeptIds(),
                reqVO.getEmployeeKeyword());
        if (employeeIds != null && CollUtil.isEmpty(employeeIds)) {
            return Collections.emptyList();
        }
        reqVO.setEmployeeIds(employeeIds);

        // 2. 查询请假列表
        return attendanceLeaveMapper.selectList(reqVO);
    }

    @Override
    public List<HrmAttendanceLeaveDO> getAttendanceLeaveListByEmployeeId(Long employeeId) {
        return attendanceLeaveMapper.selectListByEmployeeId(employeeId);
    }

    @Override
    public List<HrmAttendanceLeaveDO> getEffectiveAttendanceLeaveListByEmployeeId(Long employeeId) {
        return attendanceLeaveMapper.selectListByEmployeeIdAndApprovalStatus(employeeId, BpmProcessInstanceStatusEnum.APPROVE.getStatus());
    }

    @Override
    public List<HrmAttendanceLeaveDO> getEffectiveAttendanceLeaveListByEmployeeIdsAndTimeRange(
            Collection<Long> employeeIds, LocalDateTime[] leaveTimes) {
        if (CollUtil.isEmpty(employeeIds)) {
            return Collections.emptyList();
        }
        return attendanceLeaveMapper.selectListByEmployeeIdsAndTimeRangeAndApprovalStatus(
                employeeIds, leaveTimes, BpmProcessInstanceStatusEnum.APPROVE.getStatus());
    }

    @Override
    public void updateLeaveApprovalStatus(
            Long id, String processInstanceId, Integer status, String reason) {
        // 1. 仅处理当前审批流程的结束状态
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(status)) {
            return;
        }
        HrmAttendanceLeaveDO attendanceLeave = validateAttendanceLeaveExists(id);
        if ((attendanceLeave.getProcessInstanceId() != null
                && ObjUtil.notEqual(attendanceLeave.getProcessInstanceId(), processInstanceId))
                || ObjUtil.notEqual(attendanceLeave.getApprovalStatus(), BpmProcessInstanceStatusEnum.RUNNING.getStatus())) {
            return;
        }

        // 2. 仅允许当前审批中的流程写入一次终态
        attendanceLeaveMapper.updateApprovalResultByIdAndProcessInstanceIdAndApprovalStatus(
                id, processInstanceId, BpmProcessInstanceStatusEnum.RUNNING.getStatus(),
                new HrmAttendanceLeaveDO().setProcessInstanceId(processInstanceId)
                        .setApprovalStatus(status).setApprovalTime(LocalDateTime.now()).setApprovalReason(reason));
    }

    private HrmAttendanceLeaveDO validateAttendanceLeaveExists(Long id) {
        HrmAttendanceLeaveDO attendanceLeave = attendanceLeaveMapper.selectById(id);
        if (attendanceLeave == null) {
            throw exception(ATTENDANCE_LEAVE_NOT_EXISTS);
        }
        return attendanceLeave;
    }

    private Collection<Long> resolveEmployeeIds(
            Long employeeId, List<Long> deptIds, String employeeKeyword) {
        if (employeeId == null && CollUtil.isEmpty(deptIds) && StrUtil.isBlank(employeeKeyword)) {
            return null;
        }
        HrmEmployeeListReqVO employeeReqVO = new HrmEmployeeListReqVO()
                .setIds(employeeId == null ? null : Collections.singletonList(employeeId))
                .setSearch(employeeKeyword).setDeptIds(deptIds);
        return convertList(employeeService.getEmployeeList(employeeReqVO), HrmEmployeeDO::getId);
    }

}
