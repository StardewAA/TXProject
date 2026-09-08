package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 培训班学员批量导入结果 Response VO
 *
 * @author 云培训学院课题小组
 */
@Schema(description = "管理后台 - 培训班学员批量导入结果 Response VO")
@Data
public class ClassStudentImportRespVO {

    @Schema(description = "成功新增学员数（已在班自动跳过，不计入）", example = "18")
    private Integer successCount;

    @Schema(description = "失败行（未匹配到系统账号等）")
    private List<FailRow> failRows;

    @Data
    @Schema(description = "导入失败行")
    public static class FailRow {

        @Schema(description = "Excel 行号（从表头下一行起算）", example = "3")
        private Integer rowNumber;

        @Schema(description = "该行手机号", example = "13800000000")
        private String mobile;

        @Schema(description = "失败原因", example = "未找到对应系统账号")
        private String reason;

        public FailRow() {
        }

        public FailRow(Integer rowNumber, String mobile, String reason) {
            this.rowNumber = rowNumber;
            this.mobile = mobile;
            this.reason = reason;
        }
    }

}
