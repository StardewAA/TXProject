package cn.iocoder.txgy.module.mes.service.set.pollutioncheck;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * MES 污染判定 AI 初筛服务（P1 规则 mock）
 *
 * ponytail: P1 用「污染特征词典 + 安全白名单」关键词规则做初筛，占位真实的 AI 大模型。
 * P2 接入 txgy-module-ai 大模型时，仅替换本类的内部实现（prescreen 签名不变）。
 * 无论真实/AI 还是 mock，结果都只是"预分类建议"，终态以人工复核二值(CLEAN/POLLUTED)为准。
 *
 * @author OPENLAB BS
 */
@Service
public class MesSetPollutionCheckAiService {

    /**
     * AI 初筛结果：无污染
     */
    public static final String AI_RESULT_CLEAN = "CLEAN";
    /**
     * AI 初筛结果：有污染
     */
    public static final String AI_RESULT_POLLUTED = "POLLUTED";
    /**
     * AI 初筛结果：不确定（必须人工复核收口）
     */
    public static final String AI_RESULT_UNCERTAIN = "UNCERTAIN";

    /**
     * 安全特征关键词（命中优先判无污染，例如"无铅"须先于"铅"）
     */
    private static final String[] CLEAN_KEYWORDS = {
            "无铅", "无溶剂", "环保", "可降解", "纯水", "不锈钢", "钢材", "铝合金", "纯铝", "铜", "纸", "木", "玻璃",
            "ABS", "PP", "PE", "布料", "无尘"
    };

    /**
     * 污染特征关键词
     */
    private static final String[] POLLUTED_KEYWORDS = {
            "铅", "镉", "铬", "汞", "砷", "镍", "锰", "氰", "苯", "甲苯", "二甲苯", "甲醛", "酚", "石棉",
            "溶剂", "油漆", "油墨", "电镀", "酸洗", "酸", "碱", "树脂", "胶", "助剂", "阻燃剂", "农药", "含硫", "含氯"
    };

    /**
     * AI 初筛判定结果
     */
    @Data
    @Accessors(chain = true)
    public static class PrescreenResult {

        /**
         * AI 初筛结果：CLEAN/POLLUTED/UNCERTAIN
         */
        private String aiResult;
        /**
         * AI 置信度(%) 0-100
         */
        private Integer aiConfidence;
        /**
         * AI 判定依据
         */
        private String aiReason;
        /**
         * AI 推荐存储方法
         */
        private String suggestedStorage;
    }

    /**
     * AI 初筛
     *
     * @param itemName 物料/产品名称（主要判据）
     * @return 预分类建议
     */
    public PrescreenResult prescreen(String itemName) {
        String matchedClean = matchKeyword(itemName, CLEAN_KEYWORDS);
        if (matchedClean != null) {
            return new PrescreenResult()
                    .setAiResult(AI_RESULT_CLEAN)
                    .setAiConfidence(ThreadLocalRandom.current().nextInt(85, 96))
                    .setAiReason("命中安全特征词「" + matchedClean + "」，未检出污染特征")
                    .setSuggestedStorage("普通仓储（常温、通风、防潮常规存放）");
        }
        String matchedPolluted = matchKeyword(itemName, POLLUTED_KEYWORDS);
        if (matchedPolluted != null) {
            return new PrescreenResult()
                    .setAiResult(AI_RESULT_POLLUTED)
                    .setAiConfidence(ThreadLocalRandom.current().nextInt(90, 99))
                    .setAiReason("命中污染特征词「" + matchedPolluted + "」，需按污染受控处置")
                    .setSuggestedStorage("污染/危废受控存储：密封防渗容器、专用污染管控库位、贴污染标识并登记暂存台账，避免混放");
        }
        return new PrescreenResult()
                .setAiResult(AI_RESULT_UNCERTAIN)
                .setAiConfidence(ThreadLocalRandom.current().nextInt(55, 76))
                .setAiReason("未命中污染词典与安全白名单，请人工复核/必要时送检")
                .setSuggestedStorage("隔离待检库位暂存，待人工复核/送检后确定存储");
    }

    /**
     * 名称是否命中关键词，返回首个命中词
     */
    private String matchKeyword(String text, String[] keywords) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

}
