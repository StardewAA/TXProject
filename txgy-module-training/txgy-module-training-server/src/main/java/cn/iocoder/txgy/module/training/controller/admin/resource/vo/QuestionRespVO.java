package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import cn.iocoder.txgy.module.training.dal.dataobject.resource.QuestionDO;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Schema(description = "管理后台 - 试题 Response VO（含标签信息）")
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionRespVO extends QuestionDO {

    @Schema(description = "已绑定标签 ID 集合")
    @TableField(exist = false)
    private List<Long> tagIds;

    @Schema(description = "已绑定标签名称（逗号拼接，展示用）")
    @TableField(exist = false)
    private String tagNames;

}
