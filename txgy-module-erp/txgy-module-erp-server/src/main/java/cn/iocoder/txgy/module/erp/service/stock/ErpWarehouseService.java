package cn.iocoder.txgy.module.erp.service.stock;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWarehouseUpsertCallbackReqDTO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.warehouse.ErpWarehouseSaveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.warehouse.ErpWarehousePageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;

/**
 * ERP 仓库 Service 接口
 *
 * @author OPENLAB BS
 */
public interface ErpWarehouseService {

    /**
     * 创建仓库
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createWarehouse(@Valid ErpWarehouseSaveReqVO createReqVO);

    /**
     * 更新ERP 仓库
     *
     * @param updateReqVO 更新信息
     */
    void updateWarehouse(@Valid ErpWarehouseSaveReqVO updateReqVO);

    /**
     * 更新仓库默认状态
     *
     * @param id     编号
     * @param defaultStatus 默认状态
     */
    void updateWarehouseDefaultStatus(Long id, Boolean defaultStatus);

    /**
     * 删除仓库
     *
     * @param id 编号
     */
    void deleteWarehouse(Long id);

    /**
     * 获得仓库
     *
     * @param id 编号
     * @return 仓库
     */
    ErpWarehouseDO getWarehouse(Long id);

    /**
     * 校验仓库列表的有效性
     *
     * @param ids 编号数组
     * @return 仓库列表
     */
    List<ErpWarehouseDO> validWarehouseList(Collection<Long> ids);

    /**
     * 获得指定状态的仓库列表
     *
     * @param status 状态
     * @return 仓库列表
     */
    List<ErpWarehouseDO> getWarehouseListByStatus(Integer status);

    /**
     * 获得仓库列表
     *
     * @param ids 编号数组
     * @return 仓库列表
     */
    List<ErpWarehouseDO> getWarehouseList(Collection<Long> ids);

    /**
     * 获得仓库 Map
     *
     * @param ids 编号数组
     * @return 仓库 Map
     */
    default Map<Long, ErpWarehouseDO> getWarehouseMap(Collection<Long> ids) {
        return convertMap(getWarehouseList(ids), ErpWarehouseDO::getId);
    }

    /**
     * 获得仓库分页
     *
     * @param pageReqVO 分页查询
     * @return 仓库分页
     */
    PageResult<ErpWarehouseDO> getWarehousePage(ErpWarehousePageReqVO pageReqVO);

    /**
     * 基于 WMS 推送的主数据 upsert ERP 仓库。
     *
     * <p>由 WMS 端在仓库增删改时通过 RPC 调用。ERP 端按 {@code wmsWarehouseCode}
     * 匹配现有仓库：不存在则新建（不设置 defaultStatus，不动 principalUserId），
     * 已存在则更新 name/address/principal/status，不动 wmsWarehouseCode、principalUserId、defaultStatus。</p>
     *
     * @param req WMS 推送仓库主数据
     */
    void upsertFromWms(@Valid ErpWarehouseUpsertCallbackReqDTO req);

}