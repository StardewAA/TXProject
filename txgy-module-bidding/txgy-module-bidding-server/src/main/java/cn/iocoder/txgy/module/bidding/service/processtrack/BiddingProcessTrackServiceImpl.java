package cn.iocoder.txgy.module.bidding.service.processtrack;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo.ProcessTrackCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo.ProcessTrackRegisterReqVO;
import cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo.ProcessTrackUploadReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackEventVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackItemVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackOptionsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackReviewReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackStatsRespVO;
import cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo.ProcessTrackRespVO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.dal.dataobject.processtrack.BiddingProcessEventDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.processtrack.BiddingProcessTrackDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.BidSectionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.TenderProjectDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectAuthDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.processtrack.BiddingProcessEventMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.processtrack.BiddingProcessTrackMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.BidSectionMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.TenderProjectMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectAuthMapper;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.flow.TenderStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.processtrack.BiddingProcessEventEnum;
import cn.iocoder.txgy.module.bidding.enums.processtrack.BiddingProcessStateEnum;
import cn.iocoder.txgy.module.bidding.enums.processtrack.ReviewResultEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Consumer;

/**
 * 投标流程跟踪实现（投标方状态机）。
 *
 * <p>状态机（幂等事件校验）：
 * <pre>
 * INTENTION --REGISTER--> QUALIFICATION_REVIEW
 * QUALIFICATION_REVIEW --APPROVE--> PENDING_BID | --REJECT--> TERMINATED
 * PENDING_BID --UPLOAD_DOCUMENT--> BIDDED
 * INTENTION/QUALIFICATION_REVIEW/PENDING_BID --CANCEL--> TERMINATED
 * </pre>
 * 递交门槛（最严格）：投标截止后禁止递交（招标项目状态 > BIDDING 即视为已截止，BIDDING_ENDED 由时间驱动触发）；
 * CA 签名必填（documentSignature），SM3 指纹必填（documentHash）——由外部 CA SDK 完成，此处只做落库校验占位。
 */
@Service
@Slf4j
public class BiddingProcessTrackServiceImpl implements BiddingProcessTrackService {

    /** 事件 → (fromState, toState) 合法映射（同事件多分支：键为 from→to 组合） */
    private static final Map<String, List<Map.Entry<String, String>>> TRANSITIONS = new HashMap<>();
    static {
        add("REGISTER", "INTENTION", "QUALIFICATION_REVIEW");
        add("APPROVE", "QUALIFICATION_REVIEW", "PENDING_BID");
        add("REJECT", "QUALIFICATION_REVIEW", "TERMINATED");
        add("UPLOAD_DOCUMENT", "PENDING_BID", "BIDDED");
        add("CANCEL", "INTENTION", "TERMINATED");
        add("CANCEL", "QUALIFICATION_REVIEW", "TERMINATED");
        add("CANCEL", "PENDING_BID", "TERMINATED");
    }
    private static void add(String event, String from, String to) {
        TRANSITIONS.computeIfAbsent(event, k -> new java.util.ArrayList<>()).add(Map.entry(from, to));
    }

    @Resource
    private BiddingProcessTrackMapper trackMapper;
    @Resource
    private BiddingProcessEventMapper eventMapper;
    /** 投标文件集附件（契约《投标与资格预审多文件化-2026-10-02》R2：bizType=BID_TRACK，bizCode=track.id） */
    @Resource
    private cn.iocoder.txgy.module.bidding.service.attachment.AttachmentService attachmentService;
    @Resource
    private BidSectionMapper bidSectionMapper;
    @Resource
    private TenderProjectMapper tenderProjectMapper;
    @Resource
    private SubjectAuthMapper subjectAuthMapper;
    @Resource
    private AdminUserApi adminUserApi;
    /** 投标资格门（此前只有声明与实现、全仓无调用点，本轮接线）。 */
    @Resource
    private cn.iocoder.txgy.module.bidding.service.subject.SubjectEligibilityService subjectEligibilityService;
    /** 本租户主体档案（判断"是否已建档"）。 */
    @Resource
    private cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService subjectProfileService;

    /** 无租户个人用户使用的保留租户编号（与 TenantSecurityWebFilter.PERSONAL_TENANT_ID 同源口径）。 */
    private static final long PERSONAL_TENANT_ID = 0L;

