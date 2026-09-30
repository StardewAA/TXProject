package cn.iocoder.txgy.module.infra.dal.mysql.file;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.infra.dal.dataobject.file.FileUploadTicketDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 文件上传凭证 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface FileUploadTicketMapper extends BaseMapperX<FileUploadTicketDO> {

    /**
     * 查询一张仍然有效、且属于同一登录身份的凭证
     *
     * 说明：不含租户条件，避免 tenant_id 为 NULL 时 {@code eq} 生成 {@code = null} 永不匹配；
     * 身份由 (ownerUserType, ownerUserId) 精确锁定即可。
     */
    default FileUploadTicketDO selectValid(Long configId, String path,
                                           Integer ownerUserType, Long ownerUserId,
                                           LocalDateTime now) {
        if (configId == null || path == null || ownerUserType == null || ownerUserId == null) {
            return null;
        }
        return selectLastOne(new LambdaQueryWrapperX<FileUploadTicketDO>()
                .eq(FileUploadTicketDO::getConfigId, configId)
                .eq(FileUploadTicketDO::getPath, path)
                .eq(FileUploadTicketDO::getOwnerUserType, ownerUserType)
                .eq(FileUploadTicketDO::getOwnerUserId, ownerUserId)
                .gt(FileUploadTicketDO::getExpireTime, now)
                .orderByAsc(FileUploadTicketDO::getId));
    }

    /**
     * 删除同一 (configId, path, 身份) 的历史凭证（重复预签名时刷新）
     */
    default int deleteByKey(Long configId, String path, Integer ownerUserType, Long ownerUserId) {
        if (configId == null || path == null || ownerUserType == null || ownerUserId == null) {
            return 0;
        }
        return delete(new LambdaQueryWrapperX<FileUploadTicketDO>()
                .eq(FileUploadTicketDO::getConfigId, configId)
                .eq(FileUploadTicketDO::getPath, path)
                .eq(FileUploadTicketDO::getOwnerUserType, ownerUserType)
                .eq(FileUploadTicketDO::getOwnerUserId, ownerUserId));
    }

    /**
     * 清理过期凭证
     */
    default int deleteExpired(LocalDateTime now) {
        return delete(new LambdaQueryWrapperX<FileUploadTicketDO>()
                .lt(FileUploadTicketDO::getExpireTime, now));
    }

}
