package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 电子证照共享服务·公共请求头（head）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6「通用约定」。
 * <p>设计遵循/针对：请求 JSON 容器含 head.accessId、head.accessToken 两项（36906 表 1/3/5/7/9/11 公共部分），
 * 照录英文名、全部 String；与各接口请求 VO 的 head 组件组合即完整报文。调用方认证见 36906 §5.2.2。
 */
public record EccertReqHeadVO(
        // GB/T 36906—2018 §6 通用约定｜head.accessId｜访问标识，由服务请求方生成并确保唯一性｜必填
        String accessId,
        // GB/T 36906—2018 §6 通用约定｜head.accessToken｜应用认证信息，请求方应用认证时向服务方取得｜必填
        String accessToken
) {
}
