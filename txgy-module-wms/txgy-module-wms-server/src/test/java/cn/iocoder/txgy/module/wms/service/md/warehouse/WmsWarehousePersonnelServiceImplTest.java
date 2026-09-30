package cn.iocoder.txgy.module.wms.service.md.warehouse;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.txgy.module.system.api.permission.PermissionApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseUserDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WmsWarehousePersonnelServiceImplTest {

    @Mock private WmsWarehouseUserMapper personnelMapper;
    @Mock private WmsWarehouseMapper warehouseMapper;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private PermissionApi permissionApi;
    private WmsWarehousePersonnelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WmsWarehousePersonnelServiceImpl(personnelMapper, warehouseMapper, adminUserApi, deptApi,
                permissionApi);
        when(warehouseMapper.selectById(106L)).thenReturn(WmsWarehouseDO.builder().id(106L).name("金岩原材料仓").build());
    }

    @Test
    void managerIsAutomaticallyIncludedInWarehouseMembers() {
        when(adminUserApi.getUserList(anyCollection())).thenReturn(CommonResult.success(List.of(
                user(1L, "负责人", 10L, CommonStatusEnum.ENABLE.getStatus()),
                user(2L, "员工", 20L, CommonStatusEnum.ENABLE.getStatus()))));
        when(deptApi.getDeptList(anyCollection())).thenReturn(CommonResult.success(List.of(dept(20L, "仓储物流部"))));
        when(permissionApi.getUserRoleIdListByRoleIds(anyCollection())).thenReturn(CommonResult.success(Set.of(1L)));

        service.replacePersonnel(106L, 1L, Set.of(2L));

        org.mockito.ArgumentCaptor<java.util.Collection<WmsWarehouseUserDO>> captor = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        org.mockito.Mockito.verify(personnelMapper).insertBatch(captor.capture());
        assertEquals(Set.of(1L, 2L), captor.getValue().stream().map(WmsWarehouseUserDO::getUserId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, captor.getValue().stream().filter(row -> "MANAGER".equals(row.getRoleType())).count());
    }

    @Test
    void acceptsWarehouseAdministratorAsManagerOutsideLogisticsDepartment() {
        when(adminUserApi.getUserList(anyCollection())).thenReturn(CommonResult.success(List.of(
                user(1L, "负责人", 10L, CommonStatusEnum.ENABLE.getStatus()),
                user(2L, "员工", 20L, CommonStatusEnum.ENABLE.getStatus()))));
        when(deptApi.getDeptList(anyCollection())).thenReturn(CommonResult.success(List.of(dept(20L, "仓储物流部"))));
        when(permissionApi.getUserRoleIdListByRoleIds(anyCollection())).thenReturn(CommonResult.success(Set.of(1L)));

        service.replacePersonnel(106L, 1L, Set.of(2L));
    }

    private static AdminUserRespDTO user(Long id, String nickname, Long deptId, Integer status) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setNickname(nickname);
        user.setDeptId(deptId);
        user.setStatus(status);
        return user;
    }

    private static DeptRespDTO dept(Long id, String name) {
        DeptRespDTO dept = new DeptRespDTO();
        dept.setId(id);
        dept.setName(name);
        return dept;
    }
}
