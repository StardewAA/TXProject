# 电子文件 CA 真实性验证工具

> 依据《电子招标投标办法》（20 号令）及附件《电子招标投标系统技术规范 第 1 部分：交易平台技术规范（2013）》《电子签名法》等规划的"电子文件 CA 真实性验证工具"。

## 当前形态（v0.1 · 接口占位）

Java 验证库 + CLI 演示。**已预留统一验证接口，当前校验直接返回成功**（占位实现，未执行真实密码学验签）。
真实实现按 `01-硬性条件.md` 排期落地时，只需替换实现类，`CaVerifyService` 接口与 `CaVerifyResult` 结果结构保持不变。

- 对外接口：`CaVerifyService#verify(File)` / `#verify(String)` → `CaVerifyResult`
- 占位实现：`StubCaVerifyServiceImpl`（文件存在即返回 success；文件不存在/参数错误返回失败结果）
- CLI 演示：`CaVerifyCli <待校验文件路径>`（退出码 0=通过 / 1=未通过 / 2=用法错误）

## 构建与运行

```bash
# 需要 JDK 1.8+ 与 Maven 3.x
mvn -q -DskipTests package

# 直接编译运行（无依赖，也可不装 Maven）：
javac -encoding UTF-8 -d target/classes \
  src/main/java/cn/iocoder/txgy/tool/caverify/api/*.java \
  src/main/java/cn/iocoder/txgy/tool/caverify/impl/*.java \
  src/main/java/cn/iocoder/txgy/tool/caverify/cli/*.java
java -cp target/classes cn.iocoder.txgy.tool.caverify.CaVerifyCli "某电子文件.pdf"
```

输出示例：

```json
{"success":true,"code":"SUCCESS","message":"真实性校验通过","filePath":"D:\\...\\某电子文件.pdf","verifiedAt":"2026-02-14T16:30:00"}
```

## 代码结构

```
src/main/java/cn/iocoder/txgy/tool/caverify/
├── api/CaVerifyService.java          # 对外接口（后续接入点，勿改签名）
├── api/CaVerifyResult.java           # 校验结果（success/code/message/filePath/verifiedAt）
├── impl/StubCaVerifyServiceImpl.java # 占位实现：直接返回成功
└── cli/CaVerifyCli.java              # 命令行演示入口
```

## 文档

| 文件 | 说明 |
|---|---|
| `01-硬性条件.md` | 硬性条件清单（真实验签实现时的依据） |

## 决策记录

1. 形态：Java 验证库（可内嵌复用）+ CLI 演示入口；
2. 算法：国密 SM2/SM3 为主、兼容平台实际 CA（RSA/SHA-256）双轨（实现期生效）；
3. 现状（v0.1）：只留接口，检验直接返回成功；真实验签（证书链/吊销/时间戳/完整性，见 01 文档）待排期。
