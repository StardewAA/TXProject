package cn.iocoder.txgy.module.bidding.service.processtrack;

import cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo.ProcessTrackCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo.ProcessTrackRegisterReqVO;
import cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo.ProcessTrackUploadReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackReviewReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackItemVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackOptionsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackStatsRespVO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo.ProcessTrackRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.processtrack.BiddingProcessTrackDO;

import java.util.List;

/**
 * 投标流程跟踪服务（投标方视角：意向投标→报名→资质审核→待投标→递交加密投标文件→已投标）。
 *
 * <p>设计依据：《投标流程跟踪功能设计方案》；《电子招标投标办法》第24/28条。
 * 投标文件：CA 加密 + SM3 指纹 + SM2 签名由外部 CA SDK 完成，本服务只负责状态机推进与留痕；
 * 外部 SDK 接入点：caClient（见 infra/ca/CaClientFacade，当前为占位实现，SDK 到位后替换）。
 */
public interface BiddingProcessTrackService {

    /**
     * 建档（意向投标）：员工对某标段建立投标流程跟踪（每用户每标段一条）。
     * **由用户显式发起意向投标时建档**（INTENTION）——与「关注」是两个独立动作，关注不会自动建档；
     * 已存在则直接返回既有记录（幂等）。
     */
    BiddingProcessTrackDO createTrack(ProcessTrackCreateReqVO reqVO);

    /** 报名投标（INTENTION→QUALIFICATION_REVIEW）。 */
    BiddingProcessTrackDO register(ProcessTrackRegisterReqVO reqVO);

    /** 资质审核（平台方执行；QUALIFICATION_REVIEW→PENDING_BID 或 TERMINATED）。 */
    BiddingProcessTrackDO review(ProcessTrackReviewReqVO reqVO);

    /** 上传加密投标文件（PENDING_BID→BIDDED；截止时间校验 + CA 签名校验，见 impl 注释）。 */
    BiddingProcessTrackDO uploadDocument(ProcessTrackUploadReqVO reqVO);

    /** 取消投标（INTENTION/QUALIFICATION_REVIEW/PENDING_BID→TERMINATED）。 */
    BiddingProcessTrackDO cancel(Long id, Long userId);

    /**
     * 保存文件签章结果（本地测试 CA 或真实 H5 回调均复用；不改变投标状态机）。
     * 注：document_sha256 表列尚未落地（见 30 号文档待办 #5），SHA-256 随签名信息文件归档，
     * 本方法只落库已有列 document_signature/ca_cert_no/document_hash/document_hash_algo/ca_public_key。
     */
    BiddingProcessTrackDO saveSealResult(Long id, String documentSignature, String caCertNo,
                                         String documentHash, String caPublicKey);

    /**
     * 文件签章结果落库（含<b>签章产物坐标</b>，契约 A4）。
     *
     * <p>为什么要多这两个参数：{@code /ca/seal/check} 按 fileId 匹配台账，而流程行此前**只存指纹/签名，
     * 不存文件坐标**（实测：真实签章行 {@code document_file_id} 为 NULL）⇒ 已签章文件再走"检测"
     * 匹配不到任何东西，被判"未 CA 认证"，前端据此反复提示并诱导重复签章。
     * 把签章产物的 {@code documentFileId} 写回流程行后，流转记录自证"这份文件已签章"。
     *
     * @param documentFileId 签章产物（已签章文件）的 fileId；无产物时可空
     * @param documentName   投标文件名（快照；可空则不改）
     * @param documentSize   投标文件字节数（可空则不改）
     */
    BiddingProcessTrackDO saveSealResult(Long id, String documentSignature, String caCertNo,
                                         String documentHash, String caPublicKey,
                                         Long documentFileId, String documentName, Long documentSize);

    /** 查我的投标流程（按用户）。 */
    List<BiddingProcessTrackDO> listMy(Long userId);

    /**
     * 查我的投标流程并填充投标文件集附件（契约《投标与资格预审多文件化-2026-10-02》R2：
     * 每行附 attachments = attachment/list?bizType=BID_TRACK&bizCode=trackId，可为空数组）。
     */
    List<ProcessTrackRespVO> listMyWithAttachments(Long userId);

    /**
     * 投标入口统一门禁（契约《门户投标个人用户提示与资格门-2026-09-29》3.2/3.3/3.4）。
     *
     * <p><b>为什么需要</b>：此前三个投标入口只有 {@code @PreAuthorize} 权限码，导致两类问题：
     * <ol>
     *   <li>个人用户（保留租户 0）不可能持有企业权限码 ⇒ 必然裸 403「没有该操作权限」，
     *       与"本功能未对个人用户开放"语义不符，且前端对租户越权 403 会清会话
     *       （表现为"明明已登录、点建档却要求登录"）；</li>
     *   <li>{@code SubjectEligibilityService#assertBidderEligible} 写好了却**全仓无调用点**
     *       —— "未授权/档案未完善不得投标"这条规则实际从未生效
     *       （实测：租户 2019 无主体档案仍能建档成功）。</li>
     * </ol>
     *
     * <p><b>判定顺序</b>（先用户类型、再租户资格），保证个人用户不会被误报成"档案没完善"：
     * <pre>
     *   ① tenantId == 0（保留租户）        → 106_049 本功能未对个人用户开放
     *   ② 企业租户但未建档                 → 106_109_037 请联系贵公司对企业档案进行完善
     *   ③ 已建档但未授权 / 授权停用 / 档案未完善 → 106_031 / 106_034（复用资格门）
     * </pre>
     *
     * <p><b>返回码口径</b>：一律抛**业务错误码**（ServiceException），**不经 403 通道** ——
     * 前端据此与"真的没权限"区分，且不会触发租户越权的会话清理逻辑。
     */
    void assertBidPortalAllowed();

    /** 查单个流程（含事件留痕）。 */
    BiddingProcessTrackDO getTrack(Long id);

    // ==================== 管理端总览（09-18 契约 §5：page/stats/options/get 补事件史） ====================

    /**
     * 总览分页（项目粒度，出参自带公司信息）。超管跨租户；租户视角强制只读本公司。
     *
     * @param reqVO  分页入参（keyword/state/tenantId/ownerUserId）
     * @param isSuper 是否超管（scopes 含 bidding:process-track:overview）
     */
    PageResult<ProcessTrackItemVO> getOverviewPage(ProcessTrackPageReqVO reqVO, boolean isSuper);

    /** KPI 统计（口径：ongoing=意向+审核+待投标 三态之和）。 */
    ProcessTrackStatsRespVO getOverviewStats(boolean isSuper);

    /** 筛选项（owners 全量去重；tenants 仅超管下发）。 */
    ProcessTrackOptionsRespVO getOverviewOptions(boolean isSuper);

    /** 详情（item + events[] 操作记录事件史）。 */
    ProcessTrackDetailRespVO getOverviewDetail(Long id, boolean isSuper);
}
