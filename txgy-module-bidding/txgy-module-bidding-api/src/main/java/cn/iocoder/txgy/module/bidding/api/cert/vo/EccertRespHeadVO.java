package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 电子证照共享服务·公共响应头（head）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6（表 2/4/6/8/10/12 公共部分）。
 * <p>设计遵循/针对：响应 JSON 容器含 head.status（十进制字符，0=成功）、head.message（异常说明，如 1 未获得授权/2 服务超时/3 未检索到记录），
 * 照录英文名、全部 String；与各接口响应 VO 的 head 组件组合即完整回执。
 */
public record EccertRespHeadVO(
        // GB/T 36906—2018 表2/4/6/8/10/12｜head.status｜接口执行结果代码，0=调用成功，其他值=失败｜十进制字符
        String status,
        // GB/T 36906—2018 表2/4/6/8/10/12｜head.message｜异常信息，与结果代码对应｜字符串
        String message
) {
}
