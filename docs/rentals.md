# 实例租赁：冻结与结算

租赁入口为 `/api/v1/leases`。LeaseActor 编排订单创建、实例交付、租期、续租、暂停计费、恢复与退租；
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
连接、读取超时由 RestClient 执行，OrderActor 等调用返回或抛出异常后才释放当前请求标记，避免仅 Future 超时就重复发起请求。
服务没有按订单查询钱包结果的接口，因此 OrderActor 重试原幂等操作；LeaseActor 轮询 OrderActor 的状态。
HTTP 超时或恢复时，资金端可能仍在处理旧请求；钱包需保留已结算订单的幂等结果，避免迟到冻结重开订单。
本版不处理剩余冻结款的自动释放。

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

创建订单或受理结算后立即执行对应资金操作；异常、HTTP 超时、空响应或任务提交失败后，先保存错误，再安排一次延迟重试。
相同错误不重复写事件，但仍继续重试；等待钱包响应期间不启动重试定时器，重复创建或结算命令只返回原状态。
冻结成功后进入 LOCKED，等待结算命令；结算成功或余额不足后不再执行资金请求。
恢复时仅重试 LOCKING / DEDUCTING，使用新的请求 token；旧回调不会修改状态、清除新请求标记或启动重试。

订单事件分别为 `OrderCreated`、`OrderLocked`、
`OrderDeductionRequested`、`OrderDeducted`、`OrderInsufficientFunds`、`OrderOperationFailed`。

## LeaseActor

LeaseState 的阶段为 `NEW / WAITING_LOCK / PROVISIONING / ACTIVE / RENEWING / EXPIRED / SUSPENDED / TERMINATING / TERMINATED`。
`currentPeriod` 保存订单、方案、金额和起止时间；`lastPeriodEndsAt` 保存最近租期的结束时间，
清空 currentPeriod 后仍保留，作为普通续租起点；暂停后的恢复不使用该时间。

- `pendingRenewal`：首期、普通续租或恢复后的待购买租期，保存订单 ID、期号、方案、方案版本、报价金额和 startsAt，不再保存独立阶段。
- `pendingRenewal.startsAt`：普通续租固定为上一期结束时间；首期和恢复时为空，冻结成功并确认实例 ACTIVE 后才确定新一期起点。
- amount=0 表示尚未保存报价，报价成功后立即保存金额，冻结阶段仍为 WAITING_LOCK。
- `pendingSettlement`：待结算订单，保存订单 ID、已确认的原金额和固定消费金额。
- `terminatedAt` 保存业务终止时间；退租时保留 currentPeriod 表示还需生成结算待办，生成后清空。

两个待办互斥。RENEWING 保存已受理的续租意图，期间先检查实例、结算旧单，确认结算完成并再次检查实例后才创建 pendingRenewal。
RENEWING 即使两个待办都为空也有工作要继续，不依赖 autoRenew 决定是否恢复。
WAITING_LOCK 内查询原订单、按需取价创建及等待冻结；确认 LOCKED 后进入 PROVISIONING，等待实例可用，金额保持不变。
SUSPENDED 表示实例 FAILED 后已停止计费，保留实例；只推进已有结算，不自动恢复购买。

业务命令为 `CreateLease`、`GetLeaseState`、`ChangeRenewalPlan`、`ConfigureAutoRenew`、`RenewLease`、`ResumeLease` 和 `TerminateLease`。
外部扫描发送 `ReconcileLease` 检查使用中的实例、触发到期和自动续租；内部 `PollLeaseProgress` 推进已有订单及实例待办，
订单请求通过 `OrderOperationCompleted` 返回，实例查询和创建/删除分别通过 `InstanceQueryCompleted`、
`InstanceOperationCompleted` 返回，恢复前的实例检查通过 `ResumeInstanceChecked` 返回，报价通过 `PriceQuoteCompleted` 返回。

