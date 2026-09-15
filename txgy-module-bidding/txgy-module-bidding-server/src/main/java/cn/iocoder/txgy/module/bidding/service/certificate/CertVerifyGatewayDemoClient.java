package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertRespHeadVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyFileReqVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyFileRespVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyInfoReqVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyInfoRespVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Iterator;

/**
 * 本地演示核验通道（默认实现）：尚未与真实电子证照共享服务（四库一平台等）打通时，
 * 以可切换的“本地模式”闭环验真主流程 —— 对请求报文做结构完整性自检并返回模拟回执，
 * 供台账验真/自查流程演示与联调占位。
 *
 * <p>规范出处：GB/T 36906—2018 §6（回执语义 head.status=0 成功、data.result 0=校验正确；
 * 表10 result 0~5 含义照录）；《招投标模块开发规范》§6（外部集成先立接口、未开通以日志/占位闭环）。
 * <p>设计遵循/针对：配置 bidding.cert.verify.mock=false 后本通道抛 CERT_VERIFY_GATEWAY_UNAVAILABLE，
 * 由管理员在对接真实网关（实现 CertVerifyGateway 的另一 @Component）后替换；服务层不感知通道差异。
 */
@Slf4j
@Component
public class CertVerifyGatewayDemoClient implements CertVerifyGateway {

    /** 演示模式开关（默认开）：true=本地自检回执；false=通道未就绪报错 */
    @Value("${bidding.cert.verify.mock:true}")
    private boolean mock;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public EccertVerifyInfoRespVO verifyInfo(EccertVerifyInfoReqVO request) {
        ensureReady();
        String certificateData = request.data() == null ? null : request.data().certificateData();
        // 结构自检：报文存在且含任意证书编号字段即视为通过（真实通道应对照官方底账）
        String found = findIdentifier(certificateData);
        boolean pass = found != null && !found.isBlank();
        String result = pass ? "0" : "4";
        String message = pass ? "0 信息正确且有效" : "4 信息不全，无法验证";
        log.info("[cert-verify][demo-verifyinfo] 报文自检：{} → result={}", pass ? "通过" : "未通过", result);
        return new EccertVerifyInfoRespVO(new EccertRespHeadVO("0", "调用成功"),
                new EccertVerifyInfoRespVO.Data(result, message));
    }

    @Override
    public EccertVerifyFileRespVO verifyFile(EccertVerifyFileReqVO request) {
        ensureReady();
        String certificate = request.data() == null ? null : request.data().certificate();
        boolean pass = certificate != null && !certificate.isBlank();
        String result = pass ? "0" : "4";
        String message = pass ? "0 信息正确且有效" : "4 信息不全，无法验证";
        log.info("[cert-verify][demo-verifyfile] 文件自检：{} → result={}", pass ? "通过" : "未通过", result);
        return new EccertVerifyFileRespVO(new EccertRespHeadVO("0", "调用成功"),
                new EccertVerifyFileRespVO.Data(result, message));
    }

    /** 在报文 JSON 中取证书编号类字段（GB/T 36903 ZZBH/英文名及台账惯用键），演示自检用。 */
    private String findIdentifier(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            for (Iterator<String> it = root.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                if (key.equalsIgnoreCase("ZZBH") || key.equalsIgnoreCase("certificateNo")
                        || key.equalsIgnoreCase("certNo") || key.equalsIgnoreCase("cert_no")) {
                    JsonNode v = root.get(key);
                    return v == null || v.isNull() ? null : v.asText();
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[cert-verify][demo] 报文解析失败：{}", e.getMessage());
            return null;
        }
    }

    private void ensureReady() {
        if (!mock) {
            throw cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.CERT_VERIFY_GATEWAY_UNAVAILABLE);
        }
    }
}
