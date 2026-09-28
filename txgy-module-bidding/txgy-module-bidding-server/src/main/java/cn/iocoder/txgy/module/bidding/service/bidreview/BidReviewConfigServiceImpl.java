package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewAiProbeRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewConfigRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewConfigUpdateReqVO;
import cn.iocoder.txgy.module.bidding.framework.ai.config.BidReviewAiProperties;
import cn.iocoder.txgy.module.bidding.framework.ai.core.BidReviewAiClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 智慧标书审查 · AI 配置实现。
 *
 * <p>设计遵循：
 * ① <b>密钥只读不回写</b>：页面只得到掩码，写入接口不接收 apiKey（规范 §4.8 L4）；
 * ② 本期配置修改作用于运行时内存（重启回落 application.yaml / 环境变量）；
 *    需要"改完持久生效"时落 bidding_rule_config（P1，方案 §5.2 注释已声明），不为此提前建表；
 * ③ 自检用最小 prompt，真实打通一次并把错误原文回传，避免"看起来配好了"。
 */
@Slf4j
@Service
public class BidReviewConfigServiceImpl implements BidReviewConfigService {

    @Resource
    private BidReviewAiProperties properties;
    @Resource
    private BidReviewAiClient aiClient;

    @Override
    public BidReviewConfigRespVO getConfig() {
        BidReviewConfigRespVO resp = new BidReviewConfigRespVO();
        resp.setEnabled(properties.getEnabled());
        resp.setBaseUrl(properties.getBaseUrl());
        resp.setModel(properties.getModel());
        resp.setJsonMode(properties.getJsonMode());
        resp.setTemperature(properties.getTemperature());
        resp.setMaxTokens(properties.getMaxTokens());
        resp.setTimeoutSeconds(properties.getTimeoutSeconds());
        resp.setRetryTimes(properties.getRetryTimes());
        resp.setConcurrency(properties.getConcurrency());
        resp.setChunkSize(properties.getChunkSize());
        // 只回显掩码：明文密钥永不出现在响应里
        resp.setApiKeyMasked(aiClient.maskedApiKey());
        resp.setAvailable(aiClient.isAvailable());
        return resp;
    }

    @Override
    public void updateConfig(BidReviewConfigUpdateReqVO reqVO) {
        if (reqVO.getEnabled() != null) {
            properties.setEnabled(reqVO.getEnabled());
        }
        if (StrUtil.isNotBlank(reqVO.getModel())) {
            properties.setModel(reqVO.getModel());
        }
        if (reqVO.getJsonMode() != null) {
            properties.setJsonMode(reqVO.getJsonMode());
        }
        if (reqVO.getTemperature() != null) {
            properties.setTemperature(reqVO.getTemperature());
        }
        if (reqVO.getMaxTokens() != null) {
            properties.setMaxTokens(reqVO.getMaxTokens());
        }
        if (reqVO.getTimeoutSeconds() != null) {
            properties.setTimeoutSeconds(reqVO.getTimeoutSeconds());
        }
        if (reqVO.getRetryTimes() != null) {
            properties.setRetryTimes(reqVO.getRetryTimes());
        }
        if (reqVO.getConcurrency() != null) {
            properties.setConcurrency(reqVO.getConcurrency());
        }
        if (reqVO.getChunkSize() != null) {
            properties.setChunkSize(reqVO.getChunkSize());
        }
        // 配置影响线上判定：留痕（P1 落 bidding_rule_config 的变更历史表）
        log.info("[updateConfig][AI 配置变更，原因={}]", StrUtil.nullToEmpty(reqVO.getChangeReason()));
    }

    @Override
    public BidReviewAiProbeRespVO probe() {
        BidReviewAiProbeRespVO resp = new BidReviewAiProbeRespVO();
        resp.setModel(properties.getModel());
        long start = System.currentTimeMillis();
        try {
            aiClient.probe();
            resp.setOk(true);
        } catch (Exception e) {
            resp.setOk(false);
            resp.setErrorMsg(StrUtil.sub(e.getMessage(), 0, 200));
        }
        resp.setCostMs(System.currentTimeMillis() - start);
        return resp;
    }
}
