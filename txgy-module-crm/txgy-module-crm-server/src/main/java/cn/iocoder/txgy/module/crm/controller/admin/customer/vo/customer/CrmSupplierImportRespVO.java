package cn.iocoder.txgy.module.crm.controller.admin.customer.vo.customer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 供应商 Word 导入结果
 *
 * @author 防腐保温项目组
 */
@Schema(description = "管理后台 - 供应商 Word 导入结果")
@Data
public class CrmSupplierImportRespVO {

    @Schema(description = "文档内识别到的条目总数", example = "7")
    private Integer totalCount;

    @Schema(description = "新增成功数量", example = "5")
    private Integer successCount;

    @Schema(description = "更新成功数量（选择更新已存在供应商时）", example = "2")
    private Integer updateCount;

    @Schema(description = "已存在（未更新）数量", example = "2")
    private Integer duplicateCount;

    @Schema(description = "解析失败数量", example = "1")
    private Integer failCount;

    @Schema(description = "新增成功的供应商名称")
    private List<String> successNames;

    @Schema(description = "更新成功的供应商名称")
    private List<String> updateNames;

    @Schema(description = "已存在的供应商明细")
    private List<Item> duplicates;

    @Schema(description = "解析失败的条目明细")
    private List<Item> failures;

    @Schema(description = "导入明细条目")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        @Schema(description = "文档内的行号（按表格顺序，从 1 开始）", example = "3")
        private Integer rowNo;

        @Schema(description = "供应商名称（可能为空）", example = "江苏鑫盛防腐涂料有限公司")
        private String name;

        @Schema(description = "原始行内容", example = "GYS001 | 江苏鑫盛防腐涂料有限公司 | ...")
        private String rawContent;

        @Schema(description = "原因", example = "未识别到供应商名称")
        private String reason;

    }

}
