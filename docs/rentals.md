# 实例租赁：冻结与结算

租赁入口为 `/api/v1/leases`。LeaseActor 编排订单创建、实例交付、租期、续租与退租；
OrderActor 使用 `FundWalletService` 冻结本期资金，并按已保存的消费金额结算。
实例操作复用现有 InstanceWorkflowActor 的创建、查询和删除接口。

## 资金接口

- 创建订单：先持久化订单，再调用 `lock(userId, amount, orderId, "GPU_RENTAL", null)`。
- 结算订单：先持久化消费金额，再调用 `deduct(userId, orderId, deductAmount, null)`。
- Lease、Order、事件和查询金额统一使用 `BigDecimal`，单位为元；订单输入金额按 HALF_UP 四舍五入保留两位小数后比较、持久化，直接传给钱包接口。
- 结算传明确消费金额，包括全额和零额；`expireAt`、`refundAmount` 均传 null，鉴权沿用 FundWalletService。
- 非空响应的 `!response.isSuccess()` 表示余额不足；异常、超时和空响应保留原操作继续重试。
- 重试复用订单 ID 和持久化金额，依赖钱包按订单 ID 幂等；结算金额受理后不可修改。

钱包调用是同步 HTTP，请求在现有阻塞任务线程池中执行，通过异步消息返回 Actor。
服务没有按订单查询钱包结果的接口，因此 OrderActor 重试原幂等操作；LeaseActor 轮询 OrderActor 的状态。
钱包需保留已结算订单的幂等结果，避免迟到冻结重开订单。本版不处理剩余冻结款的自动释放。

## OrderActor

OrderState 保存订单 ID、用户 ID、租约 ID、intent、订单金额、阶段、结算金额和错误。

```text
NEW → LOCKING → LOCKED → DEDUCTING → SETTLED
NEW → SETTLED                         未创建订单的零额结算
LOCKING / DEDUCTING → INSUFFICIENT_FUNDS
```

`deductAmount=null` 表示尚未请求结算，`0` 是有效的零消费结算。
冻结结果未知时收到结算命令，先保存消费金额；冻结确认后直接进入 DEDUCTING。
`SettleOrder` 只结算原订单，金额未知时的处理见退租流程。

| OrderActor 命令 | 作用 |
| --- | --- |
| `CreateOrder` | 创建订单并冻结资金；相同绑定重试返回原订单，保留首次保存的金额 |
| `SettleOrder` | 保存消费金额并推进扣款；金额未知时，已有订单返回原状态，未创建订单保存零额终态 |
| `GetOrderState` | 返回订单状态 |
| `PollOrderProgress` | 重试未确认的冻结或扣款 |
| `WalletOperationCompleted` | 接收当前钱包请求的结果 |

订单事件分别为 `OrderCreated`、`UnopenedOrderSettled`、`OrderLocked`、
`OrderDeductionRequested`、`OrderDeducted`、`OrderInsufficientFunds`、`OrderOperationFailed`。
`UnopenedOrderSettled` 只保存用户、租约和 intent，订单金额与消费金额均固定为零。

## LeaseActor

LeaseState 的阶段为 `NEW / WAITING_LOCK / PROVISIONING / ACTIVE / EXPIRED / TERMINATING / TERMINATED`。
`currentPeriod` 保存订单、intent、方案、金额和起止时间；`lastPeriodEndsAt` 保存最近租期的结束时间，
到期清空 currentPeriod 后仍保留，作为续租起点。

- `pendingRenewal`：首期或续租的待购买租期，保存订单 ID、intent、方案、方案版本、已确认金额和阶段。
- 购买阶段为 `WAITING_SETTLEMENT / CREATING_ORDER / LOCKING / WAITING_ACTIVATION`；amount=0 表示金额尚未确认，订单可能已经创建或冻结。
- `pendingSettlement`：待结算订单，保存订单 ID、intent、原金额和固定消费金额；原金额未知时先以零占位。

业务命令为 `CreateLease`、`GetLeaseState`、`ChangeRenewalPlan`、`ConfigureAutoRenew`、`RenewLease` 和 `TerminateLease`。
外部扫描发送 `ReconcileLease` 触发到期和自动续租；内部 `PollLeaseProgress` 推进已有订单及实例待办，
异步操作通过 `LeaseOperationCompleted` 返回结果。

