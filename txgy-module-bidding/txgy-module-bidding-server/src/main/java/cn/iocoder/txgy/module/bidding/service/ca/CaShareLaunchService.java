package cn.iocoder.txgy.module.bidding.service.ca;

import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaShareLaunchParamVO;

/**
 * 官方 CA 互认签章「调起参数」签发服务（契约 R3）。
 *
 * <p><b>为什么必须由后端签发</b>：官方 §5.3.1 的调起参数里含
 * {@code caShareComponentConfig.clientSecret} / {@code accessKeySecret}（**平台密钥**）。
 * 若把参数构造放在前端，这两个密钥就必须打进前端产物 → 任何人 F12 即可取走，
 * 等于把 CA 共享组件的调用权公开（可代本平台发起签章请求）。故前端只拿"能调起这一次"的参数，
 * 密钥由后端在服务端拼装，**明文密钥不出服务端**。
 *
 * <p><b>与签章业务的关系</b>：本接口只负责"把官方工具调起来"，不产生任何签章效力；
 * 真正的签章结果仍由官方工具/组件按 {@code tradingSystemConfig.callBackUrl} 回调后落库
 * （见 {@link CaShareCallbackService}）。
 */
public interface CaShareLaunchService {

    /**
     * 签发一次调起参数（含平台密钥，服务端拼装后整体下发）。
     *
     * @param awaitingSignatureFilePath 待签章文件地址（官方支持本地路径或网络路径）
     * @param awaitingSignatureFileName 待签章文件名称
     * @param signatureFileSavePath     签章后保存路径（缺省时后端给默认值）
     * @param signatureFileName         签章后文件名称（缺省时后端按原名派生）
     * @param fileType                  文件类型 pdf / ofd（缺省 pdf）
     * @return 官方 §5.3.1 调起参数
     */
    CaShareLaunchParamVO issueLaunchParam(String awaitingSignatureFilePath, String awaitingSignatureFileName,
            String signatureFileSavePath, String signatureFileName, String fileType);
}
