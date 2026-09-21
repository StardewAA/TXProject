package cn.iocoder.txgy.module.hrm.controller.admin.employee;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.approval.HrmEmployeeApprovalCallbackReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.approval.HrmEmployeeApprovalPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.approval.HrmEmployeeApprovalStartReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.approval.HrmEmployeeApprovalDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.approval.HrmEmployeeApprovalMapper;
import cn.iocoder.txgy.module.hrm.enums.employee.approval.HrmEmployeeApprovalTypeEnum;
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

import java.time.LocalDateTime;

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
public class HrmEmployeeApprovalRecordController {

    @Resource
    private HrmEmployeeApprovalMapper approvalMapper;
    @Resource
    private HrmEmployeeService employeeService;

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
    public CommonResult<Boolean> approve(@Valid @RequestBody HrmEmployeeApprovalCallbackReqVO reqVO) {
        HrmEmployeeApprovalDO approval = findPending(reqVO);
        boolean approved = Boolean.TRUE.equals(reqVO.getApproved());
        approvalMapper.updateById(new HrmEmployeeApprovalDO().setId(approval.getId())
                .setStatus(approved ? 2 : 3)
                .setApproverName(cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname())
                .setApproveTime(LocalDateTime.now())
                .setApproveComment(reqVO.getComment()));
        // 通过后自动生效：转正可直接生效
        if (approved && HrmEmployeeApprovalTypeEnum.REGULAR.getType().equals(approval.getType())) {
            boolean applied = employeeService.applyEmployeeRegular(approval.getEmployeeId());
            approvalMapper.updateById(new HrmEmployeeApprovalDO().setId(approval.getId()).setApplied(applied));
        }
        return success(true);
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
