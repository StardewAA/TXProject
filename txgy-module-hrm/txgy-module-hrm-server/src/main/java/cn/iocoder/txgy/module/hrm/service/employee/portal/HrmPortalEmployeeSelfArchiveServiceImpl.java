package cn.iocoder.txgy.module.hrm.service.employee.portal;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.educationexperience.HrmEmployeeEducationExperienceSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.workexperience.HrmEmployeeWorkExperienceSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.employee.vo.HrmPortalEmployeeEducationExperienceSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.employee.vo.HrmPortalEmployeeWorkExperienceSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeEducationExperienceDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeWorkExperienceDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.enums.MessageTemplateConstants;
import cn.iocoder.txgy.module.hrm.service.employee.experience.HrmEmployeeEducationExperienceService;
import cn.iocoder.txgy.module.hrm.service.employee.experience.HrmEmployeeWorkExperienceService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_RESOURCE_BELONG_INVALID;

/**
 * 员工端「我的档案」自助维护 Service 实现
 *
 * @author OPENLAB BS
 */
@Service
@Validated
@Slf4j
public class HrmPortalEmployeeSelfArchiveServiceImpl implements HrmPortalEmployeeSelfArchiveService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private HrmEmployeeEducationExperienceService educationExperienceService;
    @Resource
    private HrmEmployeeWorkExperienceService workExperienceService;
    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    // ==================== 教育经历 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createEducation(Long userId, HrmPortalEmployeeEducationExperienceSaveReqVO reqVO) {
        HrmEmployeeDO employee = employeeService.validateEmployeeBySelf(userId);
        // 归属强制：入参 VO 不含 employeeId，由服务端强制为登录人本人的员工档案
        HrmEmployeeEducationExperienceSaveReqVO saveReqVO =
                BeanUtils.toBean(reqVO, HrmEmployeeEducationExperienceSaveReqVO.class);
        saveReqVO.setId(null);
        saveReqVO.setEmployeeId(employee.getId());
        Long id = educationExperienceService.createEducationExperience(saveReqVO);
        notifyArchiveUpdated(employee.getId(), "教育经历", "新增");
        return id;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEducation(Long userId, HrmPortalEmployeeEducationExperienceSaveReqVO reqVO) {
        HrmEmployeeDO employee = employeeService.validateEmployeeBySelf(userId);
        HrmEmployeeEducationExperienceDO existing = reqVO.getId() == null ? null
                : educationExperienceService.validateEducationExperienceExists(reqVO.getId());
        if (existing == null || ObjUtil.notEqual(existing.getEmployeeId(), employee.getId())) {
            throw exception(EMPLOYEE_RESOURCE_BELONG_INVALID);
        }
        HrmEmployeeEducationExperienceSaveReqVO saveReqVO =
                BeanUtils.toBean(reqVO, HrmEmployeeEducationExperienceSaveReqVO.class);
        saveReqVO.setEmployeeId(employee.getId());
        educationExperienceService.updateEducationExperience(saveReqVO);
        notifyArchiveUpdated(employee.getId(), "教育经历", "修改");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEducation(Long userId, Long id) {
        HrmEmployeeDO employee = employeeService.validateEmployeeBySelf(userId);
        HrmEmployeeEducationExperienceDO existing = educationExperienceService.validateEducationExperienceExists(id);
        if (ObjUtil.notEqual(existing.getEmployeeId(), employee.getId())) {
            throw exception(EMPLOYEE_RESOURCE_BELONG_INVALID);
        }
        educationExperienceService.deleteEducationExperience(id);
        notifyArchiveUpdated(employee.getId(), "教育经历", "删除");
    }

    // ==================== 工作经历 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createWork(Long userId, HrmPortalEmployeeWorkExperienceSaveReqVO reqVO) {
        HrmEmployeeDO employee = employeeService.validateEmployeeBySelf(userId);
        HrmEmployeeWorkExperienceSaveReqVO saveReqVO =
                BeanUtils.toBean(reqVO, HrmEmployeeWorkExperienceSaveReqVO.class);
        saveReqVO.setId(null);
        saveReqVO.setEmployeeId(employee.getId());
        Long id = workExperienceService.createWorkExperience(saveReqVO);
        notifyArchiveUpdated(employee.getId(), "工作经历", "新增");
        return id;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWork(Long userId, HrmPortalEmployeeWorkExperienceSaveReqVO reqVO) {
        HrmEmployeeDO employee = employeeService.validateEmployeeBySelf(userId);
        HrmEmployeeWorkExperienceDO existing = reqVO.getId() == null ? null
                : workExperienceService.validateWorkExperienceExists(reqVO.getId());
        if (existing == null || ObjUtil.notEqual(existing.getEmployeeId(), employee.getId())) {
            throw exception(EMPLOYEE_RESOURCE_BELONG_INVALID);
        }
        HrmEmployeeWorkExperienceSaveReqVO saveReqVO =
                BeanUtils.toBean(reqVO, HrmEmployeeWorkExperienceSaveReqVO.class);
        saveReqVO.setEmployeeId(employee.getId());
        workExperienceService.updateWorkExperience(saveReqVO);
        notifyArchiveUpdated(employee.getId(), "工作经历", "修改");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWork(Long userId, Long id) {
        HrmEmployeeDO employee = employeeService.validateEmployeeBySelf(userId);
        HrmEmployeeWorkExperienceDO existing = workExperienceService.validateWorkExperienceExists(id);
        if (ObjUtil.notEqual(existing.getEmployeeId(), employee.getId())) {
            throw exception(EMPLOYEE_RESOURCE_BELONG_INVALID);
        }
        workExperienceService.deleteWorkExperience(id);
        notifyArchiveUpdated(employee.getId(), "工作经历", "删除");
    }

    // ==================== 留痕 + 提醒 ====================

    @Override
    public void notifyArchiveUpdated(Long employeeId, String changeType, String changeAction) {
        HrmEmployeeDO employee = employeeService.getEmployee(employeeId);
        if (employee == null) {
            return;
        }
        // 1. 员工档案备注留痕：HR 在后台打开该员工即可看到"员工自助更新待复核"
        try {
            String stamp = "【系统】" + LocalDateTime.now().format(TIME_FORMATTER) + " 员工自助"
                    + changeAction + changeType + "，请 HR 复核";
            String remark = StrUtil.maxLength(StrUtil.blankToDefault(employee.getRemark(), "") + stamp, 480);
            employeeService.updateEmployeeRemark(employee.getId(), remark);
        } catch (Throwable ex) {
            log.warn("[notifyArchiveUpdated][员工({}) 档案备注留痕失败]", employeeId, ex);
        }
        // 2. 提醒直属上级（有绑定后台账号时）
        if (employee.getLeaderEmployeeId() == null) {
            return;
        }
        try {
            HrmEmployeeDO leader = employeeService.getEmployee(employee.getLeaderEmployeeId());
            if (leader == null || leader.getUserId() == null) {
                return;
            }
            Map<String, Object> params = new HashMap<>(4);
            params.put("employeeName", StrUtil.blankToDefault(employee.getName(), "员工"));
            params.put("changeType", changeType);
            params.put("changeAction", changeAction);
            notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                    .setUserId(leader.getUserId())
                    .setTemplateCode(MessageTemplateConstants.EMPLOYEE_ARCHIVE_UPDATED)
                    .setTemplateParams(params));
        } catch (Throwable ex) {
            log.warn("[notifyArchiveUpdated][员工({}) 提醒直属上级失败]", employeeId, ex);
        }
    }

}
