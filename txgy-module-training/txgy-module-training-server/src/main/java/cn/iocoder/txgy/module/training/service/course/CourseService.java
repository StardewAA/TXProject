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

    // ========== 媒资绑定 ==========

    Long createMedia(CourseMediaSaveReqVO createReqVO);

    void updateMedia(CourseMediaSaveReqVO updateReqVO);

    void deleteMedia(Long id);

    List<CourseMediaDO> getMediaList(Long courseId);

    // ========== 课程评论 ==========

    Long createComment(CourseCommentSaveReqVO createReqVO);

    void deleteComment(Long id);

    PageResult<CourseCommentDO> getCommentPage(CourseCommentPageReqVO pageReqVO);

    // ========== 审核日志 ==========

    PageResult<CourseAuditLogDO> getAuditLogPage(CourseAuditLogPageReqVO pageReqVO);

}
