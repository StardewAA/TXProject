package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 资源中心操作日志 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogPageReqVO extends PageParam {

    @Schema(description = "关键词（操作人姓名模糊匹配）")
    private String keyword;

    @Schema(description = "动作：UPLOAD/CREATE/UPDATE/DELETE/ONLINE/OFFLINE/ARCHIVE/CREDIT/TRANSCODE/ENABLE/DISABLE/IMPORT")
    private String action;

    @Schema(description = "对象类型：document/video/question/category/tag")
    private String targetType;

    @Schema(description = "创建时间范围")
    private LocalDateTime[] createTime;

}
