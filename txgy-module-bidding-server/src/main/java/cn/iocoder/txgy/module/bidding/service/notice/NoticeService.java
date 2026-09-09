package cn.iocoder.txgy.module.bidding.service.notice;
// 规范依据：《招投标模块开发规范》§3.1/3.2、§4.4/4.5/4.8、五章五；05 §3.1/3.3

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeDeadlineRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticePublicSyncReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticePublicSyncRespVO;
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

    /**
     /**
      * 全量重建本租户公告 ES 索引（管理端人工触发，2026-09-10）：遍历本租户全部公告逐条 indexDoc（幂等 PUT 覆盖），
      * 补偿 es.enabled 启用前的存量（keyword 检索此前不可用/空命中）；各租户须以自己的上下文触发覆盖本租户数据。
      */
     String reindexIndex();

    /**
     * 平台级全租户索引补偿（2026-09-10，es.enabled 启用前各租户存量公告从未建索引）：忽略租户遍历全部公告 indexDoc（幂等 PUT）。
     * 仅平台租户可调用（Controller 校验）；租户自服务用 {@link #reindexIndex()}。
     */
    String reindexAllTenants();

     /** 公开同步（05 §2.2 BID-SUP-GATHER-02）：白名单校验（PUBLISHED+非内部+SOCIAL）后
     * 触发检索文档同步，返回任务编号。幂等键=公告编号+dataTimestamp（由调用方框架幂等保证）。
     */
    NoticePublicSyncRespVO syncPublicDocument(NoticePublicSyncReqVO reqVO);

    /**
     * 截止时间查询（05 §3.1 BID-ENT-NOTICE-02，模块内部只读）：
     * 按标段编号在已发布的招标/变更公告中定位，返回文件发出/澄清/递交截止时间。
     */
    NoticeDeadlineRespVO getBidDeadline(String bidSectionCode);
}
