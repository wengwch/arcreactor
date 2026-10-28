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

服务端从规格读取 vCPU、内存（GB）和 GPU 数量，使用镜像和规格对应的 OpenStack ID
提交 Actor 创建流程。调度候选宿主机限定在所选区域和宿主机类型内，
并且必须处于 ACTIVE 状态且有足够资源。
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
| `dependency-poll-interval` | `INSTANCE_DEPENDENCY_POLL_INTERVAL` | 2 秒 |
| `network-cidr` | `INSTANCE_NETWORK_CIDR` | `10.0.0.0/16` |
| `network-dns` | `INSTANCE_NETWORK_DNS` | 空，使用网络服务默认值 |
| `network-gateway` | `INSTANCE_NETWORK_GATEWAY` | 空，由 Neutron 分配 |
| `segmentation-id` | `INSTANCE_SEGMENTATION_ID` | 100 |
| `parent-port-bandwidth-limit.max-kbps` | `INSTANCE_PARENT_PORT_MAX_KBPS` | 0，不限速 |
| `parent-port-bandwidth-limit.max-burst-kbits` | `INSTANCE_PARENT_PORT_MAX_BURST_KBITS` | 0 |
| `sub-port-bandwidth-limit.max-kbps` | `INSTANCE_SUB_PORT_MAX_KBPS` | 0，不限速 |
| `sub-port-bandwidth-limit.max-burst-kbits` | `INSTANCE_SUB_PORT_MAX_BURST_KBITS` | 0 |
| `ssh-username` | `INSTANCE_SSH_USERNAME` | `clouduser` |
| `host-routes` | 在 YAML 中配置目标网段到下一跳的映射 | 空对象 |

每个用户在同一区域内复用同一个共享存储和私有网络。区域应预先配置好工作流所需的
LOCAL、SHARED 网络和 OpenStack 集群连接；CIDR、DNS、VLAN 和静态路由应匹配实际环境。
当前接口提交的流程不包含可选的浮动 IP 端口转发。

实例的父端口和子端口各自绑定一个专用 QoS policy。OpenStack 端口创建方法内部先创建
QoS 和限速规则，再通过端口创建请求的 `qos_policy_id` 完成绑定，Actor 无需单独编排 QoS 创建和绑定。
创建 Command 的 `spec.network.parentPortBandwidthLimit` 和 `spec.network.subPortBandwidthLimit`
分别携带父、子端口限速，随实例创建事件持久化；恢复时沿用提交时的值。
HTTP 层从以上服务端配置组装这两个参数，直接调用 `InstanceLifecycle` 的调用方可以逐实例传入不同限速：

```java
NetworkRequirement network = new NetworkRequirement(sharedNetworkId, sharedSubnetId,
    userNetworkId, userSubnetId, segmentationId, userNetworkSpec,
    new PortBandwidthLimit(100_000, 10_000),   // 父端口：100 Mbps，突发 10,000 kbit
    new PortBandwidthLimit(1_000_000, 100_000)); // 子端口：1 Gbps，突发 100,000 kbit
// 将 network 传给 InstanceLifecycle.CreateRequest，最终进入 CreateInstance.spec.network。
```

每个端口的同一组参数分别创建 ingress 和 egress 两条规则。`maxKbps` 单位为 kbit/s，
`maxBurstKbits` 单位为 kbit（对应 Neutron 的 `max_burst_kbps` 字段）。
单位和方向遵循 [OpenStack 带宽限速规则 API](https://docs.openstack.org/api-ref/network/v2/index.html#create-bandwidth-limit-rule)。
两个值均不得为负；`maxKbps = 0` 表示不创建限速规则，此时 `maxBurstKbits` 也必须为 0。
已存在的规则会校验方向、带宽和突发值，避免重复创建或复用不匹配的限速配置。
工作流按操作标记查找资源；创建失败的补偿会先删除端口，再清理对应 QoS，包括未绑定端口的残留 policy。

## 创建流程与恢复

接口服务先准备用户项目，检查区域 SHARED 网络，再等待 Share 和用户网络就绪。
这些资源由 UserActor 持久化管理：`projects` 以 OpenStack `clusterId` 为 key，
同一用户在同一集群的多个区域复用一个项目；Share 和用户网络仍按 `regionId` 复用。
项目投影写入 `user_cluster_project`，主键为 `(user_id, cluster_id)`，建表定义见 `sql/read-model.sql`。
接口失败或单个实例失败不会删除这些共享资源。
只有依赖全部就绪后才向 InstanceActor 发送 Command，包含已解析的 `projectId`、
带访问数据的 `ShareRequirement`、已解析父子网络和子网 ID 的 `NetworkRequirement`。

InstanceActor 只保留以下主要阶段：

```text
PREPARING_NETWORK → SCHEDULING → PROVISIONING → CONFIRMING → ACTIVE
                                      失败 → COMPENSATING → FAILED
```

若指定浮动 IP 转发，则在网络就绪后、调度前增加 `PREPARING_FORWARDING`。
`prepareInstanceNetwork()` 在一个操作内完成父端口和 QoS、子端口和 QoS、Trunk；
Actor 只记录创建意图和整套网络就绪结果，不再持久化每个网络子步骤。
Nova 的创建、等待和结果查询统一为 `PROVISIONING`，纯查询轮询不产生事件；
阶段变化和资源 ID、状态变化才持久化，数据库投影使用相同阶段。

网络和 Nova 的创建意图先持久化，再调用 OpenStack。发生超时或 Actor 恢复时只查询已有资源，
不会盲目重发创建请求。若网络只完成一部分，超时后清理该操作标记下的资源；
Nova 超时后确认资源存在但未就绪，或确认不存在时，进入补偿。
查询持续失败、无法确认 Nova 实际结果时继续查询，不提前释放资源。
补偿按 Nova、端口转发、实例网络/QoS、资源预留的顺序执行，失败后继续重试。

## 返回值

共享依赖全部就绪且 Actor 持久化受理请求后，接口返回 HTTP `202 Accepted`；实例网络和 Nova 创建继续异步执行。
等待依赖期间使用 Spring MVC 异步响应，不占用 Servlet 请求线程。
`INSTANCE_CREATE_REQUEST_TIMEOUT` 默认为 11 分钟，应大于依赖准备超时与 Actor 受理等待时间之和。
依赖准备失败或超时时不提交实例创建；已创建的用户级资源可供下一次请求复用。
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
