# 实例租赁：冻结与结算

租赁入口为 `/api/v1/leases`。LeaseActor 编排报价、实例交付、租期、续租与退租；
OrderActor 使用 `FundWalletService` 冻结本期资金，并按已保存的消费金额结算。
实例操作复用现有 InstanceWorkflowActor 的创建、查询和删除接口，不增加停机或恢复流程。

## 资金接口

- 创建订单：先持久化订单，再调用 `lock(userId, amount, orderId, "GPU_RENTAL", null)`。
- 结算订单：先持久化消费金额，再调用 `deduct(userId, orderId, deductAmount, null)`。
- Actor 内部金额单位为分，调用钱包时使用 `BigDecimal.valueOf(amount, 2)` 转成元。
- 正常结算传明确金额，包括全额和零额；不使用迁移数据专用的 `refundAmount`。
- 非空响应的 `!response.isSuccess()` 按约定表示余额不足；异常、超时和空响应保留原操作继续重试。
- `lock` 和 `deduct` 按订单 ID 幂等，重试复用原订单和原金额。结算金额受理后不可修改。
- 本版不扩展冻结自动释放或 token 获取，`expireAt` 传 null，保留 FundWalletService 的现有鉴权实现。

钱包调用是同步 HTTP，请求在现有阻塞任务线程池中执行，通过异步消息返回 Actor。
服务没有按订单查询钱包结果的接口，因此 OrderActor 重试原幂等操作；LeaseActor 轮询 OrderActor 的状态。
调用方的操作标记用于忽略迟到回调，资金侧仍需保留同一订单的幂等结果，避免迟到冻结重开已结算订单。

## 状态、命令与事件

OrderState 保存订单 ID、用户 ID、租约 ID、intent、订单金额、阶段、结算金额和错误。

```text
NEW → LOCKING → LOCKED → DEDUCTING → SETTLED
          └──────────────────────→ INSUFFICIENT_FUNDS
```

`deductAmount=null` 表示尚未请求结算，`0` 是有效的零消费结算。
冻结结果未知时收到结算命令，会先保存结算金额，冻结确认后再扣款，不跳过未知冻结。
退租早于订单创建且消费为零时，保存零额终态，迟到的创建命令不能重新冻结。
若续租已经产生消费但订单尚未创建，先用原订单 ID 冻结，再按已保存金额结算。

| OrderActor 命令 | 作用 |
| --- | --- |
| `CreateOrder` | 创建订单并冻结资金；相同绑定与金额可重试 |
| `SettleOrder` | 固定结算金额并推进扣款 |
| `GetOrderState` | 返回订单状态 |
| `PollOrderProgress` | 重试未确认的冻结或扣款 |
| `WalletOperationCompleted` | 接收当前钱包请求的结果 |

订单事件分别为 `OrderCreated`、`UnopenedOrderSettled`、`OrderLocked`、
`OrderDeductionRequested`、`OrderDeducted`、`OrderInsufficientFunds`、`OrderOperationFailed`。
不再保留支付、关单、独立退款命令或退款记录列表。

LeaseState 的阶段为 `NEW / WAITING_LOCK / PROVISIONING / ACTIVE / EXPIRED / TERMINATING / TERMINATED`。
当前租期保存订单、intent、金额和起止时间；`lastPeriodEndsAt` 在到期清空当前租期后仍保留连续计费边界。

- `pendingSettlement`：一个待结算订单，保存原订单金额和固定的消费金额。
- `pendingRenewal`：一个待购买租期，保存固定订单 ID、方案、报价和阶段。
- 购买阶段为 `WAITING_SETTLEMENT / PRICING / CREATING_ORDER / LOCKING / WAITING_ACTIVATION`。
- 不保存历史订单结果缓存或退款列表。订单终态和资金结果由 OrderActor 维护。
- `settled` 表示退租后的资金待办已结束；余额不足仍通过订单阶段和租约 error 展示，不代表扣款成功。

LeaseActor 继续处理创建、查询、修改下期方案、配置自动续租、手动续租和退租命令。
`ReconcileLease` 由外部扫描触发到期和自动续租，`PollLeaseProgress` 只推进已有订单及实例待办。
实例操作与价格查询通过独立异步完成消息回到 Actor。

租约事件按事实拆分为创建、方案变更、自动续租配置、购买请求、报价、购买阶段变更、
结算请求、结算完成、租期开始/结束、实例创建意图、退租请求、资源删除完成、重试错误和检查时间。
每个 eventHandler 直接处理具体事件；命令侧直接持久化事件，不预演状态，不保存完整 state 快照事件。

## 创建与连续续租

首次购买流程：

```text
保存 pendingRenewal → 查询价格 → 创建订单并 lock
→ Lease 查询到 LOCKED → 创建实例 → 查询到 ACTIVE → 开始首期
```

首次租期从实例交付确认时开始。首次交付前退租或创建失败，按零消费结算已有冻结。

到期续租流程：

```text
保存旧单全额 pendingSettlement、PeriodEnded 和下期 pendingRenewal
→ 查询旧单并提交 SettleOrder
→ 确认旧单 SETTLED
→ 下期进入 PRICING → 创建新订单并 lock
→ 确认 LOCKED 和实例 ACTIVE → 开始下一期
```

