package cn.iocoder.txgy.module.wms.dal.dataobject.md.item;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("wms_item_quality_report")
@KeySequence("wms_item_quality_report_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WmsItemQualityReportDO extends BaseDO {
    @TableId
    private Long id;
    private Long itemId;
    private Integer status;
    /** JSON 字符串数组 */
    private String imageUrls;
    private String remark;
}
