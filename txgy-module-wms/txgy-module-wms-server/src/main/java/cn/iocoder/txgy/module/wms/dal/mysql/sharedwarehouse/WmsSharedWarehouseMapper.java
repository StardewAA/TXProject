package cn.iocoder.txgy.module.wms.dal.mysql.sharedwarehouse;

import cn.iocoder.txgy.module.wms.dal.dataobject.sharedwarehouse.WmsSharedWarehouseProductQueryDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.sharedwarehouse.WmsSharedWarehouseQueryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 共享仓库只读聚合查询 Mapper。
 */
@Mapper
public interface WmsSharedWarehouseMapper {

    List<WmsSharedWarehouseQueryDO> selectWarehousePage(@Param("code") String code,
                                                        @Param("name") String name,
                                                        @Param("sourceSystem") String sourceSystem,
                                                        @Param("offset") long offset,
                                                        @Param("pageSize") int pageSize);

    List<WmsSharedWarehouseQueryDO> selectWarehouseList(@Param("code") String code,
                                                        @Param("name") String name,
                                                        @Param("sourceSystem") String sourceSystem);

    Long selectWarehouseCount(@Param("code") String code,
                              @Param("name") String name,
                              @Param("sourceSystem") String sourceSystem);

    List<WmsSharedWarehouseProductQueryDO> selectProductPage(@Param("warehouseId") Long warehouseId,
                                                             @Param("itemName") String itemName,
                                                             @Param("sourceSystem") String sourceSystem,
                                                             @Param("offset") long offset,
                                                             @Param("pageSize") int pageSize);

    Long selectProductCount(@Param("warehouseId") Long warehouseId,
                            @Param("itemName") String itemName,
                            @Param("sourceSystem") String sourceSystem);

}
