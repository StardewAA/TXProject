package cn.iocoder.txgy.module.product.dal.dataobject.review;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 追加式经营资料/审核日志。不使用逻辑删除或通用更新时间，禁止随商品删除。 */
@Data
@TableName("product_spu_review_record")
public class ProductSpuReviewRecordDO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long spuId;
    private Long shopId;
    private Long contentVersion;
    private String submissionId;
    private String action;
    private Integer actorType;
    private Long actorId;
    private String remark;
    private String snapshot;
    private LocalDateTime createTime;
}
