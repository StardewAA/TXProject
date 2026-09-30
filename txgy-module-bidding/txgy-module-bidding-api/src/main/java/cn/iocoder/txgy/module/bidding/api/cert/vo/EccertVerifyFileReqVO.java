package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 电子证照共享服务·证照文件验证请求（接口 verifyfile）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6.5 证照文件验证（输入参数见表 11）。
 * <p>设计遵循/针对：data.certificate 必填——待验证的电子证照原件或加注件内容（解码后符合 GB/T 36905—2018；
 * 真实性/完整性/有效性验证语义见 GB/T 36901—2018 §7.4 b)）；加注件含加注件元数据（GB/T 36903—2018 §5.4），
 * 服务方对原件/加注件的锁定签名分别校验（GB/T 36905—2018 §7.2）。
 */
public record EccertVerifyFileReqVO(
        // GB/T 36906—2018 §6 通用约定｜公共请求头
        EccertReqHeadVO head,
        // GB/T 36906—2018 表11｜请求 data 容器
        Data data
) {
    /**
     * 证照文件验证请求 data（GB/T 36906—2018 表 11）。
     */
    public record Data(
            // 表11｜data.certificate｜待验证的电子证照原件或加注件内容｜是
            String certificate
    ) {
    }
}
