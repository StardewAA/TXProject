package cn.iocoder.txgy.module.bidding.service.ca;

import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaShareLaunchParamVO;
import cn.iocoder.txgy.module.bidding.infra.ca.CaSdkProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * {@link CaShareLaunchService} 实现：按官方 §5.3.1 结构拼装调起参数。
 *
 * <p>字段名**逐字照抄官方**（含 {@code CAShareUseScene} 大驼峰、{@code ClientSercet} 拼写瑕疵、
 * {@code callBackUrl} 大写 B）——官方工具按字面解析，改名即对接失败。
 */
@Service
@Slf4j
public class CaShareLaunchServiceImpl implements CaShareLaunchService {

    /** 官方使用场景：00 文件签章（01 为文件加密，本模块不涉及）。 */
    private static final String USE_SCENE_FILE_SEAL = "00";

    /** 官方文件类型默认值（20241024 变更说明：由 00/01 改为 pdf/ofd，默认 pdf）。 */
    private static final String DEFAULT_FILE_TYPE = "pdf";

    /**
     * 回调地址固定为官方回调端点：H5 组件与客户端共用同一个地址没意义（响应结构一致，
     * 但语义不同），此处按**客户端回调**签发——调起检测安装工具后拉起的是签章客户端。
     * 前端若要测 H5 组件路线，走 {@code /bidding/ca/share/seal/callback}。
     */
    private static final String CLIENT_CALLBACK_PATH = "/admin-api/bidding/ca/share/seal/client-callback";

    @Resource
    private CaSdkProperties caSdkProperties;

    @Override
    public CaShareLaunchParamVO issueLaunchParam(String awaitingSignatureFilePath,
            String awaitingSignatureFileName, String signatureFileSavePath, String signatureFileName,
            String fileType) {
        CaShareLaunchParamVO param = new CaShareLaunchParamVO();
        param.setCAShareUseScene(USE_SCENE_FILE_SEAL);

        // ---- keyParameter.document[] ----
        CaShareLaunchParamVO.KeyParameter keyParameter = new CaShareLaunchParamVO.KeyParameter();
        CaShareLaunchParamVO.KeyParameter.DocumentItem item = new CaShareLaunchParamVO.KeyParameter.DocumentItem();
        item.setAwaitingSignatureFilePath(awaitingSignatureFilePath);
        item.setAwaitingSignatureFileName(awaitingSignatureFileName);
        // 签后路径/名称缺省时后端派生：官方要求二者必填，但前端只关心"签哪个文件"，
        // 让前端为"签完存哪"编路径既啰嗦又易错（且前端签名册路径与客户端所在机器不一致）。
        item.setSignatureFileSavePath(StringUtils.hasText(signatureFileSavePath)
                ? signatureFileSavePath : deriveSavePath(awaitingSignatureFilePath));
        item.setSignatureFileName(StringUtils.hasText(signatureFileName)
                ? signatureFileName : deriveSealedName(awaitingSignatureFileName));
        item.setAwaitingSignatureFileType(StringUtils.hasText(fileType) ? fileType : DEFAULT_FILE_TYPE);
        keyParameter.setDocument(List.of(item));
        // 印章配置：不传，由用户在 CA 客户端内选择自己的印章（传了反而可能覆盖用户选择）。
        param.setKeyParameter(keyParameter);

        // ---- caShareComponentConfig（平台密钥在此拼装，不下发到前端源码）----
        CaShareLaunchParamVO.CaShareComponentConfig componentConfig = new CaShareLaunchParamVO.CaShareComponentConfig();
        componentConfig.setTradingSystemCode(caSdkProperties.getTradingSystemCode());
        componentConfig.setServiceUrl(caSdkProperties.getServiceUrl());
        componentConfig.setClientId(caSdkProperties.getClientId());
        // 同时填 clientSecret 与 ClientSercet：官方文档表格/示例写法不一致，官方工具按哪种实现未知，
        // 两个都填可确保无论对方读哪个字段都能取到（值相同，不存在歧义）。
        componentConfig.setClientSecret(caSdkProperties.getClientSecret());
        componentConfig.setClientSercet(caSdkProperties.getClientSecret());
        componentConfig.setAccessKeySecret(caSdkProperties.getSignatureSecret());
        param.setCaShareComponentConfig(componentConfig);

        // ---- tradingSystemConfig ----
        CaShareLaunchParamVO.TradingSystemConfig tradingSystemConfig = new CaShareLaunchParamVO.TradingSystemConfig();
        String callbackUrl = resolveDomain() + CLIENT_CALLBACK_PATH;
        tradingSystemConfig.setCallBackUrl(callbackUrl);
        // 官方参数表写 callbackRequestUrl、示例写 callBackUrl，同 clientSecret 的处理：两个都填。
        tradingSystemConfig.setCallbackRequestUrl(callbackUrl);
        param.setTradingSystemConfig(tradingSystemConfig);

        log.info("[CaShareLaunch] 已签发调起参数 tradingSystemCode={} serviceUrl={} file={} callback={}",
                componentConfig.getTradingSystemCode(), componentConfig.getServiceUrl(),
                awaitingSignatureFileName, callbackUrl);
        return param;
    }

    /**
     * 回调域名：官方工具在**用户本机**回调，故缺省为本机地址；
     * 生产须把 {@code bidding.ca.sdk.callback-domain} 配成用户机器可达的地址（见 {@link CaSdkProperties#getCallbackDomain()}）。
     */
    private String resolveDomain() {
        String domain = caSdkProperties.getCallbackDomain();
        return StringUtils.hasText(domain) ? domain : "http://127.0.0.1:48110";
    }

    /** 由待签文件路径派生签后保存路径（官方要求必填，缺省时同目录加 _sealed 后缀）。 */
    private String deriveSavePath(String awaitingSignatureFilePath) {
        if (!StringUtils.hasText(awaitingSignatureFilePath)) {
            return "";
        }
        int dot = awaitingSignatureFilePath.lastIndexOf('.');
        return dot > 0 ? awaitingSignatureFilePath.substring(0, dot) + "_sealed"
                + awaitingSignatureFilePath.substring(dot) : awaitingSignatureFilePath + "_sealed";
    }

    /** 由待签文件名派生签后文件名。 */
    private String deriveSealedName(String awaitingSignatureFileName) {
        if (!StringUtils.hasText(awaitingSignatureFileName)) {
            return "";
        }
        int dot = awaitingSignatureFileName.lastIndexOf('.');
        return dot > 0 ? awaitingSignatureFileName.substring(0, dot) + "_sealed"
                + awaitingSignatureFileName.substring(dot) : awaitingSignatureFileName + "_sealed";
    }
}
