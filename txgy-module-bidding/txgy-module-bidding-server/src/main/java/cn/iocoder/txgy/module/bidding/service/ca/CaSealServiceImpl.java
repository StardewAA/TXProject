package cn.iocoder.txgy.module.bidding.service.ca;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealCheckRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealStatsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.SealCallbackReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.SealDocumentRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.file.vo.BiddingFileRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ca.CaSealDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.ca.CaSealMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.infra.ca.CaSdkProperties;
import cn.iocoder.txgy.module.bidding.infra.ca.LocalCryptoUtils;
import cn.iocoder.txgy.module.bidding.infra.ca.LocalSealUtils;
import cn.iocoder.txgy.module.bidding.service.file.BiddingFileService;
import cn.iocoder.txgy.module.bidding.service.processtrack.BiddingProcessTrackService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文件 CA 电子签章编排。
 *
 * <p>enabled=false（默认测试配置）：不返回假字符串，而是用统一测试账号的固定 SM2 私钥
 * 对文件 SM3 摘要做真实签名，并生成可见 PDF 红章或非 PDF 分离式签名信息文件；
 * <b>每次签章直接落库 CA 签章台账（bidding_ca_seal）</b>——测试直接入库，演示台账/验签闭环的数据基础；
 * businessCode 可解析为投标流程跟踪 id 时同步回写 bidding_process_track 签名证据链。
 * enabled=true：保留真实官方 H5/共享组件接入开关，待交易平台注册与 document 签发接口就绪。
 *
 * <p>演示增强（2026-09-28）：stats() 统计台账（演示仪表卡）；demoSeed() 一键生成带真实签章与
 * 红色公章的演示 PDF 直接入库（验签必然 PASS），让演示页打开即有华丽数据。
 */
@Service
@Slf4j
public class CaSealServiceImpl implements CaSealService {

    private static final String ORIGINAL_DIR = "bidding/seal/original";
    private static final String SEALED_DIR = "bidding/seal/result";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEMO_SEED_MAX = 20;
    /**
     * 单文件签章体积上限（**15MB**）。
     *
     * <p><b>沿革</b>：10MB（2026-09-22）→ 16MB（2026-09-23，对齐框架）→ <b>15MB（2026-09-24，实测回退）</b>。
     * 10→16 的初衷：签章限 10MB、其它上传走框架 16MB，口径不齐且招投标大文件（含扫描件）10MB 偏窄。
     *
     * <p><b>为什么又退到 15MB（2026-09-24 实测）</b>：框架 {@code max-file-size: 16MB} 先于 Controller 生效，
     * 且签章链路还要把文件经 {@code fileApi.createFile} 二次上传到 infra 文件服务。实测四档（真实 API）：
     * <ul>
     *   <li>456KB、15MB 整 → {@code code:0} 签章成功；</li>
     *   <li><b>16MB 整</b> → 框架放行、本检查也放行，但 infra 二次上传失败，
     *       返回 {@code code:500 系统异常}（日志 {@code CommonResult.checkError}）——用户拿到裸 500，无法定位；</li>
     *   <li>&gt;16MB → 框架直接 {@code code:400 上传文件过大}，请求到不了本方法。</li>
     * </ul>
     * 即"恰好 16MB"这一档两层都不拦，却在更深处失败成 500。故把业务上限<strong>下调到 15MB</strong>，
     * 留 1MB 余量：15~16MB 的文件在本方法被 {@code CA_SEAL_FILE_TOO_LARGE} 提前拦下，
     * 前端拿到可读错误码而非裸 500。
     *
     * <p>若后续要支持更大文件：须<strong>同时</strong>上调框架 {@code max-file-size}
     * <em>和</em> infra 文件服务上限，并令本值<strong>严格小于</strong>二者中的较小者。
     * 当前为本地测试环境，未上调。
     */
    private static final long MAX_SEAL_FILE_BYTES = 15L * 1024 * 1024;

