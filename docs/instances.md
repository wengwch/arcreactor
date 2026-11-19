# 创建实例

`POST /api/v1/instances`，请求头使用 `Content-Type: application/json`。

```json
{
  "name": "gpu-instance-01",
  "description": "实例创建接口测试",
  "flavorId": "demo-flavor-t4",
  "imageId": "demo-image-ubuntu",
  "hypervisorType": "demo-hv-t4",
  "hostname": "demo-gpu-01",
  "regionId": "demo-region",
  "userId": "demo-user"
}
```

除 `description`、`hostname` 外其余字段必填，各字段最多 255 个字符。`flavorId`、`imageId`、
`hypervisorType` 是本地数据库中的 ID。区域、镜像、规格和宿主机类型必须启用，
镜像和规格必须属于所选区域，规格的宿主机类型必须与请求一致。
示例中的 ID 需要替换为已配置并启用的实际资源。

OpenStack 资源关联 ID 统一使用 `os_` 数据库列名，Java 属性和 JSON 字段使用 `os...` 驼峰名，
例如 `os_project_id` / `osProjectId`、`os_parent_port_id` / `osParentPortId`。
此约定覆盖项目、网络、子网、路由、端口、QoS、安全组、Nova、Share 和端口转发的资源 ID，
也覆盖集群配置中的项目、域、角色及默认区域。`instanceId`、`hypervisorId`、`shareId`、
`regionId` 等本地业务引用沿用原名；创建请求中的 `imageId`、`flavorId` 在组装 `InstanceSpec` 时
转换为 `osImageId`、`osFlavorId`。
新库使用当前建表 SQL；已有表先补齐之前的字段迁移（包括 `add-instance-port-qos.sql`），
再执行 `sql/migrations/prefix-openstack-reference-fields.sql` 统一改名。
该脚本仅修改关系表字段，开发阶段不提供旧 Actor 事件、快照或旧接口字段名的兼容转换。

`hostname` 可选，使用宿主机记录 `hypervisor_host.hostname` 的值。省略、传 `null` 或空白字符串时自动调度；
指定时去除首尾空白并按大小写精确匹配，只在匹配的主机上预留资源。
主机仍需满足请求的区域、宿主机类型、ACTIVE 状态及 CPU、内存、磁盘、GPU 资源要求；
指定主机不存在、不符合条件或预留被拒绝时，实例创建以 `NO_VALID_HOST` 失败。
hostname 约束保存在创建事件的 `InstanceSpec` 中，重启恢复调度时继续生效。

服务端从规格读取 vCPU、内存（GB）、磁盘（GB）和 GPU 数量，使用镜像和规格对应的 OpenStack ID
提交 Actor 创建流程。调度候选宿主机限定在所选区域和宿主机类型内，
并且必须处于 ACTIVE 状态且有足够资源；指定 hostname 时，数据库在候选数量限制之前完成筛选，
调度器的 `HostnameFilter` 再校验匹配结果。
`SchedulerService` 直接实现 `HypervisorPlacement`：通过 `HypervisorHostRepo` 查询候选并完成筛选、排名，
通过 `HypervisorActor` 执行预留、确认、取消和释放。各筛选器和评分器统一使用
`HypervisorPlacement.ScheduleRequest`，调度不再经过额外的候选查询、预留调用或请求转换包装层。
实例工作流仍先持久化目标主机和预留 ID，再请求该主机预留资源；查询候选本身不产生预留。
GPU 请求采用堆叠调度：满足全部资源要求后，优先选择可用 GPU 数量最少的宿主机，
使本次分配后的 GPU 余量尽量小。可用数量为 `gpus - used_gpus - reserved_gpu`，包含创建中的预留占用。
例如请求 1 张 GPU 时，剩余 2 张的主机优先于剩余 8 张的主机；CPU、内存评分只在 GPU 余量相同时比较，
两者权重各为 0.5，最后按宿主机 ID 稳定排序。数据库在候选 `LIMIT` 前也先按可用 GPU 数量升序排序。
请求不需要 GPU 时，按 CPU、内存综合评分选择宿主机。
磁盘需求取规格的 `disk`，以 `diskGb` 随创建请求和调度请求传递；宿主机在维护状态下人工同步
OpenStack 数据，启用时先向 Actor 提交容量和基础占用。候选查询和 Actor 最终预留都会检查剩余磁盘，
预留、确认、取消和释放均同步更新资源计数，可用磁盘等于 `disk - used_disk - reserved_disk_gb`。
`description` 保存在实例工作流的 metadata 中，并作为 Nova metadata 提交。

## 服务端默认配置

创建接口保持上述请求字段，存储和网络配置通过 `application.yaml` 的
`instance.creation` 配置。至少需要配置以下两个环境变量：

```text
INSTANCE_SHARE_TYPE=<实际的 Manila CEPHFS share type 名称或 ID>
MANILA_FS_NAME=<实际的 Ceph 文件系统名称>
```

其余配置及默认值：

| 配置 | 环境变量 | 默认值 |
| --- | --- | --- |
| `share-size` | `INSTANCE_SHARE_SIZE` | 100 GB |
| `dependency-poll-interval` | `INSTANCE_DEPENDENCY_POLL_INTERVAL` | 2 秒，查询 UserActor 的资源状态 |
| `forwarding-ports` | `INSTANCE_FORWARDING_PORTS` | `22,8888`，每个内部端口独立分配公网端口 |
| `network-cidr` | `INSTANCE_NETWORK_CIDR` | `10.0.0.0/16` |
| `network-dns` | `INSTANCE_NETWORK_DNS` | 空，使用网络服务默认值 |
| `network-gateway` | `INSTANCE_NETWORK_GATEWAY` | 空，由 Neutron 分配 |
| `segmentation-id` | `INSTANCE_SEGMENTATION_ID` | 100 |
| `parent-port-bandwidth-limit.max-kbps` | `INSTANCE_PARENT_PORT_MAX_KBPS` | 102400 kbit/s |
| `parent-port-bandwidth-limit.max-burst-kbits` | `INSTANCE_PARENT_PORT_MAX_BURST_KBITS` | 10240 kbit |
| `sub-port-bandwidth-limit.max-kbps` | `INSTANCE_SUB_PORT_MAX_KBPS` | 102400 kbit/s |
| `sub-port-bandwidth-limit.max-burst-kbits` | `INSTANCE_SUB_PORT_MAX_BURST_KBITS` | 10240 kbit |
| `ssh-username` | `INSTANCE_SSH_USERNAME` | `clouduser` |
| `host-routes` | 在 YAML 中配置目标网段到下一跳的映射 | 空对象 |

