package cn.iocoder.txgy.module.wms.dal.dataobject.ai;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * WMS AI 客服配置 DO
 *
 * <p>只保存「用 ai_model 里的哪个模型」以及超时等运行参数；模型本身（platform、
 * model、key_id → ai_api_key 的 Key 与 BaseURL）仍由 AI 模块的 ai_model / ai_api_key 维护，
 * 因此这里天然支持接入多个厂商。
 */
@TableName("wms_ai_config")
@KeySequence("wms_ai_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WmsAiConfigDO extends BaseDO {

    /** 主键编号 */
    @TableId
    private Long id;

    /** 使用的 ai_model.id；为空时回落环境变量 */
    private Long modelId;

    /** 是否启用界面配置：启用则忽略环境变量 */
    private Boolean enabled;

    /** 调用超时（秒），空则用默认值 */
    private Integer timeoutSeconds;

    /** 备注 */
    private String remark;

}
