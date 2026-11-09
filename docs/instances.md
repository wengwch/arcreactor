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

`hostname` 可选，使用宿主机记录 `hypervisor_host.hostname` 的值。省略、传 `null` 或空白字符串时自动调度；
指定时去除首尾空白并按大小写精确匹配，只在匹配的主机上预留资源。
主机仍需满足请求的区域、宿主机类型、ACTIVE 状态及 CPU、内存、磁盘、GPU 资源要求；
指定主机不存在、不符合条件或预留被拒绝时，实例创建以 `NO_VALID_HOST` 失败。
hostname 约束保存在创建事件的 `InstanceSpec` 中，重启恢复调度时继续生效。

服务端从规格读取 vCPU、内存（GB）、磁盘（GB）和 GPU 数量，使用镜像和规格对应的 OpenStack ID
提交 Actor 创建流程。调度候选宿主机限定在所选区域和宿主机类型内，
并且必须处于 ACTIVE 状态且有足够资源；指定 hostname 时，数据库在候选数量限制之前完成筛选，
调度器的 `HostnameFilter` 再校验匹配结果。
磁盘需求取规格的 `disk`，以 `diskGb` 随创建请求和调度请求传递；宿主机在维护状态下人工同步
OpenStack 数据，启用时先向 Actor 提交容量和基础占用。候选查询和 Actor 最终预留都会检查剩余磁盘，
预留、确认、取消、过期和释放均同步更新资源计数，可用磁盘等于 `disk - used_disk - reserved_disk_gb`。
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
| `dependency-preparation-timeout` | `INSTANCE_DEPENDENCY_PREPARATION_TIMEOUT` | 10 分钟，项目、Share、用户网络共用 |
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
`http/floating-ip-api.http`。创建实例时按区域读取已登记且 `enabled=true` 的地址，按名称、业务 ID 排序，
将其 OpenStack ID 作为候选列表随创建事件持久化；不会在 HTTP 请求期间调用 OpenStack 创建转发。
没有候选地址时返回业务错误 `11718`，不提交实例工作流。
新建或登记浮动 IP 时发送 `EnableFloatingIp`，持久化 `FloatingIpEnabled` 保存启用状态、身份和初始端口位图后返回。
创建参数不再接收 `portMask`。`initOpenstack=true` 时新建地址并使用空占用启用；
`initOpenstack=false` 时，服务端在登记和自动启用前通过 `OpenStackClient.listPortForwardings()`
查询该浮动 IP 的现有转发规则，将规则 ID 和公网端口列表传给 Actor 计算占用。
查询失败则中止登记和启用；初始记录及创建响应的 `portMask` 根据查询结果生成，只记录 10000–40000 内的占用。
内部 `enable(identity, forwardings)` 和 `EnableFloatingIp` 命令只接收转发列表，由 Actor 生成启用事件中的位图。
创建响应的 `enabled=true` 表示 Actor 已确认启用；数据库字段由投影异步更新，投影完成后地址才进入实例候选列表。
`FloatingIpState.enabled` 初始为 `false`，收到 `FloatingIpEnabled` 后变为 `true`，收到 `FloatingIpDisabled` 后变为 `false`。
`PUT /api/v1/floating-ips/{id}/disable` 禁止受理新的 claim，已有创建、查询、删除和恢复继续处理，当前位图保留。
`PUT /api/v1/floating-ips/{id}/enable` 可不带请求体，沿用当前位图；已启用时重复调用同样成功。
启用请求可携带 `forwardings` 列表，每项包含规则 `id` 和 `externalPort`（也接受 OpenStack 的 `external_port`）。
只有 Actor 当前为禁用状态时才能携带列表更新端口占用；已启用时携带列表会被拒绝，即使列表与已有占用一致。
非 `null` 列表根据 OpenStack 转发规则生成位图，与当前 `usedPorts` 做 OR 合并，既有占用全部保留。
省略列表、传 `null` 或 `[]` 均不清除端口位；启用操作只增加占用。位图不区分占用来源，
TCP/UDP 共用同一端口位，范围外的端口不进入位图。
同步前由操作人员先禁用浮动 IP，确认没有在途创建、删除或恢复请求，再查询完整 OpenStack 规则列表并提交启用请求。
无在途请求由人工保证，Actor 不自动等待或增加在途请求冲突校验。
启用和禁用接口均在事件持久化后返回 HTTP `202`，读模型随后更新；`{id}` 是登记表业务 ID。
Actor 直接按区域和浮动 IP ID 启动并恢复日志，不使用 `withStash` 等待启用。
禁用时提交新 claim 立即返回 `REJECTED`；相同 claim 的重复提交返回已有状态，查询和清理已有 claim 正常进行。
从未启用的 Actor 没有 claim，查询或协调返回 `NOT_FOUND`，删除按无资源处理并立即完成。
调用方必须先等待启用成功再创建转发；普通调用不查询 OpenStack 身份，也不隐式发送启用命令。
内部端口默认是 SSH `22` 和 JupyterLab `8888`；配置必须是非空、无重复的 `1–65535` 端口列表。
每个实例先创建一个专属安全组，按上述内部端口配置添加 IPv4 入站 TCP/UDP 规则，来源为 `0.0.0.0/0`。
父端口和子端口创建时开启 port security，并只绑定该专属安全组；出站沿用 Neutron 新建安全组的默认规则。
安全组由实例工作流管理，FloatingIpActor 只负责公网端口分配和 TCP/UDP 转发。

