package cn.iocoder.txgy.module.hrm.service.employee.portal;

import cn.iocoder.txgy.module.hrm.controller.admin.portal.employee.vo.HrmPortalEmployeeEducationExperienceSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.employee.vo.HrmPortalEmployeeWorkExperienceSaveReqVO;

/**
 * 员工端「我的档案」自助维护 Service
 *
 * <p>把「员工自己补档案 → 后台同一条记录立即可见」闭环补全：</p>
 * <ol>
 *   <li>写库前强制归属：只能维护本人的教育/工作经历（不接受前端传 employeeId）；</li>
 *   <li>写库后给员工的直属上级发站内信，并在员工档案备注里留痕，提示 HR 复核。</li>
 * </ol>
 *
 * @author OPENLAB BS
 */
public interface HrmPortalEmployeeSelfArchiveService {

    Long createEducation(Long userId, HrmPortalEmployeeEducationExperienceSaveReqVO reqVO);

    void updateEducation(Long userId, HrmPortalEmployeeEducationExperienceSaveReqVO reqVO);

    void deleteEducation(Long userId, Long id);

    Long createWork(Long userId, HrmPortalEmployeeWorkExperienceSaveReqVO reqVO);

    void updateWork(Long userId, HrmPortalEmployeeWorkExperienceSaveReqVO reqVO);

    void deleteWork(Long userId, Long id);

    /**
     * 员工自助更新档案后的留痕 + 提醒（基础信息更新与子档案增删改共用）
     *
     * @param employeeId   员工编号
     * @param changeType   变更内容，如「基本信息」「教育经历」「工作经历」
     * @param changeAction 动作，如「新增」「修改」「删除」
     */
    void notifyArchiveUpdated(Long employeeId, String changeType, String changeAction);

}
