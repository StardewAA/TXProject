package cn.iocoder.txgy.module.bidding.service.noticeexternal;
// 规范依据：设计文档 v0.6 §7/§15.1/§15.3（SOCIAL+ACTIVE 过滤、ES 检索事件预留、不开放业务 LIKE）；
// 《招投标模块开发规范》§4.4、红线 8；01（公开边界）

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalFilterOptionsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManageDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManagePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManageRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalAttachmentDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalAttachmentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalAuditStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalContentTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalStatusEnum;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.indexer.ExternalNoticeIndexNotifier;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.sanitize.HtmlSanitizer;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.search.ExternalNoticeSearchEngine;
import cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.UrlHostGuard;
import cn.iocoder.txgy.module.bidding.service.noticefollow.FollowService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 外部公告（G 轨聚合主档）服务实现（对外只读展示切片）。
 */
@Slf4j
@Service
public class NoticeExternalServiceImpl implements NoticeExternalService {

    /** 公开范围常量：社会公开（01 公开级别对齐 PublicLevelEnum.SOCIAL） */
    private static final String PUBLIC_SCOPE_SOCIAL = "SOCIAL";

    /** 外部公告扇出源标识（关注/提醒侧 source） */
    private static final String SOURCE_EXTERNAL = "EXTERNAL";

    /**
     * 关键词检索的**基础**取回条数（首屏/浅翻页够用，且不让 IN 条件过度膨胀）。
     *
     * <p>注意：这不是"命中总数上限"——用户翻到后面时按需扩大，见 {@link #resolveMatchLimit}。
     */
    private static final int KEYWORD_MATCH_LIMIT = 200;

    /**
     * 关键词检索的**取回硬上限**（安全阀：防止 pageNo 极大时把整库 id 灌进 IN 条件）。
     *
     * <p>与 ES {@code index.max_result_window}（默认 10000）保持同一量级；本模块公告量级为万级以内，
     * 该上限足以覆盖全量。若未来公告数逼近此值，应改为 search_after 游标分页（见类尾 TODO）。
     */
    private static final int MAX_KEYWORD_MATCH_LIMIT = 10000;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private NoticeExternalMapper noticeExternalMapper;
    @Resource
    private NoticeExternalAttachmentMapper noticeExternalAttachmentMapper;
    @Resource
    private FollowService followService;
    @Resource
    private ExternalNoticeIndexNotifier externalNoticeIndexNotifier;
    @Resource
    private ExternalNoticeSearchEngine externalNoticeSearchEngine;
    @Resource
    private cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.HttpFetchClient httpFetchClient;
    @Resource
    private cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper crawlSourceMapper;
    @Resource
    private cn.iocoder.txgy.module.infra.api.file.FileApi fileApi;
    /** 原文下载限速闸：批量回填 PDF 属"从目标站点下载公告原文文件"，同样受合规频控约束 */
    @Resource
    private cn.iocoder.txgy.module.bidding.service.externalfetch.throttle.CrawlDownloadGate crawlDownloadGate;
    /** robots 合规检查：在线预览若回退外站直链，同样属于"抓取目标站点内容" */
    @Resource
    private cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.RobotsGuard robotsGuard;

