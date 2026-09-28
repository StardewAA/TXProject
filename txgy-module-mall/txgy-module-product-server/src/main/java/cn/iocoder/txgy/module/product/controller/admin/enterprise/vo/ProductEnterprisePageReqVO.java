package cn.iocoder.txgy.module.product.controller.admin.enterprise.vo;
import lombok.Data; import java.time.LocalDateTime; import java.util.Collection; import cn.iocoder.txgy.framework.common.pojo.PageParam;
@Data public class ProductEnterprisePageReqVO extends PageParam { private String name; private String creditCode; private Integer auditStatus; private LocalDateTime[] createTime; private Collection<Long> enterpriseIds; }
