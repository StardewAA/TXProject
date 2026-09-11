package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - 试题批量导入结果 Response VO")
@Data
public class QuestionImportRespVO {

    @Schema(description = "成功导入条数")
    private Integer successCount;

    @Schema(description = "失败明细")
    private List<ErrorItem> errors = new ArrayList<>();

    @Data
    public static class ErrorItem {

        @Schema(description = "Excel 行号（数据行，从 2 开始）")
        private Integer row;

        @Schema(description = "失败原因")
        private String message;

        public ErrorItem() {}

        public ErrorItem(Integer row, String message) {
            this.row = row;
            this.message = message;
        }
    }

}
