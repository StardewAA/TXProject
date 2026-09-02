package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 课程媒资绑定/修改 Request VO")
@Data
public class CourseMediaSaveReqVO {

    @Schema(description = "媒资 ID（修改时必填）", example = "1")
    private Long id;

    @Schema(description = "课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "课程 ID 不能为空")
    private Long courseId;

    @Schema(description = "媒资类型：1 文档 2 视频 3 图片", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "媒资类型不能为空")
    private Integer mediaType;

    @Schema(description = "媒资名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "工艺规程.pdf")
    @NotEmpty(message = "媒资名称不能为空")
    @Size(max = 200, message = "媒资名称长度不能超过 200 个字符")
    private String name;

    @Schema(description = "访问地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://file.example.com/xxx.pdf")
    @NotEmpty(message = "访问地址不能为空")
    @Size(max = 500, message = "访问地址长度不能超过 500 个字符")
    private String url;

    @Schema(description = "文件大小（字节）", example = "1024000")
    private Long sizeBytes;

    @Schema(description = "视频时长（秒）", example = "2700")
    private Integer durationSeconds;

    @Schema(description = "排序", example = "0")
    private Integer sort;

}
