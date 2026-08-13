// Package worker 는 채점 큐를 소비해 채점 서비스에 넘긴다.
package worker

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"sync"
	"sync/atomic"
	"time"

	"github.com/redis/go-redis/v9"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// Judger 는 채점 작업 하나를 처리한다.
type Judger interface {
	Judge(ctx context.Context, job contract.JudgeJob)
}

// Consumer 는 채점 큐를 소비한다.
type Consumer struct {
	redis        *redis.Client
	judger       Judger
	consumerName string
	concurrency  int
	// streams 는 이 워커가 읽을 스트림이다. 차선(#62)이 정한다.
	streams []string
	lane    string
	// running 은 지금 도는 루프 수다 (#390). 조정과 상태 보고가 같은 값을 본다.
	running atomic.Int32
	/*
		reclaimMinIdle 은 "얼마나 놀았으면 죽은 것으로 볼지" 다 (#415).

		**필드로 두는 이유는 시험 때문만이 아니다.** 이 값은 채점 한 건이 걸릴 수 있는
		시간에 매여 있고, 그것은 배포마다 다를 수 있다.
	*/
	reclaimMinIdle time.Duration
	log            *slog.Logger
}

const (
	concurrencyPollInterval = 5 * time.Second
	minConcurrency          = 1
	// 기동 설정의 몇 배까지 허용할지. 실수로 큰 수를 넣어도 노드가 죽지 않아야 한다.
	maxConcurrencyFactor = 8

	// ack 는 **끊긴 ctx 로 하면 실패한다** (#415). 종료 중에도 반드시 나가야 하는
	// 호출이라 취소되지 않는 ctx 를 쓰되, 영원히 매달리지는 않게 시간을 준다.
	ackTimeout = 5 * time.Second

	/*
		회수 주기와 "얼마나 놀았으면 죽은 것으로 볼지" (#415).

		**minIdle 은 채점 한 건이 걸릴 수 있는 시간보다 넉넉히 길어야 한다.** 짧으면
		멀쩡히 일하는 워커의 작업을 다른 워커가 빼앗아 같은 제출을 두 번 채점한다.

		10분은 API 의 `StaleSubmissionSweeper`(3분)보다 한참 뒤다 — 그래서 순서는
		이렇게 된다: 화면은 3분 만에 SYSTEM_ERROR 로 풀리고(멈춘 화면을 막는 것이
		그 장치의 목적이다), 그 뒤 회수가 **다시 채점해서 판정을 덮어쓴다.**
	*/
	reclaimInterval = 1 * time.Minute
	reclaimMinIdle  = 10 * time.Minute
	// 한 번에 회수할 수 있는 개수. 한꺼번에 다 물면 회수가 곧 부하가 된다.
	reclaimBatch = 10
	/*
		**몇 번까지 다시 채점할 것인가** (#415).

		무한히 재시도하면 정말 채점기를 죽이는 제출이 영원히 돌면서 큐를 막는다.
		상한을 넘으면 ack 하고 남긴다 — 그 제출은 API 의 안전망이 SYSTEM_ERROR 로
		끝내고, 로그가 어느 제출이었는지 말한다.
	*/
	maxDeliveries = 3
)

// NewConsumer 는 채점 큐 소비자를 만든다.
//
// lane 은 이 워커가 설 차선이다 (#62). 대회 워커와 일반 워커를 나누는 것이
// 격리의 전부다 — 등급 순서를 어떻게 정하든 워커 수가 유한하기 때문이다.
func NewConsumer(
	client *redis.Client,
	judger Judger,
	consumerName string,
	concurrency int,
	lane string,
	log *slog.Logger,
) *Consumer {
	return &Consumer{
		redis:          client,
		judger:         judger,
		consumerName:   consumerName,
		concurrency:    concurrency,
		streams:        contract.JudgeStreamsFor(lane),
		lane:           lane,
		reclaimMinIdle: reclaimMinIdle,
		log:            log,
	}
}

