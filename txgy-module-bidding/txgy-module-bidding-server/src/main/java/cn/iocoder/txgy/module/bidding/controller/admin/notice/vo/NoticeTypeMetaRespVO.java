package cn.iocoder.txgy.module.bidding.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 公告类型字段模板元数据（2026-09-10）：前端按类型渲染表单（必填按 VO 数据规范 必填=是；
 * 可选=数据集其余字段），替代"一张全字段表/JSON 直填"的通用创建方式。
 */
@Schema(description = "管理后台 - 公告类型字段模板 Response")
@Data
public class NoticeTypeMetaRespVO {

    @Schema(description = "公告类型 code（NoticeTypeEnum.name）", example = "TENDER_NOTICE")
    private String type;

    @Schema(description = "公告类型名称", example = "招标公告")
    private String name;

    @Schema(description = "对应权威数据集（VO 名）", example = "TenderNoticeVO")
    private String dataset;

    @Schema(description = "字段模板（必填优先，均含中文名）")
    private List<FieldMeta> fields;

    @Schema(description = "字段元数据")
    @Data
    public static class FieldMeta {

        @Schema(description = "字段 code（VO 数据规范英文名，payload 键）", example = "NOTICE_NAME")
        private String code;

        @Schema(description = "中文名（未登记回退 code）", example = "公告/公示名称")
        private String label;

        @Schema(description = "是否必填（必填=是）")
        private Boolean required;

        public static FieldMeta of(String code, String label, boolean required) {
            FieldMeta meta = new FieldMeta();
            meta.setCode(code);
            meta.setLabel(label == null ? code : label);
            meta.setRequired(required);
            return meta;
        }
    }
}
