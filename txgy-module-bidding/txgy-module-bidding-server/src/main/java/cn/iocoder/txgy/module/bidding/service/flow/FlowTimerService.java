package cn.iocoder.txgy.module.bidding.service.flow;

/**
 * 时间驱动调度注册服务：招标公告/变更公告创建或更新时，
 * 从权威 payload 的规范时间字段向 bidding_flow_timer 注册/更新调度记录。
 *
 * <p>规范依据：《VO数据对象规范（国家V2.0模版·陕西适配全量版）》——
 * 投标文件/资格预审申请文件递交截止时间 = BID_DOC_REFER_END_TIME（招标公告，必填，社会公开）；
 * 变更公告投标截止时间 = BID_END_TIME（投标截止时间/申请截止时间，覆盖前者）；
 * 文件获取开始时间 = DOC_GET_START_TIME（≈投标起始）。
 * 时间为规范 yyyyMMddHHmmss 字符串，注册时精确到秒写入 trigger_time，
 * 调度器按索引扫到期行（见 TimeEventScheduler），不做全表轮询。
 */
public interface FlowTimerService {

    /**
     * 招标公告/变更公告登记后注册：BIDDING_STARTED@DOC_GET_START_TIME、BIDDING_ENDED@BID_DOC_REFER_END_TIME
     * （变更公告以 BID_END_TIME 覆盖 BIDDING_ENDED 触发时间）。
     *
     * @param tenderProjectCode 招标项目编号（payload.TENDER_PROJECT_CODE；调度作用于 TENDER_PROJECT 状态机）
     * @param docGetStartTime   招标文件/资审文件获取开始时间（yyyyMMddHHmmss，可空）
     * @param bidDocReferEndTime 投标文件/资审申请文件递交截止时间（yyyyMMddHHmmss，可空）
     * @param bidEndTime        变更公告投标截止时间（yyyyMMddHHmmss，可空；非空则覆盖递交截止）
     */
    void registerForTenderNotice(String tenderProjectCode, String docGetStartTime,
                                 String bidDocReferEndTime, String bidEndTime);

    /** 查询某实体的调度记录（按触发时间倒序；全状态含 WAITING/DONE/SKIPPED）。 */
    java.util.List<cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowTimerRespVO> listTimers(String bizType, String bizCode);
}
