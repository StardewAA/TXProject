package cn.iocoder.txgy.tool.caverify.impl;

import cn.iocoder.txgy.tool.caverify.api.CaVerifyResult;
import cn.iocoder.txgy.tool.caverify.api.CaVerifyService;

import java.io.File;

/**
 * 占位实现（v0.1）：不执行真实密码学验签，对任意存在的文件直接返回成功。
 *
 * <p>真实实现（证书链/吊销/时间戳/完整性等）按 01-硬性条件.md 排期落地后，替换本类即可，
 * 对外接口 {@link CaVerifyService} 不变。</p>
 */
public class StubCaVerifyServiceImpl implements CaVerifyService {

    @Override
    public CaVerifyResult verify(File file) {
        if (file == null) {
            return CaVerifyResult.failure(CaVerifyResult.CODE_PARAM_ERROR,
                    "参数为空：未指定待校验文件", null);
        }
        if (!file.exists() || !file.isFile()) {
            return CaVerifyResult.failure(CaVerifyResult.CODE_FILE_NOT_FOUND,
                    "文件不存在或不是普通文件：" + file.getAbsolutePath(), file.getAbsolutePath());
        }
        // 占位：直接返回成功。待真实验签实现替换此处逻辑。
        return CaVerifyResult.success(file.getAbsolutePath());
    }

    @Override
    public CaVerifyResult verify(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return CaVerifyResult.failure(CaVerifyResult.CODE_PARAM_ERROR,
                    "参数为空：未指定待校验文件路径", null);
        }
        return verify(new File(filePath));
    }

}
