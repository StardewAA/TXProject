package cn.iocoder.txgy.module.mes.dal.mysql.wm.itemconsume;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.wm.itemconsume.vo.MesWmItemConsumeLinePageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.itemconsume.MesWmItemConsumeLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MES 物料消耗记录行 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesWmItemConsumeLineMapper extends BaseMapperX<MesWmItemConsumeLineDO> {

    default PageResult<MesWmItemConsumeLineDO> selectPage(MesWmItemConsumeLinePageReqVO reqVO,
                                                          Long consumeId) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesWmItemConsumeLineDO>()
                .eq(MesWmItemConsumeLineDO::getConsumeId, consumeId)
                .orderByDesc(MesWmItemConsumeLineDO::getId));
    }

    default List<MesWmItemConsumeLineDO> selectListByConsumeId(Long consumeId) {
        return selectList(MesWmItemConsumeLineDO::getConsumeId, consumeId);
    }

}