事件为 `LeaseCreated`、`RenewalPlanChanged`、`AutoRenewConfigured`、`PurchaseRequested`、`PurchasePhaseChanged`、
`SettlementRequested`、`SettlementCompleted`、`PeriodStarted`、`PeriodEnded`、`InstanceCreationRequested`、
`LeaseTerminationRequested`、`LeaseTerminated`、`LeaseRetryScheduled` 和 `LeaseReconciliationScheduled`。
两个 Actor 均直接持久化具体事件，由各自 eventHandler 更新状态。

## 创建与连续续租

首次购买流程：

```text
保存 pendingRenewal（CREATING_ORDER）→ 查询原订单 → 不存在时取价并 CreateOrder、lock
→ Lease 查询到 LOCKED → 创建实例 → 查询到 ACTIVE → 开始首期
```

首次租期从实例交付确认时开始。`LeaseActor.createOrder` 内完成原订单查询、取价和创建；
已存在则复用，未创建时重试可重新取价。`OrderCreated` 固定首次保存的订单金额，
Lease 通过 `PurchasePhaseChanged` 同步订单阶段和已确认金额。

到期续租流程：

```text
保存旧单全额 pendingSettlement、PeriodEnded 和下期 pendingRenewal（WAITING_SETTLEMENT）
→ SettleOrder、deduct → 确认旧单 SETTLED
→ 下期进入 CREATING_ORDER → 查询原订单，不存在时取价并 CreateOrder、lock
→ 确认 LOCKED 和实例 ACTIVE → 开始下一期
```

旧单结果未知时，新订单不会冻结。旧单扣款或新单冻结明确余额不足时，终止租约并删除实例；
其他错误保留实例运行、保留同一待办并重试。
关闭自动续租后，已到期的旧单仍会全额结算；已有 pendingRenewal 继续推进，没有待续租时实例保持运行，等待手动续租或退租。

下一期始终从上一期结束时间开始。例如上一期 10:00 到期，新单 10:20 才冻结成功，
一小时的新租期仍为 10:00–11:00。开启自动续租时，已过期的续租周期由后续到期检查逐期结算、续接。
手动续租一次只发起一期，也沿用这个边界。

本版只结算订单覆盖的使用金额，未覆盖的运行时间不另建欠费或补偿账单。

## 退租

首次受理退租时固定 `terminatedAt`；订单金额已知时同时固定消费金额，未知时待确认订单后按该截止时间计算。
消费金额保存后，重复请求及恢复不重新计费。
当前租期按 `订单金额 × 已使用整分钟数 ÷ 租期总分钟数` 计算消费，不足一分钟的部分舍弃。
全程使用 BigDecimal 计算，最终消费金额使用 HALF_UP 四舍五入保留两位小数。
金额单位为元，截止时间限制在租期起止范围内，消费在零到订单金额之间。

- 当前租期已开始：按实际使用金额结算当前订单。
- 续租尚未确认：从 `lastPeriodEndsAt` 起计算新订单的消费；冻结结果未知时继续确认后结算。
- 等待旧单结算：终止待购买租期，继续完成已经保存的旧单结算。
- 首次交付前退租或实例创建失败：消费为零。

统一通过 `SettleOrder` 处理结算。订单金额未知时先发送 amount=0、deductAmount=0：
未创建则持久化 `UnopenedOrderSettled`，阻止迟到创建；已创建则返回原状态，
Lease 按原金额和固定截止时间计算消费，保存后再次提交结算。占位零额不会将已有订单按零消费结算。
金额已知但订单不存在时拒绝结算并保留待办重试，SettleOrder 不补建或冻结新订单。

退租时停止新增续租，实例删除与已保存的结算待办独立推进。
尚未发起实例创建时直接完成资源收尾；创建回复丢失时用原 requestId 和规格确认创建结果，
等待现有实例工作流进入 ACTIVE/FAILED 且无在途管理操作后，再调用既有删除接口。
`TERMINATED` 表示资源清理完成，资金待办仍可继续重试；`settled` 表示退租后的资金待办已结束。
余额不足通过订单阶段和租约 error 展示，settled 不等同于扣款成功。

