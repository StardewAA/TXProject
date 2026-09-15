package cn.iocoder.txgy.tool.caverify.api;

import java.io.File;

/**
 * 电子文件 CA 真实性校验服务（统一对外接口）。
 *
 * <p>后续真实验签实现（按 01-硬性条件.md）只替换实现类，本接口与 {@link CaVerifyResult}
 * 结果结构保持不变，调用方（CLI / bidding-server 集成 / 其他模块）无需改动。</p>
 *
 * <p>当前 v0.1 占位实现：{@code cn.iocoder.txgy.tool.caverify.impl.StubCaVerifyServiceImpl}，
 * 对任意存在的文件直接返回 success=true（未执行真实密码学验签）。</p>
 */
public interface CaVerifyService {

    /**
     * 校验指定电子文件（真实性与完整性，含 CA 证书链、吊销状态、时间戳等——真实实现按
     * 01-硬性条件.md 第 3 节逐项落地）。
     *
     * @param file 待校验文件，不可为 null
     * @return 校验结果，不为 null
     */
    CaVerifyResult verify(File file);

    /**
     * 校验指定路径的电子文件。
     *
     * @param filePath 待校验文件路径；为 null 时返回参数错误结果
     * @return 校验结果，不为 null
     */
    CaVerifyResult verify(String filePath);

}