实例的父端口和子端口各自绑定一个专用 QoS policy。OpenStack 端口创建方法内部先创建
QoS 和限速规则，再通过端口创建请求的 `qos_policy_id` 完成绑定，Actor 无需单独编排 QoS 创建和绑定。
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
调整配置仅影响之后提交的实例；已有工作流会继续使用创建事件中持久化的限速值，重启不会为旧 policy 自动补规则。
已存在的规则会校验方向、带宽和突发值，避免重复创建或复用不匹配的限速配置。
工作流按操作标记查找资源；人工触发实例删除后，先删除端口，再清理对应 QoS，包括未绑定端口的残留 policy。

## 创建流程与恢复

`InstanceService` 完成参数和本地资源校验后，构造包含原始 Share、网络规格的 `InstanceSpec`，
通过 `PekkoRuntime.instanceActor(instanceId)` 直接 ask `CreateInstance`。
查询同样向 Actor 发送 `GetInstanceState`，将返回状态转换成 `InstanceView`；
创建和查询均使用 `cloud.instance.ask-timeout`。
InstanceActor 持久化 `InstanceCreationRequested` 和 `SchedulingStarted`，进入 `SCHEDULING` 并立即回复受理；
先根据区域、宿主机类型和 CPU、内存、磁盘、GPU 规格选择宿主机并预留资源。
调度失败直接进入 `FAILED`，不启动依赖和实例专属资源的创建。
调度成功后持久化 `HypervisorSelected` 和 `DependencyPreparationStarted`，保存宿主机、预留 ID 和依赖准备截止时间，
进入 `PREPARING_DEPENDENCIES` 后异步调用 `InstanceDependencyPreparer.prepare()`，准备用户项目、检查区域 SHARED 网络，
再等待 Share 和用户网络就绪。准备器依次调用 `createProject()`、`createShare()`、`createNetwork()`，
创建请求持久化后即返回当前状态，首次受理为 `IN_PROGRESS`，已有资源直接返回其当前状态。
准备器每隔 `dependency-poll-interval` 调用对应的 `getProject()`、`getShare()`、`getNetwork()`；
仅 `READY` 才继续下一步，`FAILED`、`REJECTED` 直接结束准备。
ask 超时表示回复尚未确认，继续查询；查询确认 `NOT_FOUND` 时才重新提交幂等创建命令。
准备器向 UserActor 发送的创建和查询 ask 均使用 `cloud.user.ask-timeout`（默认 3 秒），不占用 Actor 等待远端资源就绪。
准备过程共用 `dependency-preparation-timeout`，从调度成功后开始计时，绝对截止时间随 `DependencyPreparationStarted` 持久化。
结果通过 `DependenciesReady` 或 `DependenciesFailed` 命令回到 InstanceActor。
成功后持久化 `DependenciesPrepared`，保存项目 ID、Share 访问数据和父子网络/子网 ID，
再创建专属安全组，随后依次进入父端口、子端口和 Trunk 三个阶段。
原始请求规格保持不变，重复请求仍按原始规格判断幂等。
准备失败或超时直接持久化实例 `FAILED` 及失败码，用户级依赖资源继续保留。
若 Actor 恢复时仍处于 `SCHEDULING`，使用原请求和预留 ID 继续调度；宿主机选定并进入后续阶段后不再调度。
若仍处于 `PREPARING_DEPENDENCIES`，保留选定的宿主机和资源预留，使用原始规格和原截止时间重新执行准备器：
同规格创建命令只返回已持久化的资源状态，已有 `READY` 资源直接复用，未完成资源继续查询。
停机时间计入准备超时，重启不会延长截止时间；已记录的失败不会自动重试。
当前 `pekko.cluster.sharding.remember-entities = off`，应用启动不会主动唤醒所有历史实例。
实例收到消息（例如 `InstanceService.get(instanceId)`）后才恢复，准备器的创建和查询消息会唤醒对应 UserActor。
这些资源由 UserActor 持久化管理：`projects` 以 OpenStack `clusterId` 为 key，
同一用户在同一集群的多个区域复用一个项目；Share 和用户网络仍按 `regionId` 复用。
UserActor 调用 `createProjectWithAdminRole()`，先在集群配置的默认 Domain 中按名称查询项目，
存在则复用，不存在才创建，然后授予管理员角色，全部成功后才标记 `READY`。
`CreateProject` 先持久化创建意图，再异步调用 OpenStack；结果通过 `ProjectReady` 或
`ProjectFailed` 命令回到 Actor，持久化最终状态，供后续查询。
创建、就绪、失败分别持久化 `ProjectCreationRequested`、`ProjectBecameReady`、
`ProjectCreationFailed` 事件，由事件处理器更新项目状态。
同规格的并发创建请求共用一次创建流程，并立即返回当前状态；Actor 不保存调用方的等待列表。
调用失败或超时记录 `FAILED`；恢复中的 `CREATING` 项目按 Domain 和名称查找并复用，
缺失时才创建，然后完成管理员授权。`project.operation-timeout` 决定创建截止时间，
创建事件持久化该时间，恢复时只使用剩余时限。
项目投影写入 `user_cluster_project`，主键为 `(user_id, cluster_id)`，建表定义见 `sql/read-model.sql`。
用户网络采用相同流程：`CreateNetwork` 持久化 `NetworkCreationRequested` 后调用
`createUserNetwork()`，依次校验 LOCAL 网络、创建用户网络、子网、路由和路由接口。
全部成功后发送 `NetworkReady`，持久化 `NetworkBecameReady`；失败或超时发送
`NetworkFailed`，持久化 `NetworkCreationFailed`，调用方通过 `getNetwork()` 查询结果。
同规格的并发请求共用一次创建流程，`createNetwork()` 在创建意图持久化后立即返回。
恢复中的 `CREATING` 网络按项目和名称分别查找已有网络、子网、路由，校验规格后复用；
路由接口按路由和子网查找，缺失步骤才继续创建。查到多个匹配资源或规格不一致直接失败。
整套创建操作的截止时间由 `network.operation-timeout` 决定并持久化，重启只使用剩余时间。
网络仍只有创建、就绪、失败三类事件，不逐步持久化资源 ID，不自动重试已失败流程或回滚。
操作超时不代表 OpenStack 已停止创建，远端可能留下部分资源。

