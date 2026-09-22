package cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("wms_warehouse_user")
@KeySequence("wms_warehouse_user_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WmsWarehouseUserDO extends BaseDO {
    @TableId private Long id;
    private Long warehouseId;
    private Long userId;
    private String roleType;
}
