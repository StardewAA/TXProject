package cn.iocoder.txgy.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * OSS 提升补传结果 VO（管理后台）
 *
 * @author OPENLAB BS
 */
@Schema(description = "OSS 提升补传结果")
@Data
public class FileOssPromoteRetryRespVO implements Serializable {

    @Schema(description = "本次扫描到的待补传文件总数")
    private int total;

    @Schema(description = "成功提升到 OSS 的文件数")
    private int success;

    @Schema(description = "仍失败（OSS 不可达等）的文件数")
    private int failed;

    @Schema(description = "被跳过（已在 OSS 或参数非法）的文件数")
    private int skipped;

    @Schema(description = "附加说明（例如单文件重试的失败原因）")
    private String message;

}
