# jvmprobe255

基于 Java 17 与 ASM 9.7 的字节码方法剖析 SDK。插桩后可区分方法的**包含耗时**（inclusive，进入到退出）与**自身耗时**（self，仅减去直接已插桩子调用的包含耗时），无需业务代码手写计时，无前端、无 HTTP。除手动转换 API 外，现支持**启动期 Java Agent**：应用 `java` 命令只加一个 `-javaagent` 即可采集，业务源码零改动。

## 启动期 Java Agent（推荐）

`mvn package` 产出独立 agent JAR `target/jvmprobe255-0.1.0.jar`，内含（重定位的）ASM 9.7，清单声明 `Premain-Class: jvmprobe255.agent.JvmProbeAgent`。

参数（agent JAR 后用 `=` 传入，键值对以 `;` 分隔，列表项用 `,` 或 `;`）：

- `packages`：必填，应用包前缀，可多个（点号或斜杠均可），按**包边界**匹配（`com/acme` 匹配 `com/acme/Foo` 与 `com/acme/x/Y`，不匹配 `com/acme2/Foo`）。
- `excludes`：可选，排除包前缀，规则同上；**排除优先于包含**。
- `report`：必填，退出时写入的 UTF-8 JSON 报告路径，父目录自动创建，原子替换落盘。
- JDK（`java/`、`javax/`、`jdk/`、`sun/`、`com/sun/`）、ASM（`org/objectweb/asm/` 与 JAR 内重定位的 `jvmprobe255/shaded/asm/`）以及 SDK 自身（`jvmprobe255/`）始终跳过。

```bash
java -javaagent:target/jvmprobe255-0.1.0.jar=\
'packages=com.acme;excludes=com.acme.internal,com.acme.legacy;report=build/probe.json' \
  -cp 'app-classpath' com.acme.Main
```

非法参数（缺少 `packages`/`report`、值为空、未知键、重复键等）在应用 `main` **之前**由 JVM 报错并中止启动。某个类转换失败时保留其原始字节、向 stderr 打印 `[jvmprobe255] skipped <类名>: <原因>`，其余类继续采集。JVM 正常退出时通过 shutdown hook 写报告；写报告失败向 stderr 明确报错并以非零状态退出。

报告为 UTF-8 JSON，仅含**已完成**调用（在途调用不算完成），每条含 `className`（内部名）、`methodName`、`descriptor` 及全部完成统计 `completedCount`、`exceptionCount`、`totalInclusiveNanos`、`totalSelfNanos`、`maxInclusiveNanos`，按类名/方法名/描述符排序。

**范围限制**：仅支持系统类加载器加载的普通 classpath 应用；不支持动态 attach、运行期重转换（`retransform`）与命名模块（JPMS）。Agent 与手动转换 API 共用同一套转换器与计时运行时，手动接口的返回值、异常与递归计时语义完全不变。

随仓库提供无 SDK 调用的示例 `agentdemo255.DemoMain`（`src/test/java`，含被排除的 `agentdemo255.internal` 子包），用全新 JVM 验证：

```bash
mvn -q test-compile
java -Xverify:all \
  -javaagent:target/jvmprobe255-0.1.0.jar=\
'packages=agentdemo255;excludes=agentdemo255.internal;report=build/probe.json' \
  -cp target/test-classes agentdemo255.DemoMain
cat build/probe.json   # 含 DemoMain/Worker 方法，无 SecretWorker（排除生效）
```

## 模块结构

- `Profiler`：公开门面。`transform(byte[], ClassLoader)` 只做字节码转换，不定义、不执行类；`snapshot()` / `clear()` 读取与重置统计。
- `agent/JvmProbeAgent`、`agent/ProfilingTransformer`、`agent/PackageFilter`、`agent/AgentOptions`：premain 入口、`ClassFileTransformer` 适配、包边界筛选与参数解析。
- `report/JsonReportWriter`：一致快照的确定性 UTF-8 JSON 输出（临时文件 + 原子替换，失败抛 `IOException`）。
- `transform/BytecodeTransformer`：ASM tree-API 转换器。校验类、过滤方法、注入进入/退出探针、重算栈帧与 max 值，幂等标记字段保证重复转换不加二次探针。
- `runtime/ExitGuard`：退出保护。探针自身任何异常都被吞掉，绝不改变业务返回值或异常。
- `runtime/ProbeRuntime`：每线程独立调用栈（ThreadLocal 语义的并发注册表）、递归/相互调用逐层结算、并发聚合与一致快照。
- `runtime/Frame`、`runtime/StatsCell`：在途调用栈帧与每方法计数单元。
- `MethodKey` / `MethodStats` / `Snapshot`：按“内部类名 + 方法名 + JVM 描述符”的不可变标识、统计值与快照。

