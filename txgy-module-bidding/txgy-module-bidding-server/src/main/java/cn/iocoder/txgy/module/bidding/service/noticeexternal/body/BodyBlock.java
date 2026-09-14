package cn.iocoder.txgy.module.bidding.service.noticeexternal.body;
// 规范依据：设计文档 v0.6 §7（结构化展示）；联调清单 R12②（正文结构化块，前端块组件渲染）

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 正文结构化块（body_blocks_json 数组元素；前端按 type 分组件渲染）。
 *
 * <p>type 取值与载荷：
 * <ul>
 *   <li>heading：{level(1~6), text}</li>
 *   <li>paragraph：{text}</li>
 *   <li>table：{caption?(可选), headers?[列名], rows[[单元格…]]}——无表头时 headers 缺省，rows 含首行</li>
 *   <li>list：{ordered(bool), items[条目]}</li>
 * </ul>
 * 序列化时无值字段省略（@JsonInclude(NON_NULL)），保持下发体积可控。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BodyBlock {

    /** heading/paragraph/table/list */
    private String type;

    /** heading 级别 1~6（仅 heading） */
    private Integer level;

    /** heading/paragraph 文本（段落为纯文本，内联样式不保留） */
    private String text;

    /** 表格说明（可选，仅 table） */
    private String caption;

    /** 表头列名（可选，仅 table；thead/首行全 th 时给出） */
    private List<String> headers;

    /** 表格数据行（仅 table；单元格为文本） */
    private List<List<String>> rows;

    /** 是否有序列表（仅 list） */
    private Boolean ordered;

    /** 列表条目（仅 list） */
    private List<String> items;

    public static BodyBlock heading(int level, String text) {
        BodyBlock block = new BodyBlock();
        block.setType("heading");
        block.setLevel(level);
        block.setText(text);
        return block;
    }

    public static BodyBlock paragraph(String text) {
        BodyBlock block = new BodyBlock();
        block.setType("paragraph");
        block.setText(text);
        return block;
    }

    public static BodyBlock list(boolean ordered, List<String> items) {
        BodyBlock block = new BodyBlock();
        block.setType("list");
        block.setOrdered(ordered);
        block.setItems(items);
        return block;
    }

    public static BodyBlock table(List<String> headers, List<List<String>> rows) {
        BodyBlock block = new BodyBlock();
        block.setType("table");
        block.setHeaders(headers);
        block.setRows(rows);
        return block;
    }

}
