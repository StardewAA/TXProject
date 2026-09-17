package cn.iocoder.txgy.module.bidding.enums;
// 规范依据：设计文档 v0.6 §7.3/§15.6（PDF 抽取状态机：空结果=FAIL_EMPTY 直接判失败，不入库展示）

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 外部公告 PDF 抽取状态。
 */
@Getter
@AllArgsConstructor
public enum NoticeExternalExtractionStatusEnum {

    NONE("NONE", "未抽取"),
    SUCCESS("SUCCESS", "抽取成功（文本+表格已落库）"),
    FAIL_EMPTY("FAIL_EMPTY", "抽取结果为空（扫描件/无可提取文本表格）"),
    FAIL("FAIL", "抽取失败（引擎异常/文件损坏）");

    private final String status;
    private final String description;

    public static NoticeExternalExtractionStatusEnum of(String status) {
        return Arrays.stream(values()).filter(e -> e.getStatus().equals(status)).findFirst().orElse(null);
    }

}
