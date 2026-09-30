package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 项目关键岗位变更统计（防挂靠/管理失控）。
 *
 * <p>规范出处：用户业务口径"增加对同一项目关键岗位人员频繁变更的监控（防挂靠/管理失控）"；
 * 《电子资质七大模块技术方案.md》§1.3（GET /bidding/project/{id}/key-post-changes）。
 * <p>为什么按岗位拆开且给出阈值：运维要能直接看到"哪个岗位换了几次、阈值是多少、换的是谁"，
 * 只给一个总数无法定位问题，也无法解释"为什么算超标"。
 * <p>业务目标：挂靠嫌疑可定位到岗位与人。
 */
@Schema(description = "管理后台 - 项目关键岗位变更统计")
@Data
public class KeyPostChangeRespVO {

    @Schema(description = "项目主键", example = "1024")
    private Long projectId;

    @Schema(description = "统计窗口天数", example = "90")
    private Integer windowDays;

    @Schema(description = "窗口起点")
    private LocalDate windowStart;

    @Schema(description = "全项目是否有关键岗位超标", example = "true")
    private Boolean anyExceeded;

    @Schema(description = "各岗位明细")
    private List<PostChangeItem> items;

    @Schema(description = "岗位变更明细项")
    @Data
    public static class PostChangeItem {

        @Schema(description = "岗位 code", example = "PROJECT_MANAGER")
        private String postCode;

        @Schema(description = "岗位中文名", example = "项目经理")
        private String postLabel;

        @Schema(description = "窗口内变更次数", example = "3")
        private Integer changeCount;

        @Schema(description = "阈值（取自配置 project.key.post.change.threshold）", example = "2")
        private Integer threshold;

        @Schema(description = "是否超阈值", example = "true")
        private Boolean exceeded;

        @Schema(description = "涉及人员主键（按时间顺序）")
        private List<Long> personIds;

        @Schema(description = "最近一次变更日期")
        private LocalDate lastChangeDate;
    }
}