旧单结果未知时，新订单不会冻结。旧单扣款或新单冻结明确余额不足时，终止租约并删除实例；
其他错误保留实例运行、保留同一待办并重试。
即使关闭自动续租，已到期的旧单仍会全额结算；没有 pending 时不会自动创建下一单。

下一期始终从上一期结束时间开始。例如上一期 10:00 到期，新单 10:20 才冻结成功，
一小时的新租期仍为 10:00–11:00。开启自动续租时，已过期的续租周期由后续到期检查逐期结算、续接。
手动续租一次只发起一期，也沿用这个边界。

先结算旧单再冻结新单会存在未冻结的运行区间。本版只结算对应订单覆盖的使用金额；
余额不足或未报价时的未覆盖费用不另建欠费、追扣或补偿账单。

## 退租

首次受理退租时固定 `terminatedAt` 和消费金额，重复请求及恢复不重新计费。
当前租期按 `订单金额 − floor(订单金额 × 剩余时长 ÷ 总时长)` 计算消费，单位为分，
截止时间限制在租期起止范围内，因此消费在零到订单金额之间。

- 当前租期已开始：按实际使用金额结算当前订单。
- 续租仍在冻结或等待确认：从 `lastPeriodEndsAt` 起计算该订单的消费，保留结算意图等待冻结结果。
- 仍在报价或等待旧单结算：终止未发起冻结的新购买；已经保存的旧单结算继续完成。
- 首次交付前退出：消费为零；已发起冻结则继续确认并零额结算，未创建的订单保存零额终态。
- 没有独立取消续租接口，终止 pending 统一使用退租。

退租时停止新增续租，实例删除与已保存的结算待办独立推进。
尚未发起实例创建时直接完成资源收尾；创建回复丢失时用原 requestId 和规格确认创建结果，
等待现有实例工作流进入 ACTIVE/FAILED 且无在途管理操作后，再调用既有删除接口。
资源删除后若结算未确认，仍继续重试，直到订单终态明确。

## 方案、价格与配置

当前租期方案保持不变，修改只影响下一期。
`targetPeriodSeq` 必须为下一期，`expectedPlanRevision` 必须匹配当前方案版本。
存在 pendingRenewal 时不允许修改方案，包括等待旧单结算、报价、冻结及实例确认阶段；相同方案也拒绝。
关闭自动续租只影响后续周期，不取消已经受理的续租。

周期固定为 HOUR=1 小时、DAY=24 小时、WEEK=7 天、MONTH=30 天，再乘以正整数 quantity。
RentalPriceService 通过现有 HypervisorTypeRepo / MyBatis 查询 hypervisor_type，
按周期优先使用 discount_hourly/daily/weekly/monthly_pay_price，未设置时取对应 original 字段。
元单价乘以 quantity，使用 HALF_UP 换算为分。报价持久化后重试不重新取价，新一期重新报价。

- `cloud.ask-timeout`：Actor 请求、报价和钱包调用的等待时间。
- `cloud.rental.retry-interval`：订单钱包重试及租约待办轮询间隔。
- `cloud.rental.scan-interval`：外部租约到期/续租扫描间隔。
- `cloud.projection.enabled`：启用查询投影和外部租约扫描。
- `fund-service.base-url`：FundWalletService 的资金服务地址，按其现有 Spring 配置读取。

Actor 配置继续通过 PekkoRuntime.Config getter 读取，资金服务配置沿用 FundWalletService。
两个 Actor 在 RecoveryCompleted 时清除内存请求标记，按持久化中间状态立即恢复已有待办。
没有待办时停止内部轮询。自动续租只由外部检查触发，不增加提前续租。

## 投影与查询

LeaseProjection / OrderProjection 消费具体事件，通过 MyBatis Mapper 更新 lease_view / order_view 的具体列。
lease_view 保存当前租期、pendingRenewal、pendingSettlement 和 next_action_at；
order_view 保存资金订单阶段与结算金额。新表结构不再需要退款或订单结果明细表。

投影沿用 workflow_version 检查事件顺序并去重，字段更新与 offset 在同一事务提交。
投影不调用 Actor，也不通过重放状态类转换来更新查询实体。
RentalViewRepo 只保留租约列表和到期扫描查询，LeaseDueEntry 按时间和租约 ID 分页。
单条租约和订单直接询问 Actor；列表是最终一致的投影数据。

新环境使用 sql/rentals.sql。已有但尚未使用的旧版表，可按
[结构调整脚本](../sql/migrations/rental-wallet-settlement.sql) 更新。
这不是旧 mock 资金数据或事件的兼容迁移：旧支付/退款日志不能当作真实钱包冻结重放；
已有旧版租约需先单独处理，不能只执行 DDL 后直接切换。历史明细表不自动删除。

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
相同用户、requestId 及参数可重试，参数冲突拒绝。
公开租约视图包含 pendingRenewal、pendingSettlement、lastPeriodEndsAt 和 settled，不包含 instanceSpec。
创建响应仍返回用户名、密码和 Jupyter token；受理不代表实例已交付。

请求示例见 [http/rentals.http](../http/rentals.http)。
