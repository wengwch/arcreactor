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

OrderState 保存订单 ID、用户 ID、租约 ID、订单金额、阶段、结算金额和错误。

```text
NEW → LOCKING → LOCKED → DEDUCTING → SETTLED
LOCKING / DEDUCTING → INSUFFICIENT_FUNDS
```

`deductAmount=null` 表示尚未请求结算，`0` 是有效的零消费结算。
`SettleOrder` 在订单冻结成功后受理，原金额必须大于零且与订单一致；未创建或仍在冻结中的订单拒绝结算。

| OrderActor 命令 | 作用 |
| --- | --- |
| `CreateOrder` | 创建订单并冻结资金；相同绑定重试返回原订单，保留首次保存的金额 |
| `SettleOrder` | 冻结成功后保存消费金额并推进扣款；重复请求返回原状态，消费金额不可变更 |
| `GetOrderState` | 返回订单状态 |
| `RetryWalletOperation` | 延迟重试未确认的冻结或扣款 |
| `WalletOperationCompleted` | 接收当前钱包请求的结果 |

创建订单或受理结算后立即执行对应资金操作；异常、超时或空响应后才安排一次延迟重试，
等待钱包响应期间不启动重试定时器。冻结成功后进入 LOCKED，等待结算命令。
钱包操作由当前订单阶段决定，回调通过请求 token 排除过期结果。

订单事件分别为 `OrderCreated`、`OrderLocked`、
`OrderDeductionRequested`、`OrderDeducted`、`OrderInsufficientFunds`、`OrderOperationFailed`。

## LeaseActor

LeaseState 的阶段为 `NEW / WAITING_LOCK / PROVISIONING / ACTIVE / EXPIRED / TERMINATING / TERMINATED`。
`currentPeriod` 保存订单、方案、金额和起止时间；`lastPeriodEndsAt` 保存最近租期的结束时间，
到期清空 currentPeriod 后仍保留，作为续租起点。

- `pendingRenewal`：首期或续租的待购买租期，保存订单 ID、期号、方案、方案版本、已确认金额和阶段。
- 购买阶段为 `WAITING_LOCK / WAITING_ACTIVATION`；冻结确认前 amount=0，订单可能已经创建或冻结。
- `pendingSettlement`：待结算订单，保存订单 ID、已确认的原金额和固定消费金额。

先推进 pendingSettlement，完成后再推进 pendingRenewal，不单独保存等待旧单结算的购买阶段。
WAITING_LOCK 内统一查询原订单、按需取价创建及等待冻结；确认 LOCKED 后保存金额，进入 WAITING_ACTIVATION。

业务命令为 `CreateLease`、`GetLeaseState`、`ChangeRenewalPlan`、`ConfigureAutoRenew`、`RenewLease` 和 `TerminateLease`。
外部扫描发送 `ReconcileLease` 触发到期和自动续租；内部 `PollLeaseProgress` 推进已有订单及实例待办，
订单和实例异步操作通过 `LeaseOperationCompleted` 返回结果，报价通过 `PriceQuoteCompleted` 返回。

事件为 `LeaseCreated`、`RenewalPlanChanged`、`AutoRenewConfigured`、`PurchaseRequested`、`PurchaseLocked`、
`SettlementRequested`、`SettlementCompleted`、`PeriodStarted`、`PeriodEnded`、`InstanceCreationRequested`、
`LeaseTerminationRequested`、`LeaseTerminated`、`LeaseRetryScheduled` 和 `LeaseReconciliationScheduled`。
两个 Actor 均直接持久化具体事件，由各自 eventHandler 更新状态。

## 创建与连续续租

首次购买流程：

```text
保存 pendingRenewal（WAITING_LOCK）→ 查询原订单 → 不存在时异步取价
→ 报价返回 LeaseActor，确认租约和待购买订单仍有效 → CreateOrder、lock
→ Lease 查询到 LOCKED，保存 PurchaseLocked（WAITING_ACTIVATION）
→ 创建实例 → 查询到 ACTIVE → 开始首期
```

首次租期从实例交付确认时开始。每次重试先查询原订单，已存在则复用，不存在才异步取价。
报价结果通过内部命令回到 LeaseActor；核对请求 token、订单 ID、购买阶段，且未退租、没有待结算订单后才创建。
退租后或恢复前的旧报价直接丢弃；报价不持久化，不增加租约阶段，未创建时重试可重新取价。
`OrderCreated` 固定首次保存的订单金额，
Lease 通过 `PurchaseLocked` 保存冻结确认和订单金额，不再同步 OrderActor 的中间阶段。

