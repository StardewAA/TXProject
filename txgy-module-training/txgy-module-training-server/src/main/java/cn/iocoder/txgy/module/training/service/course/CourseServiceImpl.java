package cn.iocoder.txgy.module.training.service.course;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.*;
import cn.iocoder.txgy.module.training.dal.dataobject.course.*;
import cn.iocoder.txgy.module.training.dal.mysql.course.*;
import cn.iocoder.txgy.module.training.enums.course.CourseMediaTypeEnum;
import cn.iocoder.txgy.module.training.enums.course.CourseStatusEnum;
import cn.iocoder.txgy.module.training.service.course.external.CourseExternalApiService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.*;

/**
 * 课程管理 Service 实现类
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class CourseServiceImpl implements CourseService {
    @Resource private cn.iocoder.txgy.module.training.service.live.LiveCenterService liveCenter;

    @Resource
    private CourseMapper courseMapper;
    @Resource
    private CourseMediaMapper courseMediaMapper;
    @Resource
    private CourseAuditLogMapper courseAuditLogMapper;
    @Resource
    private CourseCommentMapper courseCommentMapper;
    @Resource
    private CourseExternalApiService externalApiService;

    // ========== 课程 CRUD ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCourse(CourseSaveReqVO createReqVO) {
        liveCenter.checkCourseCreation();
        CourseDO course = BeanUtils.toBean(createReqVO, CourseDO.class);
        course.setStatus(CourseStatusEnum.DRAFT.getStatus());
        course.setVersion(1);
        course.setIsCurrent(true);
        course.setMediaType(0);
        course.setMediaCount(0);
        course.setCommentCount(0);
        course.setAuditCount(0);
        course.setSort(course.getSort() == null ? 0 : course.getSort());
        courseMapper.insert(course);
        // 回填版本链根 ID（同族根 = 自身）
        course.setRootId(course.getId());
        courseMapper.updateById(course);
        return course.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCourse(CourseSaveReqVO updateReqVO) {
        CourseDO course = validateCourseExists(updateReqVO.getId());
        // 状态机：仅【草稿】可编辑（提交后需先撤审）
        if (!CourseStatusEnum.DRAFT.getStatus().equals(course.getStatus())) {
            throw exception(COURSE_STATUS_NOT_ALLOW_UPDATE, getStatusName(course.getStatus()));
        }
        CourseDO updateObj = BeanUtils.toBean(updateReqVO, CourseDO.class);
        courseMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourse(Long id) {
        CourseDO course = validateCourseExists(id);
        if (!CourseStatusEnum.DRAFT.getStatus().equals(course.getStatus())) {
            throw exception(COURSE_STATUS_NOT_ALLOW_DELETE, getStatusName(course.getStatus()));
        }
        // 生命周期保护：已被学员学习引用的课程禁止删除（占位对接模块五，返回 409）
        if (externalApiService.hasLearningRef(id)) {
            throw exception(COURSE_DELETE_HAS_LEARNING_REF);
        }
        // 若同版本族内存在历史上架/审核记录，仅删除当前草稿；否则整族删除
        List<CourseDO> family = courseMapper.selectList(new cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX<CourseDO>()
                .eq(CourseDO::getRootId, course.getRootId()));
        boolean hasPublished = family.stream().anyMatch(item ->
                !CourseStatusEnum.DRAFT.getStatus().equals(item.getStatus()));
        if (hasPublished) {
            courseMapper.deleteById(id);
        } else {
            family.forEach(item -> courseMapper.deleteById(item.getId()));
        }
    }

    @Override
    public PageResult<CourseDO> getCoursePage(CoursePageReqVO pageReqVO) {
        return courseMapper.selectPage(pageReqVO);
    }

    @Override
    public CourseDO getCourse(Long id) {
        return courseMapper.selectById(id);
    }

    // ========== 审核状态机 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long courseId) {
        CourseDO course = validateCourseCurrent(courseId);
        if (!CourseStatusEnum.DRAFT.getStatus().equals(course.getStatus())) {
            throw exception(COURSE_STATUS_NOT_ALLOW_SUBMIT, getStatusName(course.getStatus()));
        }
        CourseDO update = new CourseDO();
        update.setId(course.getId());
        update.setStatus(CourseStatusEnum.PENDING_REVIEW.getStatus());
        update.setSubmitterId(SecurityFrameworkUtils.getLoginUserId());
        update.setSubmittedAt(LocalDateTime.now());
        courseMapper.updateById(update);
        insertAuditLog(course.getId(), "SUBMIT", course.getStatus(),
                CourseStatusEnum.PENDING_REVIEW.getStatus(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long courseId) {
        CourseDO course = validateCourseCurrent(courseId);
        if (!CourseStatusEnum.PENDING_REVIEW.getStatus().equals(course.getStatus())) {
            throw exception(COURSE_STATUS_NOT_ALLOW_WITHDRAW, getStatusName(course.getStatus()));
        }
        CourseDO update = new CourseDO();
        update.setId(course.getId());
        update.setStatus(CourseStatusEnum.DRAFT.getStatus());
        courseMapper.updateById(update);
        insertAuditLog(course.getId(), "WITHDRAW", course.getStatus(),
                CourseStatusEnum.DRAFT.getStatus(), "提交人撤审");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long courseId) {
        CourseDO course = validateCourseCurrent(courseId);
        if (!CourseStatusEnum.PENDING_REVIEW.getStatus().equals(course.getStatus())) {
            throw exception(COURSE_STATUS_NOT_ALLOW_APPROVE, getStatusName(course.getStatus()));
        }
        CourseDO update = new CourseDO();
        update.setId(course.getId());
        update.setStatus(CourseStatusEnum.APPROVED.getStatus());
        update.setLastAuditTime(LocalDateTime.now());
        update.setAuditCount((course.getAuditCount() == null ? 0 : course.getAuditCount()) + 1);
        // 审核完成人/时间留痕（24 审核流细化）
        update.setReviewerId(SecurityFrameworkUtils.getLoginUserId());
        update.setReviewedAt(LocalDateTime.now());
        // 审核通过后课程编码锁定：自动生成一次
        if (StrUtil.isBlank(course.getCode())) {
            update.setCode("TC" + course.getId());
        }
        courseMapper.updateById(update);
        insertAuditLog(course.getId(), "APPROVE", course.getStatus(),
                CourseStatusEnum.APPROVED.getStatus(), "审核通过");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long courseId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw exception(COURSE_REJECT_REASON_REQUIRED);
        }
        CourseDO course = validateCourseCurrent(courseId);
        if (!CourseStatusEnum.PENDING_REVIEW.getStatus().equals(course.getStatus())) {
            throw exception(COURSE_STATUS_NOT_ALLOW_REJECT, getStatusName(course.getStatus()));
        }
        CourseDO update = new CourseDO();
        update.setId(course.getId());
        update.setStatus(CourseStatusEnum.DRAFT.getStatus());
        update.setRejectReason(reason);
        update.setAuditCount((course.getAuditCount() == null ? 0 : course.getAuditCount()) + 1);
        update.setReviewerId(SecurityFrameworkUtils.getLoginUserId());
        update.setReviewedAt(LocalDateTime.now());
        courseMapper.updateById(update);
        insertAuditLog(course.getId(), "REJECT", course.getStatus(),
                CourseStatusEnum.DRAFT.getStatus(), reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void online(Long courseId) {
        CourseDO course = validateCourseCurrent(courseId);
        if (!CourseStatusEnum.APPROVED.getStatus().equals(course.getStatus())) {
            throw exception(COURSE_STATUS_NOT_ALLOW_ONLINE, getStatusName(course.getStatus()));
        }
        // 上架校验：必须已绑定至少一份媒资
        Long mediaCount = courseMediaMapper.selectCountByCourseId(courseId);
        if (mediaCount == null || mediaCount <= 0) {
            throw exception(COURSE_MEDIA_REQUIRED);
        }
        CourseDO update = new CourseDO();
        update.setId(course.getId());
        update.setStatus(CourseStatusEnum.ON_SHELF.getStatus());
        update.setLastAuditTime(LocalDateTime.now());
        courseMapper.updateById(update);
        insertAuditLog(course.getId(), "ONLINE", course.getStatus(),
                CourseStatusEnum.ON_SHELF.getStatus(), "上架");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long courseId) {
        CourseDO course = validateCourseCurrent(courseId);
        if (!CourseStatusEnum.ON_SHELF.getStatus().equals(course.getStatus())) {
            throw exception(COURSE_STATUS_NOT_ALLOW_OFFLINE, getStatusName(course.getStatus()));
        }
        CourseDO update = new CourseDO();
        update.setId(course.getId());
        update.setStatus(CourseStatusEnum.OFF_SHELF.getStatus());
        update.setLastAuditTime(LocalDateTime.now());
        courseMapper.updateById(update);
        insertAuditLog(course.getId(), "OFFLINE", course.getStatus(),
                CourseStatusEnum.OFF_SHELF.getStatus(), "下架");
    }

    // ========== 版本管理 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publishNewVersion(Long courseId) {
        CourseDO course = validateCourseCurrent(courseId);
        boolean allowPublish = CourseStatusEnum.ON_SHELF.getStatus().equals(course.getStatus())
                || CourseStatusEnum.OFF_SHELF.getStatus().equals(course.getStatus())
                || CourseStatusEnum.APPROVED.getStatus().equals(course.getStatus());
        if (!allowPublish) {
            throw exception(COURSE_STATUS_NOT_ALLOW_PUBLISH, getStatusName(course.getStatus()));
        }
        // 1. 旧版本标记为非当前，保留历史数据
        CourseDO oldUpdate = new CourseDO();
        oldUpdate.setId(course.getId());
        oldUpdate.setIsCurrent(false);
        courseMapper.updateById(oldUpdate);
        // 2. 复制生成新版本（状态回草稿，需重新走审核）
        CourseDO newCourse = BeanUtils.toBean(course, CourseDO.class);
        newCourse.setId(null);
        newCourse.setVersion((course.getVersion() == null ? 0 : course.getVersion()) + 1);
        // 版本链根兜底：老数据 root_id 可能为空，此时以原课程自身作为族根，
        // 否则新版本 root_id 为空会导致「版本管理」查询不到任何版本
        newCourse.setRootId(course.getRootId() == null ? course.getId() : course.getRootId());
        newCourse.setIsCurrent(true);
        newCourse.setStatus(CourseStatusEnum.DRAFT.getStatus());
        newCourse.setCommentCount(0);
        newCourse.setAuditCount(0);
        newCourse.setRejectReason(null);
        newCourse.setSubmitterId(null);
        newCourse.setSubmittedAt(null);
        newCourse.setLastAuditTime(null);
        newCourse.setCreateTime(null);
        newCourse.setUpdateTime(null);
        courseMapper.insert(newCourse);
        // 3. 复制媒资绑定到新版本
        List<CourseMediaDO> medias = courseMediaMapper.selectListByCourseId(course.getId());
        for (CourseMediaDO media : medias) {
            CourseMediaDO copy = BeanUtils.toBean(media, CourseMediaDO.class);
            copy.setId(null);
            copy.setCourseId(newCourse.getId());
            copy.setCreateTime(null);
            copy.setUpdateTime(null);
            courseMediaMapper.insert(copy);
        }
        refreshCourseMediaStat(newCourse.getId());
        insertAuditLog(course.getId(), "PUBLISH", course.getStatus(),
                CourseStatusEnum.DRAFT.getStatus(), "发布新版本 v" + newCourse.getVersion()
                        + "，新版本课程 ID：" + newCourse.getId());
        return newCourse.getId();
    }

    @Override
    public List<CourseDO> getVersionList(Long rootId) {
        CourseDO course = validateCourseExists(rootId);
        Long actualRootId = course.getRootId() == null ? course.getId() : course.getRootId();
        return courseMapper.selectList(new cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX<CourseDO>()
                .eq(CourseDO::getRootId, actualRootId)
                .orderByDesc(CourseDO::getVersion));
    }

    // ========== 媒资绑定 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMedia(CourseMediaSaveReqVO createReqVO) {
        validateCourseEditable(createReqVO.getCourseId());
        validateMediaType(createReqVO.getMediaType());
        // 外部资源引用校验（占位：VR 项目编号 / URL 规则，对接资源中心后增强）
        externalApiService.validateMediaReference(
                createReqVO.getMediaType(), createReqVO.getUrl(), createReqVO.getMediaRefId());
        // 自含媒资同 URL 不允许重复绑定（仅 URL 媒资参与去重）
        if (StrUtil.isNotBlank(createReqVO.getUrl())
                && courseMediaMapper.selectByUrl(createReqVO.getCourseId(), createReqVO.getUrl()) != null) {
            throw exception(COURSE_MEDIA_URL_DUPLICATE);
        }
        CourseMediaDO media = BeanUtils.toBean(createReqVO, CourseMediaDO.class);
        media.setSort(media.getSort() == null ? 0 : media.getSort());
        normalizeMediaUrl(media);
        courseMediaMapper.insert(media);
        refreshCourseMediaStat(createReqVO.getCourseId());
        return media.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMedia(CourseMediaSaveReqVO updateReqVO) {
        CourseMediaDO media = validateMediaExists(updateReqVO.getId());
        validateCourseEditable(media.getCourseId());
        validateMediaType(updateReqVO.getMediaType());
        // 外部资源引用校验（占位）
        externalApiService.validateMediaReference(
                updateReqVO.getMediaType(), updateReqVO.getUrl(), updateReqVO.getMediaRefId());
        // 排除自身后的 URL 去重（仅 URL 媒资参与去重）
        if (StrUtil.isNotBlank(updateReqVO.getUrl())) {
            CourseMediaDO same = courseMediaMapper.selectByUrl(media.getCourseId(), updateReqVO.getUrl());
            if (same != null && !same.getId().equals(updateReqVO.getId())) {
                throw exception(COURSE_MEDIA_URL_DUPLICATE);
            }
        }
        CourseMediaDO updateObj = BeanUtils.toBean(updateReqVO, CourseMediaDO.class);
        normalizeMediaUrl(updateObj);
        courseMediaMapper.updateById(updateObj);
        refreshCourseMediaStat(media.getCourseId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMedia(Long id) {
        CourseMediaDO media = validateMediaExists(id);
        // 解绑仅解除课程与素材的关联关系，不删除资源中心原始素材；
        // 与新增/修改保持一致：仅草稿态且为当前版本的课程允许调整媒资
        validateCourseEditable(media.getCourseId());
        courseMediaMapper.deleteById(id);
        refreshCourseMediaStat(media.getCourseId());
    }

    @Override
    public List<CourseMediaDO> getMediaList(Long courseId) {
        return courseMediaMapper.selectListByCourseId(courseId);
    }

    // ========== 课程评论 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createComment(CourseCommentSaveReqVO createReqVO) {
        CourseDO course = validateCourseExists(createReqVO.getCourseId());
        // 仅已上架课程可评论
        if (!CourseStatusEnum.ON_SHELF.getStatus().equals(course.getStatus())) {
            throw exception(COURSE_ONLY_ONLINE_CAN_COMMENT);
        }
        // 回复场景：父评论必须存在且属于同一课程
        Long parentId = createReqVO.getParentId() == null ? 0L : createReqVO.getParentId();
        if (parentId > 0) {
            CourseCommentDO parent = courseCommentMapper.selectById(parentId);
            if (parent == null || !parent.getCourseId().equals(createReqVO.getCourseId())) {
                throw exception(COURSE_COMMENT_NOT_EXISTS);
            }
        }
        CourseCommentDO comment = BeanUtils.toBean(createReqVO, CourseCommentDO.class);
        comment.setParentId(parentId);
        comment.setUserId(SecurityFrameworkUtils.getLoginUserId());
        comment.setUserName(SecurityFrameworkUtils.getLoginUserNickname());
        courseCommentMapper.insert(comment);
        // 评论数冗余 +1
        incrCommentCount(course.getId(), course.getCommentCount() == null ? 0 : course.getCommentCount(), 1);
        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long id, String reason) {
        CourseCommentDO comment = validateCommentDeletable(id);
        softDeleteComment(comment, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMyComment(Long id) {
        CourseCommentDO comment = validateCommentDeletable(id);
        // 学员仅能删除自己的评论
        if (comment.getUserId() != null
                && !comment.getUserId().equals(SecurityFrameworkUtils.getLoginUserId())) {
            throw exception(COURSE_COMMENT_DELETE_NOT_ALLOW);
        }
        softDeleteComment(comment, "学员删除");
    }

    // ========== 业务属性配置（23 课程业务属性配置） ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBusinessAttributes(Long id, CourseAttributesReqVO attrReqVO) {
        CourseDO course = validateCourseExists(id);
        // 业务属性仅允许在草稿态（当前生效版本）配置，与课程编辑约束一致
        if (!Boolean.TRUE.equals(course.getIsCurrent())
                || !CourseStatusEnum.DRAFT.getStatus().equals(course.getStatus())) {
            throw exception(COURSE_STATUS_NOT_ALLOW_UPDATE, getStatusName(course.getStatus()));
        }
        CourseDO update = new CourseDO();
        update.setId(id);
        // 成长等级/学分/时长/行业/岗位/归属/标签/必修/复合/结课考试（考试占位模块三）
        update.setCourseLevel(attrReqVO.getCourseLevel());
        update.setCredit(attrReqVO.getCredit());
        update.setDurationMinutes(attrReqVO.getDurationMinutes());
        update.setIndustry(attrReqVO.getIndustry());
        update.setApplicablePosts(attrReqVO.getApplicablePosts());
        update.setOwnerFlag(attrReqVO.getOwnerFlag());
        update.setBizTags(attrReqVO.getBizTags());
        update.setIsRequired(attrReqVO.getIsRequired());
        update.setIsComposite(attrReqVO.getIsComposite());
        update.setCompositeTags(attrReqVO.getCompositeTags());
        update.setExamId(attrReqVO.getExamId());
        courseMapper.updateById(update);
        insertAuditLog(course.getId(), "UPDATE_ATTR", course.getStatus(), course.getStatus(), "业务属性配置");
    }

    /**
     * VR/3D 仿真媒资无访问地址，统一落库为空串（DB 列 NOT NULL）
     */
    private void normalizeMediaUrl(CourseMediaDO media) {
        if (CourseMediaTypeEnum.VR.getType().equals(media.getMediaType()) && media.getUrl() == null) {
            media.setUrl("");
        }
    }

    // ========== 媒资排序（22 媒资绑定） ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortMedia(Long courseId, List<Long> mediaIds) {
        validateCourseEditable(courseId);
        if (mediaIds == null || mediaIds.isEmpty()) {
            return;
        }
        int sort = 0;
        for (Long mediaId : mediaIds) {
            if (mediaId == null) {
                continue;
            }
            CourseMediaDO update = new CourseMediaDO();
            update.setId(mediaId);
            update.setSort(sort++);
            courseMediaMapper.updateById(update);
        }
    }

    // ========== 评论治理（26 课程评论管理） ==========

    private CourseCommentDO validateCommentDeletable(Long id) {
        CourseCommentDO comment = courseCommentMapper.selectById(id);
        if (comment == null) {
            throw exception(COURSE_COMMENT_NOT_EXISTS);
        }
        if (Integer.valueOf(2).equals(comment.getStatus())) {
            throw exception(COURSE_COMMENT_ALREADY_DELETED);
        }
        return comment;
    }

    /**
     * 软删除评论：原文保留（status=2）+ 删除人/时间/原因留痕，可见评论数 -1
     */
    private void softDeleteComment(CourseCommentDO comment, String reason) {
        CourseCommentDO update = new CourseCommentDO();
        update.setId(comment.getId());
        update.setStatus(2);
        update.setDeletedBy(SecurityFrameworkUtils.getLoginUserId());
        update.setDeletedAt(LocalDateTime.now());
        update.setDeleteReason(reason);
        courseCommentMapper.updateById(update);
        CourseDO course = courseMapper.selectById(comment.getCourseId());
        incrCommentCount(comment.getCourseId(),
                course == null || course.getCommentCount() == null ? 0 : course.getCommentCount(), -1);
    }

    @Override
    public PageResult<CourseCommentDO> getCommentPage(CourseCommentPageReqVO pageReqVO) {
        return courseCommentMapper.selectPage(pageReqVO);
    }

    // ========== 审核日志 ==========

    @Override
    public PageResult<CourseAuditLogDO> getAuditLogPage(CourseAuditLogPageReqVO pageReqVO) {
        return courseAuditLogMapper.selectPage(pageReqVO);
    }

    // ========== 私有方法 ==========

    private CourseDO validateCourseExists(Long id) {
        CourseDO course = courseMapper.selectById(id);
        if (course == null) {
            throw exception(COURSE_NOT_EXISTS);
        }
        return course;
    }

    /**
     * 校验课程存在且为当前生效版本
     */
    private CourseDO validateCourseCurrent(Long id) {
        CourseDO course = validateCourseExists(id);
        if (!Boolean.TRUE.equals(course.getIsCurrent())) {
            throw exception(COURSE_NOT_CURRENT_VERSION);
        }
        return course;
    }

    /**
     * 校验课程存在且处于可编辑状态（草稿）
     */
    private void validateCourseEditable(Long courseId) {
        CourseDO course = validateCourseExists(courseId);
        if (!Boolean.TRUE.equals(course.getIsCurrent())
                || !CourseStatusEnum.DRAFT.getStatus().equals(course.getStatus())) {
            throw exception(COURSE_STATUS_NOT_ALLOW_UPDATE, getStatusName(course.getStatus()));
        }
    }

    private CourseMediaDO validateMediaExists(Long id) {
        CourseMediaDO media = courseMediaMapper.selectById(id);
        if (media == null) {
            throw exception(COURSE_MEDIA_NOT_EXISTS);
        }
        return media;
    }

    private void validateMediaType(Integer mediaType) {
        if (CourseMediaTypeEnum.getByType(mediaType) == null) {
            throw exception(COURSE_MEDIA_TYPE_INVALID);
        }
    }

    /**
     * 刷新课程内容类型与媒资数量冗余字段
     * 规则：含图片，或同时含文档与视频 → 图文(3)；仅视频 → 视频(2)；仅文档 → 文档(1)；无媒资 → 0
     */
    private void refreshCourseMediaStat(Long courseId) {
        List<CourseMediaDO> medias = courseMediaMapper.selectListByCourseId(courseId);
        Set<Integer> types = new HashSet<>();
        boolean hasDoc = false;
        boolean hasVideo = false;
        boolean hasImage = false;
        for (CourseMediaDO media : medias) {
            types.add(media.getMediaType());
            if (CourseMediaTypeEnum.DOCUMENT.getType().equals(media.getMediaType())) {
                hasDoc = true;
            } else if (CourseMediaTypeEnum.VIDEO.getType().equals(media.getMediaType())) {
                hasVideo = true;
            } else if (CourseMediaTypeEnum.IMAGE.getType().equals(media.getMediaType())) {
                hasImage = true;
            }
        }
        int aggregated;
        if (medias.isEmpty()) {
            aggregated = 0;
        } else if (hasImage || (hasDoc && hasVideo)) {
            aggregated = 3;
        } else if (hasVideo) {
            aggregated = 2;
        } else {
            aggregated = 1;
        }
        CourseDO update = new CourseDO();
        update.setId(courseId);
        update.setMediaType(aggregated);
        update.setMediaCount(medias.size());
        courseMapper.updateById(update);
    }

    private void incrCommentCount(Long courseId, int current, int delta) {
        CourseDO update = new CourseDO();
        update.setId(courseId);
        update.setCommentCount(Math.max(0, current + delta));
        courseMapper.updateById(update);
    }

    private void insertAuditLog(Long courseId, String action, Integer fromStatus, Integer toStatus, String reason) {
        CourseAuditLogDO log = new CourseAuditLogDO();
        log.setCourseId(courseId);
        log.setAction(action);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperatorId(SecurityFrameworkUtils.getLoginUserId());
        log.setOperatorName(SecurityFrameworkUtils.getLoginUserNickname());
        log.setReason(reason);
        courseAuditLogMapper.insert(log);
    }

    private String getStatusName(Integer status) {
        CourseStatusEnum statusEnum = CourseStatusEnum.getByStatus(status);
        return statusEnum == null ? String.valueOf(status) : statusEnum.getName();
    }

}
