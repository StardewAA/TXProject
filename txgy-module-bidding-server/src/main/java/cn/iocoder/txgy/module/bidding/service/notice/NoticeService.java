package cn.iocoder.txgy.module.bidding.service.notice;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeSaveReqVO;

/**
 * 公告/公示服务（后台管理 + 大厅公开只读）。
 */
public interface NoticeService {

    /** 新建公告（草稿入库；正文 v1 版本化；权威字段全集规整入 fieldsJson） */
    Long createNotice(NoticeSaveReqVO reqVO);

    /** 修改公告（正文有实质变更时追加新版本，只追加不覆盖） */
    void updateNotice(NoticeSaveReqVO reqVO);

    /** 删除公告（逻辑删除；同步删检索文档） */
    void deleteNotice(Long id);

    /** 发布（DRAFT→PUBLISHED；要求存在正文） */
    void publishNotice(Long id);

    /** 撤下（PUBLISHED→UNPUBLISHED；UNPUBLISHED→PUBLISHED 重发走 publish） */
    void unpublishNotice(Long id);

    /** 管理端分页（本租户全部：草稿/发布/撤下/归档，可按内部公告过滤） */
    PageResult<NoticeRespVO> getNoticePage(NoticePageReqVO reqVO);

    /** 管理端详情 */
    NoticeDetailRespVO getNoticeDetail(Long id);

    /** 大厅公开分页（跨租户；仅 PUBLISHED、非内部、到发布时间、SOCIAL 公开级别；关键词全文走检索组件） */
    PageResult<NoticeRespVO> getPublicNoticePage(NoticePageReqVO reqVO);

    /** 大厅公开详情（同上可见性规则） */
    NoticeDetailRespVO getPublicNoticeDetail(Long id);
}
