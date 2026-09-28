package cn.iocoder.txgy.module.bidding.service.bidagent;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentTaskVO;

import java.util.List;

/** 一键生成标书任务服务（前台/后台共用） */
public interface BidAgentTaskService {

    /** 发起一键生成（幂等：同标段已有进行中任务时报 1_060_111_092，除非 force=true） */
    Long generate(BidAgentTaskVO.GenerateReqVO reqVO);

    /** 任务详情（查询时懒同步底座最新状态） */
    BidAgentTaskVO.RespVO get(Long id);

    /** 任务分页（只返回本租户可见任务） */
    PageResult<BidAgentTaskVO.RespVO> page(BidAgentTaskVO.PageReqVO reqVO);

    /** 取消（通知底座中断并置 CANCELED） */
    void cancel(Long id);

    /** 人工确认（HIL）：把待确认问题的答复回填底座，继续推进等待中的任务 */
    void confirm(Long id, List<BidAgentTaskVO.ConfirmReqVO.Answer> answers);

    /** 重试（失败/取消后重跑：新建底座任务并复用同一台账） */
    void retry(Long id);

    /** 删除任务（软删除；运行中会先中断底座任务） */
    void delete(Long id);

    /** 章节树（outline 结果 + 章节草稿） */
    BidAgentTaskVO.OutlineRespVO outline(Long id);

    /** 产物下载地址（无产物时抛 1_060_111_091） */
    String artifactUrl(Long id);
}
