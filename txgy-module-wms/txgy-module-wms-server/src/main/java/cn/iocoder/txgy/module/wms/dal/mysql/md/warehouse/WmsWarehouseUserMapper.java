package cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseUserDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper
public interface WmsWarehouseUserMapper extends BaseMapperX<WmsWarehouseUserDO> {
    default List<WmsWarehouseUserDO> selectByWarehouseId(Long warehouseId) {
        return selectList(new LambdaQueryWrapper<WmsWarehouseUserDO>().eq(WmsWarehouseUserDO::getWarehouseId, warehouseId));
    }
    /**
     * Replace-all semantics require a physical delete. Logical deletion would
     * leave a unique-key tombstone (tenant, warehouse, user, deleted=1), and
     * the next assignment of the same user would fail with a duplicate key.
     */
    @Delete("DELETE FROM wms_warehouse_user WHERE warehouse_id = #{warehouseId}")
    void deleteByWarehouseId(Long warehouseId);
    default Set<Long> selectWarehouseIdsByUserId(Long userId) {
        return selectList(new LambdaQueryWrapper<WmsWarehouseUserDO>().eq(WmsWarehouseUserDO::getUserId, userId))
                .stream().map(WmsWarehouseUserDO::getWarehouseId).collect(Collectors.toSet());
    }
}
