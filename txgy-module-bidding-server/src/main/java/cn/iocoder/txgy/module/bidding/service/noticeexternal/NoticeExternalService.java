package cn.iocoder.txgy.module.bidding.service.noticeexternal;
// 规范依据：设计文档 v0.6 §7/§15.1（G 轨主档、SOCIAL+ACTIVE 公开过滤）；《招投标模块开发规范》§4.4

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManageDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManagePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManageRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalRespVO;

/**
 * 外部公告（G 轨聚合主档）服务。
 *
 * <p>职责：外部网站公告留档后的“对外只读展示”：公开分页（SOCIAL+ACTIVE 过滤、只出主列）、
 * 详情三形态（HTML 原样/PDF JS 预览+下载/附件下载列表，均带 origUrl）。
 * 关键词全文检索走 ES（MQ 事件建索引预留），不开放业务表 LIKE。
 */
public interface NoticeExternalService {

    /**
     * 公开分页（大厅只读）：仅 publicScope=SOCIAL 且 status=ACTIVE，按 publishTime 倒序。
     *
     * @param reqVO 分页与过滤
     * @return 主列列表行
     */
    PageResult<NoticeExternalRespVO> getPage(NoticeExternalPageReqVO reqVO);

    /**
     * 公开详情（大厅只读）：SOCIAL+ACTIVE 校验；按内容形态回填原文/PDF/附件。
     *
     * @param id 外部公告主档 ID
     * @return 三形态详情
     */
    NoticeExternalDetailRespVO getDetail(Long id);

    /**
     * 管理分页（审核/运营列表）：不做 SOCIAL+ACTIVE 硬过滤，可按 status/auditStatus/来源/类型/时间等维度
     * 查看 NEW/ACTIVE/INACTIVE/ARCHIVED 全量；行内含审核/抽取/AI/结构化状态（供 approve/reject/take-down/ai-extract/body-structure）。
     *
     * @param reqVO 管理分页与过滤
     * @return 管理列表行
     */
    PageResult<NoticeExternalManageRespVO> getManagePage(NoticeExternalManagePageReqVO reqVO);

    /**
     * 管理详情（BR-7，审核预览）：全状态返回（NEW/ACTIVE/INACTIVE/ARCHIVED 均可读），
     * 供审核页“读完正文再 approve”；字段含净化正文/正文结构化块/PDF/附件病毒状态/抽取摘要。
     *
     * @param id 外部公告主档 ID
     * @return 管理详情（全状态）
     */
    NoticeExternalManageDetailRespVO getManageDetail(Long id);

    /**
     * 审核通过：status NEW→ACTIVE、audit UNREVIEWED→APPROVED，随后触发关注扇出与 ES 索引事件（均不阻断审核）。
     * 已生效/已处理（ACTIVE+APPROVED）调用幂等返回。
     *
     * @param id 外部公告主档 ID
     */
    void approveExternal(Long id);

    /**
     * 驳回（审核不通过）：audit UNREVIEWED→REJECTED；驳回后不可直接 approve（需重新入库/重抓）。
     *
     * @param id 外部公告主档 ID
     */
    void rejectExternal(Long id);

    /**
     * 下线（曾上线被撤下）：ACTIVE→INACTIVE、audit→TAKEN_DOWN；展示接口随即拒绝（TOKEN_DOWN）。
     *
     * @param id 外部公告主档 ID
     */
    void takeDownExternal(Long id);

    /**
     * 存量 PDF 归档回填：遍历 content_type=PDF 且 pdf_download_url 为空的记录，从 meta_json 外站 pdfDownloadUrl 重下载 →
     * 上传平台文件服务 → 回填 pdf_download_url（2026-09-10；失败单条记录不阻断，返回处理汇总）。
     *
     * @return 处理条数汇总（如 "total=14,ok=12,failed=2"）
     */
    String backfillPdfDownloadUrls();

    /**
     * PDF 预览：按公告 id 取归档 PDF（pdf_download_url），转发文件字节与 Content-Type。
     * 前端 iframe/embed 直接预览；返回 null=非 PDF 形态或无归档（调用方 404）。
     */
    byte[] pdfContent(Long id);

}
