package cn.iocoder.txgy.module.bidding.service.attachment;
// 规范依据：《招投标模块开发规范》§5 三-5（文件经 FileApi，库内只记 file_id+sha256+size，URL 非一致性依据）
//   + 红线 8（附件三件套）+ api/vo/AttachmentSetVO（附件集结构）+ 用户裁定（一对 N 附件表/URL/对象存储）

import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo.AttachmentRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo.AttachmentSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.attachment.AttachmentDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.attachment.AttachmentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class AttachmentServiceImpl implements AttachmentService {

    @Resource
    private AttachmentMapper attachmentMapper;

    @Override
    public AttachmentRespVO save(AttachmentSaveReqVO reqVO) {
        // 幂等：同一 (bizType, bizCode, fileId) 已绑定则返回原行（重复提交不重复建行）
        AttachmentDO exists = attachmentMapper.selectOne(new LambdaQueryWrapper<AttachmentDO>()
                .eq(AttachmentDO::getBizType, reqVO.getBizType())
                .eq(AttachmentDO::getBizCode, reqVO.getBizCode())
                .eq(AttachmentDO::getFileId, reqVO.getFileId())
                .last("LIMIT 1"));
        if (exists != null) {
            return toResp(exists);
        }
        AttachmentDO entity = BeanUtils.toBean(reqVO, AttachmentDO.class);
        if (entity.getVirusStatus() == null) {
            entity.setVirusStatus("UNKNOWN"); // 默认：扫描结果回填前不下发下载 URL
        }
        // 序号：同实体最大 seq + 1（首条为 0）
        Long maxSeq = attachmentMapper.selectCount(new LambdaQueryWrapper<AttachmentDO>()
                .eq(AttachmentDO::getBizType, reqVO.getBizType())
                .eq(AttachmentDO::getBizCode, reqVO.getBizCode()));
        entity.setSeq(maxSeq == null ? 0 : maxSeq.intValue());
        attachmentMapper.insert(entity);
        log.info("[AttachmentServiceImpl][save] 绑定附件成功 bizType={} bizCode={} fileId={}",
                reqVO.getBizType(), reqVO.getBizCode(), reqVO.getFileId());
        return toResp(entity);
    }

    @Override
    public void remove(Long id) {
        AttachmentDO entity = attachmentMapper.selectById(id);
        if (entity == null) {
            // 幂等语义：解绑一个不存在的附件 = 已解绑（重复删除/并发删除不报错）
            log.info("[AttachmentServiceImpl][remove] 解绑幂等跳过 id={}（附件行不存在，视为已解绑）", id);
            return;
        }
        attachmentMapper.deleteById(id);
        log.info("[AttachmentServiceImpl][remove] 解绑附件成功 id={} bizType={} bizCode={} fileId={}",
                id, entity.getBizType(), entity.getBizCode(), entity.getFileId());
    }

    @Override
    public List<AttachmentRespVO> list(String bizType, String bizCode) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizCode)) {
            return List.of();
        }
        List<AttachmentDO> rows = attachmentMapper.selectList(new LambdaQueryWrapper<AttachmentDO>()
                .eq(AttachmentDO::getBizType, bizType)
                .eq(AttachmentDO::getBizCode, bizCode)
                .orderByAsc(AttachmentDO::getSeq));
        return rows.stream().map(this::toResp).toList();
    }

    private AttachmentRespVO toResp(AttachmentDO entity) {
        // createTime 由 BeanUtils 自动映射（DO/RespVO 均为 LocalDateTime，序列化由平台统一输出 epoch 毫秒）
        return BeanUtils.toBean(entity, AttachmentRespVO.class);
    }

}