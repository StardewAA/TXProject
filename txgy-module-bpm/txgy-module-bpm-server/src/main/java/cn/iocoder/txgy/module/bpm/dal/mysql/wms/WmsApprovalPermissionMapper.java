package cn.iocoder.txgy.module.bpm.dal.mysql.wms;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** WMS 专用审批候选人只读查询；不改变共享权限服务或 ERP/MES 的分配策略。 */
@Mapper
public interface WmsApprovalPermissionMapper {
    @InterceptorIgnore(tenantLine = "true") // 下列共享表查询均显式绑定租户，system_menu 为全局元数据。
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT id FROM system_menu WHERE permission=#{permission} AND type=3 AND status=0 AND deleted=0")
    Long selectEnabledMenuId(@Param("permission") String permission);

    @InterceptorIgnore(tenantLine = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT p.menu_ids FROM system_tenant t
        JOIN system_tenant_package p ON p.id=t.package_id AND p.status=0 AND p.deleted=0
        WHERE t.id=#{tenantId} AND t.status=0 AND t.deleted=0
        """)
    String selectPackageMenuIds(@Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT DISTINCT u.id FROM system_users u
        JOIN system_user_role ur ON ur.user_id=u.id AND ur.tenant_id=u.tenant_id AND ur.deleted=0
        JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=u.tenant_id AND r.status=0 AND r.deleted=0
        JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=u.tenant_id AND rm.deleted=0
        WHERE u.tenant_id=#{tenantId} AND u.status=0 AND u.deleted=0 AND rm.menu_id=#{menuId}
        ORDER BY u.id
        """)
    List<Long> selectUserIds(@Param("tenantId") Long tenantId, @Param("menuId") Long menuId);
}
