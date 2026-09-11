package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 电子证照共享服务·证照信息验证请求（接口 verifyinfo）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6.4 证照信息验证（输入参数见表 9）。
 * <p>设计遵循/针对：data.certificateData 必填——待验证证照数据（键=GB/T 36903—2018 元数据英文名，查验项按实际需求取舍）；
 * 与证照信息获取响应（表 8 certificateData）互为对照：下载/获取来的数据可回送验证，语义见 GB/T 36901—2018 §7.4 a)（元数据核对）。
 */
public record EccertVerifyInfoReqVO(
        // GB/T 36906—2018 §6 通用约定｜公共请求头
        EccertReqHeadVO head,
        // GB/T 36906—2018 表9｜请求 data 容器
        Data data
) {
    /**
     * 证照信息验证请求 data（GB/T 36906—2018 表 9）。
     */
    public record Data(
            // 表9｜data.certificateData｜要验证的证照数据（英文名称按 GB/T 36903—2018）｜是
            String certificateData
    ) {
    }
}
