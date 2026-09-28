package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 电子证照共享服务·证照文件验证响应（接口 verifyfile）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6.5（输出参数见表 12）。
 * <p>设计遵循/针对：data.result 校验结果，0=正确；data.message 结果说明。与 EccertVerifyInfoRespVO 的区别：
 * 本接口验证的是文件本身（真实性/完整性/有效性），信息验证验证的是元数据核对（表 9/10）。
 */
public record EccertVerifyFileRespVO(
        // GB/T 36906—2018 表12｜公共响应头
        EccertRespHeadVO head,
        // GB/T 36906—2018 表12｜验证结果 data 容器
        Data data
) {
    /**
     * 证照文件验证响应 data（GB/T 36906—2018 表 12）。
     */
    public record Data(
            // 表12｜data.result｜校验结果，0=正确
            String result,
            // 表12｜data.message｜校验结果说明
            String message
    ) {
    }
}
