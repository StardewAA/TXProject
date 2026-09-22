package cn.iocoder.txgy.module.product.dal.mysql.audit;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.product.dal.dataobject.audit.ProductBusinessOperationAuditDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductBusinessOperationAuditMapper extends BaseMapperX<ProductBusinessOperationAuditDO> {
}
