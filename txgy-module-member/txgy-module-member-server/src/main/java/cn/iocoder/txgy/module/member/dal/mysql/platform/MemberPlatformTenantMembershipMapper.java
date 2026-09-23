package cn.iocoder.txgy.module.member.dal.mysql.platform;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformTenantMembershipDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemberPlatformTenantMembershipMapper extends BaseMapperX<MemberPlatformTenantMembershipDO> {

    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT *
        FROM member_platform_tenant_membership
        WHERE platform_account_id=#{platformAccountId} AND status=0 AND deleted=FALSE
          AND (valid_from IS NULL OR valid_from<=NOW())
          AND (valid_until IS NULL OR valid_until>NOW())
          AND tenant_id<>3000
        ORDER BY tenant_id,enterprise_id,member_type
        """)
    List<MemberPlatformTenantMembershipDO> selectActiveByPlatformAccountId(
            @Param("platformAccountId") Long platformAccountId);
}
