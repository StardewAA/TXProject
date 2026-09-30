package cn.iocoder.txgy.module.bidding.service.ca;

import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaShareSealCallbackReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaShareSealClientCallbackReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ca.CaSealDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.ca.CaSealMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 官方 CA 互认签章回调处理实现。
 *
 * <p>设计遵循：① 官方字段**逐字读取**（结构不符即对接失败，应显式暴露而非静默兜底）；
 * ② 以「待签文件 Hash」匹配本模块台账——官方 {@code awaitingSignatureFileHash} 对应
 * {@link CaSealDO#getFileSha256()}（签章前登记的原文件指纹）；
 * ③ 幂等：同一签后 Hash 重复回调不重复回写（官方可能重试）；
 * ④ 匹配不到只记日志不抛错（官方回调可能属于其他交易系统的文件；抛错会导致对方反复重试）。
 *
 * <p><b>落库字段映射</b>（官方字段 → 本模块台账列）：
 * <table border="1">
 *   <tr><th>官方字段</th><th>台账列</th><th>说明</th></tr>
 *   <tr><td>awaitingSignatureFileHash</td><td>file_sha256</td><td>匹配键（签章前原文件指纹）</td></tr>
 *   <tr><td>signatureFileName</td><td>seal_file_name</td><td>签后文件名 / 对象存储 FileKey</td></tr>
 *   <tr><td>fileSignatureStatus</td><td>verify_status</td><td>已签章已验章→PASS；其余→UNVERIFIED</td></tr>
 *   <tr><td>completedSignatureTime</td><td>seal_time</td><td>完成签章时间</td></tr>
 *   <tr><td>fileCurrentSealCount</td><td>remark</td><td>本模块无"签章数量"列，记入备注留痕</td></tr>
 * </table>
 */
@Slf4j
@Service
public class CaShareCallbackServiceImpl implements CaShareCallbackService {

    /** 官方 completedSignatureTime 格式（yyyy-MM-dd HH:mm:ss）。 */
    private static final DateTimeFormatter OFFICIAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private CaSealMapper caSealMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleH5Callback(CaShareSealCallbackReqVO reqVO) {
        if (reqVO == null || reqVO.getData() == null) {
            log.warn("[CaShare][H5] 回调报文为空或缺少 data，忽略");
            return;
        }
        CaShareSealCallbackReqVO.CallbackData data = reqVO.getData();
        // 官方 status：10 进行中 / 20 已完成 / 30 失败——仅"已完成"才落库
        if (!"20".equals(data.getStatus())) {
            log.info("[CaShare][H5] 非完成状态，忽略 action={} status={}", reqVO.getAction(), data.getStatus());
            return;
        }
        CaShareSealCallbackReqVO.CallbackData.SignatureInfo info = data.getSignatureInfo();
        if (info == null || info.getDocument() == null || info.getDocument().isEmpty()) {
            log.warn("[CaShare][H5] status=20 但缺少 signatureInfo/document，忽略 transactionId={}", data.getTransactionId());
            return;
        }
        for (CaShareSealCallbackReqVO.CallbackData.SignatureInfo.DocumentItem doc : info.getDocument()) {
            applySeal(doc.getAwaitingSignatureFileHash(), doc.getSignatureFileName(),
                    doc.getFileSignatureStatus(), info.getCompletedSignatureTime(),
                    doc.getFileCurrentSealCount(), "官方H5组件回调 transactionId=" + data.getTransactionId());
        }
        log.info("[CaShare][H5] 回调处理完成 action={} transactionId={} 文档数={}",
                reqVO.getAction(), data.getTransactionId(), info.getDocument().size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleClientCallback(CaShareSealClientCallbackReqVO reqVO) {
        if (reqVO == null || reqVO.getDocument() == null || reqVO.getDocument().isEmpty()) {
            log.warn("[CaShare][Client] 回调报文为空或缺少 document，忽略");
            return;
        }
        // 官方 callbackRequestType：exitSignature 退出程序 / signedAndVerified 完成签章与验。
        // 退出程序也带 document，但语义是"用户关程序"，不应视为完成签章。
        boolean signed = "signedAndVerified".equals(reqVO.getCallbackRequestType());
        for (CaShareSealClientCallbackReqVO.DocumentItem doc : reqVO.getDocument()) {
            if (!signed) {
                log.info("[CaShare][Client] 非完成签章场景（{}），仅留痕不落库：file={}",
                        reqVO.getCallbackRequestType(), doc.getAwaitingSignatureFileName());
                continue;
            }
            applySeal(doc.getAwaitingSignatureFileHash(), doc.getSignatureFileName(),
                    doc.getFileSignatureStatus(), reqVO.getCompletedSignatureTime(),
                    doc.getFileCurrentSealCount(), "官方客户端回调 ip=" + reqVO.getIpAddress()
                            + " mac=" + reqVO.getMacAddress() + " cpu=" + reqVO.getCpuSn());
        }
        log.info("[CaShare][Client] 回调处理完成 type={} 文档数={}",
                reqVO.getCallbackRequestType(), reqVO.getDocument().size());
    }

    /**
     * 按「待签文件 Hash」匹配台账并回写签章结果（字段映射见类注释表格）。
     *
     * <p>匹配不到：只记日志（官方回调可能来自其他交易系统或非本模块登记的文件），不抛异常——
     * 否则官方会收到失败响应并重试，形成无效风暴。
     */
    private void applySeal(String awaitingHash, String signedFileName, String fileSignatureStatus,
                           String completedSignatureTime, Integer currentSealCount, String remark) {
        if (!StringUtils.hasText(awaitingHash)) {
            log.warn("[CaShare] 回调缺少待签文件 Hash，无法匹配台账，跳过");
            return;
        }
        // 跨租户找候选（本方法在 @TenantIgnore 下执行）：相同内容文件在不同租户下哈希相同，
        // 故候选可能来自多个租户——**必须逐个以「其所属租户」执行回写**，不能裸更新（会误改他租户台账）。
        List<CaSealDO> matched = caSealMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CaSealDO>()
                .eq(CaSealDO::getFileSha256, awaitingHash));
        if (matched == null || matched.isEmpty()) {
            log.warn("[CaShare] 待签文件 Hash 未匹配到台账（可能非本系统文件）hash={}", awaitingHash);
            return;
        }
        // 按租户分组：同一租户内才允许批量回写
        java.util.Map<Long, List<CaSealDO>> byTenant = matched.stream()
                .filter(s -> s.getTenantId() != null)
                .collect(java.util.stream.Collectors.groupingBy(CaSealDO::getTenantId));
        for (java.util.Map.Entry<Long, List<CaSealDO>> entry : byTenant.entrySet()) {
            cn.iocoder.txgy.framework.tenant.core.util.TenantUtils.execute(entry.getKey(),
                    () -> applySealInTenant(entry.getValue(), signedFileName, fileSignatureStatus,
                            completedSignatureTime, currentSealCount, remark));
        }
    }

    /** 在指定租户上下文内回写（调用方已按租户分组，故此处查询/更新天然被租户拦截器限定在该租户内）。 */
    private void applySealInTenant(List<CaSealDO> matched, String signedFileName, String fileSignatureStatus,
                                   String completedSignatureTime, Integer currentSealCount, String remark) {
        for (CaSealDO seal : matched) {
            // 幂等：已回写过同一签后文件名则跳过（官方可能重试同一回调）
            if (StringUtils.hasText(signedFileName) && signedFileName.equals(seal.getSealFileName())) {
                log.info("[CaShare] 重复回调，跳过 id={} signedFileName={}", seal.getId(), signedFileName);
                continue;
            }
            CaSealDO update = new CaSealDO();
            update.setId(seal.getId());
            update.setSealFileName(signedFileName);
            // 官方 fileSignatureStatus「已签章已验章」→ 台账验签通过；其余保持未验（不臆断为通过）
            update.setVerifyStatus("已签章已验章".equals(fileSignatureStatus) ? "PASS" : "UNVERIFIED");
            update.setVerifyTime(LocalDateTime.now());
            update.setSealTime(parseOfficialTime(completedSignatureTime, seal.getSealTime()));
            // 本模块无"签章数量"列：将官方字段与来源一并记入备注留痕（不丢弃官方信息）
            update.setRemark(buildRemark(seal.getRemark(), remark, currentSealCount));
            caSealMapper.updateById(update);
            log.info("[CaShare] 已回写签章结果 id={} verifyStatus={} sealCount={}",
                    seal.getId(), update.getVerifyStatus(), currentSealCount);
        }
    }

    /** 解析官方时间（yyyy-MM-dd HH:mm:ss）；解析失败保留原值（不因时间格式让回调失败）。 */
    private String parseOfficialTime(String officialTime, String fallback) {
        if (!StringUtils.hasText(officialTime)) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(officialTime, OFFICIAL_TIME)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("[CaShare] 官方完成签章时间格式异常，原样保留 value={}", officialTime);
            return officialTime;
        }
    }

    /** 备注拼装：保留既有备注并追加官方回调留痕（来源 + 本次签章数）。 */
    private String buildRemark(String oldRemark, String source, Integer currentSealCount) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(oldRemark)) {
            sb.append(oldRemark).append("；");
        }
        sb.append(source);
        if (currentSealCount != null) {
            sb.append("；本次签章数=").append(currentSealCount);
        }
        return sb.length() > 500 ? sb.substring(0, 500) : sb.toString();
    }
}