    @Override
    public void approveExternal(Long id) {
        // 存在性先查（错误码区分），再条件更新防丢失更新：并发下 reject/approve 谁先提交谁生效（审核操作竞态安全）
        NoticeExternalDO pre = noticeExternalMapper.selectById(id);
        if (pre == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS);
        }
        // 幂等：已生效（ACTIVE+APPROVED）直接返回；非待审核态不重放（恢复入口=同 URL 重新采集进审核，见 ingest 重投语义）
        if (NoticeExternalStatusEnum.ACTIVE.getStatus().equals(pre.getStatus())
                && NoticeExternalAuditStatusEnum.APPROVED.getStatus().equals(pre.getAuditStatus())) {
            return;
        }
        NoticeExternalDO patch = new NoticeExternalDO();
        patch.setStatus(NoticeExternalStatusEnum.ACTIVE.getStatus());
        patch.setAuditStatus(NoticeExternalAuditStatusEnum.APPROVED.getStatus());
        int rows = noticeExternalMapper.update(patch, new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getId, id)
                .eq(NoticeExternalDO::getStatus, NoticeExternalStatusEnum.NEW.getStatus())
                .eq(NoticeExternalDO::getAuditStatus, NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus()));
        if (rows == 0) {
            // 条件不满足：已被并发置为其它状态 → 拒绝覆盖（读到的旧快照不参与写回）
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_STATE_INVALID);
        }
        NoticeExternalDO notice = noticeExternalMapper.selectById(id);
        // 扇出关注动态 + ES 索引事件：均为旁路通知，失败不阻断审核。
        // 注意：设计 §7.6 的补偿扫描任务尚未实现——以下失败仅记日志，需人工复核后重放（幂等 PUT/扇出语义保证重放安全）
        try {
            followService.handleNoticeActivated(notice, SOURCE_EXTERNAL);
        } catch (Exception e) {
            log.error("[approveExternal][noticeId={}] 关注扇出失败（不影响审核；补偿扫描未实现，需人工复核重放，见设计 §7.6）", id, e);
        }
        try {
            externalNoticeIndexNotifier.indexNotice(notice);
        } catch (Exception e) {
            log.error("[approveExternal][noticeId={}] ES 索引事件失败（不影响审核；补偿扫描未实现，需人工复核重放，见设计 §7.6）", id, e);
        }
    }

    @Override
    public void rejectExternal(Long id) {
        NoticeExternalDO pre = noticeExternalMapper.selectById(id);
        if (pre == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS);
        }
        if (NoticeExternalAuditStatusEnum.REJECTED.getStatus().equals(pre.getAuditStatus())) {
            return; // 幂等：已驳回
        }
        NoticeExternalDO patch = new NoticeExternalDO();
        patch.setAuditStatus(NoticeExternalAuditStatusEnum.REJECTED.getStatus());
        int rows = noticeExternalMapper.update(patch, new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getId, id)
                .eq(NoticeExternalDO::getStatus, NoticeExternalStatusEnum.NEW.getStatus())
                .eq(NoticeExternalDO::getAuditStatus, NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus()));
        if (rows == 0) {
            // 并发竞态：该记录已被 approve/takeDown 等推进 → 拒绝旧快照覆盖
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_STATE_INVALID);
        }
        // 2026-09-10 全链路补全：驳回后从检索索引移除（doc 一般不存在，DELETE 幂等；失败仅记日志）
        removeIndexQuietly(id);
    }

    @Override
    public void takeDownExternal(Long id) {
        NoticeExternalDO pre = noticeExternalMapper.selectById(id);
        if (pre == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS);
        }
        if (NoticeExternalStatusEnum.INACTIVE.getStatus().equals(pre.getStatus())) {
            // 已下线（TAKEN_DOWN）：幂等返回；恢复入口=同 URL 内容更新重新采集（见 ingest 重投语义）
            return;
        }
        NoticeExternalDO patch = new NoticeExternalDO();
        patch.setStatus(NoticeExternalStatusEnum.INACTIVE.getStatus());
        patch.setAuditStatus(NoticeExternalAuditStatusEnum.TAKEN_DOWN.getStatus());
        int rows = noticeExternalMapper.update(patch, new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getId, id)
                .eq(NoticeExternalDO::getStatus, NoticeExternalStatusEnum.ACTIVE.getStatus())
                .eq(NoticeExternalDO::getAuditStatus, NoticeExternalAuditStatusEnum.APPROVED.getStatus()));
        if (rows == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_STATE_INVALID);
        }
        // 2026-09-10 全链路补全：下线后从检索索引移除（展示层本就靠回表 ACTIVE 过滤，删 doc 保持索引与状态一致）
        removeIndexQuietly(id);
    }

    /** 删除检索索引文档（失败仅记日志，不影响审核主流程；幂等） */
    private void removeIndexQuietly(Long id) {
        try {
            externalNoticeIndexNotifier.removeNotice(id);
        } catch (Exception e) {
            log.warn("[removeIndexQuietly][noticeId={}] ES 删除失败（可忽略，展示层有回表过滤）: {}", id, e.getMessage());
        }
    }

    @Override
    public PageResult<NoticeExternalRespVO> getPage(NoticeExternalPageReqVO reqVO) {
        LambdaQueryWrapper<NoticeExternalDO> wrapper = new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getPublicScope, PUBLIC_SCOPE_SOCIAL)
                .eq(NoticeExternalDO::getStatus, NoticeExternalStatusEnum.ACTIVE.getStatus());
        // 关键词全文：走 ES 检索引擎（bidding-notice-external），命中集约束 id，再叠结构化过滤与分页
        List<Long> matchedIds = null;
        long keywordTotal = -1L; // ES 真实命中数（-1=未走关键词检索）
        if (StringUtils.hasText(reqVO.getKeyword())) {
            // 按需取数（2026-09-22 修复"total 说 1326、翻到第 3 页却空"）：
            // 旧实现固定取 200 条，导致 total（真实命中数）与实际可翻页数不一致——
            // 前端按 total 渲染 27 页，其中 23 页是空的。这里按当前页所需扩大取回量，
            // 保证"能翻到的条数"与 total 自洽（上界 MAX_KEYWORD_MATCH_LIMIT 兜底）。
            int matchLimit = resolveMatchLimit(reqVO);
            ExternalNoticeSearchEngine.MatchResult match =
                    externalNoticeSearchEngine.matchNoticeIds(reqVO.getKeyword(), matchLimit);
            matchedIds = match.ids();
            keywordTotal = match.total();
            if (matchedIds.isEmpty()) {
                return PageResult.empty();
            }
            wrapper.in(NoticeExternalDO::getId, matchedIds);
        }
        if (StringUtils.hasText(reqVO.getTitle())) {
            // 标题精确匹配筛选（01 契约口径；标题/正文模糊全文一律走 keyword=ES——红线 4.6，禁止业务表 LIKE）
            wrapper.eq(NoticeExternalDO::getTitle, reqVO.getTitle());
        }
        if (StringUtils.hasText(reqVO.getSourceCode())) {
            wrapper.eq(NoticeExternalDO::getSourceCode, reqVO.getSourceCode());
        }
        if (StringUtils.hasText(reqVO.getContentType())) {
            wrapper.eq(NoticeExternalDO::getContentType, reqVO.getContentType());
        }
        if (StringUtils.hasText(reqVO.getIndustryFlag())) {
            wrapper.eq(NoticeExternalDO::getIndustryFlag, reqVO.getIndustryFlag());
        }
        if (StringUtils.hasText(reqVO.getNoticeTypeRaw())) {
            // 公告类型（外站原文，如“招标公告/中标候选人公示/成交结果公告”）；权威码维度后续按 notice_type_code 检索
            wrapper.eq(NoticeExternalDO::getNoticeTypeRaw, reqVO.getNoticeTypeRaw());
        }
        if (StringUtils.hasText(reqVO.getProjectCode())) {
            wrapper.eq(NoticeExternalDO::getProjectCode, reqVO.getProjectCode());
        }
        // 契约 §四：招标项目编号筛选（与扇出维度键口径一致）
        if (StringUtils.hasText(reqVO.getTenderProjectCode())) {
            wrapper.eq(NoticeExternalDO::getTenderProjectCode, reqVO.getTenderProjectCode());
        }
        // 契约 §四：标段编号筛选。bid_section_codes 是"分隔串"，须按成员匹配而非等值比较；
        // 分隔符先归一为半角逗号（兼容中文分号「；」与顿号「、」——爬虫侧会解析出这些字符，
        // 原实现读取侧只认 [,;]，会让这批标段静默失配），再走 FIND_IN_SET。
        if (StringUtils.hasText(reqVO.getBidSectionCode())) {
            wrapper.apply("FIND_IN_SET({0}, REPLACE(REPLACE(REPLACE(bid_section_codes, '；', ','), '、', ','), ';', ',')) > 0",
                    reqVO.getBidSectionCode().trim());
        }
        if (reqVO.getBeginTime() != null) {
            wrapper.ge(NoticeExternalDO::getPublishTime, reqVO.getBeginTime());
        }
        if (reqVO.getEndTime() != null) {
            wrapper.le(NoticeExternalDO::getPublishTime, reqVO.getEndTime());
        }
        // 排序（2026-09-22 契约 B2 配套修复）：
        // 关键词检索时**必须保住 ES 的相关度顺序**——ES 命中集已按 _score 排好，
        // 若这里再无条件 orderByDesc(publishTime)，相关度顺序会被 SQL 覆盖，
        // 于是"最相关的（可能较早的）公告"又被最新公告挤到 200 条之外，B2 改动等于白做。
        // 用 FIELD(id, ...) 表达 ES 的相关度序（MySQL 对 IN 列表按指定顺序排序的标准做法）。
        if (matchedIds != null && !matchedIds.isEmpty()) {
            // id 为 ES 返回的 long（非用户串），拼接无注入风险；用 FIELD() 保持 ES 相关度序。
            // 注意：这里必须用字符串拼接而非 {0} 占位——MyBatis-Plus 的 apply/last 占位符
            // 在动态长度列表上会与分页插件生成的 SQL 冲突；且 FIELD 参数是纯数字，字面量最稳。
            String ids = matchedIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            wrapper.last("ORDER BY FIELD(id, " + ids + ") ASC");
        } else {
            wrapper.orderByDesc(NoticeExternalDO::getPublishTime);
        }
        IPage<NoticeExternalDO> page = noticeExternalMapper.selectPage(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toRespVO).toList(),
                resolveTotal(page.getTotal(), keywordTotal, reqVO, matchedIds));
    }

    /**
     * 计算本次检索要从 ES 取回多少条（按当前页所需）。
     *
     * <p>取 {@code pageNo * pageSize} 与基础量 {@link #KEYWORD_MATCH_LIMIT} 的较大者，
     * 并以 {@link #MAX_KEYWORD_MATCH_LIMIT} 封顶：
     * <ul>
     *   <li>首屏（pageNo=1）→ 基础量 200，IN 条件不膨胀，与改造前开销一致；</li>
     *   <li>深翻页（如 pageNo=5, pageSize=100 → 需 500 条）→ 取 500，
     *       保证该页真的有数据，而不是"total 说有、翻过去是空"。</li>
     * </ul>
     *
     * <p>为什么用乘法而不是加法：分页语义要求"前 N 页的全部命中"，取回量必须覆盖到当前页末尾。
     * 乘法用 long 运算防止 pageNo 极大时 int 溢出。
     */
    private int resolveMatchLimit(NoticeExternalPageReqVO reqVO) {
        long pageNo = reqVO.getPageNo() == null || reqVO.getPageNo() < 1 ? 1 : reqVO.getPageNo();
        long pageSize = reqVO.getPageSize() == null || reqVO.getPageSize() < 1 ? 10 : reqVO.getPageSize();
        long needed = pageNo * pageSize; // 覆盖到当前页末尾所需的命中数
        if (needed <= KEYWORD_MATCH_LIMIT) {
            return KEYWORD_MATCH_LIMIT;
        }
        return (int) Math.min(needed, MAX_KEYWORD_MATCH_LIMIT);
    }

    /**
     * 计算下发的 {@code total}（契约 B5：不再把"取回上限"当"命中总数"）。
     *
     * <p><b>旧问题</b>：{@code total} 取自主表 COUNT，而主表受 {@code id IN (命中集)} 约束，
     * 命中集上限 200 条 → 前端永远看到"共 200 条"，翻页翻不出新内容，也无法感知截断。
     *
     * <p><b>本实现</b>：
     * <ul>
     *   <li>未走关键词检索 → 直接用主表 COUNT（原有语义不变）；</li>
     *   <li>走了关键词检索且**无其他结构化收窄条件** → 用 ES 真实命中数（{@code hits.total.value}），
     *       这是用户最关心的"到底有多少条"。此时取回量按 {@link #resolveMatchLimit} 已覆盖当前页，
     *       total 与"能翻到的条数"在 {@link #MAX_KEYWORD_MATCH_LIMIT} 以内自洽；</li>
     *   <li>走了关键词检索**且**附带结构化过滤（项目编号/类型/时间等）→ 结构化条件只在被取回的
     *       命中集内求交集，真实全量需要"ES + 结构化"联合下推（ES 索引未存全部结构化字段），
     *       故此时仍用主表 COUNT（=交集内真实条数），它反映的是"当前过滤条件下可展示的真实条数"。</li>
     * </ul>
     *
     * <p>命中数超出取回硬上限（{@link #MAX_KEYWORD_MATCH_LIMIT}）时打 WARN：
     * 这是"total 会大于可翻页条数"的唯一情形，需要运维/开发介入（改游标分页或提升上限）。
     */
    private long resolveTotal(long dbTotal, long keywordTotal, NoticeExternalPageReqVO reqVO, List<Long> matchedIds) {
        if (keywordTotal < 0) {
            return dbTotal;
        }
        if (keywordTotal > matchedIds.size()) {
            // 取回量 < 真实命中数：说明撞到了 MAX_KEYWORD_MATCH_LIMIT 硬上限（按需扩容已尽力）
            log.warn("[getPage][keyword={}] ES 真实命中 {} 条 > 本次取回 {} 条（硬上限 {}），"
                            + "超出部分无法翻页；请评估改用 search_after 游标分页或提升上限",
                    reqVO.getKeyword(), keywordTotal, matchedIds.size(), MAX_KEYWORD_MATCH_LIMIT);
        }
        return hasStructuredFilter(reqVO) ? dbTotal : keywordTotal;
    }

    /** 是否带结构化收窄条件（这些条件无法下推到 ES，会让"ES 真实命中数"不再等于最终结果数）。 */
    private boolean hasStructuredFilter(NoticeExternalPageReqVO reqVO) {
        return StringUtils.hasText(reqVO.getTitle())
                || StringUtils.hasText(reqVO.getSourceCode())
                || StringUtils.hasText(reqVO.getContentType())
                || StringUtils.hasText(reqVO.getIndustryFlag())
                || StringUtils.hasText(reqVO.getNoticeTypeRaw())
                || StringUtils.hasText(reqVO.getProjectCode())
                || StringUtils.hasText(reqVO.getTenderProjectCode())
                || StringUtils.hasText(reqVO.getBidSectionCode())
                || reqVO.getBeginTime() != null
                || reqVO.getEndTime() != null;
    }

    @Override
    public PageResult<NoticeExternalManageRespVO> getManagePage(NoticeExternalManagePageReqVO reqVO) {
        // 管理分页：不做 SOCIAL+ACTIVE 硬过滤（N1：公开 page 只出 ACTIVE+SOCIAL 无法支撑审核页；本方法出全状态/审核态行）
        LambdaQueryWrapper<NoticeExternalDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(reqVO.getStatus())) {
            wrapper.eq(NoticeExternalDO::getStatus, reqVO.getStatus());
        }
        if (StringUtils.hasText(reqVO.getAuditStatus())) {
            wrapper.eq(NoticeExternalDO::getAuditStatus, reqVO.getAuditStatus());
        }
        if (StringUtils.hasText(reqVO.getSourceCode())) {
            wrapper.eq(NoticeExternalDO::getSourceCode, reqVO.getSourceCode());
        }
        if (StringUtils.hasText(reqVO.getContentType())) {
            wrapper.eq(NoticeExternalDO::getContentType, reqVO.getContentType());
        }
        if (StringUtils.hasText(reqVO.getNoticeTypeRaw())) {
            wrapper.eq(NoticeExternalDO::getNoticeTypeRaw, reqVO.getNoticeTypeRaw());
        }
        if (StringUtils.hasText(reqVO.getIndustryFlag())) {
            wrapper.eq(NoticeExternalDO::getIndustryFlag, reqVO.getIndustryFlag());
        }
        if (StringUtils.hasText(reqVO.getProjectCode())) {
            wrapper.eq(NoticeExternalDO::getProjectCode, reqVO.getProjectCode());
        }
        // 契约 §四：与公开分页同口径的维度筛选（管理端也需要按维度定位）
        if (StringUtils.hasText(reqVO.getTenderProjectCode())) {
            wrapper.eq(NoticeExternalDO::getTenderProjectCode, reqVO.getTenderProjectCode());
        }
        if (StringUtils.hasText(reqVO.getBidSectionCode())) {
            wrapper.apply("FIND_IN_SET({0}, REPLACE(REPLACE(REPLACE(bid_section_codes, '；', ','), '、', ','), ';', ',')) > 0",
                    reqVO.getBidSectionCode().trim());
        }
        if (reqVO.getBeginTime() != null) {
            wrapper.ge(NoticeExternalDO::getPublishTime, reqVO.getBeginTime());
        }
        if (reqVO.getEndTime() != null) {
            wrapper.le(NoticeExternalDO::getPublishTime, reqVO.getEndTime());
        }
        // 管理列表：同发布时间按下线先后排（NEW 在无发布时间的行前按创建时间），保证分页稳定（主键兜底序）
        wrapper.orderByDesc(NoticeExternalDO::getPublishTime).orderByDesc(NoticeExternalDO::getId);
        IPage<NoticeExternalDO> page = noticeExternalMapper.selectPage(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toManageRespVO).toList(), page.getTotal());
    }

    @Override
    public NoticeExternalFilterOptionsRespVO getManageFilterOptions() {
        NoticeExternalFilterOptionsRespVO resp = new NoticeExternalFilterOptionsRespVO();
        // 来源站：取采集源配置（bidding_fetch_source）——名称以配置为准；公告表 source_name 只是快照，可能滞后/为空。
        // 下拉提交的仍是 sourceCode（manage/page 按 sourceCode 精确过滤）。
        List<NoticeExternalFilterOptionsRespVO.SourceOption> sourceOptions = new ArrayList<>();
        for (CrawlSourceDO src : crawlSourceMapper.selectList(
                new LambdaQueryWrapper<CrawlSourceDO>()
                        .select(CrawlSourceDO::getSourceCode, CrawlSourceDO::getSourceName)
                        .orderByAsc(CrawlSourceDO::getSourceCode))) {
            if (!StringUtils.hasText(src.getSourceCode())) {
                continue;
            }
            NoticeExternalFilterOptionsRespVO.SourceOption option = new NoticeExternalFilterOptionsRespVO.SourceOption();
            option.setCode(src.getSourceCode());
            option.setName(StringUtils.hasText(src.getSourceName()) ? src.getSourceName() : src.getSourceCode());
            sourceOptions.add(option);
        }
        resp.setSources(sourceOptions);
        // 公告类型：notice_type_raw 去重（自由文本，无枚举/字典；仅作下拉选项，不参与检索）
        resp.setNoticeTypes(noticeExternalMapper.selectDistinctNoticeTypeRaw());
        return resp;
    }

    @Override
    public NoticeExternalManageDetailRespVO getManageDetail(Long id) {
        NoticeExternalDO notice = noticeExternalMapper.selectById(id);
        if (notice == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS);
        }
        NoticeExternalManageDetailRespVO resp = new NoticeExternalManageDetailRespVO();
        resp.setId(notice.getId());
        resp.setSourceCode(notice.getSourceCode());
        resp.setSourceName(notice.getSourceName());
        resp.setTitle(notice.getTitle());
        resp.setOrigUrl(notice.getOrigUrl());
        resp.setContentType(notice.getContentType());
        resp.setNoticeTypeRaw(notice.getNoticeTypeRaw());
        resp.setLegalFlag(notice.getLegalFlag());
        resp.setIndustryFlag(notice.getIndustryFlag());
        resp.setIndustryConfidence(notice.getIndustryConfidence());
        resp.setStatus(notice.getStatus());
        resp.setAuditStatus(notice.getAuditStatus());
        resp.setPublicScope(notice.getPublicScope());
        boolean html = NoticeExternalContentTypeEnum.HTML.getType().equals(notice.getContentType());
        resp.setContentHtmlSafe(html ? HtmlSanitizer.sanitize(notice.getContentHtml()) : null);
        resp.setBodyBlocksStatus(notice.getBodyBlocksStatus());
        resp.setBodyParserCode(notice.getBodyParserCode());
        resp.setBodyBlocksJson(notice.getBodyBlocksJson());
        resp.setPdfFileId(notice.getPdfFileId());
        resp.setPdfSha256(notice.getPdfSha256());
        resp.setPdfSize(notice.getPdfSize());
        resp.setPdfDownloadUrl(notice.getPdfDownloadUrl());
        resp.setExtractionStatus(notice.getExtractionStatus());
        resp.setExtractionSummary(summarize(notice.getExtractionText()));
        resp.setAiExtractionStatus(notice.getAiExtractionStatus());
        resp.setAiMetaFieldCount(extractMetaFieldCount(notice.getAiMetaJson()));
        resp.setIssuerName(notice.getIssuerName());
        resp.setProjectKey(cleanNullable(notice.getProjectCode()));
        resp.setProjectName(notice.getProjectName());
        resp.setTenderProjectKey(cleanNullable(notice.getTenderProjectCode()));
        resp.setBidSectionKeys(NoticeTrackKeyUtils.splitSectionCodes(cleanNullable(notice.getBidSectionCodes())));
        resp.setBidSectionCodes(cleanNullable(notice.getBidSectionCodes()));
        resp.setPublishTime(notice.getPublishTime());
        resp.setEffectiveFrom(notice.getEffectiveFrom());
        resp.setEffectiveTo(notice.getEffectiveTo());
        resp.setFetchCount(notice.getFetchCount());
        resp.setLastFetchTime(notice.getLastFetchTime());
        resp.setCreateTime(notice.getCreateTime());
        resp.setUpdateTime(notice.getUpdateTime());
        // 附件（BR-7）：病毒 INFECTED 不下发下载链接（安全评审意见 1/2 同公开口径），virusStatus 照回供审核识别
        List<NoticeExternalAttachmentDO> attachments = noticeExternalAttachmentMapper
                .selectList(new LambdaQueryWrapper<NoticeExternalAttachmentDO>()
                        .eq(NoticeExternalAttachmentDO::getNoticeId, id)
                        .orderByAsc(NoticeExternalAttachmentDO::getId));
        List<NoticeExternalAttachmentDO> downloadable = attachments.stream()
                .filter(a -> !"INFECTED".equalsIgnoreCase(a.getVirusStatus()))
                .toList();
        if (!downloadable.isEmpty()) {
            resp.setAttachments(downloadable.stream().map(this::toManageAttachmentVO).toList());
        }
        return resp;
    }

    /** 抽取正文摘要：前 200 字符（无文本 null）。 */
    private String summarize(String extractionText) {
        if (!StringUtils.hasText(extractionText)) {
            return null;
        }
        String text = extractionText.trim();
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }

    /** ai_meta_json 中 metaFieldCount 读取（结构不符/缺失返回 null，不阻断预览）。 */
    private Integer extractMetaFieldCount(String aiMetaJson) {
        if (!StringUtils.hasText(aiMetaJson)) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = JSON.readTree(aiMetaJson).get("metaFieldCount");
            return node != null && node.isNumber() ? node.asInt() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private NoticeExternalManageDetailRespVO.Attachment toManageAttachmentVO(NoticeExternalAttachmentDO attachment) {
        NoticeExternalManageDetailRespVO.Attachment vo = new NoticeExternalManageDetailRespVO.Attachment();
        vo.setId(attachment.getId());
        vo.setName(attachment.getName());
        vo.setExt(attachment.getExt());
        vo.setFileId(attachment.getFileId());
        vo.setSha256(attachment.getSha256());
        vo.setSize(attachment.getSize());
        vo.setOrigDownloadUrl(attachment.getOrigDownloadUrl());
        vo.setVirusStatus(attachment.getVirusStatus());
        return vo;
    }

    private NoticeExternalManageRespVO toManageRespVO(NoticeExternalDO notice) {
        NoticeExternalManageRespVO resp = new NoticeExternalManageRespVO();
        resp.setId(notice.getId());
        resp.setSourceCode(notice.getSourceCode());
        resp.setSourceName(notice.getSourceName());
        resp.setTitle(notice.getTitle());
        resp.setOrigUrl(notice.getOrigUrl());
        resp.setContentType(notice.getContentType());
        resp.setNoticeTypeRaw(notice.getNoticeTypeRaw());
        resp.setLegalFlag(notice.getLegalFlag());
        resp.setIndustryFlag(notice.getIndustryFlag());
        resp.setIndustryConfidence(notice.getIndustryConfidence());
        resp.setIssuerName(notice.getIssuerName());
        resp.setProjectName(notice.getProjectName());
        resp.setBidSectionCodes(notice.getBidSectionCodes());
        resp.setStatus(notice.getStatus());
        resp.setAuditStatus(notice.getAuditStatus());
        resp.setExtractionStatus(notice.getExtractionStatus());
        resp.setAiExtractionStatus(notice.getAiExtractionStatus());
        resp.setBodyBlocksStatus(notice.getBodyBlocksStatus());
        resp.setBodyParserCode(notice.getBodyParserCode());
        resp.setPublishTime(notice.getPublishTime());
        resp.setEffectiveFrom(notice.getEffectiveFrom());
        resp.setEffectiveTo(notice.getEffectiveTo());
        resp.setFetchCount(notice.getFetchCount());
        resp.setLastFetchTime(notice.getLastFetchTime());
        resp.setCreateTime(notice.getCreateTime());
        resp.setUpdateTime(notice.getUpdateTime());
        return resp;
    }

    @Override
    public NoticeExternalDetailRespVO getDetail(Long id) {
        NoticeExternalDO notice = noticeExternalMapper.selectById(id);
        if (notice == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS);
        }
        // 公开详情只放行 SOCIAL + ACTIVE（01 规则 1 + 红线 2 兜底）
        if (!Objects.equals(notice.getPublicScope(), PUBLIC_SCOPE_SOCIAL)
                || !NoticeExternalStatusEnum.ACTIVE.getStatus().equals(notice.getStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_TAKEN_DOWN);
        }
        NoticeExternalDetailRespVO resp = new NoticeExternalDetailRespVO();
        copyToDetail(notice, resp);
        // 附件下载列表（FileApi 签名 downloadUrl 待 infra 文件下载能力接入后回填，先给 file_id/溯源链接）
        // 安全评审意见 1/2：病毒状态=INFECTED 的附件一律不下发链接（扫描闭环由 FileApi 病毒扫描保证）
        List<NoticeExternalAttachmentDO> attachments = noticeExternalAttachmentMapper
                .selectList(new LambdaQueryWrapper<NoticeExternalAttachmentDO>()
                        .eq(NoticeExternalAttachmentDO::getNoticeId, id)
                        .orderByAsc(NoticeExternalAttachmentDO::getId));
        List<NoticeExternalAttachmentDO> downloadable = attachments.stream()
                .filter(a -> !"INFECTED".equalsIgnoreCase(a.getVirusStatus()))
                .toList();
        if (!downloadable.isEmpty()) {
            resp.setAttachments(downloadable.stream().map(this::toAttachmentVO).toList());
        }
        return resp;
    }

    private NoticeExternalRespVO toRespVO(NoticeExternalDO notice) {
        NoticeExternalRespVO resp = new NoticeExternalRespVO();
        resp.setId(notice.getId());
        resp.setSourceCode(notice.getSourceCode());
        resp.setSourceName(notice.getSourceName());
        resp.setTitle(notice.getTitle());
        resp.setOrigUrl(notice.getOrigUrl());
        resp.setContentType(notice.getContentType());
        resp.setLegalFlag(notice.getLegalFlag());
        resp.setNoticeTypeRaw(notice.getNoticeTypeRaw());
        resp.setIndustryFlag(notice.getIndustryFlag());
        resp.setIssuerName(notice.getIssuerName());
        resp.setPublishTime(notice.getPublishTime());
        resp.setEffectiveFrom(notice.getEffectiveFrom());
        resp.setEffectiveTo(notice.getEffectiveTo());
        resp.setStatus(notice.getStatus());
        copyTrackKeys(notice, resp);
        return resp;
    }

    /** 跟踪维度键下发（BR-1）：project/tender 键 + 标段键列表；名称一期仅有 projectName 快照，其余可为 null。
     *  存量历史脏值字面 "NULL"（采集侧 ORM 拼接产物）一律按 null 下发，避免前端误当可关注键。 */
    private void copyTrackKeys(NoticeExternalDO notice, NoticeExternalRespVO resp) {
        resp.setProjectKey(cleanNullable(notice.getProjectCode()));
        resp.setProjectName(notice.getProjectName());
        resp.setTenderProjectKey(cleanNullable(notice.getTenderProjectCode()));
        resp.setTenderProjectName(null);
        resp.setBidSectionKeys(NoticeTrackKeyUtils.splitSectionCodes(cleanNullable(notice.getBidSectionCodes())));
        resp.setBidSectionNames(null);
        resp.setBidSectionCodes(cleanNullable(notice.getBidSectionCodes()));
    }

    /** 字面 "NULL"/空白 → null（归一采集侧脏值；见 ExternalNoticeIngestServiceImpl.normalizeNullStringFields 的存量场景）。 */
    private String cleanNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "NULL".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private void copyToDetail(NoticeExternalDO notice, NoticeExternalDetailRespVO resp) {
        resp.setId(notice.getId());
        resp.setSourceCode(notice.getSourceCode());
        resp.setSourceName(notice.getSourceName());
        resp.setTitle(notice.getTitle());
        resp.setOrigUrl(notice.getOrigUrl());
        resp.setContentType(notice.getContentType());
        boolean html = NoticeExternalContentTypeEnum.HTML.getType().equals(notice.getContentType());
        // HTML 形态：留档原样仅存库（供抽取/审计）——公开匿名详情不下发原样，统一净化版渲染（安全评审意见 1：存储型 XSS 载荷不主动下发公众客户端）
        resp.setContentHtml(null);
        resp.setContentHtmlSafe(html ? HtmlSanitizer.sanitize(notice.getContentHtml()) : null);
        // PDF 形态：file_id+sha+size（前端 JS 预览）；pdfDownloadUrl=平台归档 URL（爬取 PDF 已上传文件服务），前端直接预览/下载
        resp.setPdfFileId(notice.getPdfFileId());
        resp.setPdfSha256(notice.getPdfSha256());
        resp.setPdfSize(notice.getPdfSize());
        resp.setPdfDownloadUrl(notice.getPdfDownloadUrl());
        resp.setExtractionStatus(notice.getExtractionStatus());
        resp.setNoticeTypeRaw(notice.getNoticeTypeRaw());
        resp.setLegalFlag(notice.getLegalFlag());
        resp.setIndustryFlag(notice.getIndustryFlag());
        resp.setBodyParserCode(notice.getBodyParserCode());
        resp.setBodyBlocksStatus(notice.getBodyBlocksStatus());
        resp.setBodyBlocksJson(notice.getBodyBlocksJson());
        copyDetailTrackKeys(notice, resp);
        resp.setIssuerName(notice.getIssuerName());
        resp.setPublishTime(notice.getPublishTime());
        resp.setEffectiveFrom(notice.getEffectiveFrom());
        resp.setEffectiveTo(notice.getEffectiveTo());
    }

    /** 详情 VO 跟踪键（BR-1）：在既有 projectName/bidSectionCodes 快照字段外新增键列表下发（脏值 "NULL" 归一 null）。 */
    private void copyDetailTrackKeys(NoticeExternalDO notice, NoticeExternalDetailRespVO resp) {
        resp.setProjectKey(cleanNullable(notice.getProjectCode()));
        resp.setProjectName(notice.getProjectName());
        resp.setTenderProjectKey(cleanNullable(notice.getTenderProjectCode()));
        resp.setTenderProjectName(null);
        resp.setBidSectionKeys(NoticeTrackKeyUtils.splitSectionCodes(cleanNullable(notice.getBidSectionCodes())));
        resp.setBidSectionNames(null);
        resp.setBidSectionCodes(cleanNullable(notice.getBidSectionCodes()));
    }

    private NoticeExternalDetailRespVO.Attachment toAttachmentVO(NoticeExternalAttachmentDO attachment) {
        NoticeExternalDetailRespVO.Attachment vo = new NoticeExternalDetailRespVO.Attachment();
        vo.setId(attachment.getId());
        vo.setName(attachment.getName());
        vo.setExt(attachment.getExt());
        vo.setFileId(attachment.getFileId());
        vo.setSha256(attachment.getSha256());
        vo.setSize(attachment.getSize());
        vo.setOrigDownloadUrl(attachment.getOrigDownloadUrl());
        vo.setVirusStatus(attachment.getVirusStatus());
        // downloadUrl：FileApi 签名下载地址（TODO：接入 infra 文件下载后回填）
        return vo;
    }

    /** 存量 PDF 归档回填：见接口注释。单条失败记日志继续；meta_json 无外站 pdfDownloadUrl 的计 skipped。 */
    @Override
    public String backfillPdfDownloadUrls() {
        List<NoticeExternalDO> pdfs = noticeExternalMapper.selectList(new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getContentType, "PDF")
                .and(w -> w.isNull(NoticeExternalDO::getPdfDownloadUrl).or().eq(NoticeExternalDO::getPdfDownloadUrl, "")));
        int ok = 0;
        int failed = 0;
        int skipped = 0;
        for (NoticeExternalDO pdf : pdfs) {
            String externalUrl = null;
            try {
                externalUrl = pdfMetaPdfDownloadUrl(pdf.getMetaJson());
                if (!StringUtils.hasText(externalUrl)) {
                    skipped++;
                    log.warn("[backfillPdf][id={}] meta_json 无外站 pdfDownloadUrl，跳过", pdf.getId());
                    continue;
                }
                // SSRF 防护：重下载外站 PDF 前校验主机白名单（行 source_code → 源配置 base_url ∪ allowed_hosts）
                assertPdfDownloadAllowed(pdf, externalUrl);
                // 合规限速（2026-09-10 重构）：批量回填也是"下载公告原文文件"，必须逐份占用合规闸位。
                // 原实现是 for 循环里无间隔连发请求（本模块最严重的频控缺口），现改为：
                // 未到间隔即跳过本条（不下载、不改数据），由运维择时再调本接口，或等待到点后重跑——
                // 用"跳过+可重入"而不是"就地长睡"，避免管理端请求被 N×interval 长时间挂住。
                if (!crawlDownloadGate.tryAcquire(pdf.getSourceCode(), null, externalUrl)) {
                    skipped++;
                    log.info("[backfillPdf][id={}] 未到合规下载间隔，本轮跳过（到点后重跑本接口即可；可下载时间={}）",
                            pdf.getId(), crawlDownloadGate.nextDueAt(pdf.getSourceCode()));
                    continue;
                }
                byte[] bytes = httpFetchClient.getBytes(externalUrl);
                if (bytes == null || bytes.length == 0) {
                    failed++;
                    log.warn("[backfillPdf][id={}] 重下载为空 {}", pdf.getId(), externalUrl);
                    continue;
                }
                String safeName = StringUtils.hasText(pdf.getTitle())
                        ? pdf.getTitle().replaceAll("[\\/:*?\"<>|]", "_") : "notice";
                if (safeName.length() > 80) {
                    safeName = safeName.substring(0, 80);
                }
                String url = fileApi.createFile(bytes, safeName + ".pdf", "bidding/notice-external", "application/pdf");
                if (!StringUtils.hasText(url)) {
                    failed++;
                    continue;
                }
                NoticeExternalDO patch = new NoticeExternalDO();
                patch.setPdfDownloadUrl(url);
                noticeExternalMapper.updateById(patch.setId(pdf.getId()));
                ok++;
                log.info("[backfillPdf][id={}] 回填 pdf_download_url={}", pdf.getId(), url);
            } catch (Exception e) {
                failed++;
                log.warn("[backfillPdf][id={}] 回填失败 url={} err={}", pdf.getId(), externalUrl, e.getMessage());
            }
        }
        return "total=" + pdfs.size() + ",ok=" + ok + ",failed=" + failed + ",skipped=" + skipped;
    }

    @Override
    public String reindexIndex() {
        List<NoticeExternalDO> actives = noticeExternalMapper.selectList(new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getStatus, NoticeExternalStatusEnum.ACTIVE.getStatus())
                .orderByDesc(NoticeExternalDO::getId));
        int ok = 0;
        int failed = 0;
        for (NoticeExternalDO n : actives) {
            try {
                externalNoticeIndexNotifier.indexNotice(n);
                ok++;
            } catch (Exception e) {
                failed++;
                log.warn("[reindexIndex][noticeId={}] ES 索引失败: {}", n.getId(), e.getMessage());
            }
        }
        log.info("[reindexIndex] 完成 total={} ok={} failed={}", actives.size(), ok, failed);
        return "total=" + actives.size() + ",ok=" + ok + ",failed=" + failed;
    }

    /** 重下载主机白名单校验：平台文件端点豁免；外站 URL 须在行 source_code 对应采集源白名单内（源缺失=拒绝，防脏数据外链 SSRF） */
    private void assertPdfDownloadAllowed(NoticeExternalDO pdf, String externalUrl) {
        if (UrlHostGuard.isInternalFileUrl(externalUrl)) {
            return; // 平台自产文件端点
        }
        cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO source = crawlSourceMapper.selectOne(
                new LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO>()
                        .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO::getSourceCode, pdf.getSourceCode())
                        .last("LIMIT 1"));
        if (source == null) {
            throw new IllegalStateException("PDF 行无对应采集源配置，拒绝重下载（SSRF 防护），id=" + pdf.getId());
        }
        if (!UrlHostGuard.isExternalAllowed(externalUrl, source.getBaseUrl(), source.getAllowedHosts())) {
            throw new IllegalStateException("PDF 直链不在采集源白名单（SSRF 防护），url=" + externalUrl);
        }
    }

    /** 读 meta_json 中的外站 pdfDownloadUrl（缺失/解析失败返回 null）。 */
    private String pdfMetaPdfDownloadUrl(String metaJson) {
        if (!StringUtils.hasText(metaJson)) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = JSON.readTree(metaJson).get("pdfDownloadUrl");
            return node != null && node.isTextual() ? node.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }


    @Override
    public byte[] pdfContent(Long id) {
        NoticeExternalDO notice = noticeExternalMapper.selectById(id);
        if (notice == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS);
        }
        if (!"PDF".equalsIgnoreCase(notice.getContentType())) {
            return null; // 非 PDF 形态：调用方按 404 处理
        }
        // ① 平台归档（首选：pdf_download_url 指向平台文件服务，稳定且不受站点鉴权影响）
        if (StringUtils.hasText(notice.getPdfDownloadUrl())) {
            byte[] archived = fetchPdfBytes(notice.getPdfDownloadUrl(), id, "平台归档");
            if (archived != null) {
                return archived;
            }
        }
        // ② 兜底：外站直链（meta_json.pdfDownloadUrl，采集详情时记录；使用它需过采集源白名单 = SSRF 防护）
        String externalUrl = pdfMetaPdfDownloadUrl(notice.getMetaJson());
        if (StringUtils.hasText(externalUrl) && !externalUrl.equals(notice.getPdfDownloadUrl())) {
            try {
                assertPdfDownloadAllowed(notice, externalUrl);
                // 2026-09-11 缺陷修复（合规旁路）：外站直链预览同样是从目标站点下载原文文件，
                //   必须与采集链路共用同一个 2 分钟闸位，否则"预览"就成了绕过限速的后门。
                //   规范依据：《外部信息获取 v1.0》§六 频控（间隔只作用于"下载原文文件"这一动作）
                //   + 用户口径「绝对保证 2 分钟才爬一次详细页」。
                //   为什么用非阻塞 tryAcquire 而非 acquireBlocking：这是用户点开的在线预览，等 2 分钟不可接受；
                //   拿得到额度就预览，拿不到就明确 404（前端已有"暂无 PDF 原文"提示），绝不"先请求再判断"。
                if (!robotsGuard.isAllowed(externalUrl)) {
                    log.info("[pdfContent][id={}] 外站直链被 robots.txt 禁止，按 404 返回（合规优先）: {}", id, externalUrl);
                    return null;
                }
                if (!crawlDownloadGate.tryAcquire(notice.getSourceCode(), null, externalUrl)) {
                    log.info("[pdfContent][id={}] 未到合规下载间隔，本次不访问外站（避免绕过限频）: {}", id, externalUrl);
                    return null;
                }
                byte[] external = fetchPdfBytes(externalUrl, id, "外站直链");
                if (external != null) {
                    log.info("[pdfContent][id={}] 平台归档不可用，已回退外站直链预览", id);
                    return external;
                }
            } catch (Exception e) {
                log.warn("[pdfContent][id={}] 外站直链回退被拒: {}", id, e.getMessage());
            }
        }
        // 双通道皆不可用：返回 null → 404（前端提示「暂无 PDF 原文」，不再返回 0 字节的空白 PDF）
        log.warn("[pdfContent][id={}] 无可用 PDF 原文（归档 url={}，外站直链={}）",
                id, notice.getPdfDownloadUrl(), externalUrl);
        return null;
    }

    /**
     * 下载 PDF 字节并做「空体」判定。
     *
     * <p>规范依据：《外部信息获取 v1.0》§7.6/F-2 契约（PDF 预览端点：非 PDF 或无归档返回 404）。
     * 业务目标：预览必须「要么给出可看的原文，要么明确 404」。
     * 原因（2026-09-10 缺陷修复）：原实现只判 HTTP 200 + body 非 null，
     * 文件服务在文件缺失/内容为空时同样返回 200 空体，于是前端拿到 0 字节空白 PDF（表现为"预览打不开也没报错"）。
     * 现按字节长度判定，空体一律视为不可用并记录 url 与状态码，便于定位是「归档缺失」还是「存储读空」。
     *
     * @return 非空字节数组；任何失败（网络/状态码非 200/空体）返回 null
     */
    private byte[] fetchPdfBytes(String url, Long id, String channel) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        // 2026-09-11 统一走 HttpFetchClient（与采集链路同一套连接/读超时、浏览器 UA、单文件 50MB 上限、
        //   反爬特征识别），不再自建裸 HttpClient。原因：裸客户端没有 UA（部分站点直接拒绝）、没有大小上限
        //   （恶意/异常大文件会撑爆管理端响应），且无法在单测中打桩（预览回退的合规行为无法回归验证）。
        //   契约不变：任何失败/空体一律返回 null，调用方按"暂无原文"404 处理。
        try {
            // 2026-09-23 缺陷修复（与 2026-09-22 采集侧 ARCHIVE_HTML 同款分流）：平台归档地址形如
            //   http://127.0.0.1:48080/admin-api/infra/file/4/get/…（infra 存储配置 domain = 宿主回环），
            //   走带 SSRF 校验的 getBytes 会被 SsrfGuard 判「目标解析到受限地址」直接拒 ——
            //   实测 GX 源 347 条 PDF 公告里 344 条预览恒 404，应用日志原文：
            //   [pdfContent][id=…][平台归档] 获取失败: SSRF 拦截：目标解析到受限地址 127.0.0.1（host=127.0.0.1）。
            //   平台文件端点属系统自产、外部不可控（判定口径 UrlHostGuard.isInternalFileUrl），故改走免检通道；
            //   外站直链仍走带 SSRF 校验的 getBytes（双保险），SSRF 防护面不扩大。
            byte[] bytes = UrlHostGuard.isInternalFileUrl(url)
                    ? httpFetchClient.fetchTextInternal(url).getBytes()
                    : httpFetchClient.getBytes(url);
            if (bytes != null && bytes.length > 0) {
                return bytes;
            }
            log.warn("[pdfContent][id={}][{}] 不可用：空体 url={}", id, channel, url);
            return null;
        } catch (Exception e) {
            log.warn("[pdfContent][id={}][{}] 获取失败: {} url={}", id, channel, e.getMessage(), url);
            return null;
        }
    }

}