每个用户在同一区域内复用同一个共享存储和私有网络。区域应预先配置好工作流所需的
LOCAL、SHARED 网络和 OpenStack 集群连接；CIDR、DNS、VLAN 和静态路由应匹配实际环境。
当前区域还需通过 `/api/v1/floating-ips` 创建或登记至少一个用于转发的浮动 IP，示例见
`http/floating-ip-api.http`。创建实例时按区域读取已登记且 `status=ENABLED` 的地址，按名称、业务 ID 排序，
将其业务 ID 作为候选列表随创建事件持久化；不会在 HTTP 请求期间调用 OpenStack 创建转发。
没有候选地址时返回业务错误 `11718`，不提交实例工作流。
新建或登记浮动 IP 时，先提交状态为 `DISABLED` 的登记记录，再发送 `EnableFloatingIp`，
持久化 `FloatingIpEnabled` 保存启用状态、身份和初始端口位图后返回。
`FloatingIpService.create()` 使用 `NOT_SUPPORTED` 暂停调用方事务，通过 `TransactionTemplate` 只为登记插入开启短事务，
提交成功后才调用 Actor；OpenStack 调用和等待 Actor 回复均在该事务之外。
登记插入或提交失败时不发送启用命令；启用失败或超时不回滚已提交的登记记录，可按登记业务 ID 调用启用接口重试。
超时不表示 Actor 未启用，已持久化的启用事件仍会更新读模型。
创建参数不再接收 `portMask`。`initOpenstack=true` 时新建地址并使用空占用启用；
`initOpenstack=false` 时，服务端在登记和自动启用前通过 `OpenStackClient.listPortForwardingExternalPorts()`
查询该浮动 IP 的现有转发规则，提取公网端口列表传给 Actor 计算占用。
查询失败则中止登记和启用；初始记录及创建响应的 `portMask` 根据查询结果生成，只记录 10000–40000 内的占用。
内部 `enable(identity, externalPorts)` 和 `EnableFloatingIp.externalPorts` 使用非 null 的 `List<Integer>` 传递公网端口；无占用时传空列表，由 Actor 生成启用事件中的位图。
创建响应的 `status="ENABLED"` 表示 Actor 已确认启用；数据库 `status` 由投影异步更新，投影完成后地址才进入实例候选列表。
`FloatingIpState.status` 使用 `Status` 枚举统一表示 `DISABLED`、`ENABLED`、`DESTROYING`、`DESTROYED`，初始为 `DISABLED`。
启用和禁用事件分别切换到 `ENABLED`、`DISABLED`；销毁开始切换到 `DESTROYING`，远端拒绝或出现异常后回到 `DISABLED`，销毁完成后进入 `DESTROYED`。
销毁操作尚未返回结果时保持 `DESTROYING`，失败后允许重新请求销毁；不再分别保存启用和销毁布尔标记。
`PUT /api/v1/floating-ips/{id}/disable` 禁止受理新的 claim，已有创建、查询、删除和恢复继续处理，当前位图保留。
`PUT /api/v1/floating-ips/{id}/enable` 无请求体；服务端使用登记的区域、项目和 OpenStack 浮动 IP ID，
调用 `OpenStackClient.listPortForwardingExternalPorts()` 查询现有公网端口，再发送 `EnableFloatingIp`。查询失败不发送启用命令。
`FloatingIpState.portMask()` 将整数列表转换为 `BitSet`，登记和启用流程共用该方法。
Actor 已启用时重复请求直接返回成功，保留当前位图；禁用时才将查询到的公网端口转换为位图，与当前 `usedPorts` 做 OR 合并。
既有占用全部保留，查询结果为空也不清除端口位；启用操作只增加占用。位图不区分占用来源，
TCP/UDP 共用同一端口位，只记录 `10000–40000` 范围内的占用。
同步前由操作人员先禁用浮动 IP，确认没有在途创建、删除或恢复请求，再调用启用接口；端口查询由服务端完成。
无在途请求由人工保证，Actor 不自动等待或增加在途请求冲突校验。
启用和禁用接口在 Actor 确认处理完成后返回 HTTP `202`；需要改变状态时先持久化事件，读模型随后更新。`{id}` 是登记表业务 ID。
`DELETE /api/v1/floating-ips/{id}` 销毁已禁用且没有端口占用的浮动 IP，无请求体；受理后返回 HTTP `202`，不等待 OpenStack 删除完成。
Actor 仍处于启用、销毁中状态或 `usedPorts` 非空时直接拒绝销毁，返回业务错误 `11808`；被拒绝的请求不会查询或删除 OpenStack 资源，也不会等待当前销毁完成。
满足本地条件后先持久化 `FloatingIpDestructionStarted`，`onDestroy` 立即回复 `true` 确认受理，再通过 `OpenStackClient.deleteFloatingIp()` 异步检查并删除登记项目中的地址。
删除方法检查项目归属；OpenStack 仍有端口转发（包括分配范围外和登记前的规则）或绑定实例端口时拒绝删除，不清理这些资源。
Neutron 删除时返回 `409` 也按拒绝处理；其端口转发占用检查见[端口转发实现](https://github.com/openstack/neutron/blob/master/neutron/services/portforwarding/pf_plugin.py)。
远端拒绝或出现异常时持久化 `FloatingIpDestructionFailed`，结束本次销毁并恢复为 `DISABLED`，可再次调用销毁接口。
资源已经不存在或删除返回 `404` 均视为成功，持久化 `FloatingIpDestroyed` 后进入 `DESTROYED`。
销毁未成功的异步结果统一通过内部 `DestructionFailed` 消息交给 `onDestructionFailed`；`error` 为 `null` 表示明确拒绝，
`error` 非空时记录异常日志；两种情况都持久化失败事件，Actor 和读模型均恢复为 `DISABLED`。
异步结果消息不再携带 `replyTo`，完成、拒绝或失败时均不再次回复调用方；已销毁时重复请求及从未初始化的 Actor 均直接确认成功，不执行删除。
若等待 Actor 受理确认失败或超时，接口返回业务错误 `11809`，调用方应查询当前状态，不能据此判断销毁是否已开始。
销毁不会自动清理端口或改写 claim 历史。已有端口需先完成各自的清理流程；Actor 位图中的登记占用也会阻止销毁。
销毁期间禁止启用和创建转发，claim 查询及删除返回 `IN_PROGRESS`；销毁成功后返回 `DELETED`。
仍处于销毁中时，外部重复请求被拒绝且不能重新启用；失败事件持久化后恢复禁用状态，允许再次请求销毁。
Actor 在销毁结果尚未持久化时重启，恢复到 `DESTROYING` 后继续删除地址，不恢复转发创建或删除；已记录失败的销毁由调用方重试。
`GetFloatingIpState` 通过 `FloatingIpForwardings.getState(id)` 查询 Actor 当前的 `FloatingIpState`，不依赖数据库投影进度。
`GET /api/v1/floating-ips/{id}/status` 返回 HTTP `200`，统一响应体的 `result` 是 `DISABLED`、`ENABLED`、`DESTROYING` 或 `DESTROYED`。
销毁受理后可轮询该接口：`DESTROYING` 表示当前销毁仍在处理，`DESTROYED` 表示已确认删除，回到 `DISABLED` 表示本次销毁未成功，可再次调用销毁接口。
已提交登记但尚未启用的 Actor 返回 `DISABLED`；Actor 未初始化且无登记记录时返回业务错误 `11804`，状态查询失败返回 `11810`。
读模型随后将登记记录标记为 `status=DESTROYED`，从业务查询和候选列表中排除；数据库保留记录和版本号，防止旧事件重放时恢复已销毁的地址。
Actor 直接按区域和浮动 IP ID 启动并恢复日志，不使用 `withStash` 等待启用。
禁用时提交新 claim 立即返回 `REJECTED`；相同 claim 的重复提交返回已有状态，查询和清理已有 claim 正常进行。
从未启用的 Actor 没有 claim，查询或协调返回 `NOT_FOUND`，删除按无资源处理并立即完成。
调用方必须先等待启用成功再创建转发；普通调用不查询 OpenStack 身份，也不隐式发送启用命令。
内部端口默认是 SSH `22` 和 JupyterLab `8888`；配置必须是包含 `22`、无重复的 `1–65535` 端口列表。
每个实例先创建一个专属安全组。每个转发的 FIP claim 就绪后，工作流放行相应内部端口的 IPv4 TCP/UDP 入站流量，来源为 `0.0.0.0/0`。
父端口和子端口创建时开启 port security，并只绑定该专属安全组；出站沿用 Neutron 新建安全组的默认规则。
安全组由实例工作流管理，FloatingIpActor 只负责公网端口分配和 TCP/UDP 转发。
端口转发通过 `ForwardingKind` 区分 `SYSTEM` 和 `USER`：实例创建时配置的全部转发均为 `SYSTEM`，
包括默认的 `22`、`8888`；实例创建完成后通过管理 API 新增的转发为 `USER`。
系统转发不支持单独删除，只能随整个实例删除；用户转发可以单独创建和删除。

实例的父端口和子端口各自绑定一个专用 QoS policy。OpenStack 端口创建方法内部先创建
QoS 和限速规则，再通过端口创建请求的 `qos_policy_id` 完成绑定，Actor 无需单独编排 QoS 创建和绑定。
创建和恢复查询结果通过 `PreparedNetwork.parentQos`、`subQos` 保存各端口的 `osPolicyId`、
`osIngressRuleId`、`osEgressRuleId` 和当前 `bandwidthLimit`，随 `NetworkPrepared` 一次持久化，并写入 `instance_view`。
不限速时仍保存 policy ID，两个 rule ID 为 NULL；后续子端口和 Trunk 创建保留前序 QoS 记录。
创建 Command 的 `spec.network.parentPortBandwidthLimit` 和 `spec.network.subPortBandwidthLimit`
分别携带父、子端口限速，随实例创建事件持久化；恢复时沿用提交时的值。
服务层从以上配置组装这两个参数，直接发送 `CreateInstance` 命令的调用方可以逐实例传入不同限速：

```java
NetworkRequirement network = new NetworkRequirement(segmentationId, userNetworkSpec,
    new PortBandwidthLimit(100_000, 10_000),   // 父端口：100 Mbps，突发 10,000 kbit
    new PortBandwidthLimit(1_000_000, 100_000)); // 子端口：1 Gbps，突发 100,000 kbit
// 将 network 传入 InstanceSpec，再通过 CreateInstance 命令提交。
```

每个端口的同一组参数分别创建 ingress 和 egress 两条规则。`maxKbps` 单位为 kbit/s，
`maxBurstKbits` 单位为 kbit（对应 Neutron 的 `max_burst_kbps` 字段）。
单位和方向遵循 [OpenStack 带宽限速规则 API](https://docs.openstack.org/api-ref/network/v2/index.html#create-bandwidth-limit-rule)。
两个值均不得为负；`maxKbps = 0` 表示不创建限速规则，此时 `maxBurstKbits` 也必须为 0。
YAML 中的默认值与 Java 配置类保持一致；环境变量仍可覆盖，排查空 policy 时先检查是否被配置为 `0 / 0`。
调整默认配置仅影响之后提交的实例；创建中的工作流继续使用创建事件中持久化的限速值。
运行中实例的限速通过下文 QoS 管理 API 修改，修改意图和结果独立持久化，重启不从当前默认配置取值。
已存在的规则会校验方向、带宽和突发值，避免重复创建或复用不匹配的限速配置。
工作流按操作标记查找资源；人工触发实例删除后，先删除端口，再清理对应 QoS，包括未绑定端口的残留 policy。

## 创建流程与恢复

所有业务 Actor 的注册和引用获取统一由 `PekkoRuntime` 管理，提供 `hypervisorActor`、
`instanceActor`、`userActor` 和 `floatingIpActor` 四个入口。
`PekkoConfig` 在 Spring 单例构造完成后统一注册实体并启动投影，避免 Runtime 与调用服务形成循环依赖。
`SchedulerService`、`UserResources` 和 `FloatingIpForwardings` 通过 Runtime 获取引用，再发送各自的业务命令。
运行时配置的读取方法集中在 `PekkoRuntime.Config` 内部类中，通过 `PekkoRuntime.getConfig()` 访问。
`HypervisorActor` 创建时传入 `HypervisorSettings`，预留结果保留时长和清理间隔由 Runtime
读取 `cloud.reservation` 配置后组装，Actor 内部通过 settings 使用。
宿主机、实例、用户资源和浮动 IP 的 Actor ask 共用 `cloud.ask-timeout`（默认 3 秒），通过 `PekkoRuntime.getConfig().getAskTimeout()` 获取。

`InstanceService` 完成参数和本地资源校验后，构造包含原始 Share、网络规格的 `InstanceSpec`，
通过 `PekkoRuntime.instanceActor(instanceId)` 直接 ask `CreateInstance`。
依赖准备完成前，`ShareRequirement.shareId`、实例状态中的 `shareId` 及 `instance_view.share_id` 均为空，不使用占位 ID。
查询同样向 Actor 发送 `GetInstanceState`，将返回状态转换成 `InstanceView`；
创建和查询均使用 `cloud.ask-timeout`。
InstanceActor 持久化 `InstanceCreationRequested` 和 `SchedulingStarted`，进入 `SCHEDULING` 并立即回复受理；
此阶段仅根据区域、宿主机类型和 CPU、内存、磁盘、GPU 规格查询、排序候选宿主机，不发送资源预留命令。
选定候选后先持久化 `HypervisorReservationStarted`，记录当前宿主机、预留 ID 和剩余候选，
进入 `RESERVING_RESOURCES` 后才向该宿主机发送预留命令。
收到明确的 `UNAVAILABLE` 才选择下一候选，仍须先持久化新的预留目标再发送命令；
`UNKNOWN` 表示预留结果未确认，保留同一宿主机和预留 ID 重试，不切换宿主机，避免同时占用多台宿主机的资源。
宿主机不可用或资源不足时，HypervisorActor 先持久化 `ReservationRejected` 再回复拒绝；
拒绝记录保留期间，同一预留 ID 的后续请求返回 `RESERVATION_UNAVAILABLE`，不再分配资源。
因此先前结果未知的迟到预留请求不会在实例已切换到另一候选后又占用原宿主机。
候选耗尽或预留被明确拒绝时进入 `FAILED`，不启动依赖和实例专属资源的创建。
预留成功后持久化 `HypervisorSelected` 和 `DependencyPreparationStarted`，清空剩余候选，
进入 `PREPARING_DEPENDENCIES` 后异步调用 `InstanceDependencyPreparer.prepare()`，准备用户项目、检查区域 SHARED 网络，
再等待系统 Share 和用户网络就绪。准备器依次调用 `createProject()`、`createSystemShare()`、`createNetwork()`，
创建请求持久化后即返回当前状态，首次受理为 `IN_PROGRESS`，已有资源直接返回其当前状态。
准备器每隔 `dependency-poll-interval` 调用对应的 `getProject()`、`getSystemShare()`、`getNetwork()`；
仅 `READY` 才继续下一步，`FAILED`、`REJECTED` 直接结束准备。
ask 超时表示回复尚未确认，继续查询；查询确认 `NOT_FOUND` 时才重新提交幂等创建命令。
准备器识别 Pekko typed ask 返回的 `java.util.concurrent.TimeoutException`，
包括多层 `CompletionException`、`ExecutionException` 包装，普通准备和重启恢复采用同样的处理。
准备器向 UserActor 发送的创建和查询 ask 均使用 `cloud.ask-timeout`（默认 3 秒），不占用 Actor 等待远端资源就绪。
依赖准备不设置整体截止时间，持续查询至资源就绪或明确失败。
结果通过 `DependenciesReady` 或 `DependenciesFailed` 命令回到 InstanceActor。
成功后持久化 `DependenciesPrepared`，保存项目 ID、Share 回复中的真实逻辑 `shareId`、访问数据和父子网络/子网 ID，
再创建专属安全组，随后进入网络准备阶段，依次创建父端口、子端口和 Trunk。
原始请求规格保持不变，重复请求仍按原始规格判断幂等。
准备失败直接持久化实例 `FAILED` 及失败码，用户级依赖资源继续保留。
若 Actor 恢复时仍处于 `SCHEDULING`，使用原请求重新查询、排序候选，此阶段尚未发送预留命令。
若处于 `RESERVING_RESOURCES`，使用已持久化的宿主机和预留 ID 独立恢复预留，
已受理的相同预留幂等返回，结果仍不明确时继续等待同一宿主机；只有明确不可用才按已保存的候选顺序推进。
这样即使预留成功后回复丢失或应用重启，也不会因重新调度而遗留另一台宿主机的预留；进入后续阶段后不再调度。
若仍处于 `PREPARING_DEPENDENCIES`，保留选定的宿主机和资源预留，使用原始规格重新执行准备器：
同规格创建命令直接复用已有 `READY` 资源，未完成资源继续查询。
项目、网络或 Share 已失败时，新的同规格创建命令会重新发起对应资源的创建。
当前 `pekko.cluster.sharding.remember-entities = off`，应用启动不会主动唤醒所有历史实例。
实例收到消息（例如 `InstanceService.get(instanceId)`）后才恢复，准备器的创建和查询消息会唤醒对应 UserActor。
这些资源由 UserActor 持久化管理：`projects` 以 OpenStack `clusterId` 为 key，
同一用户在同一集群的多个区域复用一个项目；用户网络按 `regionId` 复用。
Share 状态按逻辑 `shareId` 保存，系统 Share 在每个用户、每个区域内唯一，用户 Share 在同一区域可以有多个。
实例依赖准备只创建或复用 `SYSTEM` Share，不会选取用户通过 API 创建的 `USER` Share。
UserActor 调用 `createProjectWithAdminRole()`，先在集群配置的默认 Domain 中按名称查询项目，
存在则复用，不存在才创建，然后授予管理员角色，全部成功后才标记 `READY`。
`CreateProject` 先持久化创建意图，再异步调用 OpenStack；结果通过 `ProjectReady` 或
`ProjectFailed` 命令回到 Actor，持久化最终状态，供后续查询。
创建、就绪、失败分别持久化 `ProjectCreationRequested`、`ProjectBecameReady`、
`ProjectCreationFailed` 事件，由事件处理器更新项目状态。
同名创建命令立即返回当前状态；已处于 `CREATING` 时重新执行幂等创建，沿用当前 generation，
不重复记录创建事件。Actor 不保存调用方的等待列表。
已有项目为 `FAILED` 时，同名 `CreateProject` 会再次持久化 `ProjectCreationRequested`，
清除失败信息并递增 generation，然后立即回复 `IN_PROGRESS`，重新执行幂等创建。
旧一轮的异步结果不会覆盖新一轮状态；名称不同的请求仍被拒绝，`READY` 只返回当前状态。
项目创建流程等待 OpenStack 调用结果，成功记录 `READY`，调用失败记录 `FAILED`，不设置创建截止时间。
恢复中的 `CREATING` 项目按 Domain 和名称查找并复用，缺失时才创建，然后完成管理员授权。
恢复时向自身发送 `CreateProject`，回复交给 `ignoreRef()`；由 `onCreateProject` 的 `CREATING` 分支
重新执行创建，沿用当前 generation，不额外区分恢复轮次。
同一 generation 的新旧调用结果都可以推进创建，先进入 `READY` 或 `FAILED` 的结果生效；
进入终态后忽略其余回调，失败后的新创建命令通过递增 generation 隔离上一轮结果。
项目投影写入 `user_cluster_project`，主键为 `(user_id, cluster_id)`，建表定义见 `sql/read-model.sql`。
用户网络采用相同流程：`CreateNetwork` 持久化 `NetworkCreationRequested` 后调用
`createUserNetwork()`，依次校验 LOCAL 网络、创建用户网络、子网、路由和路由接口。
网络状态及创建、就绪、失败事件均记录 `clusterId`，网络仍按 `regionId` 复用；
已有网络只接受相同集群和相同规格的创建命令。
全部成功后发送 `NetworkReady`，持久化 `NetworkBecameReady`；调用失败发送
`NetworkFailed`，持久化 `NetworkCreationFailed`，调用方通过 `getNetwork()` 查询结果。
首次 `createNetwork()` 在创建意图持久化后立即返回；已处于 `CREATING` 时立即回复当前状态，
再执行幂等创建，沿用当前 generation，不重复记录创建事件；`READY` 只返回当前状态。
已有网络为 `FAILED` 时，同规格 `CreateNetwork` 会再次持久化 `NetworkCreationRequested`，
清除失败信息并递增 generation，立即回复 `IN_PROGRESS`，再执行幂等创建；旧一轮回调被忽略。
重试要求原 OpenStack 项目仍为 `READY`，不允许切换项目。
恢复时使用网络状态中保存的 `clusterId` 和规格向自身发送 `CreateNetwork`，回复交给 `ignoreRef()`；
由 `onCreateNetwork` 的 `CREATING` 分支继续创建，不额外区分恢复轮次。
同一 generation 的新旧调用结果都可以推进创建，先进入终态的结果生效，其余回调被忽略。
恢复中的 `CREATING` 网络按项目和名称分别查找已有网络、子网、路由，校验规格后复用；
路由接口按路由和子网查找，缺失步骤才继续创建。查到多个匹配资源或规格不一致直接失败。
网络创建流程等待 OpenStack 调用结果，不设置业务截止时间。
网络仍只有创建、就绪、失败三类事件，不逐步持久化资源 ID，不自动重试已失败流程或回滚。
调用失败仍可能留下部分已创建的远端资源。

Share 的应用类型由 `ShareKind` 区分：`SYSTEM` 用于实例依赖，每个用户在同一区域最多一个；
`USER` 由用户通过 API 创建，同一用户、同一区域允许多个。两者都属于对应的 UserActor。
用户 Share 通过以下接口创建和查询：

```http
POST /api/v1/users/{userId}/shares
Content-Type: application/json

{
  "regionId": "<区域 ID>",
  "size": 100,
  "shareType": "<Manila CEPHFS Share Type>"
}

GET /api/v1/users/{userId}/shares/{shareId}
```

创建接口固定使用 `USER` 类型和 `CEPHFS` 协议，`size` 为正整数，单位为 GiB。
创建请求不接收 `shareId`，由 UserActor 内部生成并随响应返回。
每次用户 Share 创建请求都申请新资源，即使区域和规格相同也互不复用；重复提交不具备幂等性。
接口先准备该区域所属集群的用户项目，再提交 Share 创建命令，受理后返回 HTTP `202`；
Share 的实际创建和访问授权异步进行，使用返回的逻辑 `shareId` 查询进度。
用户 Share 独立保存，实例创建继续复用该用户在区域内的系统 Share。
创建与查询返回 `ShareView`，包含 `shareId`、`userId`、`regionId`、`kind`、`phase`、规格、
`osShareId`、`exportLocationPath` 和失败原因；不返回 `accessKey`。查询成功使用 HTTP `200`。

Share 的创建和访问授权都由 Manila 异步完成，流程只保留三种状态：

```text
CREATING → READY
    └──→ FAILED
系统 Share：FAILED ──同规格普通 CreateShare──→ CREATING
```

`ShareSpec.kind` 表示应用类型，原 `shareType` 仍表示 Manila 的存储类型，两者分别保存。
规格还包含区域、项目 ID、容量和协议。系统 Share 的名称和描述为 `veryai:sys:<userId>`，
CephX 访问用户名为 `veryai_<userId>`；用户 Share 的名称和描述为 `veryai:user:<token>`，
访问用户名为 `veryai_user_<token>`。`token` 使用用户 ID 与逻辑 Share ID 的无歧义组合生成稳定 UUID，
保证重试和恢复使用相同名称，同时限制名称长度并区分同区域的不同 Share。
名称、描述与访问用户名通过 `UserShareNaming` 的静态方法 `name`、`description` 和 `accessTo` 生成，
每次调用直接传入用户 ID、应用类型和逻辑 Share ID。
实例依赖准备完成时，将系统 Share 的访问用户名连同资源 ID、导出路径和 access key
保存到 `PreparedShare`；cloud-init 使用该准备结果中的 `accessTo`。

内部 `CreateShare` 携带规格、回复地址和可选的 `shareId`。正常创建通过不带 ID 的构造方法提交，
HTTP 创建接口和服务层均不接收该 ID；UserActor 首次创建系统 Share 或每次创建用户 Share 时生成 UUID。
系统 Share 的普通创建请求按区域查找已有记录，同规格且处于 `CREATING` 或 `READY` 时直接返回当前状态，
不会重复推进正在创建的流程；处于 `FAILED` 时允许同规格重试。
恢复命令携带已持久化的 ID，按 ID 查找原 Share，
记录不存在或规格不一致时拒绝，不会生成新资源。
创建意图随 `ShareCreationRequested` 持久化后立即回复 `IN_PROGRESS`，再异步创建；
系统 Share 的重复请求、Actor 恢复和事件回放沿用已持久化的 ID。Manila 返回 `osShareId` 时持久化
`ShareCreated`。定时查询 Share，待状态为 `AVAILABLE` 且存在非管理员
专用、包含 ID 和路径的 export location 后，持久化 `ShareExportBecameReady`，再提交一次
CephX 授权请求。获得访问规则 ID 后持久化 `ShareAccessCreated`；这些中间步骤均保持 `CREATING`，
分别保存已确认的资源字段并递增 generation。访问规则变为 `ACTIVE` 且 access key 非空时，
持久化 `ShareBecameReady`，进入 `READY`。
调用错误或远端失败状态产生 `ShareCreationFailed`，保留已确认的资源 ID；调用方通过 `getShare()` 查询。
系统 Share 在 `FAILED` 时收到不带 ID 的同规格 `CreateShare`，再次持久化 `ShareCreationRequested`，
沿用逻辑 `shareId` 和规格，保留已确认的 Manila Share、导出位置、授权规则 ID 和导出路径，
清空 access key 和失败信息，递增 generation，回复 `IN_PROGRESS`，从首个未完成步骤继续。
旧一轮异步回调因 generation 不匹配而被忽略；已确认的资源不会重新创建。
用户 Share 的新建命令始终创建独立资源，不通过创建请求重试已有的失败 Share。

等待阶段统一通过单次定时器查询：首次查询、恢复后的查询和阶段切换后的查询，
均先等待 `share.poll-interval`（默认 2 秒）。正常轮询在查询完成后，若资源尚未就绪或暂不可见（404），
再安排下一次单次定时器；本次查询完成到下一次查询之间等待 `share.poll-interval`，其他调用错误直接失败。
`PollShare` 携带从 1 开始的 `attempt`，仅由定时器触发查询；`ShareExportChecked`、`ShareAccessChecked`
透传本次尝试次数，空结果字段表示未就绪，结果处理器据此安排 `attempt + 1` 的下一轮，字段有效时则持久化对应的就绪事件。
等待导出位置和等待访问授权两个阶段各最多执行 15 次轮询，404 和其他未就绪结果都计入次数。
第 16 次 `PollShare` 触发时不再查询 OpenStack，直接持久化 `ShareCreationFailed`、记录未就绪阶段并取消轮询定时器。
进入新的轮询阶段、失败重试或 Actor 恢复时，从第 1 次开始；尝试次数只随消息传递，不持久化。
恢复时仅对 `CREATING` Share 向自身发送携带原 `shareId` 和规格的 `CreateShare`，回复地址使用 `ignoreRef()`。
只有携带 ID 的恢复命令在回复已有状态后，才对 `CREATING` Share 调用 `advanceShare`，
按以下顺序检查已持久化字段并继续推进；沿用原 ID、资源字段和 generation，不重复记录创建事件。
每次恢复为每个未完成 Share 发送一条恢复命令，后续由异步结果和单次定时器衔接流程；已启动的轮询定时器不会重复启动。
异步回调按 `shareId` 查找状态，并校验 generation、创建状态和对应步骤的资源字段。
带 ID 的恢复命令遇到终态只返回状态，不重试失败流程：

| 已持久化字段 | 下一步 |
| --- | --- |
| 缺少 `osShareId` | 按项目和 description 精确匹配查找 Share，缺失时创建，不写入操作 metadata |
| 已有 Share ID，但缺少 `osExportLocationId` 或 `exportLocationPath` | 查询 Share 和 export location，未就绪时继续轮询 |
| 已有 Share 和完整 export location，但缺少 `osGrantAccessId` | 按 Share、访问类型和用户名查找授权，缺失时创建 |
| Share、export location 和访问规则 ID 均完整 | 查询访问规则，等待 `ACTIVE` 和 access key |

已有授权的权限级别必须匹配；多个匹配资源直接失败，不任意选择一个继续。
Share 的定时器、轮询命令和异步回调均使用 `shareId` 定位，同一区域多个 Share 独立推进。
读取旧规格时，缺少 `kind` 默认归为 `SYSTEM`；恢复旧的区域键快照时，Share 集合按已存储的 `shareId` 重建索引。
异步回调校验 generation 和对应步骤的资源字段，避免旧步骤或其他 Share 的结果覆盖当前进度。
Share 流程等待 OpenStack 调用结果，不设置业务超时或截止时间；异步资源尚未就绪时按上述尝试次数上限轮询。
系统 Share 收到不带 ID 的同规格创建命令时，`CREATING`、`READY` 只返回当前状态，`FAILED` 发起重试；规格不同则拒绝。
用户 Share 的重复创建命令各自创建新资源，恢复仍使用事件中持久化的 ID 继续原有资源。
UserActor 恢复仅继续 `CREATING`，不会自动重试 `FAILED`，也不执行自动回滚。

创建前查询已有资源用于恢复和复用；OpenStack 查询与创建不是一个原子操作。
若旧请求在重启时仍由远端执行，仍可能出现并发创建窗口，不能据此保证外部资源恰好创建一次。
发现多个匹配资源时流程会记录失败，保留资源供核对。

Share、用户网络和路由的投影见下方“用户资源读模型”。
接口失败或单个实例失败不会删除这些共享资源。
InstanceActor 包含以下主要阶段：

```text
SCHEDULING → RESERVING_RESOURCES → PREPARING_DEPENDENCIES → PREPARING_SECURITY_GROUP
  → PREPARING_NETWORK
  → PREPARING_FORWARDING → PROVISIONING → CONFIRMING → ACTIVE
任一创建阶段失败 → FAILED
FAILED → 人工发送 DeleteInstance → DELETING_NOVA → DELETING_FORWARDINGS
  → DELETING_NETWORK → DELETING_SECURITY_GROUP → CANCELLING_RESERVATION
  → RELEASING_ALLOCATION → DELETED
```

HTTP 创建流程在网络就绪后进入 `PREPARING_FORWARDING`，依次完成 22、8888 两个内部端口的映射。
每个内部端口使用独立 claim ID（包含实例 ID、请求 ID、内部端口），分配一个 `10000–40000`
范围内的公网端口，同时创建 TCP/UDP 转发，目标为实例父端口的 IPv4 地址。
每个端口分别按候选顺序尝试浮动 IP；分配前确认地址无效或端口耗尽时可切换到下一个候选。
候选列表 `floatingIpIds` 保存 `floating_ip.id`，实例创建、查询、删除和恢复均通过此内部 ID 定位 FIP Actor。
`CreateInstanceForwarding` 可携带有序、不重复且 ID 非空白的 `floatingIpIds`，受理后保存到转发状态中供候选切换和恢复使用。
未指定或传空列表时，初始化使用实例配置中的候选列表，用户转发使用系统 SSH 映射的浮动 IP；HTTP 用户转发接口沿用此默认选择。
两个端口可能落在不同浮动 IP 上，调用方应分别使用查询结果中的地址和公网端口。
实例初始化和管理 API 都先持久化 `ForwardingCreationRequested`，再按 claim ID 提交创建和查询。
FIP 返回有效的 `READY` 后，工作流补建该内部端口缺失的 TCP、UDP 入站规则，再持久化 `ForwardingCreated`。创建最终失败时持久化 `ForwardingCreationFailed`。
实例初始化发起的转发成功后继续下一个配置端口，最后一个端口就绪时持久化 `NovaCreationStarted` 并进入 `PROVISIONING`。管理 API 发起的创建在该转发就绪后完成。
实例状态只保存以 `claimId` 为键的 `forwardingStates`，每个 `InstanceForwardingState` 同时保存生命周期和可空的 `InstanceForwardingResources resources`。
`InstanceForwardingResources` 仅保存 `osFloatingIpId`、浮动 IP 地址、域名、公网端口以及 TCP/UDP 规则 ID。
`claimId`、`internalPort`、`kind` 由 `InstanceForwardingState` 保存，内部 `floatingIpId` 由候选列表与当前索引确定；FIP 返回结果的标识和规格在接收时校验。
创建就绪前 `resources` 为空，`ForwardingCreated` 携带含资源的就绪状态；删除中和删除失败时保留已确认资源，切换候选浮动 IP 时清空资源。
命令、回调和删除直接按 `claimId` 定位；内部端口保留为转发属性，创建前按内部端口检查占用，初始化和 SSH 检查也按内部端口查找。
尚未发起请求的系统端口直接读取 `InstanceSpec.forwarding`，不创建占位状态。删除完成后移除该 claim，释放相应端口；同一端口重新创建时使用新的 `claimId`。
转发状态直接保存 claim、候选浮动 IP、阶段和代次；`kind` 区分系统初始化和用户管理流程。当前结构用于开发阶段，不提供旧格式转发事件及实例快照的兼容转换。
`osFloatingIpId` 由 FIP Actor 返回并保存在 `InstanceForwardingState.resources` 中，转发操作通过内部 `floatingIpId` 和 `claimId` 发起。
创建受理前校验 `forwarding` 必须存在且包含内部端口 `22`，不满足时直接拒绝，不记录创建事件。
正常创建和恢复在网络准备完成后均进入 `PREPARING_FORWARDING`；各阶段使用第一步已选定的宿主机。
转发阶段不设置整体等待超时，保留轮询以等待异步创建完成。
重启后沿用已持久化的候选、内部端口和已完成映射，查询当前未完成的 claim；
只有确认 claim 不存在时才重新提交该 claim，已完成端口不会重建。

FloatingIpActor 的实体 key 为 `floating_ip.id`，通过 `PekkoRuntime.floatingIpActor(floatingIpId)` 获取。
`FloatingIpIdentity` 持久化内部 `floatingIpId`，以及 OpenStack 调用所需的区域、项目和 `osFloatingIpId`。
FloatingIpActor 的 claim 状态为 `CREATING`、`READY`、`FAILED`、`DELETING`、`DELETE_FAILED`、`DELETED`。
创建失败使用 `FAILED`，删除失败使用 `DELETE_FAILED`，不再保存 `paused` 标记。
`CreatePortForwarding` 根据 Actor 当前总位图 `usedPorts` 选择端口，将 `ForwardingCreationRequested` 和
`ForwardingPortAllocated` 一起持久化，然后返回 `IN_PROGRESS` 并立即调用 `OpenStackClient.createPortForwardings()`；
端口耗尽时持久化创建和失败事件并返回 `FAILED`，不调用 OpenStack。
不再保存 `order` 或通过全局 `Drive` 排队，不同 claim 的 OpenStack 异步操作可以并行执行。
创建不调用 `prepareForwardingTarget`，也没有内部端口预留 ask；分配端口只依据本地位图。
`executeCreation()` 在一次异步操作中查询并复用已有 TCP、UDP 规则，按协议补建缺失规则；整套流程成功发送 `ForwardingReady` 并持久化
`ForwardingBecameReady`，失败发送 `ForwardingFailed` 并持久化 `ForwardingCreationFailed`。
`onReady` 和 `onFailed` 只记录结果，不发起删除。
Actor 不再逐协议保存创建阶段，也不记录查询尝试次数。
端口选择和预留均在 Actor 内串行处理，使用最新位图，避免不同 claim 分配到同一端口。
每次分配从 `10000–40000` 内随机位置开始向后查找，到末尾仍未找到时从范围起点继续查找；
位图中已占用的端口会跳过，整个范围都已占用才返回端口耗尽。恢复已有预留时沿用原端口。
每个 claim 分别跟踪当前操作标识，同一 claim 不会同时发起创建和删除；回调仅清除自己的操作标识，
其他 claim 的回调以及重启前的迟到回调不会使当前操作失效。
异步结果直接发送携带 token 的 `ForwardingReady`、`ForwardingFailed`、`DeletionCompleted` 或 `DeletionFailed`。
各处理方法先校验并清除匹配的 token，再校验 generation 和阶段、记录对应业务事件。
恢复调用生成新 token；就绪结果不完整时直接记录创建失败，避免重复消费同一次回调的 token。
端口预留沿用本次创建的 generation，统一由最终结果完成该操作。

`createPortForwardings()` 先校验浮动 IP 存在且属于请求项目，再以 `internal_port_id=spec.osInternalPortId()` 过滤转发列表，
按 claim 和协议对应的操作标识查找并校验 TCP、UDP 规则，补建缺失协议。
相同 claim 和规格的重复调用复用已有规则 ID；上次只完成一种协议时，重试只补建另一种协议。
创建事件不保存截止时间，SDK 方法和 Actor 均不额外设置创建超时；底层 HTTP 超时仍按 SDK 配置执行。
规则描述中的操作标识为 `veryai:pf:<claimId>:<protocol>`，协议为 `TCP` 或 `UDP`，供正常创建和恢复时定位资源。
`signalHandler` 收到 `RecoveryCompleted` 后通过 `recoverState → recoverClaim` 按持久化状态分流。
`CREATING` 复用 `executeCreation → OpenStackClient.createPortForwardings()` 恢复，沿用已持久化的 claim、规格和公网端口，
与正常创建使用同一套项目归属校验、规则查询和补建逻辑。
恢复先按 `internal_port_id` 查询一次当前浮动 IP 中目标 Neutron 端口的转发列表，按 claim 和协议对应的操作标识定位 TCP、UDP，
校验公网端口、内部端口、目标 Neutron 端口、固定 IP、协议和规则 ID。
两条规则均存在时直接复用并返回就绪；只存在一条时只补建另一条，都不存在时才创建两条。
两种协议均完成校验后才补建；操作标识重复、参数不一致或规则 ID 缺失时进入 `FAILED` 并保留端口，等待核对。
恢复沿用已预留端口、不增加创建超时；结果通过同一套 Ready/Failed 消息和事件记录。
`DELETING` 复用 `executeDeletion → OpenStackClient.deletePortForwardings()` 恢复，沿用已持久化的 claim、规格和资源，
与正常删除使用同一套项目归属校验、规则查询和幂等删除逻辑。
按持久化的 TCP、UDP 规则 ID 逐条查询；ID 缺失时先以 `internal_port_id` 过滤列表，再按 claim、协议操作标识定位，
校验公网端口、内部端口、目标端口、固定 IP、协议和规则 ID 后继续删除，已不存在的跳过。
两条规则均确认删除后通过 `ForwardingDeleted` 释放端口并更新投影，失败则通过 `ForwardingDeletionFailed` 进入 `DELETE_FAILED`。
恢复不增加超时；操作标识重复或规格不匹配时保留端口，等待外部核对。
禁用浮动 IP 不阻止已受理创建、删除的恢复，也不阻止外部删除命令。
中断前已在远端创建但尚未持久化结果的规则，也能通过操作标识发现并复用。
恢复只处理 `CREATING` 和 `DELETING`；`FAILED`、`DELETE_FAILED` 不会自动重试。

删除使用 `ForwardingDeletionStarted`、`ForwardingDeleted` 和 `ForwardingDeletionFailed`
分别记录开始、完成和失败。
FIP Actor 统一使用 `DeletePortForwarding` 按 `claimId` 删除，不区分转发类型或调用场景。
实例 Actor 只接受用户转发的单条删除请求；
实例整体清理使用同一删除命令清理两种类型。已开始但因重启中断的删除恢复时复用相同的删除入口。
删除通过独立的命令和事件执行。`CREATING` 状态拒绝删除并返回 `REJECTED`，
不写事件、不记录删除意图，也不改变创建状态；调用方需在创建进入 `READY` 或 `FAILED` 后重新发送删除命令。
因此不再保存删除意图字段；重启后仍按 `CREATING` 恢复创建。
实例删除和删除恢复均从 `forwardingStates` 取得系统与用户转发，复用逐条删除流程，
使用每条记录的内部 `floatingIpId` 和 `claimId` 调用 `delete` 和 `get`。
删除返回 `DELETED` 后还需完成该内部端口的专属安全组规则清理；返回 `IN_PROGRESS` 后通过 `get` 继续查询，
查询仍为 `IN_PROGRESS` 只等待，`READY`、`FAILED`、`NOT_FOUND` 时重新提交删除并确认结果。
`DELETE_FAILED` 持久化为单条删除失败，实例清理仅重试未完成的记录。
列表为空时直接完成该步骤；全部转发及其专属规则清理完成后才持久化 `InstanceForwardingsDeleted` 并进入 `DELETING_NETWORK`。
系统转发清理已就绪的映射和当前已开始创建的 claim。用户转发清理 `forwardingStates` 中登记的全部用户转发。
因此用户创建失败或未保存远端资源 ID 时，仍能按已持久化的转发标识清理可能存在的 claim。
创建结果尚未返回的系统或用户转发也保留内部 `floatingIpId`，删除和恢复无需重新查询数据库确定 Actor。
外部对尚未创建的 claim 发送单个删除命令时，FloatingIpActor 同样记录 `ForwardingDeleted`，
在 `outcomes` 中保存已删除标记，阻止保留期内的迟到创建，不调用 OpenStack，也不改变端口位图。
FloatingIpActor 不再提供实例级批量删除命令，也不保存实例级取消记录。
同一 claim 已有删除请求在执行时，重复删除只返回当前进度，不并发提交删除。
`deletePortForwardings()` 支持重复调用：按已保存的 TCP/UDP 规则 ID 先查询规则详情，校验操作标识和规格后才调用删除接口。
方法返回 `CompletionStage<Void>`，正常完成即确认删除；Actor 根据异常发送 `DeletionCompleted` 或 `DeletionFailed`，不再判断布尔结果。
规则 ID 缺失时以 `internal_port_id=spec.osInternalPortId()` 查询当前浮动 IP 的转发列表，
按 claim、协议对应的操作标识定位并校验，补齐待删除的规则 ID。
查询返回空或 404 时直接视为已删除；删除成功或返回 404 同样视为完成。
浮动 IP 已不存在，或归属校验后查询转发列表返回 404 时，也视为完成；删除后不复查，操作标识重复或规格不匹配则失败。
上次只删除一种协议时，重试跳过已不存在的规则并继续删除另一种协议。
两条规则都删除完成后，通过 `ForwardingDeleted` 直接清除该 claim 的端口位，不再区分占用来源。
因此创建成功但响应未持久化的规则，也可由外部触发的删除流程发现并清理。
删除流程不额外设置超时，事件和 claim 不保存删除截止时间；Actor 直接等待 OpenStackClient 的异步结果，
底层 HTTP 超时仍按 SDK 配置执行。删除失败后进入 `DELETE_FAILED`，保留资源、端口占用和失败原因；
外部再次发送删除命令时，通过 `ForwardingDeletionStarted` 回到 `DELETING` 并重试。
应用重启时，`DELETING` claim 自动恢复删除，`DELETE_FAILED` 等待外部命令。
删除完成后进入 `DELETED`；创建失败时保留 `FAILED`、失败原因和已占用端口，等待外部决定是否删除。

`claims` 仅保留仍需处理的完整记录。端口耗尽等未分配资源的失败，以及确认删除完成的 claim，
立即移入精简的 `outcomes`，仅保存 `FAILED`／`DELETED`、失败原因和完成时间。
`claims` 不保留 `DELETED` 或未占用端口的 `FAILED`；`onCreate` 检查重复服务时直接匹配全部 claim 的
`instanceId`、`osInternalPortId` 和 `internalPort`，匹配即拒绝创建。
对于 `claims` 中的记录，`onDelete` 拒绝 `CREATING` 后只需检查在途操作；有在途操作时返回当前进度，
否则记录 `ForwardingDeletionStarted` 并执行删除。
`FloatingIpState.updated()` 只更新 claim；`allocated()` 保存已分配的 claim 并直接置位，
创建命令计算事件位图和应用 `ForwardingPortAllocated` 事件均使用该分配方法。
公网端口范围由 `FloatingIpActor.availablePort()` 保证，事件重放沿用已持久化的端口。
`creationFailed()` 按是否持有端口直接保留失败 claim 或移入 `outcomes`，保留已有端口位图。
同一浮动 IP 的每个公网端口由单个 claim 独占；`deleted()` 直接移除 claim、清除其已预留的端口位并写入删除结果，
无需遍历其他 claim。删除流程已拒绝 `CREATING`，进入 `deleted()` 的现存 claim 必定持有端口，可直接清位；
claim 不存在时不修改位图。
失败和删除直接构造最终状态，普通状态更新不再承担端口分配和释放。
`ForwardingSpec` 使用 record 默认构造方法，`kind` 非空、标识字段非空且非空白以及 `internalPort` 在 `1–65535` 范围内均由调用方保证。
`CreatePortForwarding` 的 `claimId` 非空且非空白、`replyTo` 非空由调用方保证，保留接收 `ForwardingSpec` 的重载构造方法。
`DeletePortForwarding` 和 `GetPortForwarding` 使用 record 默认构造方法，`claimId` 非空且非空白、`replyTo` 非空由调用方保证。
`ForwardingClaim` 使用 record 默认构造方法，`floatingIpId` 非空且非空白、`spec`、`phase` 和 `resources` 非空均由调用方保证。
`ForwardingOutcome.from()` 直接转换终态记录，调用方保证传入不持有端口的 `FAILED` 或 `DELETED` claim。
`ForwardingOutcome` 使用 record 默认构造方法，终态约束和 `completedAt` 非空均由调用方保证。
仍在 `claims` 中的记录通过完整 `ForwardingSpec` 校验重复创建，相同 claim ID 携带不同参数会被拒绝。
仍占用端口或处于创建、就绪、删除中的 claim 不会被清理，`DELETE_FAILED` 保留完整记录等待重试。
进入 `outcomes` 后，保留期内按相同 `claimId` 查询或重复创建均返回原终态，不再比较请求参数，终态回复的 `claim` 为 `null`。
尚未创建就收到删除命令的 claim 也保存在 `outcomes` 中；迟到的创建请求返回 `DELETED`，不会发起 OpenStack 创建。
已压缩的无资源失败结果收到删除命令时，也通过 `ForwardingDeleted` 转为 `DELETED`，保留失败原因。

`cloud.fip.outcome-retention` 默认 `24h`，`outcome-cleanup-interval` 默认 `10m`。
Actor 恢复后立即检查一次，之后定期将过期记录 ID 持久化为 `ForwardingOutcomesCleaned` 再移除。
`claims` 与 `outcomes` 中的 `claimId` 互斥；`expiredOutcomes()` 仅按 `completedAt <= cutoff` 筛选过期结果。
完成时间来自事件，事件重放不会重新开始保留期，重复查询或重复删除也不会延长已删除结果的保留期。
过期清理后查询返回 `NOT_FOUND`，旧 claim ID 不再保证幂等；保留期应覆盖调用方可能重试的时间范围。
清理终态记录不改变 `portmask`，不发起 OpenStack 删除，也不删除 journal 历史事件，已有 projection 可以继续消费。

创建失败统一返回 `FAILED`，删除失败返回 `DELETE_FAILED`；超时或响应丢失等结果仍保留已占用端口，等待外部核对或清理。
不提供显式恢复命令；重启恢复继续 `CREATING`、`DELETING`，已失败的操作不会重新执行。
失败资源由外部删除命令清理；删除失败后通过再次发送删除命令重试。
实例创建阶段的系统转发失败且仍持有端口时，实例工作流进入 `FAILED`，不切换浮动 IP，也不自动发送删除命令。
人工发送 `DeleteInstance` 后，实例删除流程才向 FloatingIpActor 发送外部删除命令。
只有没有占用端口的失败才允许继续尝试其他候选。

Cloud-init 显式指定 JupyterLab 的 `ServerApp.port=8888`、`ServerApp.port_retries=0`，
使监听端口与转发目标保持一致；端口占用时不会自动改用其他端口。
参数定义见 [Jupyter Server 配置文档](https://jupyter-server.readthedocs.io/en/stable/other/full-config.html#ServerApp.port)。
`forwarding-ports` 配置转发目标和安全组放行端口，镜像中的 SSH/JupyterLab 服务仍须监听对应内部端口。

依赖就绪后先持久化 `SecurityGroupPreparationStarted`，再调用 `prepareInstanceSecurityGroup()`
按项目 ID 和名称查询实例专属安全组。查到唯一同名组且描述匹配时复用其 ID，未找到时创建，
同名组有多个或描述不匹配时失败。安全组就绪后持久化 `SecurityGroupPrepared`，记录安全组 ID，再开始创建端口。失败使用
`InstanceProvisioningFailed` 记录失败原因并进入 `FAILED`，不重新提交失败的安全组创建流程。
安全组名称和描述统一为 `veryai:sg:{instanceId}`。重启恢复调用 `recoverInstanceSecurityGroup()`，按相同条件查找并复用安全组。
准备阶段直接等待异步调用结果，不设置业务截止时间。
恢复中的查找与创建并非原子操作，旧请求在远端继续执行时仍可能出现重复创建窗口。

父端口、子端口和 Trunk 统一在一个网络准备阶段创建：

| 阶段 | 创建意图事件 | 就绪事件 | OpenStack 调用 |
| --- | --- | --- | --- |
| `PREPARING_NETWORK` | `NetworkPreparationStarted` | `NetworkPrepared` | 调用 `prepareInstanceNetwork()`，依次创建父子端口、QoS、子端口专属浮动 IP 和 Trunk |

Actor 通过 `requestNetwork()` 调用 `OpenStackClient.prepareInstanceNetwork()`，
由客户端顺序编排父端口 → 子端口 → Trunk；中间资源在链内传递，全部成功后才发送 `NetworkPreparationCompleted`。
整个网络准备使用一个请求标识，阶段、执行代次及请求标识共同过滤迟到结果。
Actor 重启或停止后，先前启动的网络创建或恢复仍可能继续执行后续 OpenStack 调用。
成功后一起持久化 `NetworkPrepared` 和 `ForwardingPreparationStarted`，再启动端口转发创建。
恢复流程通过独立的 `recoverNetwork()` 编排，不调用正常创建流程。

OpenStackClient 的 `prepareInstanceNetwork()` 依次调用 `prepareInstanceParentPort()`、
`prepareInstanceSubPort()` 和 `prepareInstanceTrunk()`。
三个方法分别直接创建对应资源，
父端口负责端口及 QoS，子端口负责端口、QoS 和专属浮动 IP，Trunk 负责父子端口的 VLAN 组合。
查询分别使用 `findInstanceParentPort()`、`findInstanceSubPort()`、`findInstanceTrunk()`，
校验资源归属、网络、安全组、QoS、浮动 IP 绑定和 VLAN；端口、Trunk 或子端口浮动 IP
未找到时返回空结果，查询不创建缺失资源，也不补建 QoS 规则。
创建、只读查询和恢复使用独立方法，不通过 `allowCreate` 参数切换。正常创建调用创建方法，
成功返回有效资源就执行下一步；调用异常或缺少结果时持久化 `FAILED`，保留资源等待人工删除。
正常创建不安排网络阶段的 Check 轮询。重启恢复由 `InstanceRecovery` 分别调用
`recoverInstanceParentPort()`、`recoverInstanceSubPort()`、`recoverInstanceTrunk()`，
按项目和操作标识发现并复用资源，依次补齐整组网络资源中尚未完成的部分。
恢复可复用尚未绑定端口的 QoS policy、补齐缺失方向的带宽规则，并为已有子端口补建缺失的浮动 IP；
操作标识重复、资源归属或规格不匹配时明确失败，不任意选取一个资源继续。

`NetworkPreparationStarted` 先持久化空的网络资源记录，确保网络准备中途失败后仍能进入网络清理。
`NetworkPrepared` 一次保存父端口 ID、MAC、固定 IP、父子端口 QoS policy/规则 ID，
子端口 ID、固定 IP、浮动 IP ID 和地址，以及 Trunk ID。
状态查询和数据库投影在整组网络资源就绪后一次更新，准备过程中保持 `PREPARING_NETWORK`。
中途失败或重启时，尚未持久化的资源通过同一实例操作标识查询；人工删除也按该标识发现并清理部分资源。
子端口浮动 IP 从用户路由出口对应的 `LOCAL` 网络分配，依赖准备时将该网络 ID 保存到
`NetworkRequirement.osSubFloatingNetworkId`，重启后继续使用同一网络。
分配时指定子端口 ID 和固定 IP，并写入实例操作标识；浮动 IP 绑定确认后才继续创建 Trunk，
整条网络创建链成功后持久化 `NetworkPrepared`。
`InstanceView.network` 返回 `osSubFloatingIpId`、`subFloatingIpAddress`，`instance_view` 同步保存
`os_sub_floating_network_id`、`os_sub_floating_ip_id`、`sub_floating_ip_address`。
该地址属于实例网络资源，父端口的 22、8888 转发继续使用原有候选浮动 IP。
网络准备不设置等待截止时间，当前步骤成功后才执行下一步。
实例在 `PROVISIONING` 阶段提交 Nova 创建请求，随后轮询 VM 状态和 SSH 连通性。
`NovaServerCreated` 记录已观察到的实例 ID 和 Nova 状态。轮询时只有资源 ID、Nova 状态或工作流阶段发生变化才持久化事件。
创建请求异常或未返回实例时，直接持久化 `InstanceProvisioningFailed` 并进入 `FAILED`，保留资源等待人工删除。
正常状态查询缺少已保存的实例 ID 时，转入 `InstanceRecovery.recoverProvisioning()` 独立恢复，
按已知实例 ID 或操作标识找回实例；恢复期间只查询，不重新提交 Nova 创建。

每轮先查询 Nova 状态，返回 `ACTIVE` 后在同一轮探测 SSH。如果 SSH 暂未连通，投影会记录 Nova 状态为 `ACTIVE`，实例阶段仍为 `PROVISIONING`。
`InstanceSshChecker` 通过 `forwardingResourcesByInternalPort(22)` 读取对应转发状态中的 `resources`，
使用对应的 `floatingIp:externalPort` 和创建规格中的 SSH 用户名调用 `SSHValidator`，
不连接浮动 IP 的固定 `22` 端口，也不使用子端口独立分配的浮动 IP。
探测在虚拟线程中执行，连接和读取超时各设为 5 秒，不阻塞 Actor。
SSH 握手后的认证拒绝也视为连通，不使用实例密码登录；这一步不验证账号凭据或 cloud-init 是否全部完成。
连接暂时不通时保持 `PROVISIONING`，下一轮重新查询 VM 状态。此时实例仍在创建中，删除请求会被拒绝。
缺少内部端口 `22` 的转发、公网地址或 TCP 规则 ID 时，记录 `SSH_CHECK_FAILED`，
保留资源等待人工删除。检查成功后持久化独立的 `SshConnectivityVerified`，
进入 `CONFIRMING`，资源预留确认成功后才进入 `ACTIVE`；投影同步这些阶段。
重启处于 `PROVISIONING` 时，`InstanceRecovery.recoverProvisioning()` 先查询 VM 状态，再按相同条件探测已保存的 SSH 转发目标。

实例端口/QoS/Trunk 和 Nova 的创建意图先持久化，再调用 OpenStack。
Actor 恢复时仍处于 `PREPARING_NETWORK`，独立恢复链依次按操作标识查询父端口、子端口和 Trunk，
复用已存在的资源并创建或补齐缺失部分；整组就绪后持久化 `NetworkPrepared` 并进入端口转发准备。
子端口恢复同时按项目和操作标识查询浮动 IP，并校验外部网络、子端口和固定 IP；
地址缺失时补建，多个匹配地址或绑定不一致时失败。
恢复中遇到暂时连接故障、OpenStack 5xx 或限流时保留当前阶段重试；确定的资源结构或规格异常记录失败。
查找与创建并非原子操作，旧请求在远端继续执行时仍可能产生重复资源，不能保证远端恰好创建一次。
重复资源会被明确拒绝并保留，供人工核对。
Nova 恢复仍只查询，不重新发送创建请求。尚未保存服务器 ID 且按项目和操作标识未查到实例时，
记录 `NOVA_CREATION_UNCONFIRMED` 并进入 `FAILED`；已保存服务器 ID 但查询返回 404 时，
记录 `NOVA_SERVER_MISSING`。两种情况都保留资源与预留，等待人工核对后触发删除。
已找到且尚未就绪的 Nova 实例继续查询，明确返回 `ERROR` 时进入 `FAILED`；
暂时无法完成查询时继续重试，不提前释放资源。

`RecoveryCompleted` 只进入独立的 `InstanceRecovery` 恢复编排，不直接调用创建或删除入口。
恢复使用专用的 `RetryRecovery`、`RecoveryStepCompleted` 命令和轮询定时器，
独立处理当前持久化阶段的查询、结果判断和重试；阶段、轮询代次与请求标识共同校验异步结果，
恢复期间忽略旧创建、删除流程的迟到回调，状态查询和重复受理仍正常响应。
恢复当前阶段时沿用原有资源操作标识，不额外设置恢复超时。
端口转发恢复先查询已有 claim，缺失时使用相同 claim ID 提交创建。claim 就绪后检查安全组规则并补齐缺失协议，即使重启前只创建了一条规则也能继续。
网络恢复查询并补齐整组网络资源，Nova 恢复只查询已有实例。
当前操作确认完成并持久化明确的就绪事件后，才交给创建逻辑启动下一项尚未执行的操作。
只有资源操作标识、领域事件以及 OpenStack/Actor 客户端等基础接口共用，恢复的请求与结果处理可独立修改。

创建失败统一持久化 `InstanceProvisioningFailed`，保留失败原因以及 Nova、专属安全组、父子端口、
Trunk、QoS、子端口浮动 IP 和端口转发等已创建资源；端口创建失败时也不自动删除专用 QoS。
进入 `FAILED` 后停止实例创建的定时任务，忽略迟到的创建结果，重启恢复也不会发起删除。
已提交到其他 Actor 或 OpenStack 的请求可能继续完成，实例失败不会取消用户级项目、Share 或网络准备。
不再提供创建失败后自动清理的配置开关，也不会因为修改配置或重启应用而启动清理。

运行中的实例或创建失败的实例，可通过 `DELETE /api/v1/instances/{instanceId}` 删除，服务层发送 `DeleteInstance`。
该入口允许对 `ACTIVE`、`FAILED` 实例开始删除；创建中的实例会拒绝，业务错误码为 `11721`。
用户转发创建、删除、QoS 修改、重启或重建尚在进行时也拒绝实例删除，避免管理请求与资源清理同时执行。
`InstanceDeletionStarted` 持久化后进入 `DELETING_NOVA` 并返回 HTTP `202`，所有删除阶段及 `DELETED` 下的重复删除请求均幂等受理。
删除按以下六个阶段执行，当前阶段完成后先持久化明确的完成事件，再执行下一阶段：

| 当前阶段 | 完成事件 | 下一阶段 |
| --- | --- | --- |
| `DELETING_NOVA` | `NovaServerDeleted` | `DELETING_FORWARDINGS` |
| `DELETING_FORWARDINGS` | `InstanceForwardingsDeleted` | `DELETING_NETWORK` |
| `DELETING_NETWORK` | `InstanceNetworkDeleted` | `DELETING_SECURITY_GROUP` |
| `DELETING_SECURITY_GROUP` | `InstanceSecurityGroupDeleted` | `CANCELLING_RESERVATION` |
| `CANCELLING_RESERVATION` | `InstanceReservationCancelled` | `RELEASING_ALLOCATION` |
| `RELEASING_ALLOCATION` | `InstanceDeleted` | `DELETED` |

没有对应资源的阶段也持久化完成事件后推进。事件保留已知资源信息，阶段变化同时更新执行代次，
配合每次请求的标识过滤迟到回调；已经完成的阶段不因后续阶段重试而再次执行。

`onDelete` 持久化删除意图后，已有 Nova 实例 ID 时直接调用 `requestNovaDeletion()` 提交删除；
只有操作标识时先查询实例，两者均不存在时记录 `NovaServerDeleted` 并推进。
`requestNovaDeletionStatus()` 只查询 Nova，查询结果通过 `NovaDeletionChecked` 返回；
存在且尚未受理删除时，调用独立的 `requestNovaDeletion()` 提交删除，结果通过 `NovaDeletionSubmitted` 返回。
删除请求被接受后持久化 `NovaDeletionAccepted`，保存服务器 ID，阶段仍为 `DELETING_NOVA`。
此后按 `cloud.instance.poll.max-delay` 定时查询该 ID，不重复提交已受理的删除请求。
服务器仍存在或查询失败时保持当前阶段；确认不存在（404）后记录 `NovaServerDeleted`，才开始删除端口转发。

`DELETING_FORWARDINGS` 通过 `deleteForwardings()` 处理全部系统及用户转发，与单个删除共用逐条删除流程。
每条转发先持久化 `ForwardingDeletionRequested`，再通过 `requestDeletion()` 提交删除或查询状态。
删除返回 `DELETED` 后清理该内部端口的专属安全组规则，两步完成才记录 `ForwardingDeleted`；
`IN_PROGRESS` 表示已受理，随后通过 `get` 等待最终状态。全部转发完成后记录 `InstanceForwardingsDeleted`，才推进到网络清理。
查询仍在删除中的 claim 不重复发送删除命令，查到需要删除或重试的 claim 时只对这些记录再次提交。
查询异常或未确认的结果保持当前阶段，不能作为删除成功处理。

`DELETING_NETWORK` 按操作标识删除子端口浮动 IP，再删除 Trunk；先完成全部子端口、父端口的删除，
再统一删除对应的 QoS，任一端口删除失败都不会进入 QoS 删除。响应丢失或已解除关联的实例浮动 IP 也能找到并释放。
端口删除同时使用已持久化的父子端口 ID 和按项目、操作标记查询的结果，按 ID 去重后删除；
已记录端口查询返回 404 时视为不存在，其他查询错误或项目、标记不匹配时停止当前阶段。
正常删除和重启恢复都携带已持久化的端口 ID，列表查询为空不会跳过仍能按 ID 查到的端口。
网络清理完成后持久化 `InstanceNetworkDeleted`，再在 `DELETING_SECURITY_GROUP` 按项目和操作标识清理专属安全组及规则，
包括创建成功但响应未持久化的安全组。

端口转发按 `cloud.instance.poll.initial-delay` 查询进行中的删除；删除失败和其他删除阶段按 `cloud.instance.poll.max-delay` 检查、重试当前阶段，不设置删除等待超时。
普通进度查询不产生事件；端口转发逐条持久化删除意图和结果，阶段完成后记录相应完成事件，Nova 删除受理另由 `NovaDeletionAccepted` 记录。
已受理的删除在重启后继续，端口转发由 `InstanceWorkflowActor` 恢复并复用逐条删除流程；
其他阶段由 `InstanceRecovery` 按当前持久化阶段独立查询、删除和重试。
恢复 Nova 阶段时，已有 `NovaDeletionAccepted` 就只查询；尚未记录受理则先查询，存在才提交删除。
恢复端口转发阶段时先查询各 claim 状态，再决定等待或提交删除；其他阶段只重做该阶段尚未确认完成的幂等操作。
每个恢复阶段的完成事件持久化后，恢复流程进入下一删除阶段，不回到已经完成的阶段。

宿主机资源预留不设置 TTL，也不自动过期释放。创建中、重启后及创建失败后均保留预留资源，
成功时通过确认命令转为分配；实例删除先在 `CANCELLING_RESERVATION` 取消预留，
确认成功后持久化 `InstanceReservationCancelled`，进入 `RELEASING_ALLOCATION`，通过 `HypervisorPlacement.release()`
发送 `ReleaseResources(instanceId)` 释放已确认分配。释放成功才持久化 `InstanceDeleted` 并进入 `DELETED`。
释放根据宿主机实际保存的 allocation 处理，覆盖宿主机已确认但实例工作流未收到确认回复的情况；
`ResourcesReleased` 使 Actor 移除分配，并由投影异步扣减数据库中的已用资源及实例数量。
取消或释放结果未确认时保留各自阶段，后续只重试该操作；删除恢复独立执行同样顺序。
预留或分配已经不存在时返回成功，重试和重启恢复不会重复扣减资源。
已结束的预留结果（包括 `ReservationRejected` 记录的拒绝终态）仍按 `cloud.reservation.outcome-retention` 定期清理，
该清理不影响进行中的预留和分配。拒绝记录只保存预留 ID 和终态时间，不增加资源占用；
对应投影只推进 `resource_version`，不修改容量、预留或已用计数。
用户级项目、Share 和网络由多个实例复用，实例删除不会删除这些资源。
查询接口及 `instance_view` 投影展示当前具体删除阶段，保留已知资源 ID 和原创建失败原因供排查。
删除接口等待受理超时返回 `11719`，未能确认受理返回 `11720`；这些错误不等于删除已撤销，应先查询实例状态。

## 返回值

Actor 持久化受理请求后，接口即返回 HTTP `202 Accepted`；项目、Share、用户网络准备以及
实例安全组、网络、端口转发和 Nova 创建都在工作流中异步执行。等待 Actor 受理期间使用 Spring MVC 异步响应。
`INSTANCE_CREATE_REQUEST_TIMEOUT` 默认为 30 秒，只需覆盖 Actor 受理等待时间
（`cloud.ask-timeout` 默认 3 秒），不再覆盖依赖准备时间。
受理后的依赖准备失败通过 `GET /api/v1/instances/{instanceId}`、`InstanceService.get(instanceId)` 和 `instance_view` 的
`phase`、`failureCode` / `failure_code`、`failureMessage` / `failure_message` 查询，已创建的用户级资源继续保留。
响应沿用全局 `RespWrapper`，`result` 包含：

```json
{
  "code": 200,
  "result": {
    "instanceId": "生成的实例 UUID",
    "requestId": "生成的请求 UUID",
    "username": "clouduser",
    "password": "自动生成的登录密码",
    "jupyterToken": "自动生成的 Jupyter token"
  }
}
```

密码和 token 使用 `SecureRandom` 独立生成，返回时并不表示实例已处于 ACTIVE 状态。
参数校验和业务错误通过现有国际化异常处理器返回；创建请求确认超时表示受理结果未确定。
ask 和底层 HTTP 的通信超时继续保留，不作为实例工作流的整体截止时间，也不会自动释放宿主机预留。

## 查询用户实例

用户实例查询直接读取数据库 `instance_view`，按路径中的 `userId` 限定范围。

| 接口 | 参数与结果 |
| --- | --- |
| `GET /api/v1/users/{userId}/instances` | 可选 `regionId`、`phase`，返回实例列表 |
| `GET /api/v1/users/{userId}/instances/{instanceId}` | 返回该用户的单个实例；记录不存在或不属于该用户时返回业务 `code = 404` |

`phase` 使用 `InstancePhase` 的枚举名称，例如 `ACTIVE`、`FAILED`、`DELETED`，大小写须一致。
省略 `phase` 时包含所有阶段，包括已删除的记录。列表按 `created_at DESC, instance_id ASC` 排序，
一次返回符合条件的全部记录，无匹配项时 `result` 为 `[]`。

响应沿用 `RespWrapper`，字段按 `instance_view` 列名转为 camelCase，包含名称、描述、用户、区域、
资源规格、实例阶段、已投影的资源 ID、限速值、失败信息和 `workflowVersion`。
`qosUpdate`、`serverOperation` 是 JSON 对象或 `null`；端口转发通过独立的转发查询接口读取。
`createdAt`、`updatedAt` 使用带偏移量的时间格式；默认 JDBC 连接时区为 `Asia/Shanghai`，返回 `+08:00`。
以下仅展示列表中的部分字段：

```json
{
  "code": 200,
  "result": [
    {
      "instanceId": "实例 UUID",
      "userId": "test-user-003",
      "name": "test-instance-003",
      "regionId": "Test-SH-01",
      "phase": "ACTIVE",
      "qosUpdate": null,
      "createdAt": "2026-11-17T10:00:00+08:00",
      "updatedAt": "2026-11-17T10:01:00+08:00"
    }
  ]
}
```

此查询读取已完成的投影，创建或变更接口返回 HTTP `202` 后，新记录和新状态可能尚未可见。
接口不触发 Actor 查询，也不额外读取 `port_forwarding`，不会返回密码、Jupyter token 或 Share 访问凭据。
`GET /api/v1/instances/{instanceId}` 继续提供 Actor 当前状态及已确认的端口转发映射。

## 查询端口转发

`GET /api/v1/instances/{instanceId}/forwardings` 直接查询 `port_forwarding`，不调用 Actor。
返回该实例的全部 `SYSTEM`、`USER` 转发记录，包括创建中、失败和已删除的记录，
按 `createdAt DESC, floatingIpId ASC, claimId ASC` 排序；没有已投影的匹配记录时返回 `[]`。
响应字段按表结构平铺，包含 `claimId`、`floatingIpId`、`kind`、`internalPort`、`externalPort`、
`phase`、`failureMessage`、TCP/UDP 规则 ID、`resourceVersion` 和时间字段。
`claimId` 对应创建或删除接口返回的 `operationId`，也用于删除接口的 `claimId` 路径参数。
同一 claim 在不同候选浮动 IP 上的尝试按 `(floatingIpId, claimId)` 分别返回。
该查询读取异步投影，HTTP `202` 返回后可能暂时没有记录；`phase` 表示 FIP 规则状态，
实例安全组的放行或清理步骤由实例 Actor 继续执行，FIP 的 `READY`、`DELETED` 不代表这些步骤已完成。

创建后轮询 `GET /api/v1/instances/{instanceId}`。接口查询 Actor 的当前状态，返回创建阶段、
失败信息、专属 `osSecurityGroupId` 及 `forwardings` 列表，不包含密码、Jupyter token 或 Share 访问凭据。
不存在的实例按全局响应约定返回业务 `code = 404`。
以下仅展示相关字段，地址和端口为示例：

```json
{
  "code": 200,
  "result": {
    "instanceId": "实例 UUID",
    "phase": "ACTIVE",
    "osSecurityGroupId": "实例专属安全组 UUID",
    "network": {
      "osParentPortId": "父端口 UUID",
      "parentQos": {
        "osPolicyId": "父端口 QoS policy UUID",
        "osIngressRuleId": "父端口入站带宽规则 UUID",
        "osEgressRuleId": "父端口出站带宽规则 UUID"
      },
      "osSubPortId": "子端口 UUID",
      "subQos": {
        "osPolicyId": "子端口 QoS policy UUID",
        "osIngressRuleId": "子端口入站带宽规则 UUID",
        "osEgressRuleId": "子端口出站带宽规则 UUID"
      }
    },
    "forwardings": [
      {"internalPort": 22, "floatingIp": "203.0.113.10", "domain": "gateway.example.com", "externalPort": 10000, "kind": "SYSTEM"},
      {"internalPort": 8888, "floatingIp": "203.0.113.10", "domain": "gateway.example.com", "externalPort": 10001, "kind": "SYSTEM"}
    ]
  }
}
```

实际每条映射还包含 `claimId`、`floatingIpId`、`osFloatingIpId`、`osTcpForwardingId` 和 `osUdpForwardingId`。
`floatingIpId` 是本系统登记的浮动 IP ID，用于 Actor 路由；`osFloatingIpId` 是对应的 OpenStack 资源 ID。
`domain` 保存该转发创建时浮动 IP 的可选域名；未配置时为空，系统和用户转发均保存并返回此字段。
SSH 使用内部端口为 22 的映射：`ssh -p 10000 <创建响应中的 username>@203.0.113.10`。
JupyterLab 使用内部端口为 8888 的映射：`http://203.0.113.10:10001/lab?token=<创建响应中的 jupyterToken>`。
创建过程中列表可能为空或仅有一条记录；转发就绪先于 Nova 启动。
`ACTIVE` 表示 SSH 转发已通过连通性检查且资源预留已确认，仍不代表 cloud-init 全部完成或 JupyterLab 已就绪。

## 实例重启与重建

| 接口 | 请求内容 | 操作 |
| --- | --- | --- |
| `POST /api/v1/instances/{instanceId}/reboot` | 无请求体 | 对已有 Nova 实例执行 `HARD` 重启 |
| `POST /api/v1/instances/{instanceId}/rebuild` | `{"imageId":"demo-image-debian"}` | 使用指定镜像原地重建系统盘 |

`imageId` 是本地镜像业务 ID，必填且最多 255 个字符；镜像须已启用、有 OpenStack 镜像 ID，
并与实例属于同一区域。重建沿用原 Nova 实例、网络、端口转发、共享存储、资源分配和已保存的
cloud-init 数据，不重新调度或创建实例。成功后更新 Actor 和读模型中的 `osImageId`。
[Nova API](https://docs.openstack.org/api-ref/compute/#rebuild-server-rebuild-action) 定义了系统盘重建及异步状态语义。

首次操作要求实例为 `ACTIVE`，且没有进行中的用户转发、QoS、重启或重建操作。
重启或重建失败后，可在 `FAILED` 状态显式重试这两类操作或删除实例；创建失败的实例仍沿用原处理流程。
调用 OpenStack 前还会检查实例项目归属，要求远端没有正在执行的任务，状态为 `ACTIVE` 或 `ERROR`。
进行中的相同操作（重建时目标镜像也相同）返回已有 `operationId`，不重复提交；其他管理操作和删除等待其完成。

意图持久化后返回 HTTP `202` 和 `{"code":200,"result":{"operationId":"..."}}`。
通过实例查询或用户实例读模型的 `serverOperation` 查看 `operationId`、`type`（`REBOOT` / `REBUILD`）、
`phase`（`SUBMITTING` / `RUNNING` / `SUCCEEDED` / `FAILED`）、目标 `osImageId`、请求时间和失败信息。
实例生命周期依次为 `REBOOTING` 或 `REBUILDING` → `ACTIVE`；失败进入 `FAILED`。
Nova 恢复 `ACTIVE`、没有在途任务且原 SSH 映射连通后才完成；重建还会确认目标镜像。
失败代码使用 `NOVA_REBOOT_...`、`NOVA_REBUILD_...` 前缀。接口拒绝、受理超时和受理未确认沿用管理操作错误码。

Actor 恢复后只查询，不重新提交重启或重建。已记录 Nova 受理的操作继续等待完成；
受理响应丢失时，按对应 Nova 任务状态或已变化的目标镜像确认后继续。
无法确认是否受理时记录 `NOVA_REBOOT_UNCONFIRMED` 或 `NOVA_REBUILD_UNCONFIRMED`，等待外部核实后重试。
这些操作使用独立的操作 ID，不修改创建时的 `novaOperationId`，保留后续资源查找所用的标记。

已有数据库先执行 `sql/migrations/add-instance-server-operation.sql`；新库使用更新后的 `sql/read-model.sql`。

## 实例端口转发与 QoS 管理

实例处于 `ACTIVE` 且没有进行中的管理操作时，才接受新的用户转发创建、删除和 QoS 修改。
管理操作拥有独立状态，不改变实例的 `ACTIVE` 生命周期阶段；转发或 QoS 操作之间串行执行。
接口持久化操作意图后返回 HTTP `202`，响应为 `{"code":200,"result":{"operationId":"..."}}`，
不等待 OpenStack 完成。业务拒绝使用 `11722`，受理等待超时和结果未确认分别为 `11723`、`11724`；
结果不确定时先查询实例状态，再决定是否重试。接口示例见 `http/instances.http`。

| 接口 | 请求内容 | `operationId` |
| --- | --- | --- |
| `POST /api/v1/instances/{instanceId}/forwardings` | `{"internalPort":8080}`，端口范围 `1–65535` | 新建用户转发的 `claimId` |
| `DELETE /api/v1/instances/{instanceId}/forwardings/{claimId}` | 无请求体，仅允许 `USER` | 被删除用户转发的 `claimId` |
| `PUT /api/v1/instances/{instanceId}/qos` | 可选的 `parentPort`、`subPort`，至少指定一个 | 本次 QoS 操作的独立 ID |

用户转发复用该实例系统 SSH 映射的浮动 IP，由 FloatingIpActor 在位图空闲范围内按随机起点分配公网端口，
同时建立 TCP、UDP 映射；请求不接收类型、浮动 IP 或公网端口。内部端口已被系统转发或尚未删除的
用户转发占用时拒绝创建。FIP claim 就绪后，工作流校验转发结果，补齐该内部端口的 TCP/UDP IPv4 入站规则，再将用户转发标记为 `READY`。
通过 `GET /api/v1/instances/{instanceId}/forwardings` 按 `claimId` 查找转发记录，
可见 FIP 的 `CREATING`、`READY`、`FAILED`、`DELETING`、`DELETE_FAILED`、`DELETED` 及资源和失败信息。
顶层 `forwardings` 选取 `resources` 非空的转发，由 `InstanceForwardingView` 将状态中的标识、内部端口、类型与资源字段组装为平铺响应。
实例查询在顶层返回 `qosUpdate`；实例 Actor 保存当前转发状态，用于恢复尚未完成的操作；
已删除转发的历史通过 `port_forwarding` 查询，实例状态不保留删除历史缓存。

用户转发删除先等待 FIP claim 返回 `DELETED`，再删除实例安全组中该端口的专属规则，
两步完成后记录 `ForwardingDeleted`，并从实例的 `forwardingStates` 中移除该 claim 及其资源。
实例为 `ACTIVE` 时，重复删除同一 `DELETING` 记录或当前状态中不存在的 `claimId` 均幂等返回 HTTP `202`；
不存在的 `claimId` 包括已经删除和从未属于该实例的标识，此时不调用 FIP Actor 或 OpenStack。
创建失败、删除失败均保留实例 `ACTIVE` 和操作记录，不自动补偿；失败的用户转发可通过删除接口清理，
`DELETE_FAILED` 可再次调用删除接口重试。删除整个实例仍统一清理全部 `SYSTEM`、`USER` 转发和专属安全组。

QoS 修改示例：

```json
{
  "parentPort": {"maxKbps": 102400, "maxBurstKbits": 10240},
  "subPort": {"maxKbps": 51200, "maxBurstKbits": 5120}
}
```

省略或传 `null` 的端口保持不变；每个指定端口的两个字段均必填且不得为负。
`maxKbps` 单位为 kbit/s，`maxBurstKbits` 单位为 kbit，同一端口的 ingress 和 egress 使用相同参数。
`0 / 0` 表示删除该端口的限速规则、保留专属 QoS policy；重新设置正速率时补齐所需规则。
操作只修改已有实例端口绑定的专属 policy，不重建端口或替换 policy。
通过实例查询的顶层 `qosUpdate` 查看 `UPDATING`、`READY`、`FAILED`、请求参数和失败信息；
成功后 `network.parentQos`、`network.subQos` 更新实际规则 ID 及 `bandwidthLimit`。
失败不回滚已经修改的远端规则，实例仍为 `ACTIVE`；需使用与失败操作相同的参数重试，完成后才能提交其他限速值。

管理意图分别使用 `ForwardingCreationRequested`、`ForwardingDeletionRequested`、
`InstanceQosUpdateRequested` 持久化，成功和失败使用各自明确的结果事件。
重启后独立恢复已持久化的创建、删除和 QoS 修改意图：用户转发先查询原 claim 再补充未完成步骤，
QoS 按保存的目标参数检查并修复部分修改，不重新分配操作标识；已经失败的管理操作等待外部处理。

## 用户资源清理

清理按网络、Share、项目阶段推进，当前删除 UserActor 管理的全部用户网络和 `SYSTEM` / `USER` Share，暂时保留用户项目。
它不删除实例或平台预置的 LOCAL 网络，也不代替实例及其附着资源的删除流程。

`UserActor.CLEANUP_PROJECT_DELETION_ENABLED` 暂设为 `false`：正常清理和重启恢复均跳过项目删除，
保留项目的 Actor 状态和投影记录，清理完成后可以继续复用项目。`COMPLETED` 时 `remainingProjects`
仍返回保留的项目数量。以后将该常量改为 `true`，再次调用清理 API 即可执行保留的项目删除逻辑。

| 接口 | 请求 | 成功响应 |
| --- | --- | --- |
| `POST /api/v1/users/{userId}/cleanup` | 无请求体 | 清理意图持久化后返回 HTTP `202` 和当前清理状态 |
| `GET /api/v1/users/{userId}/cleanup` | 无请求体 | HTTP `200`，查询 Actor 中已持久化的清理阶段、当前资源、剩余资源数量和失败信息 |

成功响应的 `result` 包含 `userId`、`phase`、`currentResourceId`、`remainingNetworks`、
`remainingShares`、`remainingProjects` 和 `failureMessage`。
尚未发起清理时返回 `IDLE`。任一项目、Share 或网络仍处于 `CREATING` 时，POST 拒绝受理，
不记录清理意图；资源均为 `READY` 或 `FAILED` 时才允许开始。清理进行中及清理 `FAILED` 时，
`CreateProject`、`CreateShare`、`CreateNetwork` 均被拒绝，直到清理完成后才恢复创建。
拒绝沿用全局业务错误包装，以 HTTP `200` 返回业务 `code = 409`。

```text
IDLE → NETWORKS → SHARES → PROJECTS → COMPLETED
                 任一步失败 → FAILED
FAILED ──外部 POST 重试──→ 继续清理剩余资源
```

受理时持久化 `UserCleanupStarted`。每个资源确认在 OpenStack 中不存在后，分别持久化
`UserNetworkDeleted(regionId)`、`UserShareDeleted(shareId)` 或 `UserProjectDeleted(clusterId)`，
从 Actor 状态移除对应资源，再继续下一个。网络阶段全部完成后记录 `UserNetworksCleaned`，
Share 阶段全部完成后记录 `UserSharesCleaned`，项目阶段完成后记录 `UserCleanupCompleted`。
项目删除暂时关闭时，项目阶段直接完成，不记录 `UserProjectDeleted`。
网络清理包含该用户网络对应的路由和接口；Share 清理逐个处理系统 Share 和所有用户 Share。
网络清理允许 `network:dhcp` 和 `network:distributed` 系统端口存在，由 Neutron 随子网和网络删除清理。
路由接口仍须匹配该项目、路由、网络和子网，解绑后再删除路由；记录的接口 ID 已不存在时，
按这些关联重新查找并校验接口。其他端口仍阻止清理，失败信息包含端口 ID 和 `device_owner`。

清理过程中重复 POST 返回当前进度，不同时启动另一轮。调用失败记录 `UserCleanupFailed`，
保留失败资源和剩余资源；已确认删除的资源不恢复，也不执行自动补偿。
`FAILED` 不自动重试，外部再次 POST 才继续清理剩余资源。
若远端资源仍被实例或其他资源占用，OpenStack 的 in-use 错误会使清理失败；解除占用后再重试。

应用重启后按已持久化阶段进入独立恢复流程，先查询资源，再决定等待、继续删除或确认不存在，
不重新执行已确认完成的阶段；`FAILED` 仍等待外部重试。
清理不设置业务截止时间，HTTP 仅等待 Actor 受理，不等待整个清理完成。
ask 和底层 HTTP 的通信超时不代表清理已撤销，调用方应通过 GET 确认当前进度。

## 用户资源读模型

`UserProjection` 同时处理项目、Share 和网络事件。用户从事件持久化 ID `User|<userId>`
解析；Share 按 `(user_id, share_id)` 唯一保存，网络和路由仍按 `(user_id, region_id)` 唯一保存。
`user_region_share.share_kind` 保存 `SYSTEM` 或 `USER`，`share_type` 继续保存 Manila 类型。
生成列 `system_region_id` 仅对 `SYSTEM` 返回区域 ID，`USER` 为 NULL；唯一键
`(user_id, system_region_id)` 限制每个用户在每个区域只能有一个系统 Share，同时允许多个用户 Share。
若不同逻辑 Share ID 冲突于系统 Share 唯一键，投影不会覆盖已有 Share，而是报错并回滚当前事务与 offset。

| 表 | 事件 | 保存内容 |
| --- | --- | --- |
| `user_cluster_project` | `ProjectCreationRequested`、`ProjectBecameReady`、`ProjectCreationFailed` | 项目名称、OpenStack 项目 ID、状态和失败原因 |
| `user_region_share` | `ShareCreationRequested`、`ShareCreated`、`ShareExportBecameReady`、`ShareAccessCreated`、`ShareBecameReady`、`ShareCreationFailed` | Share 应用类型、规格、名称、描述、访问用户名、Manila ID、导出路径、授权规则 ID、access key、状态和失败原因 |
| `user_region_network` | `NetworkCreationRequested`、`NetworkBecameReady`、`NetworkCreationFailed` | 请求的网段、DNS、网关，以及项目、LOCAL 网络、用户网络和子网 ID、状态和失败原因 |
| `user_region_router` | `NetworkCreationRequested`、`NetworkBecameReady`、`NetworkCreationFailed` | 路由、接口端口及关联网络/子网 ID，以及所属网络流程的状态和失败原因 |

项目、网络和 Share 都投影 `CREATING`、`READY`、`FAILED` 三种状态。项目创建时保存名称，
就绪时保存 OpenStack 项目 ID，失败时保存失败原因；使用项目映射时应筛选 `phase = 'READY'`。
项目和网络的终态事件携带原始名称或网络规格、项目 ID 等投影所需的数据，可以独立写入读模型。
Share 的每个创建步骤仍使用独立事件，携带请求规格和当前已确认的资源 ID，能够独立 upsert。
`ShareCreated`、`ShareExportBecameReady`、`ShareAccessCreated` 逐步补齐字段，投影状态始终为 `CREATING`；
`ShareBecameReady` 和 `ShareCreationFailed` 才写入对应终态。
系统 Share 失败后重试的 `ShareCreationRequested` 会将投影重置为 `CREATING`，清空 access key 和失败信息，
保留逻辑 Share ID 及已确认的 Manila、导出位置和授权规则字段，与 Actor 的重试状态一致。
名称、描述和 `access_to` 通过 `UserShareNaming` 的静态方法传入事件所属用户 ID、Share 应用类型和逻辑 ID 生成，
再作为独立字符串参数传给 Mapper，与创建和授权流程保持一致。
等待查询没有改变状态时不写事件；查询次数不作为持久化状态或投影字段。

router 由用户网络流程创建，没有单独的路由事件。每个网络事件在同一个 JDBC 事务内
更新网络和路由两条记录，并随投影偏移量一起提交。创建事件写入请求规格和 `CREATING`
状态，就绪事件一次补齐所有资源 ID 并标记 `READY`，失败事件记录 `FAILED` 和失败原因。
就绪前对应的 OpenStack 资源 ID 为空。
router 表中的 `phase` 表示整个用户网络流程，不表示 Neutron 路由运行状态。
网络表的网关和 DNS 来自请求规格；若网关由 Neutron 自动分配，投影中的 `gateway` 保持为空。

清理确认事件物理删除相应的读模型记录：

| 事件 | 删除范围 |
| --- | --- |
| `UserNetworkDeleted` | 按 `(user_id, region_id)` 删除 `user_region_router` 和 `user_region_network` |
| `UserShareDeleted` | 按 `(user_id, share_id)` 删除 `user_region_share` |
| `UserProjectDeleted` | 按 `(user_id, cluster_id)` 删除 `user_cluster_project` |

每条 DELETE 均要求 `resource_version` 小于删除事件的日志序号，避免旧删除事件移除更新的记录。
同一 User 的日志按序消费；网络、路由的删除与投影 offset 在同一 JDBC 事务提交，
Share、项目同样沿用当前 exactly-once 投影事务。
清理阶段保存在 Actor 中，API 直接查询 Actor；资源投影会在消费到删除事件后移除对应记录。

项目和三个区域资源表均使用事件日志序号 `resource_version` 防止旧事件或重复事件覆盖最新状态。
Project、网络和 Share 的创建、就绪、失败事件通过 upsert 写入资源状态；即使就绪或失败事件先于创建事件
到达，也能插入完整记录，后到的创建事件不会清空资源 ID 或回退状态。
`created_at` 取已投影事件的最早时间，`updated_at` 取最新版本事件时间；它们不是远端资源的
创建和更新时间。`share_id` 是用户范围内的逻辑 Share ID，`os_share_id` 才是 Manila ID，
`size_gib` 的单位为 GiB。`ShareBecameReady` 将访问凭据写入 `access_key`，其余阶段该字段为 NULL；
该字段同样受 `resource_version` 保护，旧事件不会清空已就绪的 access key。

执行 `sql/read-model.sql` 初始化读模型表。`user-resources` 投影消费 User 事件更新读模型，
查询结果在投影追上事件日志后完整可见。
已有 `user_region_share` 表在升级前执行 `sql/migrations/add-share-kind.sql`，增加应用类型、修改主键及唯一约束。
历史记录归为 `SYSTEM`，已有资源、凭据和投影偏移量保留；已执行新建表 SQL 的数据库无需再次执行该迁移。
已有 `instance_view` 表需执行 `sql/migrations/add-instance-subport-floating-ip.sql` 增加子端口浮动 IP 字段。
QoS 资源记录对应 `os_parent_qos_policy_id`、`os_parent_qos_ingress_rule_id`、`os_parent_qos_egress_rule_id`
及同名 `os_sub_qos_*` 字段；已有表先使用 `sql/migrations/add-instance-port-qos.sql` 增加这六个字段，
再执行 `sql/migrations/prefix-openstack-reference-fields.sql` 统一改名。

## 浮动 IP 读模型

`FloatingIpProjection` 消费 `FloatingIpActor` 的事件，投影名为 `floating-ip`，
与其他投影一起由 `cloud.projection.enabled` 和 `cloud.projection.slice-ranges` 控制。
投影按事件中的内部 `floatingIpId` 匹配 `floating_ip.id`，直接更新已登记记录的 `status`、`port_mask`
和 `resource_version`。`status` 是非空 `VARCHAR(16)`，登记时默认为 `DISABLED`；实体和 Actor 共用 `FloatingIpState.Status` 枚举，JSON 使用枚举名称。
`FloatingIpEnabled`、`FloatingIpDisabled` 分别写入 `ENABLED`、`DISABLED`。
`FloatingIpDestructionStarted` 写入 `DESTROYING`，拒绝或异常产生的 `FloatingIpDestructionFailed` 恢复为 `DISABLED`，两者保留当前端口位图。
`FloatingIpDestroyed` 写入 `DESTROYED` 并清空端口位图。
销毁后的登记记录作为投影墓碑保留，普通查询不返回这些记录；`resource_version` 阻止旧事件和重复事件覆盖销毁状态。
Actor 保证 `DESTROYED` 为终态，`updateState` 只按事件版本更新，不再额外判断当前 `status`。
名称、描述、外部网络、地址和可选域名 `domain` 等字段仍由登记流程维护；域名最多 255 字符，省略、`null` 或空白均保存为 `NULL`。

`FloatingIpEnabled`、`FloatingIpDisabled`、`ForwardingPortAllocated` 和 `ForwardingDeleted`
携带事件生效后的完整端口位图 `usedPortMask`，投影将该总位图编码为标准 Base64 字符串，
写入 `floating_ip.port_mask` 的非空 `TEXT` 字段。空位图保存为空字符串 `''`，登记流程使用相同编码。
`FloatingIpEnabled` 只包含 `identity` 和合并后的完整 `usedPortMask`，恢复和投影使用事件中的合并结果；
状态中不再保存占用来源或单独的导入位图。
分配公网端口时置位，TCP/UDP 清理完成、Actor 释放端口后清位；失败或清理未完成时继续保留占用。
claim 已不存在时，`ForwardingDeleted` 携带当前位图，投影仍更新版本而不释放任何端口。
禁用后的启用请求将服务端查询到的占用并入总位图，同步前由人工确认无在途请求；启用和禁用均不释放任何端口。
位图第 0 位对应公网端口 10000。Actor 内部仍使用 `BitSet`，事件中的 `usedPortMask` 仍为
`BitSet.toByteArray()` 生成的 `byte[]`；Base64 转换只用于读模型存储，读取字符串后可先 Base64 解码，
再通过 `BitSet.valueOf()` 恢复位图。
分配和释放事件同时携带当时的枚举 `status`，与位图共用版本保护；禁用期间的清理不会重新启用浮动 IP。

同一投影同时将端口转发生命周期写入 `port_forwarding`，主键为
`(floating_ip_id, claim_id)`，同一 claim 在不同候选浮动 IP 上的尝试分别保存。
`floating_ip_id` 与 FIP Actor 的实体 key 一致；`region_id` 和 `os_floating_ip_id` 保留为资源信息及查询字段。
记录包含用户、实例、项目、`kind`（`SYSTEM`／`USER`）、目标 Neutron 端口、固定 IP、内部/公网端口、浮动 IP 地址及可空域名 `domain`、
TCP/UDP 规则 ID、阶段、失败原因、版本和创建/更新/删除时间。

| 事件 | 端口转发记录 |
| --- | --- |
| `ForwardingCreationRequested` | 写入请求规格和创建时的浮动 IP 域名，阶段为 `CREATING` |
| `ForwardingPortAllocated` | 写入预留的公网端口 |
| `ForwardingBecameReady` | 写入 TCP/UDP 规则 ID，阶段为 `READY` |
| `ForwardingCreationFailed` | 阶段为 `FAILED`，记录失败原因 |
| `ForwardingDeletionStarted` | 阶段为 `DELETING` |
| `ForwardingDeletionFailed` | 阶段为 `DELETE_FAILED`，记录失败原因 |
| `ForwardingDeleted` | 阶段为 `DELETED`，记录删除时间 |

浮动 IP 销毁开始、失败和完成事件同时更新 Actor 状态和登记记录的 `status`，不修改端口转发历史。
删除后保留已知的映射参数和规则 ID，供查询历史；这些字段不表示仍然占用端口，端口分配以 Actor 位图为准。
删除尚未创建的 claim 也写入 `DELETED` 记录，请求规格字段及 `kind` 为空。
Actor 清理过期 `outcomes` 时不删除这些数据库记录；同一 claim ID 在保留期后重新创建时，较新的创建事件重置该记录。
实例关联的端口转发统一从 `port_forwarding` 按 `instance_id` 查询。

创建请求、就绪、失败和删除开始等事件不改变位图。claim 状态和已删除结果继续保存在 Actor 事件及快照中。
快照保存枚举 `status`、`identity`、`claims`、`outcomes` 和总位图 `usedPorts`，不再分别保存 `enabled`、`destroying`、`destroyed`，也不包含 `importedPortMask`。
端口位图事件、实体和读模型统一使用 `status`，不再传递或保存 `enabled`、`destroyed` 布尔字段。
启用时 `identity` 非空以及 `usedPorts` 非空由调用方保证；构造方法保留两张 Map 的 `Map.copyOf()` 和 `BitSet` 克隆，
`usedPorts()` 访问器同样返回克隆，防止外部修改内部状态。
恢复保留快照及启用事件中的总位图，再按分配和删除事件更新相应端口位，
不从 `claims` 重建整个位图，登记时导入的其他端口占用继续保留。
任意命令都能触发 Actor 的日志恢复；Actor 从事件和快照恢复身份、总位图及转发状态，
未进入销毁流程时，恢复完成后继续处理 `CREATING`、`DELETING`，禁用状态也不跳过这些已受理的操作；
`DELETE_FAILED` claim 等待外部删除命令重试。
`status=DESTROYING` 时恢复仅继续删除浮动 IP；`status=DESTROYED` 时不再执行 OpenStack 资源操作。
同步总位图时使用从当次查询的完整 OpenStack 规则列表提取的公网端口整数列表。

`resource_version` 默认是 0，随后保存最近一次启停、端口位图或销毁状态事件的日志序号，只有更新的事件才能覆盖状态和位图。
`port_forwarding.resource_version` 保存该 claim 最近消费的事件序号，重复或旧事件不会覆盖较新状态。
登记记录在发送 Actor 启用命令前已提交，销毁后仍保留；`updateState` 返回 0 时直接忽略旧事件和重复事件，不再二次查询版本。
`status`、位图、端口转发记录和 offset 在同一个 JDBC 事务中提交；转发更新事件缺少对应创建记录时，
投影仍报错而不推进 offset，避免丢失转发更新。投影不调用 Actor 或 OpenStack。
新库执行 `sql/floating-ips.sql`。仍使用布尔列的旧库执行 `sql/migrations/migrate-floating-ip-status.sql`：
优先将 `destroyed=true` 映射为 `DESTROYED`，其次将 `enabled=true` 映射为 `ENABLED`，其余映射为 `DISABLED`，然后删除两列。
该迁移要求旧表已有 `enabled` 和 `destroyed`；缺少时先分别执行旧迁移 `add-floating-ip-enabled.sql` 和 `add-floating-ip-destroyed.sql`。
旧布尔列不能表示销毁中状态；`DESTROYING` 由销毁开始事件投影，已有未完成的销毁会在 Actor 恢复后的完成或失败事件中继续更新状态。
缺少版本列时执行 `sql/migrations/add-floating-ip-projection-version.sql`。新建表或迁移至 `status` 后不要再执行增加布尔列的旧迁移。
当前状态与事件结构直接用于开发阶段，不提供历史事件兼容转换。
端口转发表随 `sql/read-model.sql` 初始化，也可单独执行 `sql/migrations/add-port-forwarding-projection.sql`。
本次实体 key 和端口转发表主键调整按开发阶段重新初始化数据，不转换旧事件、快照、投影进度或旧表记录。
已有端口转发表可执行 `sql/migrations/add-port-forwarding-kind.sql` 增加类型字段；脚本不回填未知的历史类型。
缺少域名字段时执行 `sql/migrations/add-port-forwarding-domain.sql`，已有记录的域名为空；删除转发时保留该记录的原域名。
已有 `floating-ip` 投影进度时，新增表不会自动补齐之前消费的事件；重建该读模型需要从创建事件开始重放。

## 实例读模型

`InstanceProjection` 将实例状态写入 `instance_view`；端口转发记录统一由 `FloatingIpProjection`
写入 `port_forwarding`，建表定义均位于 `sql/read-model.sql`。
`instance_view.os_security_group_id` 保存实例专属安全组 ID；本次状态和事件结构调整不提供历史数据兼容转换。
网络准备统一使用 `PREPARING_NETWORK`、`NetworkPreparationStarted` 和 `NetworkPrepared`，
不提供旧网络阶段、事件或快照的兼容转换。

创建事件写入名称、描述、用户、集群、区域、资源规格、请求的逻辑 Share ID、VLAN 和
`SCHEDULING` 状态，项目及父子网络/子网 ID 暂为空。
`HypervisorReservationStarted` 写入待预留的宿主机和预留 ID，并更新为 `RESERVING_RESOURCES`；
`HypervisorSelected` 确认同一目标且保持 `RESERVING_RESOURCES`，`DependencyPreparationStarted` 再更新为 `PREPARING_DEPENDENCIES`；
`DependenciesPrepared` 补齐项目 ID、实际复用的逻辑 Share ID、父子网络和子网 ID；
`SecurityGroupPreparationStarted` 更新安全组准备阶段，`SecurityGroupPrepared` 写入专属安全组 ID；
`NetworkPreparationStarted` 更新为 `PREPARING_NETWORK`，`NetworkPrepared` 一次写入父子端口、QoS、
子端口浮动 IP 和 Trunk 资源。`ForwardingPreparationStarted` 将实例投影置为 `PREPARING_FORWARDING`。
共享转发事件只更新实例投影版本和时间；转发详情及历史统一由 FIP 事件投影到 `port_forwarding`。
QoS 请求和失败事件写入 `qos_update` JSON，完成事件同时更新实际规则 ID 和
`parent_max_kbps`、`parent_max_burst_kbits`、`sub_max_kbps`、`sub_max_burst_kbits`。
网络准备完成时也写入父子端口对应限速值；这些管理事件不改变实例的 `ACTIVE` 阶段。
重启、重建的请求、Nova 受理、完成和失败事件写入 `server_operation` JSON，并同步实例生命周期阶段；
完成事件更新已确认的 `os_image_id` 和 Nova 状态，失败事件保存失败代码及原因。
新库使用 `sql/read-model.sql`，已有 `instance_view` 可执行
`sql/migrations/add-instance-network-management.sql` 增加两列 JSON 和四列限速值，不转换旧事件或快照。
该历史脚本中的 `user_forwardings` JSON 已停用，升级后可执行
`sql/migrations/remove-instance-user-forwardings.sql` 删除旧列；新库不再创建该列。
`InstanceDeletionStarted` 将实例投影置为 `DELETING_NOVA`；各资源清理完成事件分别推进到上表中的下一删除阶段。
`NovaDeletionAccepted` 保存服务器 ID 并保持 `DELETING_NOVA`，只有 `InstanceDeleted` 才将投影置为 `DELETED`。
删除阶段切换保留已有资源 ID 和原创建失败信息，便于查询进度及排查。
实例阶段更新和实例投影偏移量在同一个 JDBC 事务内提交。
`os_project_id`、`os_image_id`、`os_flavor_id`、网络、子网、端口及共享存储 ID
来自事件中的 OpenStack 资源，`hypervisor_type` 是应用中的宿主机类型 ID。
`memory_gb` 和 `disk_gb` 单位为 GB。
密码、Jupyter token 和共享存储访问凭据不写入此表。

每次状态更新以日志序号 `workflow_version` 判断新旧，忽略相同或更早的事件，
避免重放时覆盖较新的状态和资源信息。进入 `ACTIVE` 时清除失败信息。
项目、网络等资源的字段在相应资源就绪事件投影后填入，之前保持为空。
宿主机和预留 ID 在发送预留命令前即写入，是否已预留成功需结合当前阶段判断。
新增的预留阶段、事件和状态字段不提供旧事件或快照的兼容转换。
查询实例的就绪映射时，从 `port_forwarding` 按 `instance_id` 和 `phase = 'READY'` 筛选，
并结合实例阶段判断可用性；删除和失败记录仍保留在该表中。
读模型与事件投影进度一致，HTTP `202` 返回时记录及最终状态可能尚未可见。
原有 `instance_port_forwarding` 表可通过 `sql/migrations/remove-instance-port-forwarding.sql` 删除；
该脚本保留 `port_forwarding`、Actor 持久化数据和投影进度。

## 宿主机资源读模型

宿主机元数据和资源账目统一保存在 `hypervisor_host`，调度直接查询该表。
宿主机查询统一通过 `HypervisorHostRepo` 和 `HypervisorHostMapper`；调度候选复用
`HypervisorHostEntity` 映射，再转换为调度器的 `HypervisorCandidate`。
新库使用 `sql/hypervisors.sql`；已有主机表可执行
`sql/migrations/remove-hypervisor-availability-zone.sql` 删除已移除的可用区字段。
`hypervisor_status` 表示 Actor 的 `ACTIVE`、`DRAINING`、`MAINTENANCE` 状态；
原有 `status`、`state` 仍表示 OpenStack 服务状态。
总容量只保留 `vcpus`、`ram`、`disk`、`gpus`；占用只保留 `used_vcpus`、`used_ram`、
`used_disk`、`used_gpus`，包含维护时导入的基础占用和随后由本系统确认的分配。
`reserved_*` 表示实例创建中的临时预留，与 OpenStack 的宿主机保留资源不同；
后者已包含在同步的基础占用中。`available_*` 是生成列，等于总容量减去 `used_*` 和 `reserved_*`。
内存和磁盘统一使用 GB，主机查询返回这些字段及 `resourceVersion`、`updatedAt`。

| 操作 | `reserved_*` | `used_*` | `running_vms` |
| --- | --- | --- | --- |
| 预留 | 增加请求资源 | 不变 | 不变 |
| 确认 | 扣减预留 | 增加确认分配 | +1 |
| 取消 | 扣减预留 | 不变 | 不变 |
| 释放 | 不变 | 扣减该实例的分配，保留基础占用 | -1 |

维护操作按以下顺序执行：

1. 人工同步 OpenStack 数据，将 CPU、内存、磁盘的总量和占用写入维护中的 `hypervisor_host`。
2. 人工修改主机类型和 GPU 数量，直接保存到主机表。
3. 调用 `PUT /api/v1/hypervisor-hosts/{id}/enable`，服务读取主机记录，先发送 `UpdateCapacity`，
   收到成功回复后再发送 `EnableHypervisor`。

人工同步和修改 GPU 数量不发送 Actor 更新命令。Actor 仅在 `MAINTENANCE` 且没有预留、分配时
接受 `UpdateCapacity`，通过 `CapacityUpdated` 一并持久化总容量和基础占用，投影更新主机资源及版本。
基础占用取提交时主机表的 `used_*`，保存为 Actor 的 `baselineUsage`；重启从事件或快照恢复，
不会重新查询 OpenStack 并重复叠加。内存总量和占用均按 MB 除以 1024 换算为 GB，使用整数除法。
Nova 的主机接口不提供 GPU 容量、占用，同步时保留主机表中的 GPU 数值，GPU 数量由人工配置。

进入维护前必须释放全部预留及分配；同步前还需确认没有在途的 Nova 创建、删除请求。
Actor 的计数为空不能证明外部操作已经结束。
区域同步在获取数据后通过同一个数据库事务写入，写入失败时整体回滚；非维护状态的主机跳过同步，
上游列表中缺失的主机不会自动删除。新主机也按上述顺序同步、配置并启用。
候选查询按区域、宿主机类型、可选 hostname、状态和可用资源过滤，最终仍由 Actor 原子校验资源并预留。

`resource_version` 保存最近一次资源或状态事件的日志序号，同一行内的容量、资源增减、
维护状态和维护计划共用该版本保护，重复或旧事件不会重复计数或覆盖新状态。
`running_vms` 只统计 Actor 已确认且未释放的实例分配，与资源增减在同一条 SQL 中更新，
并与 projection offset 在同一事务中提交。`CapacityUpdated` 将预留计数和 `running_vms` 置零，
此时 Actor 已保证没有预留和分配；人工同步、修改 GPU 数量及单独更新状态不修改这些计数。
分配明细保留在 Actor 的 `allocations` 中，用于释放、幂等判断和重启恢复。
主机必须先登记；若更新事件对应的主机记录不存在，投影报错并停止推进 offset，避免丢失资源账目。

新库执行 `sql/hypervisors.sql` 创建包含资源字段的主机表，再执行 `sql/read-model.sql` 创建实例、用户等读模型表。
本次按开发阶段直接调整表和 Actor 状态，不提供旧资源计数、事件或快照的兼容转换。
已有开发数据需在停止应用后重建宿主机资源读模型，并清理对应 Hypervisor 的旧日志、快照及投影进度；
恢复应用后重新同步主机、配置 GPU 并启用，不能只重置表里的计数而保留旧 Actor 状态。
`CREATE TABLE IF NOT EXISTS` 不会修改已有表结构，旧 `hypervisor_resource` 表不再使用。
原有 `instance_allocation` 表可通过 `sql/migrations/remove-instance-allocation.sql` 删除；
该脚本只删除旧表，不重置主机计数、事件版本或投影进度。
