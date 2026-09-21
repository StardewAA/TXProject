package cn.iocoder.txgy.module.wms.service.md.warehouse;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.txgy.module.system.api.permission.PermissionApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseUserDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseUserMapper;
import cn.iocoder.txgy.module.wms.enums.md.WmsWarehouseUserRoleEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.*;

@Service
public class WmsWarehousePersonnelServiceImpl implements WmsWarehousePersonnelService {
    private static final String WAREHOUSE_LOGISTICS_DEPT = "仓储物流部";
    private static final Long WAREHOUSE_MANAGER_ROLE_ID = 60096L;
    private final WmsWarehouseUserMapper personnelMapper;
    private final WmsWarehouseMapper warehouseMapper;
    private final AdminUserApi adminUserApi;
    private final DeptApi deptApi;
    private final PermissionApi permissionApi;

    public WmsWarehousePersonnelServiceImpl(WmsWarehouseUserMapper personnelMapper, WmsWarehouseMapper warehouseMapper,
                                            AdminUserApi adminUserApi, DeptApi deptApi, PermissionApi permissionApi) {
        this.personnelMapper = personnelMapper;
        this.warehouseMapper = warehouseMapper;
        this.adminUserApi = adminUserApi;
        this.deptApi = deptApi;
        this.permissionApi = permissionApi;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replacePersonnel(Long warehouseId, Long managerUserId, Set<Long> memberUserIds) {
        if (warehouseMapper.selectById(warehouseId) == null) throw exception(WAREHOUSE_NOT_EXISTS);
        if (managerUserId == null) throw exception(WAREHOUSE_MANAGER_REQUIRED);
        LinkedHashSet<Long> allUserIds = new LinkedHashSet<>();
        allUserIds.add(managerUserId);
        if (memberUserIds != null) allUserIds.addAll(memberUserIds);
        Map<Long, AdminUserRespDTO> users = adminUserApi.getUserList(allUserIds).getCheckedData().stream()
                .collect(Collectors.toMap(AdminUserRespDTO::getId, Function.identity()));
        AdminUserRespDTO manager = users.get(managerUserId);
        Set<Long> managerUserIds = permissionApi.getUserRoleIdListByRoleIds(Set.of(WAREHOUSE_MANAGER_ROLE_ID))
                .getCheckedData();
        if (manager == null || !CommonStatusEnum.ENABLE.getStatus().equals(manager.getStatus())
                || !managerUserIds.contains(managerUserId)) {
            throw exception(WAREHOUSE_MANAGER_INVALID);
        }
        Set<Long> memberOnlyUserIds = allUserIds.stream().filter(userId -> !userId.equals(managerUserId))
                .collect(Collectors.toSet());
        Set<Long> deptIds = memberOnlyUserIds.stream().map(users::get).filter(Objects::nonNull)
                .map(AdminUserRespDTO::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, DeptRespDTO> departments = deptApi.getDeptList(deptIds).getCheckedData().stream()
                .collect(Collectors.toMap(DeptRespDTO::getId, Function.identity()));
        for (Long userId : memberOnlyUserIds) {
            AdminUserRespDTO user = users.get(userId);
            DeptRespDTO department = user == null ? null : departments.get(user.getDeptId());
            if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())
                    || department == null || !WAREHOUSE_LOGISTICS_DEPT.equals(department.getName())) {
                throw exception(WAREHOUSE_USER_INVALID);
            }
        }
        personnelMapper.deleteByWarehouseId(warehouseId);
        personnelMapper.insertBatch(allUserIds.stream().map(userId -> WmsWarehouseUserDO.builder()
                .warehouseId(warehouseId).userId(userId)
                .roleType(userId.equals(managerUserId) ? WmsWarehouseUserRoleEnum.MANAGER.getCode()
                : WmsWarehouseUserRoleEnum.MEMBER.getCode()).build()).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearPersonnel(Long warehouseId) {
        personnelMapper.deleteByWarehouseId(warehouseId);
    }

    @Override
    public Long getManagerUserId(Long warehouseId) {
        return personnelMapper.selectByWarehouseId(warehouseId).stream()
                .filter(row -> WmsWarehouseUserRoleEnum.MANAGER.getCode().equals(row.getRoleType()))
                .map(WmsWarehouseUserDO::getUserId).findFirst().orElse(null);
    }

    @Override
    public Set<Long> getMemberUserIds(Long warehouseId) {
        return personnelMapper.selectByWarehouseId(warehouseId).stream().map(WmsWarehouseUserDO::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override public boolean isMember(Long warehouseId, Long userId) { return userId != null && getMemberUserIds(warehouseId).contains(userId); }
    @Override public void validateMember(Long warehouseId, Long userId) { if (!isMember(warehouseId, userId)) throw exception(WAREHOUSE_USER_NOT_MEMBER); }
    @Override public void validateConfigured(Long warehouseId) {
        if (getManagerUserId(warehouseId) == null) throw exception(WAREHOUSE_MANAGER_REQUIRED);
        if (getMemberUserIds(warehouseId).isEmpty()) throw exception(WAREHOUSE_MEMBER_REQUIRED);
    }
}