/*
Start 는 소비 루프를 띄우고 ctx 가 끝날 때까지 돈다.

**루프 수는 도는 중에 바뀐다** (#390). 큐가 밀릴 때 워커를 늘리려면 전에는 배포를
다시 해야 했는데, 늘리려는 상황이 곧 재시작하면 안 되는 상황이다 — 진행 중인 채점이
끊긴다. 그래서 원하는 수를 Redis 에서 주기적으로 읽고 그만큼 맞춘다.

**줄일 때 진행 중인 채점을 끊지 않는다.** 루프마다 ctx 를 따로 주고 그것을 취소하면,
그 루프는 **지금 처리 중인 것을 끝낸 뒤** 다음 차례에 빠져나간다.
*/
/*
Start 는 [ctx] 가 끝나면 **새 작업을 그만 받는다.** 하던 채점은 끝까지 간다 (#415).

전에는 SIGTERM 이 진행 중인 채점의 ctx 를 그 자리에서 끊었다. 그러면 채점이 중단되고,
같은 ctx 로 하는 ack 까지 거부되어 메시지가 PEL 에 남고, 3분 뒤 API 가 그 제출을
SYSTEM_ERROR 로 끝냈다 — **배포할 때마다 채점 중이던 제출이 실패로 확정됐다.**

[drain] 은 그 "끝까지" 의 상한이다. 파드의 `terminationGracePeriodSeconds` 보다
짧아야 한다 — 그러지 않으면 어차피 SIGKILL 로 끊긴다.
*/
func (c *Consumer) Start(ctx context.Context, drain time.Duration) error {
	// 차선을 잘못 적으면 읽을 스트림이 없다. 조용히 도는 대신 여기서 끊는다 (#62).
	if len(c.streams) == 0 {
		return errors.New("읽을 채점 스트림이 없습니다. CODEKR_JUDGE_LANE 을 확인하십시오")
	}
	// 등급마다 스트림이 따로 있다 (#102). 하나라도 그룹이 없으면 그 등급을 못 읽는다.
	for _, stream := range c.streams {
		if err := ensureGroup(ctx, c.redis, stream, contract.GroupJudge); err != nil {
			return err
		}
	}

	/*
		**일하는 ctx 와 멈추라는 신호를 나눈다.** 읽기는 [ctx] 로 하고(끝나면 새 작업을
		안 받는다), 채점과 ack 는 취소되지 않는 ctx 로 한다.

		그래도 상한은 둔다 — 종료 유예가 지나면 어차피 SIGKILL 이므로, 그 전에 스스로
		정리하는 편이 낫다.
	*/
	workCtx, stopWork := context.WithCancel(context.WithoutCancel(ctx))
	defer stopWork()
	go func() {
		<-ctx.Done()
		c.log.Info("종료 신호를 받았습니다. 하던 채점을 마치는 중입니다", "lane", c.lane, "drain", drain)
		select {
		case <-time.After(drain):
			c.log.Warn("유예 시간이 지나 남은 채점을 끊습니다", "lane", c.lane)
			stopWork()
		case <-workCtx.Done():
		}
	}()

	go c.reclaimLoop(ctx, workCtx)

	var wg sync.WaitGroup
	stops := make([]context.CancelFunc, 0, c.concurrency)

	setRunning := func() { c.running.Store(int32(len(stops))) }
	grow := func(to int) {
		for len(stops) < to {
			loopCtx, stop := context.WithCancel(ctx)
			stops = append(stops, stop)
			wg.Add(1)
			go func() {
				defer wg.Done()
				c.loop(loopCtx, workCtx)
			}()
		}
		setRunning()
	}
	shrink := func(to int) {
		for len(stops) > to {
			last := len(stops) - 1
			stops[last]()
			stops = stops[:last]
		}
		setRunning()
	}

	grow(c.concurrency)
	c.log.Info("채점 워커 시작", "lane", c.lane, "workers", len(stops))

	ticker := time.NewTicker(concurrencyPollInterval)
	defer ticker.Stop()
	for ctx.Err() == nil {
		select {
		case <-ctx.Done():
		case <-ticker.C:
			desired := c.desiredConcurrency(ctx)
			if desired == len(stops) {
				continue
			}
			c.log.Info("채점 워커 수를 바꿉니다", "lane", c.lane, "from", len(stops), "to", desired)
			grow(desired)
			shrink(desired)
		}
	}

	// 남은 루프들은 ctx 가 끝나며 함께 멈춘다. 취소 함수는 자원 정리용으로만 부른다.
	for _, stop := range stops {
		stop()
	}
	wg.Wait()
	return nil
}

// current 는 지금 도는 루프 수다.
func (c *Consumer) current() int {
	if running := int(c.running.Load()); running > 0 {
		return running
	}
	return c.concurrency
}

/*
desiredConcurrency 는 지금 몇 개로 돌아야 하는지 읽는다.

**읽지 못하면 지금 값을 유지한다.** Redis 가 잠깐 흔들렸다고 워커 수가 기본값으로
되돌아가면, 늘려 둔 것이 조용히 사라진다 — 그리고 그것을 아무도 모른다.

상한은 기동 설정의 배수로 둔다. 어드민이 실수로 큰 수를 넣어도 노드가 죽지 않아야 한다.
*/
func (c *Consumer) desiredConcurrency(ctx context.Context) int {
	value, err := c.redis.Get(ctx, contract.JudgeConcurrencyKey(c.lane)).Int()
	if err != nil {
		if !errors.Is(err, redis.Nil) && ctx.Err() == nil {
			c.log.Warn("워커 수를 읽지 못했습니다. 지금 값을 유지합니다", "error", err)
		}
		return c.current()
	}
	return clampConcurrency(value, c.concurrency)
}

