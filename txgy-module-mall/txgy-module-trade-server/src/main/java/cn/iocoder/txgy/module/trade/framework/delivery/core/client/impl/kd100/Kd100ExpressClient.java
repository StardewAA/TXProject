package cn.iocoder.txgy.module.trade.framework.delivery.core.client.impl.kd100;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.trade.framework.delivery.config.TradeExpressProperties;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.ExpressClient;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.ExpressTrackQueryReqDTO;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.ExpressTrackRespDTO;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.ExpressSnapshotRespDTO;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.kd100.Kd100ExpressQueryReqDTO;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.kd100.Kd100ExpressQueryRespDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.EXPRESS_API_QUERY_ERROR;
import static cn.iocoder.txgy.module.trade.framework.delivery.core.client.convert.ExpressQueryConvert.INSTANCE;

/**
 * 快递 100 客户端
 *
 * @author jason
 */
@Slf4j
@AllArgsConstructor
public class Kd100ExpressClient implements ExpressClient {

    private static final String REAL_TIME_QUERY_URL = "https://poll.kuaidi100.com/poll/query.do";

    private final RestTemplate restTemplate;
    private final TradeExpressProperties.Kd100Config config;

    /**
     * 查询快递轨迹
     *
     * @see <a href="https://api.kuaidi100.com/debug-tool/query/">接口文档</a>
     *
     * @param reqDTO 查询请求参数
     * @return 快递轨迹
     */
    @Override
    public List<ExpressTrackRespDTO> getExpressTrackList(ExpressTrackQueryReqDTO reqDTO) {
        return getSnapshot(reqDTO).getEvents();
    }

    @Override
    public ExpressSnapshotRespDTO getSnapshot(ExpressTrackQueryReqDTO reqDTO) {
        // 发起请求
        Kd100ExpressQueryReqDTO requestDTO = INSTANCE.convert2(reqDTO)
                .setExpressCode(reqDTO.getExpressCode().toLowerCase(Locale.ROOT));
        // Optional recipient contact is not sent for carriers (including the planned Deppon pilot) that do not require it.
        if (!Set.of("shunfeng", "shunfengkuaiyun", "zhongtong").contains(requestDTO.getExpressCode())) {
            requestDTO.setPhone(null);
        }
        Kd100ExpressQueryRespDTO respDTO = httpRequest(REAL_TIME_QUERY_URL, requestDTO,
                Kd100ExpressQueryRespDTO.class);

        // 处理结果
        if (respDTO != null && "500".equals(respDTO.getStatus())) {
            return new ExpressSnapshotRespDTO(); // Official "query has no result", not a transport or API failure.
        }
        if (respDTO == null || Objects.equals("false", respDTO.getResult())
                || !Objects.equals("200", respDTO.getStatus())
                || !Objects.equals(reqDTO.getLogisticsNo(), respDTO.getLogisticsNo())
                || !reqDTO.getExpressCode().equalsIgnoreCase(respDTO.getExpressCompanyCode())) {
            throw exception(EXPRESS_API_QUERY_ERROR);
        }
        return new ExpressSnapshotRespDTO().setRawStatus(respDTO.getState()).setEvents(
                CollUtil.isEmpty(respDTO.getTracks()) ? Collections.emptyList() : INSTANCE.convertList2(respDTO.getTracks()));
    }

    /**
     * 快递 100 API 请求
     *
     * @param url 请求 url
     * @param req 对应请求的请求参数
     * @param respClass 对应请求的响应 class
     * @param <Req> 每个请求的请求结构 Req DTO
     * @param <Resp> 每个请求的响应结构 Resp DTO
     */
    private <Req, Resp> Resp httpRequest(String url, Req req, Class<Resp> respClass) {
        // 请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        // 请求体
        String param = JsonUtils.toJsonString(req);
        String sign = generateReqSign(param, config.getKey(), config.getCustomer()); // 签名
        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("customer", config.getCustomer());
        requestBody.add("sign", sign);
        requestBody.add("param", param);

        // 发送请求
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        // 处理响应
        if (!responseEntity.getStatusCode().is2xxSuccessful() || responseEntity.getBody() == null
                || responseEntity.getBody().length() > 1_000_000) {
            throw exception(EXPRESS_API_QUERY_ERROR);
        }
        return JsonUtils.parseObjectQuietly(responseEntity.getBody(), respClass);
    }

    private String generateReqSign(String param, String key, String customer) {
        String plainText = String.format("%s%s%s", param, key, customer);
        return HexUtil.encodeHexStr(DigestUtil.md5(plainText), false);
    }

}
