package cn.iocoder.txgy.module.hrm.dal.mysql.sandbox;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.sandbox.HrmSandboxRecordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * HRM 沙箱数据登记 Mapper（I-025）
 *
 * @author OPENLAB BS
 */
@Mapper
public interface HrmSandboxRecordMapper extends BaseMapperX<HrmSandboxRecordDO> {

    /**
     * 按创建时间倒序列出当前租户的沙箱登记记录
     */
    default List<HrmSandboxRecordDO> selectListOrderByIdDesc() {
        return selectList(new LambdaQueryWrapperX<HrmSandboxRecordDO>()
                .orderByDesc(HrmSandboxRecordDO::getId));
    }

}