// clampConcurrency 는 받은 값을 쓸 수 있는 범위로 자른다.
//
// **0 을 허용하지 않는다.** 0 이면 그 차선의 채점이 통째로 멈추는데, 화면에서 그것은
// "적체" 로 보인다 — 원인이 조정이라는 것을 아무도 모른다.
func clampConcurrency(value, base int) int {
	// `max` 라고 부르지 않는다 — Go 1.21 의 내장 함수를 가린다 (gocritic builtinShadow).
	upper := base * maxConcurrencyFactor
	if upper < minConcurrency {
		upper = minConcurrency
	}
	if value < minConcurrency {
		return minConcurrency
	}
	if value > upper {
		return upper
	}
	return value
}

// loop 은 [readCtx] 로 큐를 읽고 [workCtx] 로 채점한다 (#415).
//
// 앞이 끝나면 **새 작업을 안 받는다.** 뒤는 하던 것을 끝내라는 뜻이므로 종료 신호에
// 끊기지 않는다.
func (c *Consumer) loop(readCtx, workCtx context.Context) {
	cycle := 0
	for readCtx.Err() == nil {
		cycle++
		if c.pickOne(readCtx, workCtx, cycle) {
			continue
		}
		// 어느 등급에도 일이 없다. 아무 데나 들어오면 깨어나도록 한 번만 블로킹으로 기다린다.
		c.waitForAny(readCtx, workCtx)
	}
}

/*
pickOne 은 등급 순서대로 훑어 **처음 발견한 작업 하나**를 처리한다.

등급마다 따로, 논블로킹으로 읽는 이유: 여러 스트림을 한 번에 블로킹으로 읽으면 Redis 가
"먼저 들어온 것" 을 주기 때문에 등급이 무시된다. 순서는 우리가 정해야 한다.
*/
func (c *Consumer) pickOne(ctx, workCtx context.Context, cycle int) bool {
	for _, stream := range streamOrder(c.streams, cycle) {
		streams, err := c.redis.XReadGroup(ctx, &redis.XReadGroupArgs{
			Group:    contract.GroupJudge,
			Consumer: c.consumerName,
			Streams:  []string{stream, ">"},
			Count:    1,
			NoAck:    false,
			Block:    -1, // 논블로킹. 비어 있으면 바로 다음 등급으로 넘어간다.
		}).Result()

		if err != nil {
			if errors.Is(err, redis.Nil) || ctx.Err() != nil {
				continue
			}
			c.log.Error("채점 큐 읽기 실패", "stream", stream, "error", err)
			time.Sleep(time.Second)
			continue
		}

		handled := false
		for _, result := range streams {
			for _, message := range result.Messages {
				c.handle(workCtx, result.Stream, message)
				handled = true
			}
		}
		if handled {
			return true
		}
	}
	return false
}

// waitForAny 는 어느 등급이든 작업이 들어올 때까지 잠깐 기다린다.
//
// 여기서는 등급이 무시되지만 상관없다 — 어차피 큐가 비어 있어 경쟁할 것이 없다.
// 깨어난 뒤 다음 차례부터 다시 등급 순서대로 읽는다.
func (c *Consumer) waitForAny(ctx, workCtx context.Context) {
	// XReadGroup 은 스트림 이름 뒤에 같은 수의 ID 를 요구한다 — 그래서 두 배다.
	args := make([]string, 0, len(c.streams)*2)
	args = append(args, c.streams...)
	for range c.streams {
		args = append(args, ">")
	}

	streams, err := c.redis.XReadGroup(ctx, &redis.XReadGroupArgs{
		Group:    contract.GroupJudge,
		Consumer: c.consumerName,
		Streams:  args,
		Count:    1,
		Block:    2 * time.Second,
	}).Result()

	if err != nil {
		if !errors.Is(err, redis.Nil) && ctx.Err() == nil {
			c.log.Error("채점 큐 대기 실패", "error", err)
			time.Sleep(time.Second)
		}
		return
	}

	for _, result := range streams {
		for _, message := range result.Messages {
			c.handle(workCtx, result.Stream, message)
		}
	}
}

