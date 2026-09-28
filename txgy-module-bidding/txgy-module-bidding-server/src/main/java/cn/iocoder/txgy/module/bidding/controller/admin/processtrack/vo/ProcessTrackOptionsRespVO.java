package cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo;

// [契约]《后端变更请求-投标流程跟踪管理端总览-2026-09-18.md》§5 接口3
// owners.value=ownerUserId(String)，label=姓名，extra=公司名（跨租户同名靠 extra 区分）；tenants 仅超管下发
// 字段沿用 doc 24 OptionItemRespVO 形状（value/label/extra），前端复用同一套组件

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 投标流程跟踪筛选项")
@Data
public class ProcessTrackOptionsRespVO {

    @Schema(description = "负责人候选（value=ownerUserId String，label=姓名，extra=公司名）")
    private List<Option> owners;

    @Schema(description = "公司候选（仅超管下发；租户视角前端已隐藏该下拉）")
    private List<Option> tenants;

    @Schema(description = "统一候选形状（doc 24 OptionItemRespVO）")
    @Data
    public static class Option {
        @Schema(description = "值（String）")
        private String value;
        @Schema(description = "标签")
        private String label;
        @Schema(description = "附加说明（如公司名，用于跨租户同名区分）")
        private String extra;
    }
}
