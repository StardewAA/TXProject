package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 电子证照共享服务·证照检索响应（接口 query）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6.1 证照检索（输出参数见表 2）。
 * <p>设计遵循/针对：head=EccertRespHeadVO（status/message）；data 按转录表 2 单条建模（certificateIdentifier/certificateName）。
 * <p>说明（待核对，见 05-规范VO表设计.md §六）：36906 §5.1.2 语义为“证照（标识）列表”，多记录形态以附录 A 完整 JSON 为准；
 * 本转录版未含附录 A 原文，先按单条结构，适配层遇数组另以 List&lt;Data&gt; 组织，不臆造 JSON 键名。
 */
public record EccertQueryRespVO(
        // GB/T 36906—2018 表2｜公共响应头
        EccertRespHeadVO head,
        // GB/T 36906—2018 表2｜检索结果 data 容器
        Data data
) {
    /**
     * 证照检索响应 data（GB/T 36906—2018 表 2）。
     */
    public record Data(
            // 表2｜data.certificateIdentifier｜电子证照标识（符合 GB/T 36904—2018）
            String certificateIdentifier,
            // 表2｜data.certificateName｜证照名称
            String certificateName
    ) {
    }
}
