package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertFileCorrectReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertFileRegisterReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertFileVersionMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.enums.CertFileCategoryEnum;
import cn.iocoder.txgy.module.bidding.enums.file.BiddingFileVirusStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 证照文件版本服务实现（T 轨；cert_file_versions 只追加，登记/扫描回写与主表同事务）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §三（file_category、同类别版本号、is_current、
 * virus_scan_status 口径）＋§18.2（file_id/hash/size、加注件元数据 4 列）；《招投标模块开发规范》§5 三-5
 * （文件本体经 FileApi 上传，本服务只登记文件对象元数据，URL 非一致性依据；上传须过病毒扫描与类型白名单）。
 * <p>设计遵循/针对：① 同证同哈希判重（UK uk_cert_file_hash）——F1 起**幂等返回已有版本**不再抛错；
 * ② 同类别版本号 = 当前最大版本 + 1，新版本行 is_current=TRUE 并关闭旧当前行，DB 唯一约束
 * uk_cert_cat_version 兜底并发，冲突映射为 CERT_FILE_VERSION_CONFLICT；③ 病毒扫描默认 PENDING，
 * CLEAN 才可预览/下载、INFECTED 自动置 is_current=false（v3.3 §三高安全底线）；④ 加注件行（GB/T 36903 §5.4）
 * 可登记 copy_* 四要素，与 GB/T 36906 §6.2 加注件下载/登记入参对应；⑤ F2 作废/更正（软删留痕）。
 */
@Service
public class CertFileVersionServiceImpl implements CertFileVersionService {


    @Resource
    private CertFileVersionMapper certFileVersionMapper;
    @Resource
    private CertificateInfoMapper certificateInfoMapper;
    /** 文件登记服务：证照文件登记时同步登记存储坐标，使其在刷新后仍可按 fileId 预览（/bidding/file/download）。 */
    @Resource
    private cn.iocoder.txgy.module.bidding.service.file.BiddingFileService biddingFileService;

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
        // F1（《后端变更通知-给前端.md》§8）：命中已存在版本时**幂等返回**已有版本主键，不再报错——
        // 前端提示"该文件已登记，无需重复上传"并刷新 file/list；判重强度不变（版本链不产生同内容两条）。
        // 原先的 CERT_FILE_HASH_DUPLICATE 错误码保留定义（契约仍引用），正常路径不再抛出。
        CertFileVersionDO existed = certFileVersionMapper.selectByCertIdAndHash(reqVO.getCertId(), reqVO.getFileHash());
        if (existed != null) {
            // 幂等命中：仍补登记存储坐标（历史数据可能首次登记时未登记），保证刷新后可见
            registerCoord(reqVO);
            return existed.getId();
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
        file.setVirusStatus(BiddingFileVirusStatusEnum.PENDING.name());
        file.setRemark(reqVO.getRemark());
        try {
            certFileVersionMapper.insert(file);
        } catch (DuplicateKeyException e) {
            // 并发同类别同版本追加被 UK 拒绝（开发规范 §5 三-2 语义类比），映射为业务错误
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_FILE_VERSION_CONFLICT);
        }
        // 登记存储坐标：证照文件本体走 /bidding/file/upload，此处把 fileId 的存储坐标登记到 bidding_file，
        // 使「刷新后仍可预览」（前端只持有 fileId，不再依赖上传瞬间的 url）
        registerCoord(reqVO);
        return file.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markScanResult(Long fileVersionId, String virusStatus) {
        CertFileVersionDO file = certFileVersionMapper.selectById(fileVersionId);
        if (file == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_FILE_NOT_EXISTS);
        }
        BiddingFileVirusStatusEnum status;
        try {
            status = BiddingFileVirusStatusEnum.valueOf(virusStatus);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_FILE_PARAM_INVALID);
        }
        file.setVirusStatus(status.name());
        if (status == BiddingFileVirusStatusEnum.INFECTED) {
            file.setIsCurrent(Boolean.FALSE); // 感染文件不得作为当前版本使用（v3.3 §三）
        }
        certFileVersionMapper.updateById(file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidVersion(Long fileVersionId, String reason) {
        CertFileVersionDO file = certFileVersionMapper.selectById(fileVersionId);
        if (file == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_FILE_NOT_EXISTS);
        }
        if (Boolean.TRUE.equals(file.getVoided())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_FILE_ALREADY_VOIDED);
        }
        // 作废=软删留痕：行保留、标作废；若为当前版本，清除 is_current（该类别失去当前版本，
        // 用户重新登记新版本——F2 闭环，不再需要"伪造一份不同内容的文件"）
        file.setVoided(Boolean.TRUE);
        file.setVoidReason(reason);
        file.setVoidedAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(file.getIsCurrent())) {
            file.setIsCurrent(Boolean.FALSE);
        }
        certFileVersionMapper.updateById(file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void correctMetadata(CertFileCorrectReqVO reqVO) {
        CertFileVersionDO file = certFileVersionMapper.selectById(reqVO.getFileVersionId());
        if (file == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_FILE_NOT_EXISTS);
        }
        // 至少一个可更正字段（remark + 加注件 copy_* 四要素）；不传的字段保持原值
        boolean anyField = reqVO.getRemark() != null || reqVO.getCopyCreationTime() != null
                || reqVO.getCopyCreator() != null || reqVO.getCopyCause() != null
                || reqVO.getCopyExpiringTime() != null;
        if (!anyField) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_FILE_CORRECT_NO_FIELD);
        }
        if (reqVO.getRemark() != null) {
            file.setRemark(reqVO.getRemark());
        }
        if (reqVO.getCopyCreationTime() != null) {
            file.setCopyCreationTime(reqVO.getCopyCreationTime());
        }
        if (reqVO.getCopyCreator() != null) {
            file.setCopyCreator(reqVO.getCopyCreator());
        }
        if (reqVO.getCopyCause() != null) {
            file.setCopyCause(reqVO.getCopyCause());
        }
        if (reqVO.getCopyExpiringTime() != null) {
            file.setCopyExpiringTime(reqVO.getCopyExpiringTime());
        }
        certFileVersionMapper.updateById(file);
    }

    /**
     * 登记文件存储坐标（fileId → configId/path），使证照文件在刷新后可经 /bidding/file/download 预览。
     *
     * <p>为什么必须做：证照文件版本出参（CertFileVersionRespVO）按契约只给 fileId/fileHash 等元数据，
     * **不给 url**（规范红线 8：url 为回显快照、不作一致性依据）。若不在业务侧登记 fileId→存储坐标，
     * 前端刷新后只拿到 fileId 就无法预览（历史缺陷「上传后刷新看不到」的根因）。
     * <p>url 来源：登记入参未带 url 时，用 fileId 反查的地址由调用方保证；此处以 fileId 为主键登记，
     * 坐标缺失不阻断登记（下载时会明确报「存储坐标解析失败」，而非静默失败）。
     */
    private void registerCoord(CertFileRegisterReqVO reqVO) {
        try {
            biddingFileService.registerStorageCoordByFileId(reqVO.getFileId(), null,
                    reqVO.getFileName(), reqVO.getFileSize(), null);
        } catch (Exception e) {
            // 登记坐标失败不应阻断证照文件登记（主流程已成功）；记录告警便于排查
            org.slf4j.LoggerFactory.getLogger(CertFileVersionServiceImpl.class)
                    .warn("[registerCoord] 存储坐标登记失败 fileId={} err={}", reqVO.getFileId(), e.getMessage());
        }
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
