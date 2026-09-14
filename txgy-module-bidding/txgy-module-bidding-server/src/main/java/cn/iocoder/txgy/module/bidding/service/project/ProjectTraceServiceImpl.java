package cn.iocoder.txgy.module.bidding.service.project;

// [规范依据]《招投标模块开发规范》§4.4（跨域聚合由服务端完成，禁止公开面 LIKE 检索）
//           + 数据规范 V2.0 编号体系（PROJECT_CODE / TENDER_PROJECT_CODE / BID_SECTION_CODE 三级关联）
//           + 主体域资格口径（docs/模块设计与规范/主体信息与授权-设计说明.md：授权 + 档案完善）
// [业务目标] "一个项目追溯到全部信息"：项目主档 → 招标项目 → 标段 → 内部公告 / 外部公告 / 备案 / 关注 / 参与主体 + 统计
// [为什么] 六域数据分散（bidding_notice / notice_external / filing / follow_item / subject_* / project 三档），
//           若前端逐域调用需 6+ 请求且要自行推断关联，易遗漏；服务端按编号集合精确命中并统一口径（外部公告标段用 FIND_IN_SET 精确匹配编号，非模糊 LIKE）

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.project.vo.*;
import cn.iocoder.txgy.module.bidding.dal.dataobject.filing.FilingDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.FollowItemDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.BidSectionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.ProjectDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.TenderProjectDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectAuthDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectProfileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.filing.FilingMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.notice.NoticeMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticefollow.FollowItemMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.BidSectionMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.ProjectMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.TenderProjectMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectAuthMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectProfileMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.NoticeTypeEnum;
import cn.iocoder.txgy.module.bidding.service.subject.SubjectCompleteness;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProjectTraceServiceImpl implements ProjectTraceService {

    /** 单域返回上限（追溯页面只展示最近动态，防大项目拖垮查询） */
    private static final int DOMAIN_LIMIT = 200;

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private TenderProjectMapper tenderProjectMapper;
    @Resource
    private BidSectionMapper bidSectionMapper;
    @Resource
    private NoticeMapper noticeMapper;
    @Resource
    private NoticeExternalMapper noticeExternalMapper;
    @Resource
    private FilingMapper filingMapper;
    @Resource
    private FollowItemMapper followItemMapper;
    @Resource
    private SubjectAuthMapper subjectAuthMapper;
    @Resource
    private SubjectProfileMapper subjectProfileMapper;

    @Override
    public ProjectTraceRespVO traceByTenderProject(String tenderProjectCode) {
        TenderProjectDO tender = selectTender(tenderProjectCode);
        if (tender == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.TENDER_PROJECT_NOT_EXISTS);
        }
        return aggregate(tenderProjectCode, "TENDER_PROJECT", tender.getProjectCode(), List.of(tender));
    }

    @Override
    public ProjectTraceRespVO traceByProject(String projectCode) {
        ProjectDO project = selectProject(projectCode);
        if (project == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROJECT_NOT_EXISTS);
        }
        List<TenderProjectDO> tenders = tenderProjectMapper.selectList(new LambdaQueryWrapper<TenderProjectDO>()
                .eq(TenderProjectDO::getProjectCode, projectCode));
        return aggregate(projectCode, "PROJECT", projectCode, tenders);
    }

    @Override
    public ProjectTraceRespVO traceByBidSection(String bidSectionCode) {
        BidSectionDO section = bidSectionMapper.selectOne(new LambdaQueryWrapper<BidSectionDO>()
                .eq(BidSectionDO::getBidSectionCode, bidSectionCode).last("LIMIT 1"));
        if (section == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_SECTION_NOT_EXISTS);
        }
        TenderProjectDO tender = selectTender(section.getTenderProjectCode());
        List<TenderProjectDO> tenders = tender == null ? List.of()
                : tenderProjectMapper.selectList(new LambdaQueryWrapper<TenderProjectDO>()
                .eq(TenderProjectDO::getProjectCode, tender.getProjectCode()));
        ProjectTraceRespVO vo = aggregate(bidSectionCode, "BID_SECTION",
                tender == null ? null : tender.getProjectCode(), tenders);
        return vo;
    }

    // ==================== 聚合 ====================

    private ProjectTraceRespVO aggregate(String queryCode, String queryType, String projectCode, List<TenderProjectDO> tenders) {
        ProjectTraceRespVO vo = new ProjectTraceRespVO();
        vo.setQueryCode(queryCode);
        vo.setQueryType(queryType);
        // 项目主档（未挂接项目时为 null，属正常态：招标项目可先于项目登记）
        ProjectDO project = StringUtils.hasText(projectCode) ? selectProject(projectCode) : null;
        vo.setProject(project == null ? null : ProjectServiceImpl.toProjectResp(project));

        List<TenderProjectDO> tenderList = tenders == null ? List.of() : tenders;
        vo.setTenderProjects(tenderList.stream().map(ProjectServiceImpl::toTenderResp).collect(Collectors.toList()));

        Set<String> tenderCodes = tenderList.stream().map(TenderProjectDO::getTenderProjectCode)
                .filter(StringUtils::hasText).collect(Collectors.toCollection(LinkedHashSet::new));
        List<BidSectionDO> sections = tenderCodes.isEmpty() ? List.of()
                : bidSectionMapper.selectList(new LambdaQueryWrapper<BidSectionDO>()
                .in(BidSectionDO::getTenderProjectCode, tenderCodes).orderByAsc(BidSectionDO::getBidSectionCode));
        vo.setSections(sections.stream().map(ProjectServiceImpl::toSectionResp).collect(Collectors.toList()));

        Set<String> sectionCodes = sections.stream().map(BidSectionDO::getBidSectionCode)
                .filter(StringUtils::hasText).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> projectCodes = new LinkedHashSet<>();
        if (StringUtils.hasText(projectCode)) {
            projectCodes.add(projectCode);
        }

        // ---- 内部公告（T 轨，本租户；NoticeDO 仅存 project_code，故按项目编号命中）----
        List<NoticeDO> notices = projectCodes.isEmpty() ? List.of()
                : noticeMapper.selectList(new LambdaQueryWrapper<NoticeDO>()
                .in(NoticeDO::getProjectCode, projectCodes)
                .orderByDesc(NoticeDO::getPublishTime).orderByDesc(NoticeDO::getId)
                .last("LIMIT " + DOMAIN_LIMIT));
        vo.setNotices(notices.stream().map(this::toNoticeItem).collect(Collectors.toList()));

        // ---- 外部公告（G 轨全平台；按项目/招标项目编号，或标段编号在 BID_SECTION_CODES 串中精确命中）----
        List<NoticeExternalDO> externals = externalsByCodes(projectCodes, tenderCodes, sectionCodes);
        vo.setExternalNotices(externals.stream().map(this::toExternalItem).collect(Collectors.toList()));

        // ---- 备案（T 轨）----
        List<FilingDO> filings = (projectCodes.isEmpty() && tenderCodes.isEmpty()) ? List.of()
                : filingMapper.selectList(new LambdaQueryWrapper<FilingDO>()
                .and(w -> {
                    if (!projectCodes.isEmpty()) {
                        w.in(FilingDO::getProjectCode, projectCodes);
                        if (!tenderCodes.isEmpty()) {
                            w.or();
                        }
                    }
                    if (!tenderCodes.isEmpty()) {
                        w.in(FilingDO::getTenderProjectCode, tenderCodes);
                    }
                })
                .orderByDesc(FilingDO::getId).last("LIMIT " + DOMAIN_LIMIT));
        vo.setFilings(filings.stream().map(this::toFilingItem).collect(Collectors.toList()));

        // ---- 关注（当前登录用户的关注项；键=项目/招标项目/标段编号）----
        Set<String> followKeys = new LinkedHashSet<>();
        followKeys.addAll(projectCodes);
        followKeys.addAll(tenderCodes);
        followKeys.addAll(sectionCodes);
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        List<FollowItemDO> follows = (followKeys.isEmpty() || userId == null) ? List.of()
                : followItemMapper.selectList(new LambdaQueryWrapper<FollowItemDO>()
                .eq(FollowItemDO::getUserId, userId)
                .in(FollowItemDO::getFollowKey, followKeys)
                .orderByDesc(FollowItemDO::getLatestDynamicTime).last("LIMIT " + DOMAIN_LIMIT));
        vo.setFollows(follows.stream().map(this::toFollowItem).collect(Collectors.toList()));

        // ---- 参与主体：招标人（招标项目主档快照）+ 本租户平台授权主体（授权状态 + 档案完善度）----
        vo.setSubjects(subjects(tenderList));

        // ---- 统计与最近动态 ----
        ProjectTraceRespVO.Stats stats = new ProjectTraceRespVO.Stats();
        stats.setTenderProjectCount(tenderList.size());
        stats.setSectionCount(sections.size());
        stats.setNoticeCount(notices.size());
        stats.setExternalNoticeCount(externals.size());
        stats.setFilingCount(filings.size());
        stats.setFollowCount(follows.size());
        stats.setLatestDynamicTime(latestTime(notices, externals, follows));
        vo.setStats(stats);
        return vo;
    }

    /** 外部公告命中：项目/招标项目编号相等，或（标段维度）标段编号出现在 BID_SECTION_CODES 逗号串中（FIND_IN_SET 精确匹配，非模糊 LIKE） */
    private List<NoticeExternalDO> externalsByCodes(Set<String> projectCodes, Set<String> tenderCodes, Set<String> sectionCodes) {
        if (projectCodes.isEmpty() && tenderCodes.isEmpty() && sectionCodes.isEmpty()) {
            return List.of();
        }
        return noticeExternalMapper.selectList(new LambdaQueryWrapper<NoticeExternalDO>()
                .and(w -> {
                    boolean first = true;
                    if (!projectCodes.isEmpty()) {
                        w.in(NoticeExternalDO::getProjectCode, projectCodes);
                        first = false;
                    }
                    if (!tenderCodes.isEmpty()) {
                        if (!first) {
                            w.or();
                        }
                        w.in(NoticeExternalDO::getTenderProjectCode, tenderCodes);
                        first = false;
                    }
                    for (String sectionCode : sectionCodes) {
                        if (!first) {
                            w.or();
                        }
                        w.apply("FIND_IN_SET({0}, bid_section_codes)", sectionCode);
                        first = false;
                    }
                })
                .orderByDesc(NoticeExternalDO::getPublishTime).orderByDesc(NoticeExternalDO::getId)
                .last("LIMIT " + DOMAIN_LIMIT));
    }

    private List<ProjectTraceRespVO.SubjectItem> subjects(List<TenderProjectDO> tenders) {
        List<ProjectTraceRespVO.SubjectItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TenderProjectDO tender : tenders) {
            if (!StringUtils.hasText(tender.getTendererName())) {
                continue;
            }
            String key = tender.getTendererCode() == null ? tender.getTendererName() : tender.getTendererCode();
            if (!seen.add(key)) {
                continue;
            }
            ProjectTraceRespVO.SubjectItem item = new ProjectTraceRespVO.SubjectItem();
            item.setSource("TENDER_PROJECT_SNAPSHOT");
            item.setSubjectRole("TENDERER");
            item.setSubjectName(tender.getTendererName());
            item.setCodeType(tender.getTendererCodeType());
            item.setCode(tender.getTendererCode());
            items.add(item);
        }
        // 本租户平台授权主体（租户拦截器自动限定当前租户）：授权状态 + 档案完善度（口径见主体信息与授权-设计说明）
        List<SubjectAuthDO> auths = subjectAuthMapper.selectList(new LambdaQueryWrapper<>());
        for (SubjectAuthDO auth : auths) {
            ProjectTraceRespVO.SubjectItem item = new ProjectTraceRespVO.SubjectItem();
            item.setSource("TENANT_AUTH");
            item.setSubjectRole(auth.getSubjectType());
            item.setSubjectName(auth.getTenantName());
            item.setAuthStatus(auth.getAuthStatus());
            SubjectProfileDO profile = subjectProfileMapper.selectOne(new LambdaQueryWrapper<SubjectProfileDO>()
                    .eq(SubjectProfileDO::getSubjectType, auth.getSubjectType()).last("LIMIT 1"));
            if (profile != null) {
                item.setSubjectName(profile.getSubjectName());
                item.setCode(profile.getUscc());
                item.setProfileComplete(SubjectCompleteness.assess(profile).complete);
            } else {
                item.setProfileComplete(false);
            }
            items.add(item);
        }
        return items;
    }

    private Long latestTime(List<NoticeDO> notices, List<NoticeExternalDO> externals, List<FollowItemDO> follows) {
        Long latest = null;
        for (NoticeDO n : notices) {
            latest = max(latest, ProjectServiceImpl.epoch(n.getPublishTime()));
        }
        for (NoticeExternalDO n : externals) {
            latest = max(latest, ProjectServiceImpl.epoch(n.getPublishTime()));
        }
        for (FollowItemDO f : follows) {
            latest = max(latest, ProjectServiceImpl.epoch(f.getLatestDynamicTime()));
        }
        return latest;
    }

    private static Long max(Long a, Long b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Math.max(a, b);
    }

    // ==================== 子项转换 ====================

    private ProjectTraceRespVO.NoticeItem toNoticeItem(NoticeDO n) {
        ProjectTraceRespVO.NoticeItem item = new ProjectTraceRespVO.NoticeItem();
        item.setId(n.getId());
        item.setNoticeCode(n.getNoticeCode());
        item.setNoticeType(n.getNoticeType());
        NoticeTypeEnum type = NoticeTypeEnum.of(n.getNoticeType());
        item.setNoticeTypeName(type == null ? n.getNoticeType() : type.getLabel());
        item.setTitle(n.getTitle());
        item.setStatus(n.getStatus());
        item.setProjectCode(n.getProjectCode());
        item.setPublishTime(ProjectServiceImpl.epoch(n.getPublishTime()));
        return item;
    }

    private ProjectTraceRespVO.ExternalNoticeItem toExternalItem(NoticeExternalDO n) {
        ProjectTraceRespVO.ExternalNoticeItem item = new ProjectTraceRespVO.ExternalNoticeItem();
        item.setId(n.getId());
        item.setTitle(n.getTitle());
        item.setSourceCode(n.getSourceCode());
        item.setSourceName(n.getSourceName());
        item.setContentType(n.getContentType());
        item.setStatus(n.getStatus());
        item.setAuditStatus(n.getAuditStatus());
        item.setOrigUrl(n.getOrigUrl());
        item.setProjectCode(n.getProjectCode());
        item.setTenderProjectCode(n.getTenderProjectCode());
        item.setBidSectionCodes(n.getBidSectionCodes());
        item.setPublishTime(ProjectServiceImpl.epoch(n.getPublishTime()));
        item.setAvailable("ACTIVE".equals(n.getStatus()));
        return item;
    }

    private ProjectTraceRespVO.FilingItem toFilingItem(FilingDO f) {
        ProjectTraceRespVO.FilingItem item = new ProjectTraceRespVO.FilingItem();
        item.setId(f.getId());
        item.setProjectCode(f.getProjectCode());
        item.setTenderProjectCode(f.getTenderProjectCode());
        item.setStatus(f.getStatus());
        item.setSubmitTime(ProjectServiceImpl.epoch(f.getSubmitTime()));
        return item;
    }

    private ProjectTraceRespVO.FollowItem toFollowItem(FollowItemDO f) {
        ProjectTraceRespVO.FollowItem item = new ProjectTraceRespVO.FollowItem();
        item.setId(f.getId());
        item.setFollowType(f.getFollowType());
        item.setFollowKey(f.getFollowKey());
        item.setDisplayName(f.getDisplayName());
        item.setFollowStatus(f.getFollowStatus());
        item.setLatestDynamicTime(ProjectServiceImpl.epoch(f.getLatestDynamicTime()));
        item.setUnreadCount(f.getUnreadCount());
        return item;
    }

    private ProjectDO selectProject(String projectCode) {
        return StringUtils.hasText(projectCode) ? projectMapper.selectOne(new LambdaQueryWrapper<ProjectDO>()
                .eq(ProjectDO::getProjectCode, projectCode).last("LIMIT 1")) : null;
    }

    private TenderProjectDO selectTender(String tenderProjectCode) {
        return StringUtils.hasText(tenderProjectCode) ? tenderProjectMapper.selectOne(new LambdaQueryWrapper<TenderProjectDO>()
                .eq(TenderProjectDO::getTenderProjectCode, tenderProjectCode).last("LIMIT 1")) : null;
    }
}