    @Override
    public void assertBidPortalAllowed() {
        Long tenantId = cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.getTenantId();
        // ① 个人用户门：保留租户 0 的用户不在"租户→角色→菜单"体系内，投标是面向企业的能力。
        //    这里给**业务码**而非 403 —— 403 会被前端当作"权限未开通"，且租户越权 403 会清会话。
        if (tenantId == null || tenantId == PERSONAL_TENANT_ID) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_PORTAL_PERSONAL_NOT_OPEN);
        }
        // ② 企业租户未建档：先给"去完善档案"的指引（比资格门的泛化提示更具体）。
        Long ownSubjectId = subjectProfileService.getOwnSubjectId();
        if (ownSubjectId == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_PROFILE_NOT_BUILT);
        }
        // ③ 已建档：交由资格门判"授权 + 完善"（含授权停用），错误码 106_031 / 106_034。
        subjectEligibilityService.assertBidderEligible(tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BiddingProcessTrackDO createTrack(ProcessTrackCreateReqVO reqVO) {
        // 投标入口统一门禁（契约 3.3：意向投标建档是最先该拦的一步）
        assertBidPortalAllowed();
        // 每用户每标段唯一
        BiddingProcessTrackDO exist = trackMapper.selectByUserIdAndSection(reqVO.getUserId(), reqVO.getBidSectionCode());
        if (exist != null) {
            return exist;
        }
        BiddingProcessTrackDO track = new BiddingProcessTrackDO();
        track.setUserId(reqVO.getUserId());
        track.setBidSectionCode(reqVO.getBidSectionCode());
        track.setTenderProjectCode(reqVO.getTenderProjectCode());
        track.setFollowKey(reqVO.getBidSectionCode());
        track.setFollowType("BID_SECTION");
        track.setProjectName(reqVO.getProjectName());
        track.setIssuerName(reqVO.getIssuerName());
        track.setCurrentState(BiddingProcessStateEnum.INTENTION.name());
        track.setIntentionTime(LocalDateTime.now());
        // 管理端总览快照（09-18 契约 §3 方案 B 建议）：负责人/公司/地区/金额/截止 建档时点取值，避免逐行 RPC 与改名漂移
        track.setOwnerUserId(reqVO.getUserId());
        track.setOwnerName(resolveUserName(reqVO.getUserId()));
        track.setOwnerPost(resolveUserPost(reqVO.getUserId()));
        track.setOwnerPhone(resolveUserPhone(reqVO.getUserId()));
        track.setTenantName(resolveTenantName());
        resolveSectionSnapshot(track, reqVO.getBidSectionCode());
        trackMapper.insert(track);
        appendEvent(track.getId(), reqVO.getUserId(), BiddingProcessEventEnum.FOLLOW_PROJECT,
                null, BiddingProcessStateEnum.INTENTION.name(), "意向投标（关注项目建档）");
        return track;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BiddingProcessTrackDO register(ProcessTrackRegisterReqVO reqVO) {
        // 投标入口统一门禁（契约 3.3：报名投标）
        assertBidPortalAllowed();
        BiddingProcessTrackDO track = requireTrack(reqVO.getId(), reqVO.getUserId());
        doTransit(track, BiddingProcessEventEnum.REGISTER, reqVO.getUserId(),
                "报名投标", (t) -> t.setRegisterTime(LocalDateTime.now()));
        return track;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BiddingProcessTrackDO review(ProcessTrackReviewReqVO reqVO) {
        BiddingProcessTrackDO track = requireTrack(reqVO.getId(), null);
        ReviewResultEnum result = ReviewResultEnum.valueOf(reqVO.getReviewResult());
        String event = result == ReviewResultEnum.APPROVED ? "APPROVE" : "REJECT";
        String desc = result == ReviewResultEnum.APPROVED ? "资质审核通过" : "资质审核不通过：" + StrUtil.nullToEmpty(reqVO.getReviewReason());
        doTransit(track, BiddingProcessEventEnum.valueOf(event), reqVO.getOperatorId(),
                desc, (t) -> {
                    t.setReviewResult(reqVO.getReviewResult());
                    t.setReviewReason(reqVO.getReviewReason());
                    t.setReviewTime(LocalDateTime.now());
                    if (result == ReviewResultEnum.APPROVED) {
                        t.setPendingBidTime(LocalDateTime.now());
                    }
                });
        return track;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BiddingProcessTrackDO uploadDocument(ProcessTrackUploadReqVO reqVO) {
        // 投标入口统一门禁（契约 3.3：递交加密投标文件 —— 最终门，务必拦）
        assertBidPortalAllowed();
        BiddingProcessTrackDO track = requireTrack(reqVO.getId(), reqVO.getUserId());
        // 最严格递交门槛 1：投标截止后禁止递交（招标项目状态 > BIDDING 视为截止；BIDDING_ENDED 由时间驱动推进）
        assertNotBidEnded(track);
        // 最严格递交门槛 2：CA 加密投标文件完整性（SM3 指纹 + CA 签名必填；加密 fileId 必填）
        if (reqVO.getDocumentFileId() == null || StrUtil.isBlank(reqVO.getDocumentHash()) || reqVO.getDocumentSize() == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROCESS_TRACK_DOCUMENT_REQUIRED);
        }
        if (StrUtil.isBlank(reqVO.getDocumentSignature())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROCESS_TRACK_SIGNATURE_REQUIRED);
        }
        if (StrUtil.isBlank(reqVO.getCaCertNo())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROCESS_TRACK_CA_REQUIRED);
        }
        // 最严格递交门槛 3（契约 A9）：指纹算法只认 SM3，显式拒绝其他值。
        // 落库值一直是硬编码 SM3，但入参此前不受约束——客户端可以传 "SHA256"/"MD5" 之类，
        // 虽然不影响落库，却会让"契约上允许传算法"变成证据链标识不可信的源头。宁可入口拒绝。
        if (StrUtil.isNotBlank(reqVO.getDocumentHashAlgo())
                && !"SM3".equalsIgnoreCase(reqVO.getDocumentHashAlgo())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROCESS_TRACK_HASH_ALGO_INVALID,
                    reqVO.getDocumentHashAlgo());
        }
        doTransit(track, BiddingProcessEventEnum.UPLOAD_DOCUMENT, reqVO.getUserId(),
                "递交加密投标文件：" + StrUtil.nullToEmpty(reqVO.getDocumentName()), (t) -> {
                    t.setDocumentFileId(reqVO.getDocumentFileId());
                    t.setDocumentName(reqVO.getDocumentName());
                    t.setDocumentSize(reqVO.getDocumentSize());
                    t.setDocumentHash(reqVO.getDocumentHash());
                    t.setDocumentHashAlgo("SM3"); // 国密指纹（用户裁定）
                    t.setDocumentSignature(reqVO.getDocumentSignature());
                    t.setCaCertNo(reqVO.getCaCertNo());
                    t.setCaPublicKey(reqVO.getCaPublicKey());
                    t.setBiddedTime(LocalDateTime.now());
                });
        return track;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BiddingProcessTrackDO cancel(Long id, Long userId) {
        BiddingProcessTrackDO track = requireTrack(id, userId);
        doTransit(track, BiddingProcessEventEnum.CANCEL, userId, "取消投标", (t) -> {});
        return track;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BiddingProcessTrackDO saveSealResult(Long id, String documentSignature, String caCertNo,
                                                String documentHash, String caPublicKey) {
        // 兼容旧签名：不带签章产物坐标（历史测试/演示链路沿用）
        return saveSealResult(id, documentSignature, caCertNo, documentHash, caPublicKey, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BiddingProcessTrackDO saveSealResult(Long id, String documentSignature, String caCertNo,
                                                String documentHash, String caPublicKey,
                                                Long documentFileId, String documentName, Long documentSize) {
        BiddingProcessTrackDO track = trackMapper.selectById(id);
        if (track == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROCESS_TRACK_NOT_EXISTS);
        }
        if (StrUtil.isBlank(documentSignature)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROCESS_TRACK_SIGNATURE_REQUIRED);
        }
        if (StrUtil.isBlank(caCertNo)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROCESS_TRACK_CA_REQUIRED);
        }
        if (StrUtil.isBlank(documentHash)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROCESS_TRACK_DOCUMENT_REQUIRED);
        }
        track.setDocumentSignature(documentSignature);
        track.setCaCertNo(caCertNo);
        track.setDocumentHash(documentHash);
        track.setDocumentHashAlgo("SM3");
        track.setCaPublicKey(caPublicKey);
        // 契约 A4：补记签章产物坐标，使流程行自证"这份文件已签章"（/ca/seal/check 按 fileId 才能匹配到）
        if (documentFileId != null) {
            track.setDocumentFileId(documentFileId);
        }
        if (StrUtil.isNotBlank(documentName)) {
            track.setDocumentName(documentName);
        }
        if (documentSize != null) {
            track.setDocumentSize(documentSize);
        }
        trackMapper.updateById(track);
        log.info("[BiddingProcessTrack][saveSealResult] 文件签章结果已落库 trackId={} cert={} hash={} sealFileId={}",
                id, caCertNo, documentHash, documentFileId);
        return track;
    }

    @Override
    public List<BiddingProcessTrackDO> listMy(Long userId) {
        return trackMapper.selectList(BiddingProcessTrackDO::getUserId, userId);
    }

    @Override
    public List<ProcessTrackRespVO> listMyWithAttachments(Long userId) {
        List<BiddingProcessTrackDO> tracks = listMy(userId);
        return tracks.stream().map(t -> {
            ProcessTrackRespVO vo = BeanUtils.toBean(t, ProcessTrackRespVO.class);
            // 投标文件集附件（契约多文件化 R2：主件之外的附加文件；可为空数组）
            vo.setAttachments(attachmentService.list("BID_TRACK", String.valueOf(t.getId())));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public BiddingProcessTrackDO getTrack(Long id) {
        return trackMapper.selectById(id);
    }

    // ==================== 内部 ====================

    private BiddingProcessTrackDO requireTrack(Long id, Long userId) {
        BiddingProcessTrackDO track = trackMapper.selectById(id);
        if (track == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROCESS_TRACK_NOT_EXISTS);
        }
        if (userId != null && !userId.equals(track.getUserId())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROCESS_TRACK_NOT_EXISTS);
        }
        return track;
    }

    /** 状态机校验 + 变更 + 留痕。 */
    private void doTransit(BiddingProcessTrackDO track, BiddingProcessEventEnum event, Long operatorId,
                           String desc, Consumer<BiddingProcessTrackDO> beforeTime) {
        String from = track.getCurrentState();
        boolean allowed = TRANSITIONS.getOrDefault(event.name(), List.of()).stream()
                .anyMatch(e -> e.getKey().equals(from));
        if (!allowed) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROCESS_TRACK_STATE_INVALID, from);
        }
        String to = TRANSITIONS.get(event.name()).stream()
                .filter(e -> e.getKey().equals(from)).findFirst().get().getValue();
        track.setPreviousState(from);
        track.setCurrentState(to);
        if (to.equals(BiddingProcessStateEnum.TERMINATED.name())) {
            track.setTerminatedTime(LocalDateTime.now());
        }
        if (beforeTime != null) {
            beforeTime.accept(track);
        }
        trackMapper.updateById(track);
        appendEvent(track.getId(), track.getUserId(), event, from, to, desc);
    }

    /** 投标截止校验：招标项目状态序号 > BIDDING 视为已截止（BIDDING_ENDED 由时间驱动触发到 PENDING_OPEN）。 */
    private void assertNotBidEnded(BiddingProcessTrackDO track) {
        if (StrUtil.isBlank(track.getTenderProjectCode())) {
            return; // 无招标项目关联时不拦截（历史数据兼容）
        }
        TenderProjectDO tp = tenderProjectMapper.selectOne(TenderProjectDO::getTenderProjectCode, track.getTenderProjectCode());
        if (tp == null) {
            return;
        }
        TenderStatusEnum cur = TenderStatusEnum.of(tp.getState());
        if (cur == null || cur.ordinal() > TenderStatusEnum.BIDDING.ordinal()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROCESS_TRACK_BID_DEADLINE_PASSED);
        }
    }

    private void appendEvent(Long trackId, Long userId, BiddingProcessEventEnum event, String from, String to, String desc) {
        BiddingProcessEventDO e = new BiddingProcessEventDO();
        e.setTrackId(trackId);
        e.setUserId(userId);
        e.setEventType(event.name());
        e.setFromState(from);
        e.setToState(to);
        e.setEventTime(LocalDateTime.now());
        e.setOperatorId(userId);
        e.setOperatorName(resolveUserName(userId)); // 操作人名称快照（RPC 降级容错：取不到留空，前端显示 —）
        e.setOperationDesc(desc);
        eventMapper.insert(e);
    }

    // ==================== 管理端总览（09-18 契约 §5） ====================

    @Override
    public PageResult<ProcessTrackItemVO> getOverviewPage(ProcessTrackPageReqVO reqVO, boolean isSuper) {
        Page<BiddingProcessTrackDO> page = trackMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                buildOverviewWrapper(reqVO, isSuper));
        List<ProcessTrackItemVO> list = page.getRecords().stream()
                .map(this::toItemVO)
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public ProcessTrackStatsRespVO getOverviewStats(boolean isSuper) {
        ProcessTrackStatsRespVO resp = new ProcessTrackStatsRespVO();
        // 超管跨租户（executeIgnore），租户自动过滤
        List<BiddingProcessTrackDO> all = TenantUtils.executeIgnore(() -> trackMapper.selectList(
                new LambdaQueryWrapper<BiddingProcessTrackDO>()
                        .eq(!isSuper, BiddingProcessTrackDO::getTenantId, currentTenantId())));
        resp.setProjectCount((long) all.size());
        resp.setTenantCount(all.stream().map(BiddingProcessTrackDO::getTenantId).filter(Objects::nonNull).distinct().count());
        resp.setOngoing(all.stream().filter(t -> isOngoing(t.getCurrentState())).count());
        resp.setBidded(all.stream().filter(t -> BiddingProcessStateEnum.BIDDED.name().equals(t.getCurrentState())).count());
        resp.setTerminated(all.stream().filter(t -> BiddingProcessStateEnum.TERMINATED.name().equals(t.getCurrentState())).count());
        resp.setOwnerCount(all.stream().map(BiddingProcessTrackDO::getOwnerUserId).filter(Objects::nonNull).distinct().count());
        Map<String, Long> byState = new LinkedHashMap<>();
        for (BiddingProcessStateEnum s : BiddingProcessStateEnum.values()) {
            byState.put(s.name(), all.stream().filter(t -> s.name().equals(t.getCurrentState())).count());
        }
        resp.setByState(byState);
        return resp;
    }

    @Override
    public ProcessTrackOptionsRespVO getOverviewOptions(boolean isSuper) {
        ProcessTrackOptionsRespVO resp = new ProcessTrackOptionsRespVO();
        List<BiddingProcessTrackDO> all = TenantUtils.executeIgnore(() -> trackMapper.selectList(
                new LambdaQueryWrapper<BiddingProcessTrackDO>()
                        .eq(!isSuper, BiddingProcessTrackDO::getTenantId, currentTenantId())));
        // owners：按负责人去重（value=ownerUserId String，label=姓名，extra=公司名）
        Map<Long, BiddingProcessTrackDO> ownerMap = new LinkedHashMap<>();
        for (BiddingProcessTrackDO t : all) {
            if (t.getOwnerUserId() != null) {
                ownerMap.putIfAbsent(t.getOwnerUserId(), t);
            }
        }
        List<ProcessTrackOptionsRespVO.Option> owners = new ArrayList<>();
        for (BiddingProcessTrackDO t : ownerMap.values()) {
            ProcessTrackOptionsRespVO.Option o = new ProcessTrackOptionsRespVO.Option();
            o.setValue(String.valueOf(t.getOwnerUserId()));
            o.setLabel(StrUtil.nullToEmpty(t.getOwnerName()));
            o.setExtra(StrUtil.nullToEmpty(t.getTenantName()));
            owners.add(o);
        }
        resp.setOwners(owners);
        // tenants：仅超管下发（租户视角前端已隐藏该下拉）
        if (isSuper) {
            Map<Long, String> tenantMap = new LinkedHashMap<>();
            for (BiddingProcessTrackDO t : all) {
                if (t.getTenantId() != null && StrUtil.isNotBlank(t.getTenantName())) {
                    tenantMap.putIfAbsent(t.getTenantId(), t.getTenantName());
                }
            }
            List<ProcessTrackOptionsRespVO.Option> tenants = new ArrayList<>();
            tenantMap.forEach((tid, name) -> {
                ProcessTrackOptionsRespVO.Option o = new ProcessTrackOptionsRespVO.Option();
                o.setValue(String.valueOf(tid));
                o.setLabel(name);
                tenants.add(o);
            });
            resp.setTenants(tenants);
        }
        return resp;
    }

    @Override
    public ProcessTrackDetailRespVO getOverviewDetail(Long id, boolean isSuper) {
        BiddingProcessTrackDO track = trackMapper.selectById(id);
        if (track == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROCESS_TRACK_NOT_EXISTS);
        }
        if (!isSuper && !Objects.equals(currentTenantId(), track.getTenantId())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PROCESS_TRACK_NOT_EXISTS);
        }
        ProcessTrackDetailRespVO resp = new ProcessTrackDetailRespVO();
        resp.setItem(toItemVO(track));
        // 操作记录事件史（只读展示，按时间升序）
        resp.setEvents(eventMapper.selectListByTrackId(id).stream().map(e -> {
            ProcessTrackEventVO vo = new ProcessTrackEventVO();
            vo.setEventType(e.getEventType());
            BiddingProcessEventEnum evEnum = BiddingProcessEventEnum.of(e.getEventType());
            vo.setEventName(evEnum != null ? evEnum.getLabel() : e.getEventType());
            vo.setFromState(e.getFromState());
            vo.setToState(e.getToState());
            vo.setOperatorName(e.getOperatorName());
            vo.setOperationDesc(e.getOperationDesc());
            vo.setOccurredAt(e.getEventTime());
            return vo;
        }).collect(Collectors.toList()));
        return resp;
    }

    // ==================== 内部辅助 ====================

    /** 总览分页条件（keyword/state/tenantId/ownerUserId；超管跨租户，租户强制本租户）。 */
    private LambdaQueryWrapper<BiddingProcessTrackDO> buildOverviewWrapper(ProcessTrackPageReqVO reqVO, boolean isSuper) {
        LambdaQueryWrapper<BiddingProcessTrackDO> wrapper = new LambdaQueryWrapper<>();
        if (!isSuper) {
            wrapper.eq(BiddingProcessTrackDO::getTenantId, currentTenantId()); // 租户强制过滤，tenantId 入参忽略
        } else if (reqVO.getTenantId() != null) {
            wrapper.eq(BiddingProcessTrackDO::getTenantId, reqVO.getTenantId());
        }
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            String kw = reqVO.getKeyword().trim();
            wrapper.and(w -> w.like(BiddingProcessTrackDO::getTenantName, kw)
                    .or().like(BiddingProcessTrackDO::getProjectName, kw)
                    .or().like(BiddingProcessTrackDO::getTenderProjectCode, kw)
                    .or().like(BiddingProcessTrackDO::getIssuerName, kw)
                    .or().like(BiddingProcessTrackDO::getOwnerName, kw));
        }
        if (StrUtil.isNotBlank(reqVO.getState())) {
            wrapper.eq(BiddingProcessTrackDO::getCurrentState, reqVO.getState());
        }
        if (reqVO.getOwnerUserId() != null) {
            wrapper.eq(BiddingProcessTrackDO::getOwnerUserId, reqVO.getOwnerUserId());
        }
        // 契约《投标与资格预审多文件化-2026-10-02》R5：标段详情页按标段聚合投标记录（精确匹配快照列）
        if (StrUtil.isNotBlank(reqVO.getBidSectionCode())) {
            wrapper.eq(BiddingProcessTrackDO::getBidSectionCode, reqVO.getBidSectionCode().trim());
        }
        wrapper.orderByDesc(BiddingProcessTrackDO::getCreateTime);
        return wrapper;
    }

    /** 进行中三态（意向+审核+待投标；09-18 契约 §5 接口2 ongoing 口径）。 */
    private boolean isOngoing(String state) {
        return BiddingProcessStateEnum.INTENTION.name().equals(state)
                || BiddingProcessStateEnum.QUALIFICATION_REVIEW.name().equals(state)
                || BiddingProcessStateEnum.PENDING_BID.name().equals(state);
    }

    /** DO → 总览项 VO（雪花 ID 一律 String）。 */
    private ProcessTrackItemVO toItemVO(BiddingProcessTrackDO t) {
        ProcessTrackItemVO vo = new ProcessTrackItemVO();
        vo.setId(String.valueOf(t.getId()));
        vo.setTenantId(String.valueOf(t.getTenantId()));
        vo.setTenantName(t.getTenantName());
        vo.setFollowItemId(t.getFollowItemId() == null ? null : String.valueOf(t.getFollowItemId()));
        vo.setFollowKey(t.getFollowKey());
        vo.setFollowType(t.getFollowType());
        vo.setProjectName(t.getProjectName());
        vo.setProjectCode(t.getTenderProjectCode());
        vo.setIssuerName(t.getIssuerName());
        vo.setRegion(t.getRegion());
        vo.setAmount(t.getAmount());
        vo.setDeadline(t.getDeadline() == null ? null : t.getDeadline().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        vo.setState(t.getCurrentState());
        BiddingProcessStateEnum stEnum = BiddingProcessStateEnum.of(t.getCurrentState());
        vo.setStateName(stEnum != null ? stEnum.getLabel() : t.getCurrentState());
        vo.setOwnerUserId(t.getOwnerUserId() == null ? null : String.valueOf(t.getOwnerUserId()));
        vo.setOwnerName(t.getOwnerName());
        vo.setOwnerPost(t.getOwnerPost());
        vo.setOwnerPhone(t.getOwnerPhone());
        vo.setIntentionTime(t.getIntentionTime());
        vo.setRegisterTime(t.getRegisterTime());
        vo.setReviewTime(t.getReviewTime());
        vo.setBiddedTime(t.getBiddedTime());
        vo.setTerminatedTime(t.getTerminatedTime());
        vo.setReviewResult(t.getReviewResult());
        vo.setReviewReason(t.getReviewReason());
        vo.setDocumentName(t.getDocumentName());
        vo.setRemark(t.getRemark());
        // 投标文件集附件（契约多文件化 R2：主件 document_file_id 之外的附加文件，逐份病毒状态；可为空数组）
        vo.setAttachments(attachmentService.list("BID_TRACK", String.valueOf(t.getId())));
        return vo;
    }

    /** 当前登录租户（无上下文返回 null）。 */
    private Long currentTenantId() {
        return cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.getTenantId();
    }

    /** 负责人姓名快照（RPC 降级容错：异常/取不到返回 null）。 */
    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            AdminUserRespDTO user = adminUserApi.getUser(userId).getCheckedData();
            return user != null ? user.getNickname() : null;
        } catch (Exception e) {
            log.warn("[resolveUserName][userId={}] AdminUserApi 异常，负责人姓名留空", userId, e);
            return null;
        }
    }

    /** 负责人职务（AdminUserRespDTO 无职务字段时返回 null，页面降级显示 —）。 */
    private String resolveUserPost(Long userId) {
        return null; // AdminUserRespDTO 未暴露职务；留空待主体档案完善后回填
    }

    /** 负责人联系方式（AdminUserRespDTO 未暴露手机号时返回 null，页面降级显示 —）。 */
    private String resolveUserPhone(Long userId) {
        return null; // AdminUserRespDTO 未暴露手机号；留空待主体档案完善后回填
    }

    /** 公司名快照（平台授权 SubjectAuth.tenantName 时点取值；无授权记录返回 null）。 */
    private String resolveTenantName() {
        Long tenantId = currentTenantId();
        if (tenantId == null) {
            return null;
        }
        try {
            SubjectAuthDO auth = TenantUtils.executeIgnore(() -> subjectAuthMapper.selectOne(
                    new LambdaQueryWrapper<SubjectAuthDO>()
                            .eq(SubjectAuthDO::getTenantId, tenantId)
                            .last("LIMIT 1")));
            return auth != null ? auth.getTenantName() : null;
        } catch (Exception e) {
            log.warn("[resolveTenantName][tenantId={}] 取公司名异常，快照留空", tenantId, e);
            return null;
        }
    }

    /** 标段快照（region/amount；deadline 待 flow-timer 域 DO 同步后回填）。 */
    private void resolveSectionSnapshot(BiddingProcessTrackDO track, String bidSectionCode) {
        if (StrUtil.isBlank(bidSectionCode)) {
            return;
        }
        try {
            BidSectionDO section = bidSectionMapper.selectOne(BidSectionDO::getBidSectionCode, bidSectionCode);
            if (section != null) {
                track.setRegion(section.getRegionCode());
                track.setAmount(section.getContractReckonPrice());
            }
        } catch (Exception e) {
            log.warn("[resolveSectionSnapshot][section={}] 取标段快照异常", bidSectionCode, e);
        }
    }
}
