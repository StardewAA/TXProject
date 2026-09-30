package cn.iocoder.txgy.module.bidding.infra.ca;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 外部 CA 互认 SDK 配置（gitee.com/cebpubservice，中国招标投标公共服务平台 CA 互认）。
 *
 * <p>bidding.ca.sdk.enabled=true 时使用真实 SDK 实现 {@link CaClientFacadeSdkImpl}（须先部署
 * CA 互认共享组件 components-server 并完成自助开通获取凭证，见 docs/接口互认/前置条件清单）；
 * 缺省 false 走本地占位实现 {@link CaClientFacadeDefault}（业务闭环可测，远程未就绪不阻塞开发）。
 *
 * <p>对接前须准备的凭证（对应自部署组件 / 官方测试节点）：
 * <ul>
 *   <li>tradingSystemCode：平台代码（自助开通时分配）</li>
 *   <li>serviceUrl：CA 互认共享组件服务地址（自部署如 http://127.0.0.1:8992，或官方测试节点）</li>
 *   <li>clientId / clientSecret：客户端凭据（自助开通系统分配的 appId / secret）</li>
 *   <li>signatureSecret：请求签名密钥（HmacSM3，Core SDK 用）</li>
 * </ul>
 */
@Component
@Data
@Validated
@ConfigurationProperties(prefix = "bidding.ca.sdk")
public class CaSdkProperties {

    /**
     * 启用真实 SDK 实现（缺省 false：本地占位，保证业务闭环可测）。
     *
     * <p><b>只管"凭证与组件连通"</b>：即 CA 共享组件调用（扫码登录/签名/解密）与
     * {@code /bidding/ca/share/**}（官方调起参数签发、官方回调）是否走真实实现。
     *
     * <p><b>不管"文件签章走哪条路"</b>——那是 {@link #sealEnabled} 的职责。
     * 2026-09-21 拆分原因：此前两者共用一个开关，导致 enabled=true（对接官方组件所必需）时
     * {@code /bidding/ca/seal/document} 与 {@code /callback} 直接抛 500，本地测试签章被连带打死。
     */
    private boolean enabled = false;

    /**
     * 文件签章是否走<b>官方 H5 document 签发接口</b>（缺省 false）。
     *
     * <p>false（缺省）= 本地测试签章：{@code /bidding/ca/seal/document} 用统一测试账号私钥做
     * <b>真实 SM2 签名 + PDF 红章</b>并直接落台账，{@code /callback} 会真实验签后入库——
     * 业务闭环可完整自测，无需官方凭证。
     *
     * <p>true = 走官方 H5 document 签发接口（<b>待实现</b>：需要官方 document 签发接口与真实凭证；
     * 当前置 true 时签章端点会显式报错而非静默降级，避免"以为盖了官方章、其实是本地测试章"）。
     *
     * <p>与 {@link #enabled} 独立：可以在开启官方组件连通（enabled=true）的同时保留本地测试签章。
     */
    private boolean sealEnabled = false;

    /** 平台代码（自助开通分配） */
    private String tradingSystemCode;

    /** CA 互认共享组件服务地址，如 http://127.0.0.1:8992 */
    private String serviceUrl;

    /** 客户端 ID（appId） */
    private String clientId;

    /** 客户端密钥（secret） */
    private String clientSecret;

    /** 请求签名密钥（HmacSM3） */
    private String signatureSecret;

    /** 待签名原文摘要算法（国密 SM3；SDK 仅校验传入摘要，算法名用于展示/日志） */
    private String digestAlgorithm = "SM3";

    /** 签名证书公钥算法（仅 RSA/SM2 两种取值，默认 SM2 国密） */
    private String publicKeyAlgorithm = "SM2";

    /**
     * 回调域名（签发官方调起参数时拼 {@code tradingSystemConfig.callBackUrl} 用）。
     *
     * <p>官方检测安装工具/签章客户端在**用户本机**发起回调，故：
     * <ul>
     *   <li>本机联调：{@code http://127.0.0.1:48110}（缺省值）；</li>
     *   <li>生产/他人机器验证：须配成**该机器可达**的地址（内网 IP 或外网域名），否则回调打不回来。</li>
     * </ul>
     * 缺省用本机地址保证"开箱可联调"，不因未配置而抛异常。
     */
    private String callbackDomain = "http://127.0.0.1:48110";
}