func (c *Consumer) handle(ctx context.Context, stream string, message redis.XMessage) {
	// 채점을 마쳤든 파싱에 실패했든 ack 한다 — 재시도해도 같은 결과다.
	// ack 는 **읽어 온 그 스트림에** 해야 한다.
	defer c.ack(ctx, stream, message.ID)

	payload, ok := message.Values[contract.MessagePayloadKey].(string)
	if !ok {
		c.log.Error("채점 작업 형식이 올바르지 않습니다", "messageId", message.ID)
		return
	}

	var job contract.JudgeJob
	if err := json.Unmarshal([]byte(payload), &job); err != nil {
		c.log.Error("채점 작업 파싱 실패", "messageId", message.ID, "error", err)
		return
	}

	c.judger.Judge(ctx, job)
}

// ensureGroup 은 consumer group 이 없으면 만든다. 이미 있으면 조용히 넘어간다.
func ensureGroup(ctx context.Context, client *redis.Client, stream, group string) error {
	err := client.XGroupCreateMkStream(ctx, stream, group, "0").Err()
	if err != nil && err.Error() != "BUSYGROUP Consumer Group name already exists" {
		return err
	}
	return nil
}

/*
ack 는 **취소되지 않는 ctx 로** 한다 (#415).

전에는 채점에 쓰던 ctx 를 그대로 썼다. 종료 신호가 오면 그 ctx 는 이미 끊겨 있어
ack 자체가 거부되고, 메시지가 PEL 에 남은 채 아무도 되찾지 않았다.
*/
func (c *Consumer) ack(ctx context.Context, stream, id string) {
	ackCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), ackTimeout)
	defer cancel()
	if err := c.redis.XAck(ackCtx, stream, contract.GroupJudge, id).Err(); err != nil {
		// 여기까지 실패하면 회수 경로가 다시 물어 온다. 조용히 넘기지는 않는다.
		c.log.Error("ack 실패", "stream", stream, "messageId", id, "error", err)
	}
}

/*
reclaimLoop 은 **죽은 소비자가 놓고 간 작업을 되찾는다** (#415).

Redis Streams 는 읽어 간 메시지를 ack 할 때까지 PEL 에 들고 있는데, 소비자 이름이
파드 이름이라 **배포하면 그 이름은 영영 돌아오지 않는다.** 위의 유예 종료를 다 갖춰도
프로세스가 강제로 죽는 경우(OOM·노드 장애)는 남는다 — 그때 되찾는 것이 여기다.

[readCtx] 가 끝나면 회수도 멈춘다. 새로 물어 오는 일이므로 종료 중에 할 일이 아니다.
*/
func (c *Consumer) reclaimLoop(readCtx, workCtx context.Context) {
	ticker := time.NewTicker(reclaimInterval)
	defer ticker.Stop()
	for {
		select {
		case <-readCtx.Done():
			return
		case <-ticker.C:
			for _, stream := range c.streams {
				c.reclaimStream(readCtx, workCtx, stream)
			}
		}
	}
}

func (c *Consumer) reclaimStream(readCtx, workCtx context.Context, stream string) {
	pending, err := c.redis.XPendingExt(readCtx, &redis.XPendingExtArgs{
		Stream: stream,
		Group:  contract.GroupJudge,
		// **놀고 있는 것만 본다.** 지금 누군가 채점 중인 작업을 빼앗으면 같은 제출이
		// 두 번 채점된다.
		Idle:  c.reclaimMinIdle,
		Start: "-",
		End:   "+",
		Count: reclaimBatch,
	}).Result()
	if err != nil {
		if !errors.Is(err, redis.Nil) && readCtx.Err() == nil {
			c.log.Error("밀린 작업 조회 실패", "stream", stream, "error", err)
		}
		return
	}

	for _, entry := range pending {
		if entry.RetryCount > maxDeliveries {
			// **더 물지 않는다.** 채점기를 죽이는 제출이 영원히 돌면 큐가 그만큼 막힌다.
			c.log.Error("여러 번 실패한 채점 작업을 포기합니다",
				"stream", stream, "messageId", entry.ID, "deliveries", entry.RetryCount)
			c.ack(workCtx, stream, entry.ID)
			continue
		}

		messages, err := c.redis.XClaim(readCtx, &redis.XClaimArgs{
			Stream:   stream,
			Group:    contract.GroupJudge,
			Consumer: c.consumerName,
			MinIdle:  c.reclaimMinIdle,
			Messages: []string{entry.ID},
		}).Result()
		if err != nil {
			if readCtx.Err() == nil {
				c.log.Error("밀린 작업 회수 실패", "stream", stream, "messageId", entry.ID, "error", err)
			}
			continue
		}

		for _, message := range messages {
			c.log.Warn("놓친 채점 작업을 다시 채점합니다",
				"stream", stream, "messageId", message.ID,
				"idle", entry.Idle, "deliveries", entry.RetryCount, "from", entry.Consumer)
			c.handle(workCtx, stream, message)
		}
	}
}
