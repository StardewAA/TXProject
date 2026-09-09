package cn.iocoder.txgy.tool.caverify.cli;

import cn.iocoder.txgy.tool.caverify.api.CaVerifyResult;
import cn.iocoder.txgy.tool.caverify.api.CaVerifyService;
import cn.iocoder.txgy.tool.caverify.impl.StubCaVerifyServiceImpl;

/**
 * 命令行演示入口。
 *
 * <p>用法：</p>
 * <pre>
 *   java -cp target/classes cn.iocoder.txgy.tool.caverify.CaVerifyCli &lt;待校验文件路径&gt;
 * </pre>
 *
 * <p>退出码：0 = 校验通过；1 = 校验未通过/无法判定；2 = 用法错误。</p>
 */
public final class CaVerifyCli {

    private CaVerifyCli() {
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("用法: CaVerifyCli <待校验文件路径>");
            System.exit(2);
            return;
        }
        CaVerifyService service = new StubCaVerifyServiceImpl();
        CaVerifyResult result = service.verify(args[0]);
        System.out.println(result.toJson());
        System.exit(result.isSuccess() ? 0 : 1);
    }

}