Share 的创建和访问授权都由 Manila 异步完成，状态依次为：

```text
CREATING → WAITING_EXPORT → GRANTING_ACCESS → WAITING_ACCESS → READY
                            任一步失败或超时 → FAILED
```

`CreateShare` 持久化 `ShareCreationRequested` 后立即回复 `IN_PROGRESS`，再异步创建；返回 Share ID 时持久化
`ShareCreated`，进入 `WAITING_EXPORT`。定时查询 Share，待状态为 `AVAILABLE` 且存在非管理员
专用、包含 ID 和路径的 export location 后，持久化 `ShareExportBecameReady`，再提交一次
CephX 授权请求。获得访问规则 ID 后持久化 `ShareAccessCreated`，进入 `WAITING_ACCESS`；
访问规则变为 `ACTIVE` 且 access key 非空时，持久化 `ShareBecameReady`。
错误、操作超时或整体超时产生 `ShareCreationFailed`，保留已确认的资源 ID；调用方通过 `getShare()` 查询。

等待阶段每隔 `share.poll-interval`（默认 2 秒）查询一次；资源尚未就绪或暂不可见（404）时
继续等待，其他调用错误直接失败。恢复后按已持久化阶段继续：
`CREATING` 按项目和操作 metadata 查找 Share，`GRANTING_ACCESS` 按 Share、访问类型和用户名
查找已有授权，缺失时才创建；`WAITING_EXPORT`、`WAITING_ACCESS` 恢复查询。
已有授权的权限级别必须匹配；多个匹配资源直接失败，不任意选择一个继续。
每次查询或写入受 `share.operation-timeout`（默认 30 秒）限制，整个流程的截止时间随
创建事件持久化，由 `share.creation-timeout`（当前配置 15 秒）决定，恢复时不会重置。
同规格并发请求立即返回同一流程的当前状态；失败不自动重试，也不执行自动回滚。

创建前查询已有资源用于恢复和复用；OpenStack 查询与创建不是一个原子操作。
若旧请求在重启时仍由远端执行，仍可能出现并发创建窗口，不能据此保证外部资源恰好创建一次。
发现多个匹配资源时流程会记录失败，保留资源供核对。

Share、用户网络和路由的投影见下方“用户资源读模型”。
接口失败或单个实例失败不会删除这些共享资源。
InstanceActor 包含以下主要阶段：

```text
SCHEDULING → PREPARING_DEPENDENCIES → PREPARING_SECURITY_GROUP
  → PREPARING_PARENT_PORT → PREPARING_SUB_PORT → PREPARING_TRUNK
  → PREPARING_FORWARDING → PROVISIONING → CONFIRMING → ACTIVE
任一创建阶段失败 → FAILED
FAILED → 人工发送 DeleteInstance → DELETING → DELETED
```

HTTP 创建流程在网络就绪后进入 `PREPARING_FORWARDING`，依次完成 22、8888 两个内部端口的映射。
每个内部端口使用独立 claim ID（包含实例 ID、请求 ID、内部端口），分配一个 `10000–40000`
范围内的公网端口，同时创建 TCP/UDP 转发，目标为实例父端口的 IPv4 地址。
每个端口分别按候选顺序尝试浮动 IP；分配前确认地址无效或端口耗尽时可切换到下一个候选。
两个端口可能落在不同浮动 IP 上，调用方应分别使用查询结果中的地址和公网端口。
每个端口就绪都持久化一条 `ForwardingPrepared`，保存到实例的映射列表；全部就绪后持久化 `NovaCreationStarted` 并进入 `PROVISIONING`。
未配置端口转发时，Trunk 就绪后直接进入 `PROVISIONING`；两条路径都使用第一步已选定的宿主机。
整个转发阶段共用 `cloud.instance.forwarding-preparation-timeout`（默认 5 分钟）。
重启后沿用已持久化的候选、内部端口、已完成映射和截止时间，查询当前未完成的 claim；
只有确认 claim 不存在时才重新提交该 claim，已完成端口不会重建。

FloatingIpActor 的 claim 状态为 `CREATING`、`READY`、`FAILED`、`DELETING`、`DELETE_FAILED`、`DELETED`。
创建失败使用 `FAILED`，删除失败使用 `DELETE_FAILED`，不再保存 `paused` 标记。
`CreatePortForwarding` 根据 Actor 当前总位图 `usedPorts` 选择端口，将 `ForwardingCreationRequested` 和
`ForwardingPortAllocated` 一起持久化，然后返回 `IN_PROGRESS` 并立即调用 `OpenStackClient.createPortForwardings()`；
端口耗尽时持久化创建和失败事件并返回 `FAILED`，不调用 OpenStack。
不再保存 `order` 或通过全局 `Drive` 排队，不同 claim 的 OpenStack 异步操作可以并行执行。
创建不调用 `prepareForwardingTarget`，也没有内部端口预留 ask；分配端口只依据本地位图。
`executeCreation()` 在一次异步操作中依次创建 TCP、UDP 规则；整套流程成功发送 `ForwardingReady` 并持久化
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

