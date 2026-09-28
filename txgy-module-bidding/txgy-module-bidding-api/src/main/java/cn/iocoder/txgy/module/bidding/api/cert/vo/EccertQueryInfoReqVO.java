package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 电子证照共享服务·证照信息获取请求（接口 queryinfo）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6.3 证照信息获取（输入参数见表 7）。
 * <p>设计遵循/针对：certificateIdentifier 必填；metadataRange 可选——以 JSON 串给出所需元数据项英文名或短名
 * （英文名/短名见 GB/T 36903—2018 §5.2/§5.4），缺省返回全部证照元数据。
 */
public record EccertQueryInfoReqVO(
        // GB/T 36906—2018 §6 通用约定｜公共请求头
        EccertReqHeadVO head,
        // GB/T 36906—2018 表7｜请求 data 容器
        Data data
) {
    /**
     * 证照信息获取请求 data（GB/T 36906—2018 表 7）。
     */
    public record Data(
            // 表7｜data.certificateIdentifier｜电子证照标识（符合 GB/T 36904—2018）｜是
            String certificateIdentifier,
            // 表7｜data.metadataRange｜要返回的证照元数据项范围（JSON 串，元数据项英文名或短名）；无输入默认返回全部｜否
            String metadataRange
    ) {
    }
}
