package cn.iocoder.txgy.module.bidding.dal.mysql.ca;

// 规范依据：《招投标模块开发规范》§5 数据层（Mapper 继承 BaseMapperX，单表零 SQL 优先）

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealPageReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ca.CaSealDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * CA 文件签章记录 Mapper。
 *
 * <p>业务目标：签章记录直接落库（测试直接入库），台账分页/详情查询。
 */
@Mapper
public interface CaSealMapper extends BaseMapperX<CaSealDO> {

    /** 签章台账分页（按文件名/签章单位/验签状态过滤）。 */
    default PageResult<CaSealDO> selectPage(CaSealPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CaSealDO>()
                .likeIfPresent(CaSealDO::getDocumentName, reqVO.getDocumentName())
                .likeIfPresent(CaSealDO::getOrgName, reqVO.getOrgName())
                .eqIfPresent(CaSealDO::getVerifyStatus, reqVO.getVerifyStatus())
                .eqIfPresent(CaSealDO::getTrackId, reqVO.getTrackId())
                .orderByDesc(CaSealDO::getId));
    }

    /** 按签章会话 documentId 查记录（回调回写用）。 */
    default CaSealDO selectByDocumentId(String documentId) {
        return selectOne(CaSealDO::getDocumentId, documentId);
    }

    /** 按原文件 fileId 查最近一条签章记录（上传后检测是否已 CA 认证）。 */
    default CaSealDO selectLatestByFileId(Long fileId) {
        return selectOne(new LambdaQueryWrapperX<CaSealDO>()
                .eq(CaSealDO::getFileId, fileId)
                .orderByDesc(CaSealDO::getId)
                .last("LIMIT 1"));
    }

    /** 按原文件 SHA-256 查最近一条签章记录（fileId 未匹配时的兜底）。 */
    default CaSealDO selectLatestBySha256(String sha256) {
        return selectOne(new LambdaQueryWrapperX<CaSealDO>()
                .eq(CaSealDO::getFileSha256, sha256)
                .orderByDesc(CaSealDO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 按「已签章产物」的 fileId 查最近一条签章记录（契约 A5 第三优先级）。
     *
     * <p>场景：用户把**已签章的那个文件**再上传一次并点"检测"，此时传进来的 fileId 是
     * {@code seal_file_id} 而不是原文件 {@code file_id}，前两个优先级都匹配不到 →
     * 会被误判为"未 CA 认证"，进而提示重复签章。
     */
    default CaSealDO selectLatestBySealFileId(Long sealFileId) {
        return selectOne(new LambdaQueryWrapperX<CaSealDO>()
                .eq(CaSealDO::getSealFileId, sealFileId)
                .orderByDesc(CaSealDO::getId)
                .last("LIMIT 1"));
    }

    /** 按验签状态计数（演示统计卡：PASS/FAIL/UNVERIFIED）。 */
    default long selectCountByVerifyStatus(String verifyStatus) {
        return selectCount(new LambdaQueryWrapperX<CaSealDO>()
                .eq(CaSealDO::getVerifyStatus, verifyStatus));
    }

    /** 按文件名前缀删除（演示数据清理：演示签章文件- / 演示篡改文件-）。 */
    default int deleteByDemoPrefix(String prefix) {
        return delete(new LambdaQueryWrapperX<CaSealDO>()
                .likeRight(CaSealDO::getDocumentName, prefix));
    }
}
