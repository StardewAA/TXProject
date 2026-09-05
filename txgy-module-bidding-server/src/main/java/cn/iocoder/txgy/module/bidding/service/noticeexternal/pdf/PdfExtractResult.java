package cn.iocoder.txgy.module.bidding.service.noticeexternal.pdf;
// 规范依据：设计文档 v0.6 §7.3（OpenDataLoader 本地抽取文本+表格）；§15.6（空结果=FAIL_EMPTY 报错失败）

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PDF 抽取结果（OpenDataLoader 本地模式：文本 + 表格）。
 * <p>表格以二维数组表达（行 → 单元格），入库时序列化为 JSON 落 extraction_tables_json。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PdfExtractResult {

    /** 抽取纯文本（可能含版面换行）；扫描件/纯图 PDF 为空串 */
    private String text;

    /** 抽取表格：行→列→单元格文本；无可提取表格为空列表 */
    private java.util.List<java.util.List<String>> tables;

    /** 页数（引擎给不到时为空） */
    private Integer pageCount;

    public boolean isEmptyResult() {
        boolean textBlank = text == null || text.isBlank();
        boolean tablesEmpty = tables == null || tables.isEmpty();
        return textBlank && tablesEmpty;
    }

}
