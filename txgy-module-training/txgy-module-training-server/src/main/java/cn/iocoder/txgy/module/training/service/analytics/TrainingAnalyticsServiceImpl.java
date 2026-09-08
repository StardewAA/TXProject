package cn.iocoder.txgy.module.training.service.analytics;

import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.analytics.vo.AnalyticsDashboardRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseDO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseMediaDO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.DocumentResourceDO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.OperationLogDO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.QuestionDO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.ResourceCategoryDO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.VideoResourceDO;
import cn.iocoder.txgy.module.training.dal.mysql.course.CourseAuditLogMapper;
import cn.iocoder.txgy.module.training.dal.mysql.course.CourseCommentMapper;
import cn.iocoder.txgy.module.training.dal.mysql.course.CourseMapper;
import cn.iocoder.txgy.module.training.dal.mysql.course.CourseMediaMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.DocumentResourceMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.OperationLogMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.QuestionMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.ResourceCategoryMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.VideoResourceMapper;
import cn.iocoder.txgy.module.training.enums.course.CourseStatusEnum;
import cn.iocoder.txgy.module.training.enums.resource.ResourceStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;

/**
 * 数据统计与档案归档 - 统计看板 Service 实现
 *
 * 全部指标基于现有培训表内存/条件聚合，不引入新业务表。
 *
 * @author 云培训学院课题小组
 */
@Service
@Validated
public class TrainingAnalyticsServiceImpl implements TrainingAnalyticsService {

    /** 操作日志查询上限（避免全表加载） */
    private static final int RECENT_LOG_LIMIT = 10;

    @Resource
    private CourseMapper courseMapper;
    @Resource
    private CourseMediaMapper courseMediaMapper;
    @Resource
    private CourseCommentMapper courseCommentMapper;
    @Resource
    private CourseAuditLogMapper courseAuditLogMapper;
    @Resource
    private DocumentResourceMapper documentResourceMapper;
    @Resource
    private VideoResourceMapper videoResourceMapper;
    @Resource
    private QuestionMapper questionMapper;
    @Resource
    private OperationLogMapper operationLogMapper;
    @Resource
    private ResourceCategoryMapper resourceCategoryMapper;

    @Override
    public AnalyticsDashboardRespVO getDashboard() {
        AnalyticsDashboardRespVO respVO = new AnalyticsDashboardRespVO();
        respVO.setCourse(buildCourseStats());
        respVO.setResource(buildResourceStats());
        respVO.setActivity(buildActivityStats());
        respVO.setOperationTrend7d(buildOperationTrend7d());
        respVO.setDocumentCategoryDistribution(buildDocumentCategoryDistribution());
        respVO.setRecentLogs(buildRecentLogs());
        return respVO;
    }

    private AnalyticsDashboardRespVO.CourseStats buildCourseStats() {
        AnalyticsDashboardRespVO.CourseStats stats = new AnalyticsDashboardRespVO.CourseStats();
        stats.setTotal(courseMapper.selectCount(null));
        stats.setDraft(courseMapper.selectCount(byStatus(CourseStatusEnum.DRAFT.getStatus())));
        stats.setPendingReview(courseMapper.selectCount(byStatus(CourseStatusEnum.PENDING_REVIEW.getStatus())));
        stats.setApproved(courseMapper.selectCount(byStatus(CourseStatusEnum.APPROVED.getStatus())));
        stats.setOnShelf(courseMapper.selectCount(byStatus(CourseStatusEnum.ON_SHELF.getStatus())));
        stats.setOffShelf(courseMapper.selectCount(byStatus(CourseStatusEnum.OFF_SHELF.getStatus())));
        stats.setMediaTotal(courseMediaMapper.selectCount(null));
        return stats;
    }

    private LambdaQueryWrapperX<CourseDO> byStatus(Integer status) {
        return new LambdaQueryWrapperX<CourseDO>().eq(CourseDO::getStatus, status);
    }

