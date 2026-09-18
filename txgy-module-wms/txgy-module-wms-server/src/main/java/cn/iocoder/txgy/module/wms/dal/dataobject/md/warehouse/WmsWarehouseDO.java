package cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * WMS 仓库 DO
 *
 * @author OPENLAB BS
 */
@TableName("wms_warehouse")
@KeySequence("wms_warehouse_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WmsWarehouseDO extends BaseDO {

    /**
     * 主键编号
     */
    @TableId
    private Long id;
    /**
     * 仓库编号
     */
    private String code;
    /**
     * 名称
     */
    private String name;
    /** RAW_MATERIAL / FINISHED_GOODS */
    private String warehouseType;
    /** 所属组织/部门 */
    private String organizationName;
    /** 所在城市 */
    private String city;
    private String address;
    private String principal;
    /** 负责人联系电话 */
    private String principalPhone;
    /** 启用状态 */
    private Integer status;
    /** 乐观锁主数据版本 */
    private Long dataVersion;
    /**
     * 备注
     */
    private String remark;
    /**
     * 排序
     */
    private Integer sort;

}
