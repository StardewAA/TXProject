package cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo;
// 规范依据：《招投标模块开发规范》§3.1（VO 规约）；设计文档 v0.6 §7（管理端列表结构化过滤）

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 外部公告管理端筛选选项 Response（来源站 / 公告类型下拉数据源）。
 *
 * <p>数据源均为数据库去重读取：来源站取采集源配置（bidding_fetch_source，名称以配置为准，
 * 公告表 source_name 只是快照、可能滞后或为空）；公告类型取公告表 notice_type_raw 去重（外站自述类型原文，
 * 自由文本、无枚举/字典，仅作下拉选项，不参与检索——检索仍走精确匹配/ES）。
 * 前端用 ApiSelect 拉取本端点填充下拉，避免硬编码来源站与公告类型。
 */
@Schema(description = "管理后台 - 外部公告筛选选项 Response")
@Data
public class NoticeExternalFilterOptionsRespVO {

    @Schema(description = "来源站选项（code=来源站 code，name=来源站名称）")
    private List<SourceOption> sources;

    @Schema(description = "公告类型（外站自述类型原文去重，升序）")
    private List<String> noticeTypes;

    @Schema(description = "来源站选项")
    @Data
    public static class SourceOption {

        @Schema(description = "来源站 code")
        private String code;

        @Schema(description = "来源站名称")
        private String name;
    }
}