到期续租流程：

```text
保存旧单全额 pendingSettlement、PeriodEnded 和下期 pendingRenewal（WAITING_LOCK）
→ SettleOrder、deduct → 确认旧单 SETTLED
→ 查询下期订单，不存在时取价，结果返回 LeaseActor 确认后 CreateOrder、lock
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

用户发起退租时，pendingRenewal 和 pendingSettlement 必须均为空；否则返回 HTTP 409、`error=ORDER_PENDING`，不进入退租流程。
这包含首期或续租的取价、冻结、等待实例交付，以及尚未完成的结算。正常使用中的当前租期可以退租。
已经受理退租的重复请求返回原状态，继续已有收尾。余额不足或实例故障触发的自动终止不受上述入口限制。

首次受理退租时固定 `terminatedAt`，有订单需要结算时同时固定消费金额。
消费金额保存后，重复请求及恢复不重新计费。
当前租期按 `订单金额 × 已使用整分钟数 ÷ 租期总分钟数` 计算消费，不足一分钟的部分舍弃。
全程使用 BigDecimal 计算，最终消费金额使用 HALF_UP 四舍五入保留两位小数。
金额单位为元，截止时间限制在租期起止范围内，消费在零到订单金额之间。

- 当前租期已开始：按实际使用金额结算当前订单。
- 新单冻结明确余额不足：终止待购买租期，无需再结算该失败订单。
- 旧单扣款明确余额不足：结束该结算待办并终止租约，不再创建下期订单。
- 冻结成功后实例不可用：首期按零消费结算，续租从 `lastPeriodEndsAt` 起计算消费。

统一通过 `SettleOrder` 提交已确认的订单原金额和固定消费金额，不使用零原金额占位或补查金额。
订单不存在时拒绝结算并保留待办重试，SettleOrder 不补建或冻结新订单。

退租时停止新增续租，实例删除与已保存的结算待办独立推进。
尚未发起实例创建时直接完成资源收尾；创建回复丢失时用原 requestId 和规格确认创建结果，
等待现有实例工作流进入 ACTIVE/FAILED 且无在途管理操作后，再调用既有删除接口。
`TERMINATED` 表示资源清理完成，资金待办仍可继续重试；`settled` 表示退租后的资金待办已结束。
余额不足通过订单阶段和租约 error 展示，settled 不等同于扣款成功。

## 方案、价格与配置

当前租期方案保持不变，修改只影响下一期。
`targetPeriodSeq` 必须为下一期，`expectedPlanRevision` 必须匹配当前方案版本。
存在 pendingRenewal 时不允许修改方案，相同方案也拒绝。关闭自动续租只影响后续周期；
pendingRenewal 不可单独取消；用户需等待购买和结算待办完成后再退租。

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
LeaseActor 没有待办时停止内部轮询；OrderActor 仅对异常、超时或空响应安排延迟重试。
到期和自动续租由外部检查触发。

## 投影与查询

租约和订单投影消费具体事件，通过 MyBatis Mapper 更新 lease_view / order_view 的具体列。
lease_view 保存当前租期、续租/结算待办和 next_action_at；order_view 保存订单金额、资金阶段和消费金额。
购买冻结确认事件同时更新阶段和金额，订单投影从 OrderCreated 开始建立。

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
| POST /api/v1/leases/{leaseId}/terminate | 无未完成订单时退租并结算 |

创建参数沿用 [实例接口](instances.md) 的 instance 对象，加 requestId、plan 和 autoRenew。
`instance.flavorId` 用于解析实例规格，`instance.hypervisorType` 用于取价。
同一用户和 requestId 标识同一次创建，重复请求返回原租约，保留已有规格和配置；创建新租约需使用新的 requestId。
控制器通过 LeaseService 提交业务命令。

响应数据在 `result` 中。创建成功返回 HTTP 202，result 包含 lease、username、password 和 jupyterToken，受理不代表实例已交付。
修改方案、配置自动续租、续租和退租受理时返回 HTTP 202，拒绝时返回 HTTP 409；result 包含 accepted、error 和 lease。
上述受理/拒绝响应的 code 均为 200，应根据 HTTP 状态和 result.accepted 判断是否受理。
公开租约视图包含 pendingRenewal、pendingSettlement、lastPeriodEndsAt 和 settled，不包含 instanceSpec。

请求示例见 [http/rentals.http](../http/rentals.http)。
