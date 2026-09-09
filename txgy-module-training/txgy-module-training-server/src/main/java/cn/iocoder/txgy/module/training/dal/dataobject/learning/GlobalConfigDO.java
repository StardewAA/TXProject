package cn.iocoder.txgy.module.training.dal.dataobject.learning;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学员学习中心 - 全局配置 DO
 *
 * <p>对应表 training_global_config：集中维护学习防挂机 / 互动 / 打卡等参数，
 * 学员端与管理端共享读取。</p>
 *
 * @author 云培训学院课题小组
 */
@TableName("training_global_config")
@KeySequence("training_global_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class GlobalConfigDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 配置键（唯一），如 danmaku_enabled / verify_interval_min / max_delta_seconds 等
     */
    private String configKey;
    /**
     * 配置值
     */
    private String configValue;
    /**
     * 备注
     */
    private String remark;
    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Boolean deleted;

}
