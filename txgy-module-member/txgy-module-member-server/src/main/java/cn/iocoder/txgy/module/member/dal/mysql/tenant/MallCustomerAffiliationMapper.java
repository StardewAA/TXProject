package cn.iocoder.txgy.module.member.dal.mysql.tenant;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.member.dal.dataobject.tenant.MallCustomerAffiliationDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MallCustomerAffiliationMapper extends BaseMapperX<MallCustomerAffiliationDO> {
    // Explicit scope in addition to the tenant interceptor. No locks or writes on profile reads.
    @Select("SELECT * FROM member_mall_customer_affiliation WHERE tenant_id=#{tenantId} "
            + "AND member_id=#{memberId} AND deleted=FALSE")
    @Options(useCache = false)
    MallCustomerAffiliationDO selectForMember(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId);

    @Select("SELECT id FROM member_user WHERE id=#{memberId} AND tenant_id=#{tenantId} "
            + "AND deleted=FALSE AND status=0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Long lockMember(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId);

    @Select("SELECT * FROM member_mall_customer_affiliation WHERE tenant_id=#{tenantId} "
            + "AND member_id=#{memberId} AND deleted=FALSE FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    MallCustomerAffiliationDO lockAffiliation(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId);
}
