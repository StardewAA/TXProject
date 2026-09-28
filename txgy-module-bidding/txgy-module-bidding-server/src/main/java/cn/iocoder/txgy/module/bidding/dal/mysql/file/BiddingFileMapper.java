package cn.iocoder.txgy.module.bidding.dal.mysql.file;

// 规范依据：《招投标模块开发规范》§4.2（Mapper/BaseMapperX）

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.file.BiddingFileDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件登记 Mapper（file_id → 存储坐标）。
 */
@Mapper
public interface BiddingFileMapper extends BaseMapperX<BiddingFileDO> {

    /** 按文件编号查登记（file_id 全局唯一，故取一条）。 */
    default BiddingFileDO selectByFileId(Long fileId) {
        return selectOne(new LambdaQueryWrapperX<BiddingFileDO>()
                .eq(BiddingFileDO::getFileId, fileId));
    }
}
