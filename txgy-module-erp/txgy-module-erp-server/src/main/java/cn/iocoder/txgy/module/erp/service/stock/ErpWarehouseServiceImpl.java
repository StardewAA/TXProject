package cn.iocoder.txgy.module.erp.service.stock;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWarehouseUpsertCallbackReqDTO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.warehouse.ErpWarehouseSaveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.warehouse.ErpWarehousePageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpWarehouseMapper;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

/**
 * ERP 仓库 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpWarehouseServiceImpl implements ErpWarehouseService {

    @Resource
    private ErpWarehouseMapper warehouseMapper;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public Long createWarehouse(ErpWarehouseSaveReqVO createReqVO) {
        fillPrincipal(createReqVO);
        createReqVO.setWmsWarehouseCode(normalizeWmsWarehouseCode(createReqVO.getWmsWarehouseCode()));
        validateWmsWarehouseCodeUnique(null, createReqVO.getWmsWarehouseCode());
        // 插入
        ErpWarehouseDO warehouse = BeanUtils.toBean(createReqVO, ErpWarehouseDO.class);
        warehouseMapper.insert(warehouse);
        // 返回
        return warehouse.getId();
    }

    @Override
    public void updateWarehouse(ErpWarehouseSaveReqVO updateReqVO) {
        updateReqVO.setWmsWarehouseCode(normalizeWmsWarehouseCode(updateReqVO.getWmsWarehouseCode()));
        // 校验存在
        validateWarehouseExists(updateReqVO.getId());
        validateWmsWarehouseCodeUnique(updateReqVO.getId(), updateReqVO.getWmsWarehouseCode());
        fillPrincipal(updateReqVO);
        // 更新
        ErpWarehouseDO updateObj = BeanUtils.toBean(updateReqVO, ErpWarehouseDO.class);
        warehouseMapper.updateById(updateObj);
    }

    private void fillPrincipal(ErpWarehouseSaveReqVO reqVO) {
        if (reqVO.getPrincipalUserId() == null) {
            return;
        }
        adminUserApi.validateUser(reqVO.getPrincipalUserId());
        reqVO.setPrincipal(adminUserApi.getUser(reqVO.getPrincipalUserId()).getCheckedData().getNickname());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWarehouseDefaultStatus(Long id, Boolean defaultStatus) {
        // 1. 校验存在
        validateWarehouseExists(id);

        // 2.1 如果开启，则需要关闭所有其它的默认
        if (defaultStatus) {
            ErpWarehouseDO warehouse = warehouseMapper.selectByDefaultStatus();
            if (warehouse != null) {
                warehouseMapper.updateById(new ErpWarehouseDO().setId(warehouse.getId()).setDefaultStatus(false));
            }
        }
        // 2.2 更新对应的默认状态
        warehouseMapper.updateById(new ErpWarehouseDO().setId(id).setDefaultStatus(defaultStatus));
    }

    @Override
    public void deleteWarehouse(Long id) {
        // 校验存在
        validateWarehouseExists(id);
        // 删除
        warehouseMapper.deleteById(id);
    }

    private void validateWarehouseExists(Long id) {
        if (warehouseMapper.selectById(id) == null) {
            throw exception(WAREHOUSE_NOT_EXISTS);
        }
    }

    private void validateWmsWarehouseCodeUnique(Long id, String wmsWarehouseCode) {
        if (wmsWarehouseCode == null || wmsWarehouseCode.isBlank()) {
            return;
        }
        ErpWarehouseDO existing = warehouseMapper.selectByWmsWarehouseCode(wmsWarehouseCode.trim());
        if (existing != null && !existing.getId().equals(id)) {
            throw exception(WAREHOUSE_WMS_CODE_DUPLICATE, wmsWarehouseCode);
        }
    }

    private String normalizeWmsWarehouseCode(String wmsWarehouseCode) {
        return wmsWarehouseCode == null || wmsWarehouseCode.isBlank() ? null : wmsWarehouseCode.trim();
    }

    @Override
    public ErpWarehouseDO getWarehouse(Long id) {
        return warehouseMapper.selectById(id);
    }

    @Override
    public List<ErpWarehouseDO> validWarehouseList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        List<ErpWarehouseDO> list = warehouseMapper.selectByIds(ids);
        Map<Long, ErpWarehouseDO> warehouseMap = convertMap(list, ErpWarehouseDO::getId);
        for (Long id : ids) {
            ErpWarehouseDO warehouse = warehouseMap.get(id);
            if (warehouseMap.get(id) == null) {
                throw exception(WAREHOUSE_NOT_EXISTS);
            }
            if (CommonStatusEnum.isDisable(warehouse.getStatus())) {
                throw exception(WAREHOUSE_NOT_ENABLE, warehouse.getName());
            }
        }
        return list;
    }

    @Override
    public List<ErpWarehouseDO> getWarehouseListByStatus(Integer status) {
        return warehouseMapper.selectListByStatus(status);
    }

    @Override
    public List<ErpWarehouseDO> getWarehouseList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return warehouseMapper.selectByIds(ids);
    }

    @Override
    public PageResult<ErpWarehouseDO> getWarehousePage(ErpWarehousePageReqVO pageReqVO) {
        return warehouseMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsertFromWms(ErpWarehouseUpsertCallbackReqDTO req) {
        // 1. 校验入参
        if (req.getWarehouseCode() == null || req.getWarehouseCode().isBlank()) {
            throw exception(WAREHOUSE_WMS_CODE_NOT_CONFIGURED, "空");
        }
        Integer status = mapWmsStatus(req.getStatus());
        String trimmedCode = req.getWarehouseCode().trim();

        // 2. 按 wmsWarehouseCode 查现有仓库
        ErpWarehouseDO existing = warehouseMapper.selectByWmsWarehouseCode(trimmedCode);
        if (existing == null) {
            // 2.1 新建：仅写 WMS 推送的字段，不动 defaultStatus / principalUserId
            validateWmsWarehouseCodeUnique(null, trimmedCode);
            ErpWarehouseDO warehouse = ErpWarehouseDO.builder()
                    .wmsWarehouseCode(trimmedCode)
                    .name(req.getName())
                    .address(req.getAddress())
                    .principal(req.getPrincipal())
                    .status(status)
                    // sort 在 erp_warehouse 中为 NOT NULL 且无数据库默认值，
                    // WMS 未下发时按 0 落库，否则 insert 会抛 "Field 'sort' doesn't have a default value"
                    .sort(req.getSort() == null ? 0L : req.getSort().longValue())
                    .build();
            warehouseMapper.insert(warehouse);
            return;
        }
        // 2.2 更新：仅更新可变字段，不动 wmsWarehouseCode、principalUserId、defaultStatus
        ErpWarehouseDO updateObj = new ErpWarehouseDO().setId(existing.getId())
                .setName(req.getName())
                .setAddress(req.getAddress())
                .setPrincipal(req.getPrincipal())
                .setStatus(status);
        if (req.getSort() != null) {
            updateObj.setSort(req.getSort().longValue());
        }
        warehouseMapper.updateById(updateObj);
    }

    private Integer mapWmsStatus(String status) {
        if ("ENABLED".equalsIgnoreCase(status)) return CommonStatusEnum.ENABLE.getStatus();
        if ("DISABLED".equalsIgnoreCase(status)) return CommonStatusEnum.DISABLE.getStatus();
        throw exception(WAREHOUSE_NOT_ENABLE, status);
    }

}
