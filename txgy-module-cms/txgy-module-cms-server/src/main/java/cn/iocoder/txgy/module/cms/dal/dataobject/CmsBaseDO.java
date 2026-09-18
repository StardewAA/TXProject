package cn.iocoder.txgy.module.cms.dal.dataobject;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * CMS 业务表公共字段基类
 *
 * 继承租户基类；同名字段遮蔽父类 creator/updater，映射到本模块的 create_by/update_by 列
 *
 * @author yuting_feng
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class CmsBaseDO extends TenantBaseDO {

    /**
     * 创建人ID（遮蔽父类 creator 字段，映射 create_by 列）
     */
    @TableField(fill = FieldFill.INSERT, value = "create_by")
    private String creator;
    /**
     * 更新人ID（遮蔽父类 updater 字段，映射 update_by 列）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE, value = "update_by")
    private String updater;
}