创建直接依次提交 TCP、UDP 规则，不调用规则列表接口，也不查询或复用已有规则。
创建事件不保存截止时间，SDK 方法和 Actor 均不额外设置创建超时；底层 HTTP 超时和实例整体转发阶段超时仍按各自配置执行。
规则描述中的操作标识为 `veryai:pf:<claimId>:<protocol>`，协议为 `TCP` 或 `UDP`，供创建恢复时定位资源。
`signalHandler` 收到 `RecoveryCompleted` 后通过独立的 `recoverState → recoverClaim` 按持久化状态分流。
`CREATING` 通过 `recoverCreation → OpenStackClient.recoverPortForwardings()` 恢复；恢复入口和远端操作编排
均独立于正常创建的 `executeCreation` 和 `createPortForwardings()`，后续可以单独调整恢复策略。
恢复先查询一次当前浮动 IP 的转发列表，按 claim 和协议对应的操作标识定位 TCP、UDP，
校验公网端口、内部端口、目标 Neutron 端口、固定 IP、协议和规则 ID。
两条规则均存在时直接复用并返回就绪；只存在一条时只补建另一条，都不存在时才创建两条。
两种协议均完成校验后才补建；操作标识重复、参数不一致或规则 ID 缺失时进入 `FAILED` 并保留端口，等待核对。
恢复沿用已预留端口、不增加创建超时；结果通过同一套 Ready/Failed 消息和事件记录。
`DELETING` 通过独立的 `recoverDeletion → OpenStackClient.recoverPortForwardingDeletion()` 恢复，
不调用正常删除的 `executeDeletion` 或 `deletePortForwardings()`。
恢复按持久化的 TCP、UDP 规则 ID 逐条查询，已不存在的跳过，仍存在的继续删除；
两条规则均确认删除后通过 `ForwardingDeleted` 释放端口并更新投影，失败则通过 `ForwardingDeletionFailed` 进入 `DELETE_FAILED`。
恢复不增加超时；缺少规则 ID 时保留端口，等待外部核对。
禁用浮动 IP 不阻止已受理创建、删除的恢复，也不阻止外部删除命令。
中断前已在远端创建但尚未持久化结果的规则，也能通过操作标识发现并复用。
恢复只处理 `CREATING` 和 `DELETING`；`FAILED`、`DELETE_FAILED` 不会自动重试。

删除使用 `ForwardingDeletionStarted`、`ForwardingDeleted` 和 `ForwardingDeletionFailed`
分别记录开始、完成和失败。
外部 `DeletePortForwarding` 命令负责发起删除和失败后的重试，已开始但因重启中断的删除由独立恢复逻辑继续。
删除通过独立的命令和事件执行。`CREATING` 状态拒绝删除并返回 `REJECTED`，
不写事件、不记录删除意图，也不改变创建状态；调用方需在创建进入 `READY` 或 `FAILED` 后重新发送删除命令。
因此不再保存删除意图字段；重启后仍按 `CREATING` 恢复创建。
实例工作流根据本次请求的所有候选浮动 IP 和内部端口还原 claim ID，逐个调用 `delete`，
人工触发实例删除后，全部返回 `DELETED` 才继续删除实例网络；未完成时由删除流程轮询并再次发送删除命令。
删除范围包含创建结果尚未收到的 claim；尚未创建的 claim 同样记录 `ForwardingDeleted`，
在 `outcomes` 中保存已删除标记，阻止保留期内的迟到创建，不调用 OpenStack，也不改变端口位图。
FloatingIpActor 不再提供实例级批量删除命令，也不保存实例级取消记录。
同一 claim 已有删除请求在执行时，重复删除只返回当前进度，不并发提交删除。
`deletePortForwardings()` 按已保存的 TCP/UDP 规则 ID 先查询规则详情，存在时才调用删除接口。
查询返回空或 404 时直接视为已删除；删除成功或返回 404 同样视为完成。
不查询列表、不校验操作标识，也不在删除后复查。
两条规则都删除完成后，通过 `ForwardingDeleted` 直接清除该 claim 的端口位，不再区分占用来源。
若任一规则 ID 缺失，仍尝试删除有 ID 的规则，
整体返回未完成并保留端口占用；创建结果丢失的规则需要另行核对，删除流程不再自动发现这些规则。
删除流程不额外设置超时，事件和 claim 不保存删除截止时间；Actor 直接等待 OpenStackClient 的异步结果，
底层 HTTP 超时仍按 SDK 配置执行。删除失败后进入 `DELETE_FAILED`，保留资源、端口占用和失败原因；
外部再次发送删除命令时，通过 `ForwardingDeletionStarted` 回到 `DELETING` 并重试。
应用重启时，`DELETING` claim 自动恢复删除，`DELETE_FAILED` 等待外部命令。
删除完成后进入 `DELETED`；创建失败时保留 `FAILED`、失败原因和已占用端口，等待外部决定是否删除。