    @Resource
    private BiddingFileService biddingFileService;
    @Resource
    private BiddingProcessTrackService processTrackService;
    @Resource
    private CaSdkProperties caSdkProperties;
    @Resource
    private CaSealMapper caSealMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SealDocumentRespVO sealDocument(MultipartFile file, String businessCode, String callbackUrl) {
        return doSealOne(file, businessCode, callbackUrl);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SealDocumentRespVO> sealDocumentBatch(List<MultipartFile> files, String businessCode, String callbackUrl) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("批量签章文件不能为空");
        }
        // 批量盖章：每份文件逐页盖红章（LocalSealUtils 每页盖章）+ 各自落库台账；批内整体一个事务（全成或全滚）
        List<SealDocumentRespVO> results = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            results.add(doSealOne(file, businessCode, callbackUrl));
        }
        log.info("[本地CA] 批量文件签章完成 count={}", results.size());
        return results;
    }

    /** 单个文件签章核心（sealDocument 与 sealDocumentBatch 共用）。 */
    private SealDocumentRespVO doSealOne(MultipartFile file, String businessCode, String callbackUrl) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("签章文件不能为空");
        }
        // 契约 A7：入口显式校验体积。Spring 的 max-file-size=16MB 超限时抛的是
        // MaxUploadSizeExceededException，前端拿到的是"长时间无响应/框架级 500"，
        // 无法定位原因；这里提前拦下并给可读错误码，把口径写死在这里（前端可据此预校验）。
        if (file.getSize() > MAX_SEAL_FILE_BYTES) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CA_SEAL_FILE_TOO_LARGE,
                    MAX_SEAL_FILE_BYTES / 1024 / 1024);
        }
        // 走哪条签章路线由 sealEnabled 决定（与 enabled 拆分；此前共用 enabled，导致对接官方组件后本地签章被连带打死）
        if (caSdkProperties.isSealEnabled()) {
            throw new IllegalStateException("bidding.ca.sdk.seal-enabled=true 时需接入官方 H5 document 签发接口；测试签章请保持 false");
        }
        try {
            byte[] content = file.getBytes();
            String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "file");
            String contentType = StrUtil.blankToDefault(file.getContentType(), "application/octet-stream");
            // 原文件先入 FileApi，保证签章前后均有 file_id/networkPath 可追溯
            BiddingFileRespVO original = biddingFileService.upload(file, ORIGINAL_DIR);
            Long trackId = parseLong(businessCode);
            // 真实签名 + 红章 + 直接落库台账
            CaSealDO record = signAndPersist(content, fileName, contentType, original, trackId);

            SealDocumentRespVO result = new SealDocumentRespVO();
            result.setDocumentId(record.getDocumentId());
            result.setTradingSystemCode(StrUtil.blankToDefault(caSdkProperties.getTradingSystemCode(), "axj11111111"));
            result.setNetworkPath(original.getUrl());
            result.setCallbackUrl(callbackUrl);
            result.setSignature(record.getSignature());
            result.setCertSn(record.getCertSn());
            result.setHash(record.getSm3Hash());
            result.setSha256(record.getFileSha256());
            result.setOrgName(record.getOrgName());
            result.setSealTime(record.getSealTime());
            result.setSealFileId(record.getSealFileId());
            result.setSealFileUrl(record.getSealFileUrl());
            result.setSealFileName(record.getSealFileName());
            result.setSealFileSize(record.getSealFileSize());
            result.setTrackId(record.getTrackId());
            log.info("[本地CA] 文件签章完成并落台账 file={} sm3={} cert={} sealFileId={} trackId={} sealRecordId={}",
                    fileName, record.getSm3Hash(), record.getCertSn(), record.getSealFileId(), record.getTrackId(), record.getId());
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("文件 CA 签章失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveCallback(SealCallbackReqVO reqVO) {
        if (caSdkProperties.isSealEnabled()) {
            throw new IllegalStateException("真实 H5 回调暂需官方 document 签发配置（bidding.ca.sdk.seal-enabled=true）");
        }
        if (!LocalCryptoUtils.isSm3Hex(reqVO.getHash())
                || !LocalCryptoUtils.sm2VerifyDigestHex(reqVO.getHash(), reqVO.getSignature())) {
            throw new ServiceException(400, "签章回调验签失败：签名值与文件 SM3 指纹不匹配");
        }
        Long trackId = parseLong(reqVO.getBusinessCode());
        if (trackId != null) {
            // 契约 A4：把签章产物坐标一并写回流程行（回调场景 sealFileId 由 reqVO 给出）
            processTrackService.saveSealResult(trackId, reqVO.getSignature(), reqVO.getCertSn(),
                    reqVO.getHash(), StrUtil.blankToDefault(reqVO.getCaPublicKey(),
                            LocalCryptoUtils.localTestPublicKeyPem()),
                    reqVO.getSealFileId(), null, null);
        }
        // 回调确认：按 documentId 回写签章台账（验签通过 → PASS，证据链补全签名值/证书号/指纹）
        if (StrUtil.isNotBlank(reqVO.getDocumentId())) {
            CaSealDO sealRecord = caSealMapper.selectByDocumentId(reqVO.getDocumentId());
            if (sealRecord != null) {
                sealRecord.setSignature(StrUtil.blankToDefault(reqVO.getSignature(), sealRecord.getSignature()));
                sealRecord.setCertSn(StrUtil.blankToDefault(reqVO.getCertSn(), sealRecord.getCertSn()));
                sealRecord.setSm3Hash(StrUtil.blankToDefault(reqVO.getHash(), sealRecord.getSm3Hash()));
                if (reqVO.getSealFileId() != null) {
                    sealRecord.setSealFileId(reqVO.getSealFileId());
                }
                sealRecord.setVerifyStatus("PASS");
                sealRecord.setVerifyTime(LocalDateTime.now());
                caSealMapper.updateById(sealRecord);
            }
        }
        log.info("[本地CA] 签章回调已处理 documentId={} businessCode={} sealFileId={}",
                reqVO.getDocumentId(), reqVO.getBusinessCode(), reqVO.getSealFileId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CaSealRespVO verifySeal(Long id) {
        CaSealDO sealRecord = caSealMapper.selectById(id);
        if (sealRecord == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CA_SEAL_NOT_EXISTS, id);
        }
        // 真实验签（GB/T 32918 SM2）：用统一测试账号公钥对「文件 SM3 指纹 + SM2 签名值」验签
        boolean pass = LocalCryptoUtils.isSm3Hex(sealRecord.getSm3Hash())
                && LocalCryptoUtils.sm2VerifyDigestHex(sealRecord.getSm3Hash(), sealRecord.getSignature());
        sealRecord.setVerifyStatus(pass ? "PASS" : "FAIL");
        sealRecord.setVerifyTime(LocalDateTime.now());
        caSealMapper.updateById(sealRecord);
        log.info("[本地CA] 台账验签 id={} document={} result={}", id, sealRecord.getDocumentName(), pass ? "PASS" : "FAIL");
        return BeanUtils.toBean(sealRecord, CaSealRespVO.class);
    }

    @Override
    public CaSealCheckRespVO checkSealAuth(Long fileId, String sha256) {
        // 匹配优先级（契约 A5，逐级兜底；命中即止）：
        //   ① 原文件 fileId —— 常态：拿原文件再来检测
        //   ② 原文件 sha256 —— 重传后 fileId 变了，但内容一致
        //   ③ 已签章产物 fileId —— 用户把"签章后的文件"当原文件再传一次来检测
        // 缺 ③ 会让已签章文件被判"未认证"，前端反复提示并诱导重复签章。
        CaSealDO record = fileId != null ? caSealMapper.selectLatestByFileId(fileId) : null;
        if (record == null && StrUtil.isNotBlank(sha256)) {
            record = caSealMapper.selectLatestBySha256(sha256);
        }
        if (record == null && fileId != null) {
            record = caSealMapper.selectLatestBySealFileId(fileId);
        }
        CaSealCheckRespVO resp = new CaSealCheckRespVO();
        resp.setCaAuthed(record != null);
        if (record != null) {
            resp.setSealId(record.getId());
            resp.setDocumentId(record.getDocumentId());
            resp.setDocumentName(record.getDocumentName());
            resp.setOrgName(record.getOrgName());
            resp.setSealTime(record.getSealTime());
            resp.setVerifyStatus(record.getVerifyStatus());
            resp.setSealFileId(record.getSealFileId());
            resp.setSealFileUrl(record.getSealFileUrl());
            resp.setSealFileSize(record.getSealFileSize());
        }
        log.info("[本地CA] 上传文件 CA 认证检测 fileId={} sha256={} caAuthed={} sealId={}",
                fileId, StrUtil.sub(sha256, 0, 12), resp.getCaAuthed(), resp.getSealId());
        return resp;
    }

    @Override
    public CaSealStatsRespVO stats() {
        CaSealStatsRespVO resp = new CaSealStatsRespVO();
        resp.setTotal(caSealMapper.selectCount());
        resp.setPass(caSealMapper.selectCountByVerifyStatus("PASS"));
        resp.setFail(caSealMapper.selectCountByVerifyStatus("FAIL"));
        resp.setUnverified(caSealMapper.selectCountByVerifyStatus("UNVERIFIED"));
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CaSealRespVO> demoSeed(Integer count) {
        int n = (count == null || count < 1) ? 5 : Math.min(count, DEMO_SEED_MAX);
        List<CaSealRespVO> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            byte[] content = buildDemoPdf(i);
            String fileName = "演示签章文件-" + i + ".pdf";
            BiddingFileRespVO original = biddingFileService.registerBytes(content, fileName, ORIGINAL_DIR, "application/pdf");
            CaSealDO record = signAndPersist(content, fileName, "application/pdf", original, null);
            // 演示种子：生成即真实验签通过（签名自洽，verify 必 PASS）
            boolean pass = LocalCryptoUtils.isSm3Hex(record.getSm3Hash())
                    && LocalCryptoUtils.sm2VerifyDigestHex(record.getSm3Hash(), record.getSignature());
            record.setVerifyStatus(pass ? "PASS" : "FAIL");
            record.setVerifyTime(LocalDateTime.now());
            caSealMapper.updateById(record);
            list.add(BeanUtils.toBean(record, CaSealRespVO.class));
        }
        log.info("[本地CA] 演示签章数据生成完成 count={}", n);
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CaSealRespVO> demoTamper(Integer count) {
        int n = (count == null || count < 1) ? 2 : Math.min(count, DEMO_SEED_MAX);
        List<CaSealRespVO> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            byte[] content = buildDemoPdf(i);
            String fileName = "演示篡改文件-" + i + ".pdf";
            BiddingFileRespVO original = biddingFileService.registerBytes(content, fileName, ORIGINAL_DIR, "application/pdf");
            // 真实自洽链路生成记录（内容 A 的指纹 + 真实签名）
            CaSealDO record = signAndPersist(content, fileName, "application/pdf", original, null);
            // 模拟文件被篡改：把台账指纹换成「内容 B（被替换后）」的指纹 → 签名与指纹不匹配 → 验签必 FAIL
            String tamperedSm3 = LocalCryptoUtils.sm3Hex(
                    ("tampered-content-" + i + "-" + content.length).getBytes(StandardCharsets.UTF_8));
            record.setSm3Hash(tamperedSm3);
            boolean pass = LocalCryptoUtils.isSm3Hex(record.getSm3Hash())
                    && LocalCryptoUtils.sm2VerifyDigestHex(record.getSm3Hash(), record.getSignature());
            record.setVerifyStatus(pass ? "PASS" : "FAIL");
            record.setVerifyTime(LocalDateTime.now());
            caSealMapper.updateById(record);
            list.add(BeanUtils.toBean(record, CaSealRespVO.class));
        }
        log.info("[本地CA] 演示「被篡改」签章数据生成完成 count={}（验签必 FAIL，展示检出能力）", n);
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long demoClear() {
        int c1 = caSealMapper.deleteByDemoPrefix("演示签章文件-");
        int c2 = caSealMapper.deleteByDemoPrefix("演示篡改文件-");
        log.info("[本地CA] 演示签章数据已清理，删除条数={}", c1 + c2);
        return (long) (c1 + c2);
    }

    /** 真实签名 + 红章 + 落库台账（sealDocument 与 demoSeed 共用）。 */
    private CaSealDO signAndPersist(byte[] content, String fileName, String contentType,
                                    BiddingFileRespVO original, Long trackId) {
        String sm3 = LocalCryptoUtils.sm3Hex(content);
        String sha256 = StrUtil.blankToDefault(original.getSha256(), DigestUtil.sha256Hex(content));
        String signature = LocalCryptoUtils.sm2SignDigestHex(sm3);
        String certSn = LocalCryptoUtils.LOCAL_TEST_CERT_SN;
        String orgName = LocalCryptoUtils.LOCAL_TEST_ORG_NAME;
        String signTime = LocalDateTime.now().format(TIME_FORMAT);
        boolean pdf = LocalSealUtils.isPdf(content);

        byte[] sealedContent;
        String sealedName;
        String sealedType;
        if (pdf) {
            sealedContent = LocalSealUtils.sealPdf(content, orgName, certSn, signTime);
            sealedName = appendSuffix(fileName, "-已签章");
            sealedType = "application/pdf";
        } else {
            String info = LocalSealUtils.buildSignatureInfo(fileName, sm3, sha256, signature,
                    certSn, orgName, signTime);
            sealedContent = info.getBytes(StandardCharsets.UTF_8);
            sealedName = fileName + ".sig.txt";
            sealedType = "text/plain";
        }
        BiddingFileRespVO sealed = biddingFileService.registerBytes(sealedContent, sealedName, SEALED_DIR, sealedType);

        // 契约 A4：把签章产物坐标（fileId/文件名/字节数）写回流程行，
        // 使流转记录能自证"已签章"，并让 /ca/seal/check 按 fileId 匹配得到。
        if (trackId != null) {
            processTrackService.saveSealResult(trackId, signature, certSn, sm3,
                    LocalCryptoUtils.localTestPublicKeyPem(),
                    sealed.getFileId(), sealed.getName(), (long) sealedContent.length);
        }

        String documentId = "SEAL-DOC-" + UUID.randomUUID().toString().replace("-", "");
        CaSealDO record = new CaSealDO();
        record.setDocumentId(documentId);
        record.setDocumentName(fileName);
        record.setDocumentType(pdf ? "pdf" : "other");
        record.setFileId(original.getFileId());
        record.setFileUrl(original.getUrl());
        record.setFileSha256(sha256);
        record.setSm3Hash(sm3);
        record.setSignature(signature);
        record.setCertSn(certSn);
        record.setOrgName(orgName);
        record.setSealTime(signTime);
        record.setSealFileId(sealed.getFileId());
        record.setSealFileUrl(sealed.getUrl());
        record.setSealFileName(sealed.getName());
        // 契约 A1：签章产物体积。取服务端实际写出的字节数（不是原文件的 size——
        // 盖章后的 PDF 会比原文大，用原文件 size 会让前端填错 documentSize）。
        record.setSealFileSize((long) sealedContent.length);
        record.setTrackId(trackId);
        record.setVerifyStatus("UNVERIFIED");
        record.setOperatorUserId(SecurityFrameworkUtils.getLoginUserId());
        caSealMapper.insert(record);
        return record;
    }

    /** 生成演示 PDF（PDFBox，ASCII 文本避免字体依赖；3 页展示每页盖章效果；真实 SM3/SM2 链路保证验签自洽）。 */
    private byte[] buildDemoPdf(int idx) {
        try (PDDocument doc = new PDDocument()) {
            for (int p = 1; p <= 3; p++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 18);
                    cs.newLineAtOffset(60, 780);
                    cs.showText("CA Seal Demo Document No." + idx + "  (Page " + p + "/3)");
                    cs.newLineAtOffset(0, -44);
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.showText("This is a demo document generated for CA e-seal presentation.");
                    cs.newLineAtOffset(0, -24);
                    cs.showText("Signed with real SM2 signature and a red seal stamped on every page.");
                    cs.newLineAtOffset(0, -24);
                    // 必须纯 ASCII：PDType1Font.HELVETICA 是 WinAnsiEncoding 内置字体，不含中文字形，
                    // 传中文会抛 IllegalArgumentException: U+62DB ('.notdef') is not available in the font Helvetica
                    // （2026-09-21 实测：demo-seed/demo-tamper 因此 500）。中文需嵌入 TTF 字体，演示 PDF 不值得引依赖。
                    cs.showText("Page " + p + " content: demo chapter, sealed with a red e-seal on every page.");
                    cs.endText();
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成演示 PDF 失败", e);
        }
    }

    @Override
    public CaSealDO get(Long id) {
        CaSealDO sealRecord = caSealMapper.selectById(id);
        if (sealRecord == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CA_SEAL_NOT_EXISTS, id);
        }
        return sealRecord;
    }

    @Override
    public PageResult<CaSealDO> page(CaSealPageReqVO reqVO) {
        return caSealMapper.selectPage(reqVO);
    }

    private static Long parseLong(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String appendSuffix(String fileName, String suffix) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName + suffix + ".pdf";
        }
        return fileName.substring(0, dot) + suffix + fileName.substring(dot);
    }
}
