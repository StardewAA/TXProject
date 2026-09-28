package cn.iocoder.txgy.module.bidding.api.dto.bidagent;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * Agent 任务类型（与 Node 侧 bid-agent-service 对齐，用于影子状态）。
 *
 * <p>放在 {@code api/dto/bidagent} 而非 {@code api/enums}：后者按《招投标模块开发规范》第七章只放
 * National 与 Shaanxi 前缀代码集及 PublicLevel（由 {@code BiddingArchitectureRulesTest.API_ENUMS_NAME_WHITELIST} 断言），
 * 本枚举属契约内部枚举，与契约 DTO 同包更贴近其用途。
 */
@Getter
@AllArgsConstructor
public enum BidTaskTypeEnum {

    PARSE("parse", "招标文件解析"),
    OUTLINE("outline", "标书大纲"),
    DRAFT("draft", "章节正文"),
    FILL("fill", "模板字段填充");

    private final String type;
    private final String desc;

    public static BidTaskTypeEnum from(String type) {
        return Arrays.stream(values())
                .filter(item -> item.type.equals(type))
                .findFirst()
                .orElse(null);
    }
}