`claims` 仅保留仍需处理的完整记录。端口耗尽等未分配资源的失败，以及确认删除完成的 claim，
立即移入精简的 `outcomes`，只保存请求参数指纹、`FAILED`／`DELETED`、失败原因和完成时间。
仍占用端口或处于创建、就绪、删除中的 claim 不会被清理，`DELETE_FAILED` 保留完整记录等待重试。
保留期内查询和相同参数的重复创建仍返回原终态，但终态回复的 `claim` 为 `null`；
相同 claim ID 携带不同参数会被拒绝。尚未创建就收到删除命令的 claim 也保存在 `outcomes` 中，阻止迟到创建。
已压缩的无资源失败结果收到删除命令时，也通过 `ForwardingDeleted` 转为 `DELETED`，保留参数指纹和失败原因。

`cloud.fip.outcome-retention` 默认 `24h`，`outcome-cleanup-interval` 默认 `10m`。
Actor 恢复后立即检查一次，之后定期将过期记录 ID 持久化为 `ForwardingOutcomesCleaned` 再移除。
完成时间来自事件，事件重放不会重新开始保留期，重复查询或重复删除也不会延长已删除结果的保留期。
过期清理后查询返回 `NOT_FOUND`，旧 claim ID 不再保证幂等；保留期应覆盖调用方可能重试的时间范围。
清理终态记录不改变 `portmask`，不发起 OpenStack 删除，也不删除 journal 历史事件，已有 projection 可以继续消费。

创建失败统一返回 `FAILED`，删除失败返回 `DELETE_FAILED`；超时或响应丢失等结果仍保留已占用端口，等待外部核对或清理。
不提供显式恢复命令；重启恢复继续 `CREATING`、`DELETING`，已失败的操作不会重新执行。
失败资源由外部删除命令清理；删除失败后通过再次发送删除命令重试。
失败且仍持有端口时，实例工作流进入 `FAILED`，不切换浮动 IP，也不自动发送删除命令。
人工发送 `DeleteInstance` 后，实例删除流程才向 FloatingIpActor 发送外部删除命令。
只有没有占用端口的失败才允许继续尝试其他候选。

