package cn.iocoder.txgy.module.hrm.controller.admin.employee;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.approval.HrmEmployeeApprovalCallbackReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.approval.HrmEmployeeApprovalPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.approval.HrmEmployeeApprovalStartReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeQuitReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.changerecord.HrmEmployeeTransferReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.approval.HrmEmployeeApprovalDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.approval.HrmEmployeeApprovalMapper;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.employee.HrmSalaryEmployeeInfoDO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.employeeinfo.HrmSalaryEmployeeInfoUpdateReqVO;
import cn.iocoder.txgy.module.hrm.enums.salary.employee.HrmSalaryChangeReasonEnum;
import cn.iocoder.txgy.module.hrm.enums.salary.employee.HrmSalaryChangeRecordTypeEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.approval.HrmEmployeeApprovalTypeEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeQuitTypeEnum;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_APPROVAL_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_APPROVAL_STATUS_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_APPROVAL_TYPE_INVALID;

/**
 * 员工异动审批（手动审批）：发起申请单 → 待审批 → 人工点「通过/驳回」→ 通过后按类型生效。
 *
 * <p>不依赖 Flowable 流程引擎，审批动作由具备该权限的人在页面上完成。</p>
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 员工异动审批（手动）")
@RestController
@RequestMapping("/hrm/employee/approval")
@Validated
@lombok.extern.slf4j.Slf4j
public class HrmEmployeeApprovalRecordController {

    @Resource
    private HrmEmployeeApprovalMapper approvalMapper;
    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private cn.iocoder.txgy.module.hrm.service.employee.employment.HrmEmployeeChangeRecordService employeeChangeRecordService;
    @Resource
    private cn.iocoder.txgy.module.hrm.service.salary.employee.HrmSalaryEmployeeInfoService salaryEmployeeInfoService;

    @PostMapping("/create")
    @Operation(summary = "发起异动审批申请", description = "生成待审批申请单，等待有权限的人审批")
    @PreAuthorize("@ss.hasPermission('hrm:employee:approval:start')")
    public CommonResult<Long> createApproval(@Valid @RequestBody HrmEmployeeApprovalStartReqVO reqVO) {
        HrmEmployeeApprovalTypeEnum type = HrmEmployeeApprovalTypeEnum.valueOf(reqVO.getType());
        if (type == null) {
            throw exception(EMPLOYEE_APPROVAL_TYPE_INVALID);
        }
        HrmEmployeeDO employee = employeeService.validateEmployeeExists(reqVO.getEmployeeId());
        HrmEmployeeApprovalDO approval = HrmEmployeeApprovalDO.builder()
                .employeeId(employee.getId())
                .employeeName(employee.getName())
                .jobNumber(employee.getJobNumber())
                .type(type.getType())
                .reason(reqVO.getReason())
                .effectTime(reqVO.getEffectTime())
                .remark(reqVO.getRemark())
                // 目标值：审批通过后据此真正落地（H-04）
                .targetDeptId(reqVO.getTargetDeptId())
                .targetPostName(reqVO.getTargetPostName())
                .targetPostLevel(reqVO.getTargetPostLevel())
                .targetLeaderEmployeeId(reqVO.getTargetLeaderEmployeeId())
                .targetWorkAddress(reqVO.getTargetWorkAddress())
                .quitType(reqVO.getQuitType())
                .quitReason(reqVO.getQuitReason())
                .salarySettlementTime(reqVO.getSalarySettlementTime())
                .targetBasicSalary(reqVO.getTargetBasicSalary())
                .targetPerformanceSalary(reqVO.getTargetPerformanceSalary())
                .status(1)
                .applied(false)
                .applicantName(cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname())
                .applyTime(LocalDateTime.now())
                .build();
        approvalMapper.insert(approval);
        return success(approval.getId());
    }

    @PutMapping("/approve")
    @Operation(summary = "审批通过/驳回", description = "通过后：转正自动生效；调岗/晋升与离职标记为待人工确认生效")
    @PreAuthorize("@ss.hasPermission('hrm:employee:approval:approve')")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Boolean> approve(@Valid @RequestBody HrmEmployeeApprovalCallbackReqVO reqVO) {
        HrmEmployeeApprovalDO approval = findPending(reqVO);
        boolean approved = Boolean.TRUE.equals(reqVO.getApproved());
        approvalMapper.updateById(new HrmEmployeeApprovalDO().setId(approval.getId())
                .setStatus(approved ? 2 : 3)
                .setApproverName(cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname())
                .setApproveTime(LocalDateTime.now())
                .setApproveComment(reqVO.getComment()));
        // 通过后自动生效：按审批类型真正落地到员工异动（H-04 / H-05）
        if (approved && HrmEmployeeApprovalTypeEnum.REGULAR.getType().equals(approval.getType())) {
            boolean applied = employeeService.applyEmployeeRegular(approval.getEmployeeId());
            approvalMapper.updateById(new HrmEmployeeApprovalDO().setId(approval.getId()).setApplied(applied));
        } else if (approved && HrmEmployeeApprovalTypeEnum.TRANSFER.getType().equals(approval.getType())) {
            // 调岗/晋升：按审批单里的目标任职信息真正发起异动
            boolean applied = applyTransferApproval(approval);
            approvalMapper.updateById(new HrmEmployeeApprovalDO().setId(approval.getId()).setApplied(applied));
            // M-04：填了目标薪资则自动套薪（晋升/调岗与薪资联动，无需 HR 再去手动调薪）
            applySalaryIfPresent(approval);
        } else if (approved && HrmEmployeeApprovalTypeEnum.QUIT.getType().equals(approval.getType())) {
            // 离职：按审批单里的离职类型与结算时间真正办理离职（立即生效会同时禁用账号）
            boolean applied = applyQuitApproval(approval);
            approvalMapper.updateById(new HrmEmployeeApprovalDO().setId(approval.getId()).setApplied(applied));
        }
        return success(true);
    }

    /**
     * 调岗/晋升审批通过后落地：写入异动记录并按生效时间应用（立即生效则同步更新员工主档）
     *
     * @return 是否已实际生效（生效时间在未来时为 false，等定时任务生效）
     */
    /**
     * 晋升/调岗审批通过后自动套薪（M-04：晋升与薪资自动联动）
     *
     * <p>审批单填写了目标基本工资 / 绩效工资时，直接更新员工薪资档案（记录类型=调薪，原因=晋升/调动）；
     * 未填写则不动作，保持原薪资不变。</p>
     */
    private void applySalaryIfPresent(HrmEmployeeApprovalDO approval) {
        if (approval.getTargetBasicSalary() == null && approval.getTargetPerformanceSalary() == null) {
            return;
        }
        HrmSalaryEmployeeInfoDO current =
                salaryEmployeeInfoService.getSalaryEmployeeInfoByEmployeeId(approval.getEmployeeId());
        if (current == null) {
            // 员工还没建薪资档案时跳过自动套薪，避免因为"没建档"把整个审批回滚；
            // HR 可在「薪资管理 → 薪资档案」建档后再调薪（口径：有档案才自动套薪）
            log.warn("[applySalaryIfPresent][员工({})尚无薪资档案，跳过自动套薪，请先在薪资档案建档]",
                    approval.getEmployeeId());
            return;
        }
        List<cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.option.HrmSalaryOptionValueVO> options =
                current.getSalaryOptions() == null ? new ArrayList<>()
                        : BeanUtils.toBean(current.getSalaryOptions(),
                                cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.option.HrmSalaryOptionValueVO.class);
        if (approval.getTargetBasicSalary() != null) {
            upsertSalaryOption(options, 10101, "基本工资", approval.getTargetBasicSalary());
        }
        if (approval.getTargetPerformanceSalary() != null) {
            upsertSalaryOption(options, 30101, "绩效工资", approval.getTargetPerformanceSalary());
        }
        salaryEmployeeInfoService.updateSalaryEmployeeInfo(new HrmSalaryEmployeeInfoUpdateReqVO()
                .setEmployeeId(approval.getEmployeeId())
                .setRecordType(HrmSalaryChangeRecordTypeEnum.SALARY_ADJUSTMENT.getType())
                .setChangeReason(HrmSalaryChangeReasonEnum.PROMOTION.getReason())
                .setEffectTime(approval.getEffectTime() == null ? LocalDateTime.now() : approval.getEffectTime())
                .setRemark("异动审批通过自动套薪：" + StrUtil.blankToDefault(approval.getReason(), ""))
                .setSalaryOptions(options));
    }

    /** 覆盖或追加某个薪资项 */
    private void upsertSalaryOption(
            List<cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.option.HrmSalaryOptionValueVO> options,
            Integer code, String name, java.math.BigDecimal value) {
        options.removeIf(item -> java.util.Objects.equals(item.getCode(), code));
        options.add(new cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.option.HrmSalaryOptionValueVO()
                .setCode(code).setName(name).setValue(value));
    }

    private boolean applyTransferApproval(HrmEmployeeApprovalDO approval) {
        HrmEmployeeTransferReqVO reqVO = new HrmEmployeeTransferReqVO();
        reqVO.setEmployeeId(approval.getEmployeeId());
        reqVO.setNewDeptId(approval.getTargetDeptId());
        reqVO.setNewPostName(approval.getTargetPostName());
        reqVO.setNewPostLevel(approval.getTargetPostLevel());
        reqVO.setNewLeaderEmployeeId(approval.getTargetLeaderEmployeeId());
        reqVO.setNewWorkAddress(approval.getTargetWorkAddress());
        reqVO.setEffectTime(approval.getEffectTime() == null ? LocalDateTime.now() : approval.getEffectTime());
        reqVO.setReason(1);
        reqVO.setRemark("异动审批通过：" + StrUtil.blankToDefault(approval.getReason(), ""));
        Long changeRecordId = employeeService.transferEmployee(reqVO);
        // 异动审计：把"谁批的、什么时候批的"回填到异动台账
        Long approverUserId = cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        employeeChangeRecordService.updateEmployeeChangeRecordApprovalInfo(changeRecordId, approval.getId(),
                approverUserId, approval.getApproverName(),
                approval.getApproveTime() == null ? LocalDateTime.now() : approval.getApproveTime());
        return approval.getEffectTime() == null || !approval.getEffectTime().isAfter(LocalDateTime.now());
    }

    /**
     * 离职审批通过后落地：生成离职单并更新员工状态（立即生效会同时禁用后台账号、回收员工角色）
     *
     * @return 是否已实际离职
     */
    private boolean applyQuitApproval(HrmEmployeeApprovalDO approval) {
        LocalDateTime planQuitTime = approval.getEffectTime() == null ? LocalDateTime.now() : approval.getEffectTime();
        HrmEmployeeQuitReqVO reqVO = new HrmEmployeeQuitReqVO();
        reqVO.setEmployeeId(approval.getEmployeeId());
        reqVO.setPlanQuitTime(planQuitTime);
        reqVO.setApplyQuitTime(LocalDateTime.now());
        reqVO.setSalarySettlementTime(approval.getSalarySettlementTime() == null
                ? planQuitTime : approval.getSalarySettlementTime());
        reqVO.setType(approval.getQuitType() == null
                ? HrmEmployeeQuitTypeEnum.VOLUNTARY.getType() : approval.getQuitType());
        reqVO.setReason(approval.getQuitReason());
        reqVO.setRemark("异动审批通过：" + StrUtil.blankToDefault(approval.getReason(), ""));
        employeeService.quitEmployee(reqVO);
        return !planQuitTime.isAfter(LocalDateTime.now());
    }

    @GetMapping("/page")
    @Operation(summary = "获得异动审批分页")
    @PreAuthorize("@ss.hasPermission('hrm:employee:approval:start')")
    public CommonResult<PageResult<HrmEmployeeApprovalDO>> getApprovalPage(
            @Valid HrmEmployeeApprovalPageReqVO pageReqVO) {
        return success(approvalMapper.selectPage(pageReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得异动审批详情")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:employee:approval:start')")
    public CommonResult<HrmEmployeeApprovalDO> getApproval(@RequestParam("id") Long id) {
        return success(approvalMapper.selectById(id));
    }

    private HrmEmployeeApprovalDO findPending(HrmEmployeeApprovalCallbackReqVO reqVO) {
        if (reqVO.getId() == null) {
            if (reqVO.getEmployeeId() == null || reqVO.getType() == null) {
                // 既没传申请单编号、也没传员工+类型，无法定位待审批单
                throw exception(EMPLOYEE_APPROVAL_NOT_EXISTS);
            }
            // 兼容按"员工 + 类型"审批：取该员工该类型最新一条待审批
            PageResult<HrmEmployeeApprovalDO> page = approvalMapper.selectPage(
                    new HrmEmployeeApprovalPageReqVO() {{
                        setPageNo(1);
                        setPageSize(1);
                        setEmployeeId(reqVO.getEmployeeId());
                        setType(reqVO.getType());
                        setStatus(1);
                    }});
            if (page.getList().isEmpty()) {
                throw exception(EMPLOYEE_APPROVAL_NOT_EXISTS);
            }
            return page.getList().get(0);
        }
        HrmEmployeeApprovalDO approval = approvalMapper.selectById(reqVO.getId());
        if (approval == null) {
            throw exception(EMPLOYEE_APPROVAL_NOT_EXISTS);
        }
        if (!Integer.valueOf(1).equals(approval.getStatus())) {
            throw exception(EMPLOYEE_APPROVAL_STATUS_INVALID);
        }
        return approval;
    }

}