事件为 `LeaseCreated`、`RenewalPlanChanged`、`AutoRenewConfigured`、`RenewalStarted`、`PurchaseRequested`、`PurchasePriced`、`PurchaseLocked`、
`SettlementRequested`、`SettlementCompleted`、`PeriodStarted`、`PeriodEnded`、`InstanceCreationRequested`、
`LeaseSuspended`、`LeaseTerminationStarted`、`LeaseTerminationPrepared`、`LeaseTerminated`、`LeaseRetryScheduled` 和 `LeaseReconciliationScheduled`。
两个 Actor 均直接持久化具体事件，由各自 eventHandler 更新状态。

## 创建与连续续租

首次购买流程：

```text
保存 pendingRenewal，租约进入 WAITING_LOCK → 查询原订单 → 不存在时异步取价
→ 报价返回 LeaseActor，校验当前 pollId
→ 保存 PurchasePriced，更新 pendingRenewal.amount → CreateOrder、lock
→ Lease 查询到 LOCKED，保存 PurchaseLocked，租约进入 PROVISIONING
→ 创建实例 → 查询到 ACTIVE → 开始首期
```

首次租期从实例交付确认时开始。每次重试先查询原订单，已存在则复用；不存在时，有已保存报价就直接创建，否则异步取价。
报价结果通过内部命令回到 LeaseActor；只处理当前轮次的结果，
先持久化 PurchasePriced，再使用保存的金额创建订单。退租或 Actor 恢复使旧轮次失效，旧报价回调直接丢弃。
报价保存后，重试和恢复均复用同一金额；报价尚未保存时可以重新取价，不增加租约阶段。
`OrderCreated` 保存订单金额，Lease 查询订单时核对金额一致；`PurchaseLocked` 保存冻结确认并推进租约阶段。

到期续租流程：

```text
RenewalStarted → RENEWING，保留当前租期 → 查询实例
→ 确认 ACTIVE，保存旧单全额 pendingSettlement、PeriodEnded，保持 RENEWING
→ SettleOrder、deduct → 确认旧单 SETTLED → SettlementCompleted，清空 pendingSettlement
→ 再查询实例，确认 ACTIVE → PurchaseRequested，创建 pendingRenewal，进入 WAITING_LOCK
→ 查询新订单，不存在时取价并保存 PurchasePriced，再 CreateOrder、lock
→ 确认 LOCKED，进入 PROVISIONING → 实例仍为 ACTIVE 才开始下一期，清空 pendingRenewal
```

检查到 FAILED 时进入 SUSPENDED，停止计费并保留实例，不生成新租期；DELETE_FAILED、删除中或 DELETED 仍终止租约。
尚未提交结算的当前租期按异常时间与本次暂停或终止时间的较小值结算。
重启、重建等过渡状态等待后重查；查询异常或超时保留待办重试，不视为实例故障。
恢复 RENEWING 时，已有 pendingSettlement 就继续结算；没有待办时查询实例，按 currentPeriod 是否存在决定结算旧单或开始购买新一期。
手动续租可以接入已有旧单结算，只记录续租意图并沿用该结算，完成后再检查实例。重复续租返回当前状态。
旧单结果未知时，不创建 pendingRenewal 或新订单。旧单扣款或新单冻结明确余额不足时，终止租约并删除实例；
其他错误保留实例运行、保留同一待办并重试。
关闭自动续租后，外部检查仍查询当前租期的实例：ACTIVE 且已到期时旧单全额结算，FAILED 时暂停计费，删除相关异常时终止；过渡状态和查询失败由后续扫描重试。
已经受理的 RENEWING 或 pendingRenewal 继续推进，没有待续租且实例正常时保持运行，等待手动续租或退租。

普通续租的下一期始终从上一期结束时间开始，购买待办保存该 startsAt。例如上一期 10:00 到期，新单 10:20 才冻结成功，
一小时的新租期仍为 10:00–11:00。开启自动续租时，已过期的续租周期由后续到期检查逐期结算、续接。
手动续租一次只发起一期，也沿用这个边界。

本版只结算订单覆盖的使用金额，未覆盖的运行时间不另建欠费或补偿账单。

## 暂停计费与恢复

使用中的租约由外部扫描定期查询实例，不必等到租期结束才检查。nextActionAt 取下次扫描间隔与本期结束时间的较小值。
查询到实例 FAILED 时，同批保存 LeaseSuspended 和按需生成的 SettlementRequested：

