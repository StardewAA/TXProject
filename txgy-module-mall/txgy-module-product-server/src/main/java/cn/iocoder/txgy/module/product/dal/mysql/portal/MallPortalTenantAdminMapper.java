package cn.iocoder.txgy.module.product.dal.mysql.portal;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.product.controller.admin.portal.vo.MallPortalTenantPageReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.portal.MallPortalTenantDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 平台公开租户目录的后台管理 Mapper。
 *
 * <p>目录本身是平台级元数据，{@code tenant_id} 表示被管理的目标租户，
 * 因此不能被当前登录租户的 MyBatis 租户拦截器改写。</p>
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface MallPortalTenantAdminMapper extends BaseMapperX<MallPortalTenantDO> {

    default PageResult<MallPortalTenantDO> selectPage(MallPortalTenantPageReqVO request) {
        return selectPage(request, new LambdaQueryWrapperX<MallPortalTenantDO>()
                .eqIfPresent(MallPortalTenantDO::getTenantId, request.getTenantId())
                .likeIfPresent(MallPortalTenantDO::getTenantName, request.getTenantName())
                .likeIfPresent(MallPortalTenantDO::getTenantSlug, request.getTenantSlug())
                .eqIfPresent(MallPortalTenantDO::getOpenStatus, request.getOpenStatus())
                .orderByAsc(MallPortalTenantDO::getSort)
                .orderByAsc(MallPortalTenantDO::getTenantId));
    }

    default MallPortalTenantDO selectBySlugExcludingId(String slug, Long excludedId) {
        return selectOne(new LambdaQueryWrapperX<MallPortalTenantDO>()
                .eq(MallPortalTenantDO::getTenantSlug, slug)
                .ne(MallPortalTenantDO::getId, excludedId)
                .last("LIMIT 1"));
    }

    @Select("SELECT id,tenant_id AS tenantId,tenant_slug AS tenantSlug,tenant_name AS tenantName,"
            + " tenant_logo AS tenantLogo,main_categories AS mainCategories,"
            + " open_status AS openStatus,sort,creator,create_time AS createTime,"
            + " updater,update_time AS updateTime,deleted"
            + " FROM mall_portal_tenant WHERE id=#{id} AND deleted=0 LIMIT 1 FOR UPDATE")
    MallPortalTenantDO selectPortalForUpdate(@Param("id") Long id);

    /**
     * 包含逻辑删除状态地读取系统租户：关闭门户是失效租户仍需允许的降权操作。
     */
    @Select("SELECT id,name,status,expire_time AS expireTime,deleted"
            + " FROM system_tenant WHERE id=#{tenantId} LIMIT 1")
    SystemTenantSnapshot selectSystemTenant(@Param("tenantId") Long tenantId);

    /**
     * 只更新门户公开元数据，显式绑定原 tenant_id，禁止通过请求迁移归属。
     */
    @Update("UPDATE mall_portal_tenant SET tenant_slug=#{row.tenantSlug},"
            + " tenant_name=#{row.tenantName},"
            + " tenant_logo=#{row.tenantLogo,jdbcType=VARCHAR},"
            + " main_categories=#{row.mainCategories,jdbcType=VARCHAR},"
            + " open_status=#{row.openStatus},sort=#{row.sort},"
            + " updater=#{updater},update_time=NOW()"
            + " WHERE id=#{row.id} AND tenant_id=#{row.tenantId} AND deleted=0")
    int updatePortalConfig(@Param("row") MallPortalTenantDO row, @Param("updater") String updater);

    @Data
    class SystemTenantSnapshot {
        private Long id;
        private String name;
        private Integer status;
        private LocalDateTime expireTime;
        private Boolean deleted;
    }
}
