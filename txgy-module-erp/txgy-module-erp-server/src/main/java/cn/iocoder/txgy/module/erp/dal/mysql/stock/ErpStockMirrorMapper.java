package cn.iocoder.txgy.module.erp.dal.mysql.stock;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpStockMirrorDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * ERP 实物库存镜像 Mapper
 *
 * <p>本表只由同步任务写入，业务代码不得直接修改。</p>
 *
 * @author OPENLAB BS
 */
@Mapper
public interface ErpStockMirrorMapper extends BaseMapperX<ErpStockMirrorDO> {

    /**
     * 按产品 + 仓库查询镜像记录
     */
    default ErpStockMirrorDO selectByProductAndWarehouse(Long productId, Long warehouseId) {
        return selectOne(ErpStockMirrorDO::getProductId, productId,
                ErpStockMirrorDO::getWarehouseId, warehouseId);
    }

    /**
     * 查询租户下全部镜像记录
     */
    default List<ErpStockMirrorDO> selectListAll() {
        return selectList(new LambdaQueryWrapper<>());
    }

}