- 清空 currentPeriod 和 pendingRenewal，租约进入 SUSPENDED；不调用删除接口、不写 terminatedAt，保留 autoRenew 配置。
- 若当前租期已开始，按 `min(instance.failedAt, 本次查询时间)` 计算消费；没有 failedAt 时使用本次查询时间。
- 若新单已 LOCKED，但尚未开始租期，则按零消费结算；若旧单已结算且新单尚未创建，则无需生成结算。
- 消费金额固定在 pendingSettlement 中，结算失败继续重试，恢复重放不会重复生成待办。

暂停期间即使实例恢复 ACTIVE 或 autoRenew=true，也不会自动恢复计费。调用 `ResumeLease` 显式恢复：

```text
SUSPENDED，pendingSettlement 为空 → 查询原实例，确认 ACTIVE
→ PurchaseRequested(startsAt=null)，进入 WAITING_LOCK，返回受理结果
→ 取价、创建新订单、冻结 → PROVISIONING → 再次确认实例 ACTIVE
→ PeriodStarted，从本次可用确认时间开始新一期，进入 ACTIVE
```

不处于 SUSPENDED 返回 LEASE_NOT_SUSPENDED，旧结算未完成返回 ORDER_PENDING；已有恢复检查在途返回 LEASE_BUSY。
实例未恢复 ACTIVE 返回 INSTANCE_NOT_ACTIVE；查询失败返回错误，均保持 SUSPENDED，不创建订单，调用方可重试。
确认可用并持久化购买待办后才返回受理；此后 Actor 重启会继续同一笔购买，仍在暂停状态的租约不会因 Actor 重启自行恢复。
退租可以中断尚未受理的恢复检查，旧回调返回 RESUME_SUPERSEDED，不得创建订单或干扰新的轮询。
恢复购买受理后沿用订单待办限制，不能修改方案或退租；重复 resume 返回 LEASE_NOT_SUSPENDED，可查询已有待办进度。
冻结后实例再次 FAILED 会重新暂停并按零消费结算该新单；余额不足仍按原策略终止租约并删除实例。

例如旧期 10:00–11:00，10:20 实例失败，12:00 恢复后的订单已冻结且确认实例可用，新一期为 12:00–13:00。
故障至恢复之间不计费，普通续租仍沿用上一期结束时间；周期、报价和整分钟结算规则不变。
SUSPENDED 下可修改下一期方案，也可在结算完成后退租。调用 renew 返回 LEASE_SUSPENDED，不能绕过恢复流程。
ResumeLease 只确认原实例可用，不执行实例修复；实例恢复仍通过现有管理流程或运维完成，不修改 InstanceWorkflowActor。
现有重启/重建接口可重试相应管理操作的失败；创建阶段 FAILED 没有通用恢复入口，需先按现有能力修复或退租。

## 退租

用户发起退租时，租约不能处于 RENEWING，且 pendingRenewal 和 pendingSettlement 必须均为空；否则返回 HTTP 409、`error=ORDER_PENDING`，不进入退租流程。
这包含续租前的实例检查、首期或续租的取价、冻结、等待实例交付，以及尚未完成的结算。正常使用中的当前租期可以退租。
已经受理退租的重复请求返回原状态，继续已有收尾。余额不足或删除相关异常触发的自动终止不受上述入口限制。