## 接入方式

```java
import jvmprobe255.Profiler;
import jvmprobe255.*;

Profiler profiler = new Profiler();
// 传入原始 class 字节与用于类型解析的 ClassLoader；不会加载/初始化该类
byte[] out = profiler.transform(classBytes, applicationClassLoader);

// 由调用方自行用目标 ClassLoader#defineClass 定义并执行业务类

Snapshot snap = Profiler.snapshot();            // 不可变、一致快照
for (var e : snap.stats().entrySet()) {
    MethodKey k = e.getKey();
    MethodStats s = e.getValue();
    // k.className()/methodName()/descriptor()
    // s.completedCount()/exceptionCount()/totalInclusiveNanos()/totalSelfNanos()/maxInclusiveNanos()
}
Profiler.clear();      // 无在途调用时清空；有调用在途时返回 false 并拒绝
Profiler.inFlightCount();
```

典型挂载点：自定义 `ClassLoader.loadClass`、Java agent 的 `ClassFileTransformer` 或构建期离线插桩。SDK 只负责转换字节，不绑定任何挂载机制。测试中的 `TransformingLoader` 给出了类加载器接入的完整示例。

## 计时与结算语义

- 计时使用 `System.nanoTime()`，**包含等待时间**（`Thread.sleep`、锁等待、`await` 等都计入包含耗时）。
- 方法进入时压入当前线程栈；正常返回与异常逃逸各有唯一结算点，每次进入只结算一次。
- 自身耗时 = 本次包含耗时 − 本次执行期间**直接**已插桩子调用的包含耗时之和。孙调用的时间已包含在子调用的包含耗时中，不会重复扣减；未插桩的调用（JDK、第三方未转换类）仍计入自身耗时。
- 异常在方法内部被 `catch/finally` 捕获时走正常路径，记为正常退出；只有**逃逸出方法体**的异常才记一次异常退出，原异常对象原样向上抛。
- 每个线程维护独立调用栈，递归、相互调用逐层压栈/出栈，跨线程互不串栈；多个线程并发执行与并发读取快照不会丢计数。
- 进入/完成发布走同一并发协议的读侧，快照与清空走写侧：快照对应一个线性一致的时刻；有在途调用时清空必被拒绝（计数不动），成功清空后注册表彻底重置，不会混入任何旧计数。
- `synchronized` 方法语义不变：探针在进入监视器后执行、退出监视器前结算，等待监视器的时间计入包含耗时。
- 方法参数、所有返回类型（含 `void`）、异常对象与 `catch/finally` 语义保持原样；产物使用 `COMPUTE_FRAMES | COMPUTE_MAXS` 重算，可通过 JVM 校验。

## 处理范围与拒绝规则

- 仅处理 **class 文件主版本恰为 Java 17（61）** 的普通类（非 interface、非 `@interface`、非 `module-info`）。抽象类中的具体实例/静态方法会被处理。
- 仅插桩实例方法与静态方法；跳过构造器 `<init>`、类初始化器 `<clinit>`、`abstract`、`native` 方法。支持重载（按描述符区分）与 `synchronized`。
- 跳过 SDK 自身（`jvmprobe255/` 前缀），返回原始字节。
- 非法字节、版本不符、不支持的类形态一律抛出 `IllegalArgumentException` 拒绝，不产出半成品。
- 前 4 字节魔数不是 `0xCAFEBABE` 立即拒绝（与解析器其它检查同一入口）。
- 输出带合成标记字段；对已转换产物再次调用 `transform` 会原样返回，不重复加探针、不重复计数。
- 若一个类没有任何可插桩方法（如只有构造器），返回原始字节。

## 自测与演示

```bash
mvn test                 # JUnit 自测（递归/相互调用/异常/重载/同步/多线程/幂等/拒绝/清空）
mvn package              # 构建 target/jvmprobe255-0.1.0.jar
```

独立演示（加载转换后的示例类，覆盖递归、转义异常、内部捕获异常与多线程统计）：

```bash
mvn test-compile
CP="target/classes:target/test-classes"
CP="$CP:$(echo .m2/repository/org/ow2/asm/asm/9.7/asm-9.7.jar)"
CP="$CP:$(echo .m2/repository/org/ow2/asm/asm-commons/9.7/asm-commons-9.7.jar)"
CP="$CP:$(echo .m2/repository/org/ow2/asm/asm-tree/9.7/asm-tree-9.7.jar)"
java -cp "$CP" demo255.DemoMain
```

运行时 SDK 类必须由业务类可见的同一套 ClassLoader 加载（通常让转换类加载器的父加载器加载 `jvmprobe255`），否则插桩字节中的 `invokestatic` 无法链接到运行时。