## 方案、价格与配置

当前租期方案保持不变，修改只影响下一期。
`targetPeriodSeq` 必须为下一期，`expectedPlanRevision` 必须匹配当前方案版本。
存在 pendingRenewal 时不允许修改方案，相同方案也拒绝。关闭自动续租只影响后续周期；
pendingRenewal 不可单独取消，主动终止只能通过退租。

周期固定为 HOUR=1 小时、DAY=24 小时、WEEK=7 天、MONTH=30 天，再乘以正整数 quantity。
RentalPriceService 通过现有 HypervisorTypeRepo / MyBatis 查询 hypervisor_type，
按周期优先使用 `discount_*_pay_price`，未设置时取对应 `original_*_pay_price`。
元单价乘以 quantity，使用 HALF_UP 保留两位小数；每期按创建订单时的价格计费。

- `cloud.ask-timeout`：Actor 请求、取价和钱包调用的等待时间。
- `cloud.rental.retry-interval`：订单钱包重试及租约待办轮询间隔。
- `cloud.rental.scan-interval`：外部租约到期/续租扫描间隔。
- `cloud.projection.enabled`：启用查询投影和外部租约扫描。
- `fund-service.base-url`：FundWalletService 的资金服务地址，按其现有 Spring 配置读取。

Actor 配置继续通过 PekkoRuntime.Config getter 读取，资金服务配置沿用 FundWalletService。
两个 Actor 在 RecoveryCompleted 时清除内存请求标记，按持久化中间状态立即恢复已有待办。
没有待办时停止内部轮询。到期和自动续租由外部检查触发。

## 投影与查询

租约和订单投影消费具体事件，通过 MyBatis Mapper 更新 lease_view / order_view 的具体列。
lease_view 保存当前租期、续租/结算待办和 next_action_at；order_view 保存订单金额、资金阶段和消费金额。
购买阶段事件同时更新已确认金额，未创建订单的结算插入零额终态。

投影使用 workflow_version 检查事件顺序并去重，字段更新与 offset 在同一事务提交，不调用 Actor。
RentalViewRepo 通过 MyBatis 查询用户列表和到期租约；LeaseView 从查询字段组装租期、待办和 settled。
单条租约和订单直接询问 Actor，列表是最终一致的投影数据；订单尚未创建时查询返回资源不存在。

建表使用 [sql/rentals.sql](../sql/rentals.sql)，金额列统一为 `DECIMAL(19,2)`，单位为元。
当前为开发阶段，不提供旧分单位数据及历史事件的兼容转换。

## HTTP 接口

| 接口 | 用途 |
| --- | --- |
| POST /api/v1/leases | 创建租约，返回 lease 和首次创建凭据 |
| GET /api/v1/leases/{leaseId} | 查询租约 Actor 状态 |
| GET /api/v1/leases?userId=... | 查询用户租约列表 |
| GET /api/v1/orders/{orderId} | 查询冻结、扣款及错误状态 |
| PUT /api/v1/leases/{leaseId}/renewal-plan | 修改下期方案 |
| PUT /api/v1/leases/{leaseId}/auto-renew | 配置后续自动续租 |
| POST /api/v1/leases/{leaseId}/renew | 到期后手动续租 |
| POST /api/v1/leases/{leaseId}/terminate | 退租并结算 |

创建参数沿用 [实例接口](instances.md) 的 instance 对象，加 requestId、plan 和 autoRenew。
`instance.flavorId` 用于解析实例规格，`instance.hypervisorType` 用于取价。
相同用户、requestId 及参数可重试，参数冲突拒绝；控制器通过 LeaseService 提交业务命令。

响应数据在 `result` 中。创建成功返回 HTTP 202，result 包含 lease、username、password 和 jupyterToken，受理不代表实例已交付。
修改方案、配置自动续租、续租和退租受理时返回 HTTP 202，拒绝时返回 HTTP 409；result 包含 accepted、error 和 lease。
上述受理/拒绝响应的 code 均为 200，应根据 HTTP 状态和 result.accepted 判断是否受理。
公开租约视图包含 pendingRenewal、pendingSettlement、lastPeriodEndsAt 和 settled，不包含 instanceSpec。

请求示例见 [http/rentals.http](../http/rentals.http)。
