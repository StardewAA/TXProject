package cn.iocoder.txgy.module.bidding.service.noticeexternal.body;

import lombok.Data;

/**
 * 正文结构化快照（返回给管理端展示）。
 */
@Data
public class NoticeBodyStructureSnapshot {

    /** 公告 id */
    private Long id;

    /** 正文结构化状态：SUCCESS/FAILED */
    private String bodyBlocksStatus;

    /** 生效策略码：HTML_STRUCTURE/AI_FALLBACK（SUCCESS 时） */
    private String bodyParserCode;

    /** 结构化块总数 */
    private Integer blockCount;

    /** 其中表格块数 */
    private Integer tableCount;

    /** 是否疑似"拍平粘连文本"（常规解析无 table 且存在超长粘连段）——提示运营可用 AI 兜底 */
    private Boolean aiRecommended;

}
