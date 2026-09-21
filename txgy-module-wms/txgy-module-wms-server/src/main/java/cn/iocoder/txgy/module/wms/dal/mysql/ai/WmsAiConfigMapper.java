package cn.iocoder.txgy.module.wms.dal.mysql.ai;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.wms.dal.dataobject.ai.WmsAiConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * WMS AI 客服配置 Mapper
 *
 * <p>单租户单条：取最新一条即可。租户条件由多租户插件自动追加。
 */
@Mapper
public interface WmsAiConfigMapper extends BaseMapperX<WmsAiConfigDO> {

    /**
     * 取当前租户最新一条 AI 配置（没有则返回 null）
     */
    default WmsAiConfigDO selectLatest() {
        return selectLastOne(new LambdaQueryWrapperX<WmsAiConfigDO>());
    }

}
