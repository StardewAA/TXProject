package cn.iocoder.txgy.module.bidding.controller.admin.meta.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典项（契约 §1.1 的 data[dictType][] 元素）。
 *
 * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§1.1（DictItem 类型定义）。
 * <p>为什么字段这样定：前端要"零猜测"渲染——label 直接显示、tagType 直接给状态标签上色、
 * terminal 直接决定"是否还能点状态流转按钮"，三者缺一都会逼前端写死映射表（正是要禁止的）。
 * <p>业务目标：枚举展示完全由后台驱动，运营改中文名/标签色/停用枚举后前端无需发版。
 */
@Schema(description = "管理后台 - 字典项")
@Data
public class DictItemRespVO {

    @Schema(description = "枚举值 code（提交给后端的值）", requiredMode = Schema.RequiredMode.REQUIRED, example = "ACTIVE")
    private String value;

    @Schema(description = "中文名（前端直接展示）", requiredMode = Schema.RequiredMode.REQUIRED, example = "正常")
    private String label;

    @Schema(description = "标签色 code：success/warning/error/info/default", example = "success")
    private String tagType;

    @Schema(description = "是否终态（终态禁止再做状态流转，前端据此禁用按钮）", example = "false")
    private Boolean terminal;

    @Schema(description = "展示顺序", example = "1")
    private Integer sort;

    /**
     * 内部排序/归组用的字典类型键。
     *
     * <p>为什么放在 VO 上却不输出：服务层需要按类型归组后再裁剪，若另建内部类会造成两份几乎相同的结构体；
     * 标 {@link com.fasterxml.jackson.annotation.JsonIgnore} 后它不参与 JSON，契约不受影响。
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String dictTypeKey;
}