    private AnalyticsDashboardRespVO.ResourceStats buildResourceStats() {
        AnalyticsDashboardRespVO.ResourceStats stats = new AnalyticsDashboardRespVO.ResourceStats();
        stats.setDocumentTotal(documentResourceMapper.selectCount(null));
        stats.setDocumentOnline(documentResourceMapper.selectCount(
                new LambdaQueryWrapperX<DocumentResourceDO>()
                        .eq(DocumentResourceDO::getStatus, ResourceStatusEnum.ONLINE.getStatus())));
        stats.setVideoTotal(videoResourceMapper.selectCount(null));
        stats.setVideoOnline(videoResourceMapper.selectCount(
                new LambdaQueryWrapperX<VideoResourceDO>()
                        .eq(VideoResourceDO::getStatus, ResourceStatusEnum.ONLINE.getStatus())));
        stats.setQuestionTotal(questionMapper.selectCount(null));
        return stats;
    }

    private AnalyticsDashboardRespVO.ActivityStats buildActivityStats() {
        AnalyticsDashboardRespVO.ActivityStats stats = new AnalyticsDashboardRespVO.ActivityStats();
        stats.setCommentTotal(courseCommentMapper.selectCount(null));
        stats.setAuditLogTotal(courseAuditLogMapper.selectCount(null));
        stats.setOperationLogTotal(operationLogMapper.selectCount(null));
        LocalDateTime sevenDaysAgo = LocalDate.now().minusDays(6).atStartOfDay();
        stats.setOperationLogLast7d(operationLogMapper.selectCount(
                new LambdaQueryWrapperX<OperationLogDO>()
                        .ge(OperationLogDO::getCreateTime, sevenDaysAgo)));
        return stats;
    }

    private List<Map<String, Object>> buildOperationTrend7d() {
        LocalDateTime sevenDaysAgo = LocalDate.now().minusDays(6).atStartOfDay();
        List<OperationLogDO> logs = operationLogMapper.selectList(
                new LambdaQueryWrapperX<OperationLogDO>()
                        .ge(OperationLogDO::getCreateTime, sevenDaysAgo)
                        .orderByDesc(OperationLogDO::getCreateTime));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        Map<String, Long> countByDate = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            countByDate.put(LocalDate.now().minusDays(i).format(formatter), 0L);
        }
        logs.forEach(log -> {
            if (log.getCreateTime() != null) {
                String key = log.getCreateTime().toLocalDate().format(formatter);
                countByDate.merge(key, 1L, Long::sum);
            }
        });
        List<Map<String, Object>> trend = new ArrayList<>(countByDate.size());
        countByDate.forEach((date, count) -> {
            Map<String, Object> item = new LinkedHashMap<>(2);
            item.put("date", date);
            item.put("count", count);
            trend.add(item);
        });
        return trend;
    }

    private List<Map<String, Object>> buildDocumentCategoryDistribution() {
        List<DocumentResourceDO> documents = documentResourceMapper.selectList(null);
        List<ResourceCategoryDO> categories = resourceCategoryMapper.selectList(null);
        Map<Long, String> categoryNameMap = convertMap(categories,
                ResourceCategoryDO::getId, ResourceCategoryDO::getName);
        Map<String, Long> countByCategory = new LinkedHashMap<>();
        documents.forEach(doc -> {
            String name = doc.getCategoryId() == null ? "未分类"
                    : categoryNameMap.getOrDefault(doc.getCategoryId(), "未知分类");
            countByCategory.merge(name, 1L, Long::sum);
        });
        List<Map<String, Object>> distribution = new ArrayList<>(countByCategory.size());
        countByCategory.forEach((name, count) -> {
            Map<String, Object> item = new LinkedHashMap<>(2);
            item.put("category", name);
            item.put("count", count);
            distribution.add(item);
        });
        return distribution;
    }

    private List<AnalyticsDashboardRespVO.RecentLogItem> buildRecentLogs() {
        List<OperationLogDO> logs = operationLogMapper.selectList(
                new LambdaQueryWrapperX<OperationLogDO>()
                        .orderByDesc(OperationLogDO::getCreateTime)
                        .last("LIMIT " + RECENT_LOG_LIMIT));
        List<AnalyticsDashboardRespVO.RecentLogItem> items = new ArrayList<>(logs.size());
        logs.forEach(log -> {
            AnalyticsDashboardRespVO.RecentLogItem item = new AnalyticsDashboardRespVO.RecentLogItem();
            item.setId(log.getId());
            item.setUserName(log.getUserName());
            item.setAction(log.getAction());
            item.setTargetType(log.getTargetType());
            item.setCreateTime(log.getCreateTime());
            items.add(item);
        });
        return items;
    }
}
