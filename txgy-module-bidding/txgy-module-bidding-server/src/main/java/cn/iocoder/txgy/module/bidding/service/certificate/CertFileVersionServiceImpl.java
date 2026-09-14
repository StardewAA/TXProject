package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertFileRegisterReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertFileVersionMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.enums.CertFileCategoryEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateVirusScanStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 证照文件版本服务实现（T 轨；cert_file_versions 只追加，登记/扫描回写与主表同事务）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §三（file_category、同类别版本号、is_current、
 * virus_scan_status 口径）＋§18.2（file_id/hash/size、加注件元数据 4 列）；《招投标模块开发规范》§5 三-5
 * （文件本体经 FileApi 上传，本服务只登记文件对象元数据，URL 非一致性依据；上传须过病毒扫描与类型白名单）。
 * <p>设计遵循/针对：① 同证同哈希判重（UK uk_cert_file_hash）——重复登记抛 CERT_FILE_HASH_DUPLICATE；
 * ② 同类别版本号 = 当前最大版本 + 1，新版本行 is_current=TRUE 并关闭旧当前行，DB 唯一约束
 * uk_cert_cat_version 兜底并发，冲突映射为 CERT_FILE_VERSION_CONFLICT；③ 病毒扫描默认 PENDING，
 * CLEAN 才可预览/下载、INFECTED 自动置 is_current=false（v3.3 §三高安全底线）；④ 加注件行（GB/T 36903 §5.4）
 * 可登记 copy_* 四要素，与 GB/T 36906 §6.2 加注件下载/登记入参对应。
 */
@Service
public class CertFileVersionServiceImpl implements CertFileVersionService {

    @Resource
    private CertFileVersionMapper certFileVersionMapper;
    @Resource
    private CertificateInfoMapper certificateInfoMapper;

    @Override
    public List<CertFileVersionDO> listFiles(Long certId) {
        return certFileVersionMapper.listAllByCertId(certId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long registerFile(CertFileRegisterReqVO reqVO) {
        CertificateInfoDO cert = certificateInfoMapper.selectById(reqVO.getCertId());
        if (cert == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NOT_EXISTS);
        }
        String category = resolveCategory(reqVO.getFileCategory());

        // 同证同哈希防重（UK uk_cert_file_hash，v3.3 §三/开发规范 §5 三-5）
        if (certFileVersionMapper.selectByCertIdAndHash(reqVO.getCertId(), reqVO.getFileHash()) != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_FILE_HASH_DUPLICATE);
        }
        // 同类别版本号：当前最大版本 + 1（v3.3 §三；只追加）
        Integer max = certFileVersionMapper.selectMaxVersion(reqVO.getCertId(), category);
        int nextVersion = (max == null ? 0 : max) + 1;

        // 关闭同类别旧当前行（is_current 只允许一行，v3.3 §三）
        List<CertFileVersionDO> currents = certFileVersionMapper.selectCurrentByCategory(reqVO.getCertId(), category);
        for (CertFileVersionDO current : currents) {
            current.setIsCurrent(Boolean.FALSE);
            certFileVersionMapper.updateById(current);
        }

        CertFileVersionDO file = new CertFileVersionDO();
        file.setCertId(reqVO.getCertId());
        file.setFileCategory(category);
        file.setFileName(reqVO.getFileName());
        file.setFileId(reqVO.getFileId());
        file.setFileHash(reqVO.getFileHash());
        file.setFileFormat(reqVO.getFileFormat());
        file.setFileSize(reqVO.getFileSize());
        file.setCopyCreationTime(reqVO.getCopyCreationTime());
        file.setCopyCreator(reqVO.getCopyCreator());
        file.setCopyCause(reqVO.getCopyCause());
        file.setCopyExpiringTime(reqVO.getCopyExpiringTime());
        file.setVersion(nextVersion);
        file.setIsCurrent(Boolean.TRUE);
        file.setVirusScanStatus(CertificateVirusScanStatusEnum.PENDING.name());
        file.setRemark(reqVO.getRemark());
        try {
            certFileVersionMapper.insert(file);
        } catch (DuplicateKeyException e) {
            // 并发同类别同版本追加被 UK 拒绝（开发规范 §5 三-2 语义类比），映射为业务错误
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_FILE_VERSION_CONFLICT);
        }
        return file.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markScanResult(Long fileVersionId, String virusScanStatus) {
        CertFileVersionDO file = certFileVersionMapper.selectById(fileVersionId);
        if (file == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_FILE_NOT_EXISTS);
        }
        CertificateVirusScanStatusEnum status;
        try {
            status = CertificateVirusScanStatusEnum.valueOf(virusScanStatus);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_FILE_PARAM_INVALID);
        }
        file.setVirusScanStatus(status.name());
        if (status == CertificateVirusScanStatusEnum.INFECTED) {
            file.setIsCurrent(Boolean.FALSE); // 感染文件不得作为当前版本使用（v3.3 §三）
        }
        certFileVersionMapper.updateById(file);
    }

    /** 类别解析：缺省 ORIGINAL；未知类别抛 CERT_FILE_PARAM_INVALID（code 存储，开发规范 §5 三-7）。 */
    private String resolveCategory(String fileCategory) {
        if (fileCategory == null || fileCategory.isBlank()) {
            return CertFileCategoryEnum.ORIGINAL.name();
        }
        try {
            return CertFileCategoryEnum.valueOf(fileCategory).name();
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_FILE_PARAM_INVALID);
        }
    }
}