Cloud-init 显式指定 JupyterLab 的 `ServerApp.port=8888`、`ServerApp.port_retries=0`，
使监听端口与转发目标保持一致；端口占用时不会自动改用其他端口。
参数定义见 [Jupyter Server 配置文档](https://jupyter-server.readthedocs.io/en/stable/other/full-config.html#ServerApp.port)。
`forwarding-ports` 配置转发目标和安全组放行端口，镜像中的 SSH/JupyterLab 服务仍须监听对应内部端口。

依赖就绪后先持久化 `SecurityGroupPreparationStarted`，再异步创建实例专属安全组和入站规则。
整组规则就绪后持久化 `SecurityGroupPrepared`，记录安全组 ID，再开始创建端口；失败使用
`InstanceProvisioningFailed` 记录失败原因并进入 `FAILED`，不重新提交失败的安全组创建流程。
安全组名称和描述包含由实例 ID、请求 ID 生成的操作标识，重启恢复时先查询匹配的安全组，
复用已有组并补齐缺失规则；找到多个匹配组则失败，避免任意选择。
准备阶段沿用已持久化的截止时间，时长使用 `cloud.instance.provisioning-timeout`。
查找与创建并非原子操作，旧请求在远端继续执行时仍可能出现重复创建窗口。

网络创建拆分为三个持久化阶段，每个阶段只执行当前资源的 OpenStack 操作：

| 阶段 | 创建意图事件 | 就绪事件 | OpenStack 调用 |
| --- | --- | --- | --- |
| `PREPARING_PARENT_PORT` | `ParentPortPreparationStarted` | `ParentPortPrepared` | `prepareInstanceParentPort()`，创建父端口及其 QoS |
| `PREPARING_SUB_PORT` | `SubPortPreparationStarted` | `SubPortPrepared` | `prepareInstanceSubPort()`，沿用父端口 MAC 创建子端口及其 QoS |
| `PREPARING_TRUNK` | `TrunkPreparationStarted` | `TrunkPrepared` | `prepareInstanceTrunk()`，使用父子端口 ID 创建 Trunk 并关联 VLAN |

当前阶段的就绪事件和下一阶段的创建意图一起持久化，之后才调用下一阶段。
父端口就绪时保存 ID、MAC 和固定 IP；子端口就绪时追加其 ID 和固定 IP；Trunk 就绪时追加 Trunk ID。
状态查询和数据库投影随每个阶段更新，网络尚未全部完成时也能看到已完成的资源。
三个阶段共用 `ParentPortPreparationStarted` 持久化的网络截止时间，时长为
`cloud.instance.provisioning-timeout`；切换阶段和重启都不会重置截止时间，过期后不再启动下一阶段的创建。
Nova 的创建、等待和结果查询统一为 `PROVISIONING`，纯查询轮询不产生事件；
阶段变化和资源 ID、状态变化才持久化，数据库投影使用相同阶段。

实例端口/QoS/Trunk 和 Nova 的创建意图先持久化，再调用 OpenStack。网络调用结果不确定或 Actor 恢复时，
只按操作标识查询当前阶段资源，复用前序阶段已持久化的数据；当前阶段确认就绪后再进入下一阶段。
不会重新提交当前阶段的创建请求，也不会重新执行已完成阶段；当前资源持续缺失时等待至网络截止时间后进入 `FAILED`。
Nova 超时后确认资源存在但未就绪，或确认不存在时，进入 `FAILED`，保留资源等待人工处理。
查询持续失败、无法确认 Nova 实际结果时继续查询，不提前释放资源。

创建失败统一持久化 `InstanceProvisioningFailed`，保留失败原因以及 Nova、专属安全组、父子端口、
Trunk、QoS 和端口转发等已创建资源；端口创建失败时也不自动删除专用 QoS。
进入 `FAILED` 后停止实例创建的定时任务，忽略迟到的创建结果，重启恢复也不会发起删除。
已提交到其他 Actor 或 OpenStack 的请求可能继续完成，实例失败不会取消用户级项目、Share 或网络准备。
不再提供创建失败后自动清理的配置开关，也不会因为修改配置或重启应用而启动清理。

排查完成后，人工调用 `DELETE /api/v1/instances/{instanceId}`，服务层发送 `DeleteInstance`。
该入口只允许对 `FAILED` 实例开始删除；创建中或 `ACTIVE` 的实例会拒绝，业务错误码为 `11721`。
`InstanceDeletionStarted` 持久化后进入 `DELETING` 并返回 HTTP `202`，重复请求在 `DELETING`、`DELETED` 下幂等受理。
清理按 Nova、端口转发、实例网络/QoS、专属安全组、资源预留的顺序执行，未进入的资源创建阶段直接跳过。
先查询 Nova，确认服务器消失后才继续删除其他资源；本进程中尚未完成的安全组请求会在删除安全组前等待结束。
删除未完成或调用失败时按 `cloud.instance.poll.max-delay` 继续检查、重试，保持 `DELETING`；
已受理的删除在重启后继续，只有全部清理成功才持久化 `InstanceDeleted` 并进入 `DELETED`。
宿主机预留的原有 TTL 机制继续生效，失败后不由实例工作流主动取消预留；人工删除时取消仍存在的预留。
`cloud.reservation.ttl` 从第一步预留资源时开始计时，默认调整为 50 分钟，覆盖依赖准备、安全组、
实例网络、端口转发、Nova 创建和资源确认。当前各创建阶段的默认超时合计为 45 分钟，额外预留 5 分钟给调度返回和确认。
调整各准备超时时应同步调整 TTL；重启不会自动延长已有预留。
专属安全组按项目和操作标识查找并删除，包含创建成功但响应未持久化的组；删除组时一并删除其规则。
用户级项目、Share 和网络由多个实例复用，实例删除不会删除这些资源。
查询接口及 `instance_view` 投影展示 `FAILED`、`DELETING`、`DELETED`，保留已知资源 ID 和原创建失败原因供排查。
删除接口等待受理超时返回 `11719`，未能确认受理返回 `11720`；这些错误不等于删除已撤销，应先查询实例状态。

## 返回值

Actor 持久化受理请求后，接口即返回 HTTP `202 Accepted`；项目、Share、用户网络准备以及
实例安全组、网络、端口转发和 Nova 创建都在工作流中异步执行。等待 Actor 受理期间使用 Spring MVC 异步响应。
`INSTANCE_CREATE_REQUEST_TIMEOUT` 默认为 30 秒，只需覆盖 Actor 受理等待时间
（`cloud.instance.ask-timeout` 默认 3 秒），不再覆盖依赖准备时间。
受理后的依赖准备失败或超时通过 `GET /api/v1/instances/{instanceId}`、`InstanceService.get(instanceId)` 和 `instance_view` 的
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

## 查询端口转发

创建后轮询 `GET /api/v1/instances/{instanceId}`。接口查询 Actor 的当前状态，返回创建阶段、
失败信息、专属 `securityGroupId` 及 `forwardings` 列表，不包含密码、Jupyter token 或 Share 访问凭据。
不存在的实例按全局响应约定返回业务 `code = 404`。
以下仅展示相关字段，地址和端口为示例：

```json
{
  "code": 200,
  "result": {
    "instanceId": "实例 UUID",
    "phase": "ACTIVE",
    "securityGroupId": "实例专属安全组 UUID",
    "forwardings": [
      {"internalPort": 22, "floatingIp": "203.0.113.10", "externalPort": 10000},
      {"internalPort": 8888, "floatingIp": "203.0.113.10", "externalPort": 10001}
    ]
  }
}
```

实际每条映射还包含 `claimId`、`floatingIpId`、`tcpForwardingId` 和 `udpForwardingId`。
SSH 使用内部端口为 22 的映射：`ssh -p 10000 <创建响应中的 username>@203.0.113.10`。
JupyterLab 使用内部端口为 8888 的映射：`http://203.0.113.10:10001/lab?token=<创建响应中的 jupyterToken>`。
创建过程中列表可能为空或仅有一条记录；转发就绪先于 Nova 启动，`ACTIVE` 也不代表
cloud-init 和实例内服务已完成启动，连接应在对应服务就绪后进行。

## 用户资源读模型

`UserProjection` 同时处理项目、Share 和网络事件。用户从事件持久化 ID `User|<userId>`
解析；Share、网络和路由按 `(user_id, region_id)` 唯一保存，支持同一用户跨区域使用资源。

| 表 | 事件 | 保存内容 |
| --- | --- | --- |
| `user_cluster_project` | `ProjectCreationRequested`、`ProjectBecameReady`、`ProjectCreationFailed` | 项目名称、OpenStack 项目 ID、状态和失败原因 |
| `user_region_share` | `ShareCreationRequested`、`ShareCreated`、`ShareExportBecameReady`、`ShareAccessCreated`、`ShareBecameReady`、`ShareCreationFailed` | Share 规格、Manila ID、导出路径、授权规则 ID、access key、阶段和失败原因 |
| `user_region_network` | `NetworkCreationRequested`、`NetworkBecameReady`、`NetworkCreationFailed` | 请求的网段、DNS、网关，以及项目、LOCAL 网络、用户网络和子网 ID、状态和失败原因 |
| `user_region_router` | `NetworkCreationRequested`、`NetworkBecameReady`、`NetworkCreationFailed` | 路由、接口端口及关联网络/子网 ID，以及所属网络流程的状态和失败原因 |

项目和网络都投影 `CREATING`、`READY`、`FAILED` 三种状态。项目创建时保存名称，
就绪时保存 OpenStack 项目 ID，失败时保存失败原因；使用项目映射时应筛选 `phase = 'READY'`。
项目和网络的终态事件携带原始名称或网络规格、项目 ID 等投影所需的数据，可以独立写入读模型。
Share 每次状态转换也使用独立事件，携带请求规格和当前已确认的资源 ID，能够独立 upsert。
等待查询没有改变状态时不写事件；查询次数不作为持久化状态或投影字段。

router 由用户网络流程创建，没有单独的路由事件。每个网络事件在同一个 JDBC 事务内
更新网络和路由两条记录，并随投影偏移量一起提交。创建事件写入请求规格和 `CREATING`
状态，就绪事件一次补齐所有资源 ID 并标记 `READY`，失败事件记录 `FAILED` 和失败原因。
就绪前对应的 OpenStack 资源 ID 为空。
router 表中的 `phase` 表示整个用户网络流程，不表示 Neutron 路由运行状态。
网络表的网关和 DNS 来自请求规格；若网关由 Neutron 自动分配，投影中的 `gateway` 保持为空。

项目和三个区域资源表均使用事件日志序号 `resource_version` 防止旧事件或重复事件覆盖最新状态。
Project、网络和 Share 的各类事件都通过 upsert 写入完整资源状态；即使就绪或失败事件先于创建事件
到达，也能插入完整记录，后到的创建事件不会清空资源 ID 或回退状态。
`created_at` 取已投影事件的最早时间，`updated_at` 取最新版本事件时间；它们不是远端资源的
创建和更新时间。`share_id` 是用户范围内的逻辑 Share ID，`os_share_id` 才是 Manila ID，
`size_gib` 的单位为 GiB。`ShareBecameReady` 将访问凭据写入 `access_key`，其余阶段该字段为 NULL；
该字段同样受 `resource_version` 保护，旧事件不会清空已就绪的 access key。

执行 `sql/read-model.sql` 初始化读模型表。`user-resources` 投影消费 User 事件更新读模型，
查询结果在投影追上事件日志后完整可见。

## 浮动 IP 读模型

`FloatingIpProjection` 消费 `FloatingIpActor` 的事件，投影名为 `floating-ip`，
与其他投影一起由 `cloud.projection.enabled` 和 `cloud.projection.slice-ranges` 控制。
投影按 `(region_id, os_floating_ip_id)` 直接更新已登记的 `floating_ip` 表中的 `enabled`、`port_mask`
和 `resource_version`。`enabled` 为非空布尔值，登记时默认为 `false`，由启用和禁用事件更新。
名称、描述、外部网络和地址等字段仍由登记流程维护。

`FloatingIpEnabled`、`FloatingIpDisabled`、`ForwardingPortAllocated` 和 `ForwardingDeleted`
携带事件生效后的完整端口位图 `usedPortMask`，投影直接使用该总位图更新 `floating_ip.port_mask`。
`FloatingIpEnabled` 只包含 `identity` 和合并后的完整 `usedPortMask`，恢复和投影使用事件中的合并结果；
状态中不再保存占用来源或单独的导入位图。
分配公网端口时置位，TCP/UDP 清理完成、Actor 释放端口后清位；失败或清理未完成时继续保留占用。
claim 已不存在时，`ForwardingDeleted` 携带当前位图，投影仍更新版本而不释放任何端口。
禁用后的启用请求将查询到的占用并入总位图，同步前由人工确认无在途请求；启用和禁用均不释放任何端口。
位图第 0 位对应公网端口 10000，使用 `BitSet.toByteArray()` 编码。
分配和释放事件同时携带当时的 `enabled`，与位图共用版本保护；禁用期间的清理不会重新启用浮动 IP。

同一投影同时将端口转发生命周期写入 `port_forwarding`，主键为
`(region_id, os_floating_ip_id, claim_id)`，同一 claim 在不同候选浮动 IP 上的尝试分别保存。
记录包含用户、实例、项目、目标 Neutron 端口、固定 IP、内部/公网端口、浮动 IP 地址、
TCP/UDP 规则 ID、阶段、失败原因、版本和创建/更新/删除时间。

| 事件 | 端口转发记录 |
| --- | --- |
| `ForwardingCreationRequested` | 写入请求规格，阶段为 `CREATING` |
| `ForwardingPortAllocated` | 写入预留的公网端口 |
| `ForwardingBecameReady` | 写入 TCP/UDP 规则 ID，阶段为 `READY` |
| `ForwardingCreationFailed` | 阶段为 `FAILED`，记录失败原因 |
| `ForwardingDeletionStarted` | 阶段为 `DELETING` |
| `ForwardingDeletionFailed` | 阶段为 `DELETE_FAILED`，记录失败原因 |
| `ForwardingDeleted` | 阶段为 `DELETED`，记录删除时间 |

删除后保留已知的映射参数和规则 ID，供查询历史；这些字段不表示仍然占用端口，端口分配以 Actor 位图为准。
删除尚未创建的 claim 也写入 `DELETED` 记录，请求规格字段为空。
Actor 清理过期 `outcomes` 时不删除这些数据库记录；同一 claim ID 在保留期后重新创建时，较新的创建事件重置该记录。
实例关联的端口转发统一从 `port_forwarding` 按 `instance_id` 查询。

创建请求、就绪、失败和删除开始等事件不改变位图。claim 状态和已删除结果继续保存在 Actor 事件及快照中。
快照保存 `enabled`、`identity`、`claims`、`outcomes` 和总位图 `usedPorts`，不再包含 `importedPortMask`。
启用状态必须包含身份，缺少端口位图时直接拒绝加载；恢复使用快照和事件中的完整位图，不从 claim 重建或补入占用。
任意命令都能触发 Actor 的日志恢复；Actor 从事件和快照恢复身份、总位图及转发状态，
恢复完成后继续处理 `CREATING`、`DELETING`，禁用状态也不跳过这些已受理的操作；
`DELETE_FAILED` claim 等待外部删除命令重试。
同步总位图时使用当次查询的完整 OpenStack 规则列表。

`resource_version` 默认是 0，随后保存最近一次启停或端口位图事件的日志序号，只有更新的事件才能覆盖启用状态和位图。
`port_forwarding.resource_version` 保存该 claim 最近消费的事件序号，重复或旧事件不会覆盖较新状态。
启用状态、位图、端口转发记录和 offset 在同一个 JDBC 事务中提交；若登记记录尚不可见，
或转发更新事件缺少对应创建记录，投影报错而不推进 offset，避免丢失更新。投影不调用 Actor 或 OpenStack。
新库执行 `sql/floating-ips.sql`，已有数据库执行 `sql/migrations/add-floating-ip-projection-version.sql`
补充版本列，缺少启用字段时执行 `sql/migrations/add-floating-ip-enabled.sql`。
增列脚本中已有记录默认未启用，不回填历史启用状态。当前状态与事件结构直接用于开发阶段，不提供历史事件兼容转换。
端口转发表随 `sql/read-model.sql` 初始化，也可单独执行 `sql/migrations/add-port-forwarding-projection.sql`。
已有 `floating-ip` 投影进度时，新增表不会自动补齐之前消费的事件；重建该读模型需要从创建事件开始重放。

## 实例读模型

`InstanceProjection` 将实例状态写入 `instance_view`；端口转发记录统一由 `FloatingIpProjection`
写入 `port_forwarding`，建表定义均位于 `sql/read-model.sql`。
`instance_view.security_group_id` 保存实例专属安全组 ID；本次状态和事件结构调整不提供历史数据兼容转换。
网络三阶段替换旧的 `PREPARING_NETWORK` 及整套网络开始、就绪事件，不提供旧网络事件或快照的兼容转换。

创建事件写入名称、描述、用户、集群、区域、资源规格、请求的逻辑 Share ID、VLAN 和
`SCHEDULING` 状态，项目及父子网络/子网 ID 暂为空。
`HypervisorSelected` 写入宿主机和预留 ID，`DependencyPreparationStarted` 更新为 `PREPARING_DEPENDENCIES`；
`DependenciesPrepared` 补齐项目 ID、实际复用的逻辑 Share ID、父子网络和子网 ID；
`SecurityGroupPreparationStarted` 更新安全组准备阶段，`SecurityGroupPrepared` 写入专属安全组 ID；
`ParentPortPrepared`、`SubPortPrepared`、`TrunkPrepared` 依次补齐父端口、子端口和 Trunk，
各阶段的开始事件更新对应阶段；`ForwardingPrepared` 在实例投影中只更新 `PREPARING_FORWARDING` 阶段。
该事件和实例 Actor 中的已确认映射继续保留，用于创建进度、重启恢复和删除流程。
实例阶段更新和实例投影偏移量在同一个 JDBC 事务内提交。
`os_project_id`、`os_image_id`、`os_flavor_id`、网络、子网、端口及共享存储 ID
来自事件中的 OpenStack 资源，`hypervisor_type` 是应用中的宿主机类型 ID。
`memory_gb` 和 `disk_gb` 单位为 GB。
密码、Jupyter token 和共享存储访问凭据不写入此表。

每次状态更新以日志序号 `workflow_version` 判断新旧，忽略相同或更早的事件，
避免重放时覆盖较新的状态和资源信息。进入 `ACTIVE` 时清除失败信息。
未就绪资源的字段为空，只有创建事件和相应资源就绪事件投影后才有值。
查询实例的就绪映射时，从 `port_forwarding` 按 `instance_id` 和 `phase = 'READY'` 筛选，
并结合实例阶段判断可用性；删除和失败记录仍保留在该表中。
读模型与事件投影进度一致，HTTP `202` 返回时记录及最终状态可能尚未可见。
原有 `instance_port_forwarding` 表可通过 `sql/migrations/remove-instance-port-forwarding.sql` 删除；
该脚本保留 `port_forwarding`、Actor 持久化数据和投影进度。

## 宿主机资源读模型

宿主机元数据和资源账目统一保存在 `hypervisor_host`，调度直接查询该表。
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
| 取消或过期 | 扣减预留 | 不变 | 不变 |
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

进入维护前必须释放全部预留及分配；同步前还需确认没有在途的 Nova 创建、删除请求，
尤其是预留已过期但云端操作仍未结束的情况。Actor 的计数为空不能证明外部操作已经结束。
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
