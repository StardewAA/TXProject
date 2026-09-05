package cn.iocoder.txgy.module.bidding.service.noticeexternal;
// 规范依据：设计文档 v0.6 §7/§15.1（G 轨主档、SOCIAL+ACTIVE 公开过滤）；《招投标模块开发规范》§4.4

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalDetailRespVO;
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

}
