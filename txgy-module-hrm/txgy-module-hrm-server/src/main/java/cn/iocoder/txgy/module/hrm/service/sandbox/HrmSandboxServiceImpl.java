package cn.iocoder.txgy.module.hrm.service.sandbox;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.module.hrm.controller.admin.attendance.vo.leave.HrmAttendanceLeaveAdminCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.HrmRecruitPostSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.attendance.record.HrmAttendanceLeaveDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.sandbox.HrmSandboxRecordDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.attendance.record.HrmAttendanceLeaveMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.post.HrmRecruitPostMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.sandbox.HrmSandboxRecordMapper;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeListReqVO;
import cn.iocoder.txgy.module.hrm.enums.employee.info.HrmEmployeeEntryStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.recruit.post.HrmRecruitPostStatusEnum;
import cn.iocoder.txgy.module.hrm.service.attendance.record.HrmAttendanceLeaveService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * HRM 沙箱（演示/培训）数据 Service 实现（I-025）
 *
 * @author OPENLAB BS
 */
@Service
@Validated
@Slf4j
public class HrmSandboxServiceImpl implements HrmSandboxService {

    /**
     * 沙箱业务类型
     */
    public static final String BIZ_POST = "post";
    public static final String BIZ_LEAVE = "leave";

    /**
     * 生成来源说明，写入登记表便于人工核对
     */
    private static final String DEMO_SOURCE = "沙箱演示数据（可一键清场）";

    @Resource
    private HrmSandboxRecordMapper sandboxRecordMapper;
    @Resource
    private HrmRecruitPostMapper recruitPostMapper;
    @Resource
    private HrmAttendanceLeaveMapper attendanceLeaveMapper;
    @Resource
    private HrmRecruitPostService recruitPostService;
    @Resource
    private HrmAttendanceLeaveService attendanceLeaveService;
    @Resource
    private HrmEmployeeService employeeService;

    @Override
    public List<HrmSandboxRecordDO> getSandboxRecordList() {
        return sandboxRecordMapper.selectListOrderByIdDesc();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generateDemoScript() {
        int created = 0;
        // 1. 草稿招聘职位（不发布到门户，仅用于演示「新建 → 草稿 → 发布」）
        HrmRecruitPostSaveReqVO postReqVO = new HrmRecruitPostSaveReqVO();
        postReqVO.setPostName("【沙箱演示】" + LocalDate.now() + " 演示职位");
        postReqVO.setJobNature(1);
        postReqVO.setStatus(HrmRecruitPostStatusEnum.DRAFT.getStatus());
        Long postId = recruitPostService.createRecruitPost(postReqVO);
        HrmRecruitPostDO post = recruitPostMapper.selectById(postId);
        sandboxRecordMapper.insert(new HrmSandboxRecordDO()
                .setBizType(BIZ_POST).setBizId(postId)
                .setBizName(post == null ? null : post.getPostName())
                .setDescription(DEMO_SOURCE));
        created++;
        // 2. HR 代提请假（挂在现成在职员工上；没有可用员工时跳过，避免新建账号）
        HrmEmployeeDO employee = findDemoEmployee();
        if (employee != null) {
            LocalDateTime startTime = LocalDate.now().plusDays(1).atTime(9, 0);
            HrmAttendanceLeaveAdminCreateReqVO leaveReqVO = new HrmAttendanceLeaveAdminCreateReqVO();
            leaveReqVO.setEmployeeId(employee.getId());
            leaveReqVO.setType("事假");
            leaveReqVO.setStartTime(startTime);
            leaveReqVO.setEndTime(startTime.withHour(18));
            leaveReqVO.setDay(BigDecimal.ONE);
            leaveReqVO.setReason("【沙箱演示】培训用请假单，可一键清场");
            leaveReqVO.setRemark(DEMO_SOURCE);
            leaveReqVO.setApproveImmediately(true);
            Long leaveId = attendanceLeaveService.createLeaveByHr(leaveReqVO);
            sandboxRecordMapper.insert(new HrmSandboxRecordDO()
                    .setBizType(BIZ_LEAVE).setBizId(leaveId)
                    .setBizName(employee.getName() + " 演示请假")
                    .setDescription(DEMO_SOURCE));
            created++;
        } else {
            log.warn("[generateDemoScript][没有可用的在职员工，跳过演示请假单]");
        }
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearSandbox() {
        List<HrmSandboxRecordDO> records = sandboxRecordMapper.selectListOrderByIdDesc();
        if (CollUtil.isEmpty(records)) {
            return 0;
        }
        int cleared = 0;
        for (HrmSandboxRecordDO record : records) {
            try {
                if (BIZ_POST.equals(record.getBizType())) {
                    recruitPostMapper.deleteById(record.getBizId());
                    cleared++;
                } else if (BIZ_LEAVE.equals(record.getBizType())) {
                    attendanceLeaveMapper.deleteById(record.getBizId());
                    cleared++;
                } else {
                    log.warn("[clearSandbox][未知的沙箱业务类型({})，跳过 bizId({})]",
                            record.getBizType(), record.getBizId());
                }
                // 登记记录一并软删除，清场后可重新登记下一批
                sandboxRecordMapper.deleteById(record.getId());
            } catch (Throwable ex) {
                log.warn("[clearSandbox][清理沙箱数据({}-{})失败]",
                        record.getBizType(), record.getBizId(), ex);
            }
        }
        return cleared;
    }

    /**
     * 选一个在职员工作为演示请假单的挂载对象（跳过自动化测试账号，避免和回归数据混在一起）
     */
    private HrmEmployeeDO findDemoEmployee() {
        return employeeService.getEmployeeList(new HrmEmployeeListReqVO()).stream()
                .filter(item -> item.getId() != null && item.getName() != null
                        && HrmEmployeeEntryStatusEnum.ACTIVE_STATUSES.contains(item.getEntryStatus())
                        && !item.getName().startsWith("E2E"))
                .findFirst().orElse(null);
    }

}
