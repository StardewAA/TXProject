package cn.iocoder.txgy.module.training.service.course;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.*;
import cn.iocoder.txgy.module.training.dal.dataobject.course.*;

import java.util.List;

/**
 * 课程管理 Service 接口
 *
 * 覆盖：课程 CRUD、状态机（提交/撤审/通过/驳回/上架/下架）、
 * 版本链（发布新版本/版本历史）、媒资绑定、评论治理
 *
 * @author 云培训学院课题小组
 */
public interface CourseService {

    // ========== 课程 CRUD ==========

    Long createCourse(CourseSaveReqVO createReqVO);

    void updateCourse(CourseSaveReqVO updateReqVO);

    void deleteCourse(Long id);

    PageResult<CourseDO> getCoursePage(CoursePageReqVO pageReqVO);

    CourseDO getCourse(Long id);

    /**
     * 课程结课考试计划列表（供模块三「考试与认证」展示当前租户的课程结课考试）
     *
     * @return 已配置结课考试试卷的当前生效版本课程
     */
    List<CourseDO> getExamPlanList();

    // ========== 审核状态机 ==========

    void submit(Long courseId);

    void withdraw(Long courseId);

    void approve(Long courseId);

    void reject(Long courseId, String reason);

    void online(Long courseId);

    void offline(Long courseId);

    // ========== 版本管理 ==========

    Long publishNewVersion(Long courseId);

    List<CourseDO> getVersionList(Long rootId);

    // ========== 业务属性配置（23 课程业务属性配置） ==========

    void updateBusinessAttributes(Long id, CourseAttributesReqVO attrReqVO);

    // ========== 媒资绑定 ==========

    Long createMedia(CourseMediaSaveReqVO createReqVO);

    void updateMedia(CourseMediaSaveReqVO updateReqVO);

    void deleteMedia(Long id);

    /**
     * 媒资批量排序（按传入顺序重排）
     */
    void sortMedia(Long courseId, List<Long> mediaIds);

    List<CourseMediaDO> getMediaList(Long courseId);

    // ========== 课程评论 ==========

    Long createComment(CourseCommentSaveReqVO createReqVO);

    /**
     * 评论治理删除（软删留痕，管理端）
     *
     * @param id     评论 ID
     * @param reason 删除原因（可选）
     */
    void deleteComment(Long id, String reason);

    /**
     * 删除自己的评论（学员端，校验评论归属）
     */
    void deleteMyComment(Long id);

    PageResult<CourseCommentDO> getCommentPage(CourseCommentPageReqVO pageReqVO);

    // ========== 审核日志 ==========

    PageResult<CourseAuditLogDO> getAuditLogPage(CourseAuditLogPageReqVO pageReqVO);

}
