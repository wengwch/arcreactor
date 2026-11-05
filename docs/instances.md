# 创建实例

`POST /api/v1/instances`，请求头使用 `Content-Type: application/json`。

```json
{
  "name": "gpu-instance-01",
  "description": "实例创建接口测试",
  "flavorId": "demo-flavor-t4",
  "imageId": "demo-image-ubuntu",
  "hypervisorType": "demo-hv-t4",
  "regionId": "demo-region",
  "userId": "demo-user"
}
```

除 `description` 外其余字段必填，各字段最多 255 个字符。`flavorId`、`imageId`、
`hypervisorType` 是本地数据库中的 ID。区域、镜像、规格和宿主机类型必须启用，
镜像和规格必须属于所选区域，规格的宿主机类型必须与请求一致。
示例中的 ID 需要替换为已配置并启用的实际资源。

服务端从规格读取 vCPU、内存（GB）、磁盘（GB）和 GPU 数量，使用镜像和规格对应的 OpenStack ID
提交 Actor 创建流程。调度候选宿主机限定在所选区域和宿主机类型内，
并且必须处于 ACTIVE 状态且有足够资源。
磁盘需求取规格的 `disk`，以 `diskGb` 随创建请求和调度请求传递；宿主机启用时将
`disk` 写入 `ResourceCapacity.totalDiskGb`。候选查询和 Actor 最终预留都会检查剩余磁盘，
预留、确认、取消、过期和释放均同步更新磁盘占用；容量不能缩减到已预留与已分配磁盘总量以下。
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
创建响应的 `enabled=true` 表示 Actor 已确认启用；数据库字段由投影异步更新，投影完成后地址才进入实例候选列表。
`FloatingIpState.enabled` 初始为 `false`，收到启用事件后变为 `true`；重复启用会校验身份和初始位图一致性，
一致时直接成功，不重复写事件，也不修改当前端口占用。
Actor 直接按区域和浮动 IP ID 启动并恢复日志，不使用 `withStash` 等待启用。
未启用时创建转发立即返回 `REJECTED`，查询或协调返回 `NOT_FOUND`，删除按无资源处理并立即完成。
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
工作流按操作标记查找资源；创建失败的补偿会先删除端口，再清理对应 QoS，包括未绑定端口的残留 policy。

## 创建流程与恢复

`InstanceService` 完成参数和本地资源校验后，构造包含原始 Share、网络规格的 `InstanceSpec`，
通过 `PekkoRuntime.instanceActor(instanceId)` 直接 ask `CreateInstance`。
查询同样向 Actor 发送 `GetInstanceState`，将返回状态转换成 `InstanceView`；
创建和查询均使用 `cloud.instance.ask-timeout`。
InstanceActor 持久化 `InstanceCreationRequested`，进入 `PREPARING_DEPENDENCIES` 并立即回复受理；
然后异步调用 `InstanceDependencyPreparer.prepare()`，准备用户项目、检查区域 SHARED 网络，
再等待 Share 和用户网络就绪。准备器依次调用 `createProject()`、`createShare()`、`createNetwork()`，
创建请求持久化后即返回当前状态，首次受理为 `IN_PROGRESS`，已有资源直接返回其当前状态。
准备器每隔 `dependency-poll-interval` 调用对应的 `getProject()`、`getShare()`、`getNetwork()`；
仅 `READY` 才继续下一步，`FAILED`、`REJECTED` 直接结束准备。
ask 超时表示回复尚未确认，继续查询；查询确认 `NOT_FOUND` 时才重新提交幂等创建命令。
准备器向 UserActor 发送的创建和查询 ask 均使用 `cloud.user.ask-timeout`（默认 3 秒），不占用 Actor 等待远端资源就绪。
准备过程共用 `dependency-preparation-timeout`，绝对截止时间随 `InstanceCreationRequested` 持久化。
结果通过 `DependenciesReady` 或 `DependenciesFailed` 命令回到 InstanceActor。
成功后持久化 `DependenciesPrepared`，保存项目 ID、Share 访问数据和父子网络/子网 ID，
再开始创建实例端口、QoS 和 Trunk。原始请求规格保持不变，重复请求仍按原始规格判断幂等。
准备失败或超时直接持久化实例 `FAILED` 及失败码；此时还没有实例专用资源，无需进入补偿。
若 Actor 恢复时仍处于 `PREPARING_DEPENDENCIES`，使用原始规格和原截止时间重新执行准备器：
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
PREPARING_DEPENDENCIES → PREPARING_SECURITY_GROUP → PREPARING_NETWORK → PREPARING_FORWARDING → SCHEDULING → PROVISIONING → CONFIRMING → ACTIVE
    依赖准备失败 → FAILED                                                    后续阶段失败 → COMPENSATING → FAILED