手动退租先持久化 LeaseTerminationStarted，固定 `terminatedAt`、关闭自动续租并进入 TERMINATING；保留当前租期等待实例查询。
查询成功后，仅在 currentPeriod 非空时生成当前租期的结算：局部计算 `cutoff = min(instance.failedAt, terminatedAt)`，failedAt 为空时取 terminatedAt。
LeaseTerminationPrepared 保存终止时间和原因，清空当前租期及购买待办；同批持久化 SettlementRequested，保存固定的消费金额，始终保持待办互斥。
之后才提交钱包结算和实例删除。当前租期已经清空，恢复和重试直接复用 pendingSettlement.deductAmount，不再计算截止时间或金额。
退租时若当前租期和待办均为空，则无需生成结算，直接推进实例删除。
删除相关异常或余额不足触发自动终止时，可以直接保存 LeaseTerminationPrepared；实例异常使用本次查询到的 failedAt 计算当前租期的消费。
没有失败时间时使用终止时间，不把删除完成时间当作故障时间；等待查询、重复退租或恢复均不改变已保存的 terminatedAt。
当前租期按 `订单金额 × 已使用整分钟数 ÷ 租期总分钟数` 计算消费，不足一分钟的部分舍弃。
全程使用 BigDecimal 计算，最终消费金额使用 HALF_UP 四舍五入保留两位小数。
金额单位为元，截止时间限制在租期起止范围内，消费在零到订单金额之间。
沿用现有 deductAt 的首尾 5 分钟处理规则：开始不足 5 分钟按零消费，结束前不足 5 分钟按全额消费。

- 当前租期已开始：按实际使用金额结算当前订单。
- 新单冻结明确余额不足：终止待购买租期，无需再结算该失败订单。
- 旧单扣款明确余额不足：结束该结算待办并终止租约，不再创建下期订单。
- 冻结成功后、尚未生成 PeriodStarted 时实例 FAILED 或进入删除流程：首期、普通续租和恢复购买均按零消费结算；FAILED 保留实例，删除流程继续终止租约。

统一通过 `SettleOrder` 提交已确认的订单原金额和固定消费金额，不使用零原金额占位或补查金额。
订单不存在时拒绝结算并保留待办重试，SettleOrder 不补建或冻结新订单。
冻结结果未知期间保留原 pendingRenewal，待明确结果后再处理；不直接清除在途订单。
已经提交的结算金额不可更改；若结算后才获知更早的异常时间，差额退款需要独立处理，本版不自动补偿。

退租时停止新增续租，实例删除与已保存的结算待办独立推进。
尚未发起实例创建时直接完成资源收尾；已经创建的实例等待工作流进入 ACTIVE/FAILED/DELETE_FAILED 且无在途管理操作后，
调用既有删除接口，DELETE_FAILED 会触发删除重试。退租流程不再补发实例创建请求。
`TERMINATED` 表示资源清理完成，资金待办仍可继续重试；`settled` 表示租约处于 TERMINATING 或 TERMINATED，且当前租期和两个待办均为空。
余额不足通过订单阶段和租约 error 展示，settled 不等同于扣款成功。
SUSPENDED 不属于终止状态，settled=false；待结算完成后退租不会再次结算已清空的租期，直接删除实例。

## 方案、价格与配置

当前租期方案保持不变，修改只影响下一期。
`targetPeriodSeq` 必须为下一期，`expectedPlanRevision` 必须匹配当前方案版本。
RENEWING 或存在 pendingRenewal 时不允许修改方案，相同方案也拒绝，保证本次续租使用受理时的方案和版本。
关闭自动续租只影响后续周期，不取消已经受理的续租；用户需等待本次续租、购买和结算完成后再退租。

周期固定为 HOUR=1 小时、DAY=24 小时、WEEK=7 天、MONTH=30 天，再乘以正整数 quantity。
RentalPriceService 通过现有 HypervisorTypeRepo / MyBatis 查询 hypervisor_type，
按周期优先使用 `discount_*_pay_price`，未设置时取对应 `original_*_pay_price`。
元单价乘以 quantity，使用 HALF_UP 保留两位小数；每期按已保存的报价计费。

- `cloud.ask-timeout`：Actor 请求、取价的等待时间，以及钱包 HTTP 连接和读取的超时时间。
- `cloud.rental.retry-interval`：订单钱包重试及租约待办轮询间隔。
- `cloud.rental.scan-interval`：外部租约扫描间隔，也用于安排当前租期的实例状态检查。
- `cloud.projection.enabled`：启用查询投影和外部租约扫描。
- `fund-service.base-url`：FundWalletService 的资金服务地址，按其现有 Spring 配置读取。

