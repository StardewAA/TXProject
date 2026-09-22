package cn.iocoder.txgy.module.product.dal.mysql.enterprise;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.product.controller.admin.enterprise.vo.ProductEnterprisePageReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper public interface ProductEnterpriseMapper extends BaseMapperX<ProductEnterpriseDO> {
 default PageResult<ProductEnterpriseDO> selectPage(ProductEnterprisePageReqVO req) { return selectPage(req, new LambdaQueryWrapperX<ProductEnterpriseDO>().likeIfPresent(ProductEnterpriseDO::getName, req.getName()).likeIfPresent(ProductEnterpriseDO::getCreditCode, req.getCreditCode()).eqIfPresent(ProductEnterpriseDO::getAuditStatus, req.getAuditStatus()).inIfPresent(ProductEnterpriseDO::getId, req.getEnterpriseIds()).orderByDesc(ProductEnterpriseDO::getId)); }
 default ProductEnterpriseDO selectByCreditCode(String code) { return selectOne(ProductEnterpriseDO::getCreditCode, code); }
 default List<ProductEnterpriseDO> selectApproved() { return selectList(ProductEnterpriseDO::getAuditStatus, 1); }
}
