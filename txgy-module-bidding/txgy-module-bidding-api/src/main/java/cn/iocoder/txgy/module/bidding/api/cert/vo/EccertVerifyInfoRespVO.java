package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 电子证照共享服务·证照信息验证响应（接口 verifyinfo）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6.4（输出参数见表 10）。
 * <p>设计遵循/针对：data.result 十进制字符，0=校验正确；data.message 为结果说明——
 * 0 信息正确且有效 / 1 信息正确且未激活 / 2 信息正确且证照暂时失效 / 3 信息正确且证照已失效 / 4 信息不全，无法验证 / 5 查无此信息；
 * 业务侧据此落 verification_status 口径（与《资质管理/03-字段口径映射与差异建议.md》§3.4 呼应）。
 */
public record EccertVerifyInfoRespVO(
        // GB/T 36906—2018 表10｜公共响应头
        EccertRespHeadVO head,
        // GB/T 36906—2018 表10｜验证结果 data 容器
        Data data
) {
    /**
     * 证照信息验证响应 data（GB/T 36906—2018 表 10）。
     */
    public record Data(
            // 表10｜data.result｜校验结果（十进制字符），0=校验正确，其他值=失效
            String result,
            // 表10｜data.message｜校验结果说明（0 正确且有效~5 查无此信息）
            String message
    ) {
    }
}
