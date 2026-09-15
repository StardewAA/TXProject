package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 电子证照共享服务·证照检索请求（接口 query）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6.1 证照检索（输入参数见表 1）。
 * <p>设计遵循/针对：报文按 36906 JSON 容器组织为 head + data 两级（head=EccertReqHeadVO）；data 字段照录表 1 英文名；
 * certificateHolderCode、certificateTypeCode 为必填（表 1 约束），holder 名称/代码类型代码可选；语义见 36901 §7.2（按持证主体+证照类型检索）。
 */
public record EccertQueryReqVO(
        // GB/T 36906—2018 §6 通用约定｜公共请求头
        EccertReqHeadVO head,
        // GB/T 36906—2018 表1｜检索条件 data 容器
        Data data
) {
    /**
     * 证照检索请求 data（GB/T 36906—2018 表 1）。字段名照录标准英文名。
     */
    public record Data(
            // 表1｜data.certificateHolder｜持证主体（符合 GB/T 36903—2018）｜否
            String certificateHolder,
            // 表1｜data.certificateHolderCode｜持证主体代码（符合 GB/T 36903—2018）｜是
            String certificateHolderCode,
            // 表1｜data.certificateHolderTypeCode｜持证主体代码类型代码（符合 GB/T 36903—2018）｜否
            String certificateHolderTypeCode,
            // 表1｜data.certificateTypeCode｜证照类型代码（符合 GB/T 36902—2018）｜是
            String certificateTypeCode
    ) {
    }
}