```

HTTP 创建流程在网络就绪后进入 `PREPARING_FORWARDING`，依次完成 22、8888 两个内部端口的映射。
每个内部端口使用独立 claim ID（包含实例 ID、请求 ID、内部端口），分配一个 `10000–40000`
范围内的公网端口，同时创建 TCP/UDP 转发，目标为实例父端口的 IPv4 地址。
每个端口分别按候选顺序尝试浮动 IP；分配前确认地址无效或端口耗尽时可切换到下一个候选。
两个端口可能落在不同浮动 IP 上，调用方应分别使用查询结果中的地址和公网端口。
每个端口就绪都持久化一条 `ForwardingPrepared`，保存到实例的映射列表；全部就绪后才进入调度。
整个转发阶段共用 `cloud.instance.forwarding-preparation-timeout`（默认 5 分钟）。
重启后沿用已持久化的候选、内部端口、已完成映射和截止时间，查询当前未完成的 claim；
只有确认 claim 不存在时才重新提交该 claim，已完成端口不会重建。

FloatingIpActor 的 claim 状态为 `CREATING`、`READY`、`FAILED`、`DELETING`、`DELETED`。
`CreatePortForwarding` 先持久化 `ForwardingCreationRequested` 并立即返回 `IN_PROGRESS`；
校验内部端口、查询远端占用后，持久化 `ForwardingPortAllocated`，预留公网端口，再调用
`OpenStackClient.createPortForwardings()`。该方法在一次异步操作中依次完成 TCP、UDP 两条规则，
成功发送 `ForwardingReady` 并持久化 `ForwardingBecameReady`，失败发送 `ForwardingFailed`
并持久化 `ForwardingCreationFailed`。Actor 不再逐协议保存创建阶段，也不记录查询尝试次数。

每条规则通过包含区域、claim ID、浮动 IP ID 和协议的操作标识查找，校验参数后复用已有规则，
缺失时才创建。因此重启后可以复用已完成的 TCP 规则，再补齐 UDP。
从受理到完成整套创建共用 `cloud.fip.operation-timeout`（默认 30 秒），截止时间随请求事件保存，
恢复只使用剩余时间；每次发起新的远端写入前都会检查截止时间。已失败的创建不会自动重做。
远端查找和创建不是原子操作，发现重复操作标识时记录失败并保留端口供核对。

删除使用 `ForwardingDeletionRequested`、`ForwardingDeletionStarted`、`ForwardingDeleted`
和 `ForwardingDeletionFailed` 分别记录意图、开始、完成和失败。创建期间的删除请求只记录意图，
先接收当前操作结果再执行清理。`deletePortForwardings()` 按操作标识查找并删除 TCP/UDP，
包含创建成功但 ID 尚未写入事件的规则；只有两条规则均确认不存在后才释放端口。
显式删除完成后进入 `DELETED`；创建失败后的自动清理完成后保留 `FAILED` 和原失败原因。

超时或响应丢失等不确定结果保留端口，并返回 `NEEDS_RECONCILIATION`；通过
`ReconcilePortForwarding` 显式恢复时记录 `ForwardingReconciliationRequested`，重新设置操作截止时间。
已有删除意图时继续清理，否则重新查找并补齐转发规则。删除失败也会暂停，重复删除请求可以再次尝试清理。
自动清理仍受 `cleanup-on-failure` 控制；失败且仍持有端口时，实例工作流进入补偿，不切换浮动 IP。
只有没有占用端口的失败才允许继续尝试其他候选。

Cloud-init 显式指定 JupyterLab 的 `ServerApp.port=8888`、`ServerApp.port_retries=0`，
使监听端口与转发目标保持一致；端口占用时不会自动改用其他端口。
参数定义见 [Jupyter Server 配置文档](https://jupyter-server.readthedocs.io/en/stable/other/full-config.html#ServerApp.port)。
`forwarding-ports` 配置转发目标和安全组放行端口，镜像中的 SSH/JupyterLab 服务仍须监听对应内部端口。

依赖就绪后先持久化 `SecurityGroupPreparationStarted`，再异步创建实例专属安全组和入站规则。
整组规则就绪后持久化 `SecurityGroupPrepared`，记录安全组 ID，再开始创建端口；失败使用
`CompensationStarted` 记录失败原因，不重新提交失败的安全组创建流程。
安全组名称和描述包含由实例 ID、请求 ID 生成的操作标识，重启恢复时先查询匹配的安全组，
复用已有组并补齐缺失规则；找到多个匹配组则失败，避免任意选择。
准备阶段沿用已持久化的截止时间，时长使用 `cloud.instance.provisioning-timeout`。
查找与创建并非原子操作，旧请求在远端继续执行时仍可能出现重复创建窗口。

`prepareInstanceNetwork()` 在一个操作内完成父端口和 QoS、子端口和 QoS、Trunk；
Actor 只记录创建意图和整套网络就绪结果，不再持久化每个网络子步骤。
Nova 的创建、等待和结果查询统一为 `PROVISIONING`，纯查询轮询不产生事件；
阶段变化和资源 ID、状态变化才持久化，数据库投影使用相同阶段。

实例端口/QoS/Trunk 和 Nova 的创建意图先持久化，再调用 OpenStack。发生超时或 Actor 恢复时只查询已有资源，
不会盲目重发创建请求。若网络只完成一部分，超时后进入补偿；
Nova 超时后确认资源存在但未就绪，或确认不存在时，进入补偿。
查询持续失败、无法确认 Nova 实际结果时继续查询，不提前释放资源。

当前排查模式默认设置 `instance.creation.cleanup-on-failure=false`
（环境变量 `INSTANCE_CLEANUP_ON_FAILURE`），创建失败后暂停自动清理：
保留 Nova、专属安全组、父子端口、Trunk、QoS 和端口转发，并保留失败原因；端口创建失败时也不自动删除专用 QoS。
实例停留在 `COMPENSATING`，不执行清理或安排清理重试，Actor 恢复后同样暂停。
转发创建失败后的自动回滚和已提交的实例转发清理也暂停，其他转发请求可继续处理。

排查完成后设置 `INSTANCE_CLEANUP_ON_FAILURE=true` 并重启应用，恢复中的工作流按
Nova、端口转发、实例网络/QoS、专属安全组、资源预留的顺序继续补偿，清理失败后继续重试，完成后进入 `FAILED`。
专属安全组按项目和操作标识查找并删除，包含创建成功但响应未持久化的组；删除组时一并删除其规则。

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
和 `resource_version`。`enabled` 为非空布尔值，登记时默认为 `false`，启用事件投影后为 `true`。
名称、描述、外部网络和地址等字段仍由登记流程维护。

`FloatingIpEnabled`、`ForwardingPortAllocated` 和 `ForwardingDeleted` 携带事件生效后的完整端口位图 `usedPortMask`，
包含导入的初始预留和所有 claim 当前持有的端口。分配公网端口时置位，TCP/UDP 清理完成、
Actor 释放端口后清位；暂停或清理未完成时继续保留占用。初始预留始终保留，
即使它与某个后来被删除的 claim 使用相同端口，也不会被误释放。
位图第 0 位对应公网端口 10000，使用 `BitSet.toByteArray()` 编码。
这三个事件都描述已启用的 Actor，投影同时写入 `enabled=true`；释放端口不会停用浮动 IP。

创建请求、就绪、失败和删除请求等事件不改变位图；`ForwardingCancelled` 记录尚未创建的 claim
或整个实例的取消意图，同样不释放端口。claim 状态和取消记录继续保存在
Actor 事件及快照中，实例已确认的映射仍由 `instance_port_forwarding` 保存。
快照保存 `enabled`、`identity`、`initialPortMask` 和 `usedPorts`；启用状态必须包含身份，
缺少端口位图时直接拒绝加载，不再从 claim 重建位图。
任意命令都能触发 Actor 的日志恢复；已启用的 Actor 从事件和快照恢复身份、初始预留及转发状态，
无需再次发送启用消息，恢复完成后继续处理未完成的转发操作。
不要把投影后的动态 `port_mask` 再作为初始预留导入。

`resource_version` 默认是 0，随后保存最近一次端口位图事件的日志序号，只有更新的事件才能覆盖启用状态和位图。
启用状态、位图更新和 offset 在同一个 JDBC 事务中提交；若登记记录尚不可见，投影报错而不推进 offset，
避免创建事务与投影并发时丢失更新。投影不调用 Actor 或 OpenStack。
新库执行 `sql/floating-ips.sql`，已有数据库执行 `sql/migrations/add-floating-ip-projection-version.sql`
补充版本列，缺少启用字段时执行 `sql/migrations/add-floating-ip-enabled.sql`。
增列脚本中已有记录默认未启用，不回填历史启用状态。当前状态与事件结构直接用于开发阶段，不提供历史事件兼容转换。

## 实例读模型

`InstanceProjection` 将实例状态写入 `instance_view`，每个内部端口的已确认映射写入
`instance_port_forwarding`，建表定义均位于 `sql/read-model.sql`。
`instance_view.security_group_id` 保存实例专属安全组 ID；本次状态和事件结构调整不提供历史数据兼容转换。

创建事件写入名称、描述、用户、集群、区域、资源规格、请求的逻辑 Share ID、VLAN 和
`PREPARING_DEPENDENCIES` 状态，项目及父子网络/子网 ID 暂为空。
`DependenciesPrepared` 补齐项目 ID、实际复用的逻辑 Share ID、父子网络和子网 ID；
`SecurityGroupPreparationStarted` 更新安全组准备阶段，`SecurityGroupPrepared` 写入专属安全组 ID；
网络就绪事件补齐端口、IP 和 Trunk；每条 `ForwardingPrepared` 按
`(instance_id, internal_port)` upsert `instance_port_forwarding`，22 和 8888 不会相互覆盖。
映射保存 claim ID、浮动 IP ID 和地址、内部/公网端口、TCP/UDP 转发 ID 及日志版本。
映射 upsert、实例阶段更新和投影偏移量在同一个 JDBC 事务内提交。
`os_project_id`、`os_image_id`、`os_flavor_id`、网络、子网、端口及共享存储 ID
来自事件中的 OpenStack 资源，`hypervisor_type` 是应用中的宿主机类型 ID。
`memory_gb` 和 `disk_gb` 单位为 GB，`external_port` 是浮动 IP 的外部转发端口。
宿主机资源投影保存 `total_disk_gb`、`reserved_disk_gb`、`allocated_disk_gb`，
`available_disk_gb` 由总量减去预留和已分配容量计算；实例分配记录同时保存 `disk_gb`。
旧数据库缺少磁盘列时，停止应用后执行 `sql/migrations/add-disk-resource-fields.sql`。
`sql/read-model.sql` 使用 `CREATE TABLE IF NOT EXISTS`，再次执行不会修改已存在的表。
升级脚本补齐磁盘列和候选索引，已有字段值、事件日志和投影偏移量保持原样。
恢复应用后调用 `PUT /api/v1/hypervisor-hosts/{id}/enable`，该接口会发送 `CapacityUpdated`，
即使宿主机已启用也会刷新包含磁盘的容量；等待投影更新后再调度。
新加的磁盘计数初始为 0，脚本不推算历史占用；已有预留或分配时，需要从 Actor 事件重建对应磁盘记账。
密码、Jupyter token 和共享存储访问凭据不写入此表。

每次状态更新以日志序号 `workflow_version` 判断新旧，忽略相同或更早的事件，
避免重放时覆盖较新的状态和资源信息。进入 `ACTIVE` 时清除失败信息。
未就绪资源的字段为空，只有创建事件和相应资源就绪事件投影后才有值。
转发表记录最后确认成功的映射；后续补偿后应结合实例阶段判断可用性，不能仅凭表中有记录就认为实例可连接。
读模型与事件投影进度一致，HTTP `202` 返回时记录及最终状态可能尚未可见。
