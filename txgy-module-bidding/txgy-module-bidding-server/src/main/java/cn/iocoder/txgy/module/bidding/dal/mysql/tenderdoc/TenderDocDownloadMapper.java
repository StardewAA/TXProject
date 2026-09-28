package cn.iocoder.txgy.module.bidding.dal.mysql.tenderdoc;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.tenderdoc.TenderDocDownloadDO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 招标文件下载记录 Mapper。
 *
 * <p>业务目标：下载名单查询（按招标项目/标段聚合）、下载人数统计、按文件+用户幂等去重。
 */
@Mapper
public interface TenderDocDownloadMapper extends BaseMapperX<TenderDocDownloadDO> {

    /** 按文件+用户查下载记录（幂等：同一文件同一用户只记一条）。 */
    default TenderDocDownloadDO selectByFileAndUser(Long fileId, Long userId) {
        return selectOne(TenderDocDownloadDO::getFileId, fileId,
                TenderDocDownloadDO::getUserId, userId);
    }

    /** 下载名单（按招标项目/标段；标段空=项目级全部）。 */
    default List<TenderDocDownloadDO> selectListByScope(String tenderProjectCode, String bidSectionCode) {
        return selectList(Wrappers.lambdaQuery(TenderDocDownloadDO.class)
                .eq(TenderDocDownloadDO::getTenderProjectCode, tenderProjectCode)
                .eq(bidSectionCode != null, TenderDocDownloadDO::getBidSectionCode, bidSectionCode)
                .orderByDesc(TenderDocDownloadDO::getDownloadTime));
    }

    /** 下载人数统计（按标段；供"投标人<3家不得开标"等判断参考）。 */
    default long countBySection(String bidSectionCode) {
        return selectCount(Wrappers.lambdaQuery(TenderDocDownloadDO.class)
                .eq(TenderDocDownloadDO::getBidSectionCode, bidSectionCode));
    }
}