Actor 配置继续通过 PekkoRuntime.Config getter 读取，资金服务配置沿用 FundWalletService。
两个 Actor 在 RecoveryCompleted 时清除内存请求标记，按持久化中间状态立即恢复已有待办。
LeaseActor 每轮依次推进订单和实例请求，等待响应期间不启动下一轮定时器；本轮完成或失败后，仍有进行中的续租、订单或资源清理才安排一次延迟轮询。
RENEWING 期间等待旧单结算确认后再检查实例；TERMINATING 期间即使结算尚未完成或失败，仍推进实例检查和删除。
实例创建、删除被拒绝时保存拒绝原因，下一轮先查询实例状态，再决定是否重试操作。
外部扫描不会打断进行中的轮询或跳过重试间隔。LeaseActor 只在内存中保存当前 `pollId`；
退租和 Actor 恢复使旧轮次失效，旧回调不会改变状态或调度下一轮。ResumeLease 的检查也使用 pollId，不能与其他实例请求并发推进。
手动续租沿用进行中的实例查询或旧单结算，完成后按 RENEWING 继续推进。
OrderActor 对异常、HTTP 超时、空响应或任务提交失败安排延迟重试。
使用中实例检查、到期和自动续租由外部扫描触发；SUSPENDED 只重试已有结算，不安排实例删除或自动恢复。

## 投影与查询

租约和订单投影消费具体事件，通过 MyBatis Mapper 更新 lease_view / order_view 的具体列。
lease_view 保存当前租期、续租/结算待办、terminated_at 和 next_action_at；order_view 保存订单金额、资金阶段和消费金额。
RenewalStarted 写入 RENEWING，PeriodEnded 保留已受理的续租阶段；PurchaseRequested 写入 WAITING_LOCK，PurchaseLocked 写入 PROVISIONING。
PurchasePriced 更新待购买金额；查询直接使用租约 phase，不另存购买子阶段。订单投影从 OrderCreated 开始建立。
待购买期单独保存订单 ID、金额和 pending_starts_at，期号按 periodSeq + 1 组装，方案和版本复用续租字段；购买期间这些字段不可修改。
结算完成只清空结算待办，保留 RENEWING；到期查询按 next_action_at 扫描，两个待办均为空的 RENEWING 也会被选中。
LeaseSuspended 写入 SUSPENDED，清空当前租期和购买字段，保留结算及自动续租配置；结算完成后仍为 SUSPENDED。
LeaseTerminationStarted 保留当前租期；LeaseTerminationPrepared 清空当前租期和购买待办，SettlementRequested 写入结算金额。查询按当前租期及两个待办是否为空计算 settled。

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
| POST /api/v1/leases/{leaseId}/resume | 暂停结算完成且实例 ACTIVE 后，受理恢复购买 |
| POST /api/v1/leases/{leaseId}/terminate | 无进行中的续租和订单待办时退租并结算 |

创建参数沿用 [实例接口](instances.md) 的 instance 对象，加 requestId、plan 和 autoRenew。
`instance.flavorId` 用于解析实例规格，`instance.hypervisorType` 用于取价。
同一用户和 requestId 标识同一次创建，重复请求返回原租约，保留已有规格和配置；创建新租约需使用新的 requestId。
控制器通过 LeaseService 直接提交业务命令，由 LeaseActor 串行判断是否受理，不根据列表投影预判。
RENEWING 即使两个待办都为空，修改方案仍返回 RENEWAL_PENDING，退租仍返回 ORDER_PENDING；重复续租返回当前状态。
配置 autoRenew 只影响后续周期，不取消已受理的续租。方案修改仍需携带 targetPeriodSeq 和 expectedPlanRevision，避免过期请求修改后续周期。

响应数据在 `result` 中。创建成功返回 HTTP 202，result 包含 lease、username、password 和 jupyterToken，受理不代表实例已交付。
修改方案、配置自动续租、续租、恢复和退租受理时返回 HTTP 202，拒绝时返回 HTTP 409；result 包含 accepted、error 和 lease。
上述受理/拒绝响应的 code 均为 200，应根据 HTTP 状态和 result.accepted 判断是否受理。
公开租约视图包含 pendingRenewal（含 startsAt）、pendingSettlement、lastPeriodEndsAt、terminatedAt 和 settled，不包含 instanceSpec。

请求示例见 [http/rentals.http](../http/rentals.http)。
