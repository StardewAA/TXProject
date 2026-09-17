package cn.iocoder.txgy.module.promotion.dal.dataobject.point;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 已成功提交的积分活动返库流水，不提供逻辑删除，避免删除后再次返库。 */
@Data
@TableName("promotion_point_stock_return")
public class PointStockReturnDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long orderItemId;
    private Long activityId;
    private Long skuId;
    private Integer returnCount;
    private LocalDateTime createTime;
}
