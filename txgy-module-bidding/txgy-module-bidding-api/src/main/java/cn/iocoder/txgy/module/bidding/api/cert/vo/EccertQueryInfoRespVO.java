package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 电子证照共享服务·证照信息获取响应（接口 queryinfo）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6.3（输出参数见表 8）。
 * <p>设计遵循/针对：data.certificateData 为 JSON 串组织的证照信息，其键名=GB/T 36903—2018 元数据英文名/短名、值域符合该标准；
 * 与本包 CertBaseInfoVO/CertCopyInfoVO 字段一一对应，供解析入库与校验。
 */
public record EccertQueryInfoRespVO(
        // GB/T 36906—2018 表8｜公共响应头
        EccertRespHeadVO head,
        // GB/T 36906—2018 表8｜证照信息 data 容器
        Data data
) {
    /**
     * 证照信息获取响应 data（GB/T 36906—2018 表 8）。
     */
    public record Data(
            // 表8｜data.certificateData｜按 JSON 串方式组织的证照信息（元数据名称/值域符合 GB/T 36903—2018）
            String certificateData
    ) {
    }
}
