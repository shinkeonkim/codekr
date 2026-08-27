package worker

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"sync"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/shinkeonkim/codekr/apps/executor/internal/metrics"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// 응답 스트림은 채점기가 읽어 가면 지워지지만, 읽는 쪽이 사라진 경우를 대비해 TTL 을 둔다.
const replyStreamTTL = 5 * time.Minute

const (
	// ack 는 종료 중에도 반드시 나가야 한다 (#415). 취소되지 않는 ctx 를 쓰되 상한은 둔다.
	ackTimeout = 5 * time.Second

	// 회수 (#415). 채점기와 같은 이유·같은 규칙이다 — 자세한 근거는 그쪽 주석에 있다.
	reclaimInterval = 1 * time.Minute
	reclaimMinIdle  = 10 * time.Minute
	reclaimBatch    = 10
	maxDeliveries   = 3
)

// Consumer 는 실행 큐를 소비해 Runner 에 넘기고 결과를 응답 스트림에 넣는다.
type Consumer struct {
	redis        *redis.Client
	runner       *Runner
	consumerName string
	concurrency  int
	// 읽을 실행 큐. **대회부터** 나열된다 (#639).
	streams []string
	log     *slog.Logger
}

// NewConsumer 는 실행 큐 소비자를 만든다.
//
// **모든 실행기가 모든 큐를 읽는다** (#639). 대회 전용 실행기를 따로 두지 않은 이유:
// 대회가 없는 동안 그 실행기가 놀고, 있는 동안에는 나머지가 논다. 우선순위로 두면
// 대회가 없을 때 모든 실행기가 평소 제출을 처리한다.
func NewConsumer(client *redis.Client, runner *Runner, consumerName string, concurrency int, log *slog.Logger) *Consumer {
	return &Consumer{
		redis:        client,
		runner:       runner,
		consumerName: consumerName,
		concurrency:  concurrency,
		streams:      contract.ExecStreamsByPriority(),
		log:          log,
	}
}

/*
Start 는 [ctx] 가 끝나면 **새 작업을 그만 받는다.** 하던 실행은 끝까지 간다 (#415).

채점기와 같은 결함을 갖고 있었다 — SIGTERM 이 실행 중인 컨테이너의 ctx 를 끊고,
같은 ctx 로 하는 ack 까지 거부되어 메시지가 PEL 에 남았다. 그러면 채점기는 응답을
받지 못해 그 테스트케이스를 실패로 적는다.

[drain] 은 "끝까지" 의 상한이다. 파드의 `terminationGracePeriodSeconds` 보다 짧아야 한다.
*/
func (c *Consumer) Start(ctx context.Context, drain time.Duration) error {
	// 차선마다 스트림이 따로 있다 (#639). 하나라도 그룹이 없으면 그 차선을 못 읽는다.
	for _, stream := range c.streams {
		if err := ensureGroup(ctx, c.redis, stream); err != nil {
			return err
		}
	}

	workCtx, stopWork := context.WithCancel(context.WithoutCancel(ctx))
	defer stopWork()
	go func() {
		<-ctx.Done()
		c.log.Info("종료 신호를 받았습니다. 하던 실행을 마치는 중입니다", "drain", drain)
		select {
		case <-time.After(drain):
			c.log.Warn("유예 시간이 지나 남은 실행을 끊습니다")
			stopWork()
		case <-workCtx.Done():
		}
	}()

	go c.reclaimLoop(ctx, workCtx)

	var wg sync.WaitGroup
	for i := 0; i < c.concurrency; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			c.loop(ctx, workCtx)
		}()
	}
	wg.Wait()
	return nil
}

// loop 은 [ctx] 로 큐를 읽고 [workCtx] 로 실행한다 (#415).
//
// **차선 순서대로 읽는다** (#639). 채점기와 같은 구조다 — 자세한 근거는 `priority.go`.
func (c *Consumer) loop(ctx, workCtx context.Context) {
	for cycle := 0; ctx.Err() == nil; cycle++ {
		if c.pickOne(ctx, workCtx, cycle) {
			continue
		}
		c.waitForAny(ctx, workCtx)
	}
}

/*
pickOne 은 차선 순서대로 훑어 **처음 발견한 작업 하나**를 처리한다 (#639).

차선마다 따로, 논블로킹으로 읽는 이유: 여러 스트림을 한 번에 블로킹으로 읽으면 Redis 가
"먼저 들어온 것" 을 주기 때문에 **순서가 무시된다.** 순서는 우리가 정해야 한다.
*/
func (c *Consumer) pickOne(ctx, workCtx context.Context, cycle int) bool {
	for _, stream := range streamOrder(c.streams, cycle) {
		streams, err := c.redis.XReadGroup(ctx, &redis.XReadGroupArgs{
			Group:    contract.GroupExec,
			Consumer: c.consumerName,
			Streams:  []string{stream, ">"},
			Count:    1,
			Block:    -1, // 논블로킹. 비어 있으면 바로 다음 차선으로 넘어간다.
		}).Result()

		if err != nil {
			if errors.Is(err, redis.Nil) || ctx.Err() != nil {
				continue
			}
			c.log.Error("실행 큐 읽기 실패", "stream", stream, "error", err)
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

// waitForAny 는 어느 차선이든 작업이 들어올 때까지 잠깐 기다린다.
//
// 여기서는 순서가 무시되지만 상관없다 — 어차피 큐가 비어 있어 경쟁할 것이 없다.
// 깨어난 뒤 다음 차례부터 다시 차선 순서대로 읽는다.
func (c *Consumer) waitForAny(ctx, workCtx context.Context) {
	// XReadGroup 은 스트림 이름 뒤에 같은 수의 ID 를 요구한다 — 그래서 두 배다.
	args := make([]string, 0, len(c.streams)*2)
	args = append(args, c.streams...)
	for range c.streams {
		args = append(args, ">")
	}

	streams, err := c.redis.XReadGroup(ctx, &redis.XReadGroupArgs{
		Group:    contract.GroupExec,
		Consumer: c.consumerName,
		Streams:  args,
		Count:    1,
		Block:    2 * time.Second,
	}).Result()

	if err != nil {
		if !errors.Is(err, redis.Nil) && ctx.Err() == nil {
			c.log.Error("실행 큐 대기 실패", "error", err)
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
	// 메시지를 해석하지 못해도 ack 한다 — 재처리해도 같은 결과이므로 큐만 막는다.
	defer c.ack(ctx, stream, message.ID)

	payload, ok := message.Values[contract.MessagePayloadKey].(string)
	if !ok {
		c.log.Error("실행 작업 형식이 올바르지 않습니다", "messageId", message.ID)
		return
	}

	var job contract.ExecJob
	if err := json.Unmarshal([]byte(payload), &job); err != nil {
		c.log.Error("실행 작업 파싱 실패", "messageId", message.ID, "error", err)
		return
	}

	started := time.Now()
	result := c.runner.Run(ctx, job)
	metrics.Executed(job.RuntimeID, time.Since(started))
	c.log.Info("실행 완료",
		"jobId", job.JobID, "runtime", job.RuntimeID,
		"status", result.Status, "elapsedMs", time.Since(started).Milliseconds())

	c.reply(ctx, job, result)
}

func (c *Consumer) reply(ctx context.Context, job contract.ExecJob, result contract.ExecResult) {
	encoded, err := json.Marshal(result)
	if err != nil {
		c.log.Error("실행 결과 직렬화 실패", "jobId", job.JobID, "error", err)
		return
	}
	if err := c.redis.XAdd(ctx, &redis.XAddArgs{
		Stream: job.ReplyStream,
		Values: map[string]any{contract.MessagePayloadKey: string(encoded)},
	}).Err(); err != nil {
		c.log.Error("실행 결과 전송 실패", "jobId", job.JobID, "error", err)
		return
	}
	c.redis.Expire(ctx, job.ReplyStream, replyStreamTTL)
}

// ensureGroup 은 consumer group 이 없으면 만든다. 이미 있으면 조용히 넘어간다.
// group 은 늘 [contract.GroupExec] 다. 인자로 받으면 **다를 수 있다는 뜻**이 되고,
// 실제로는 그런 호출이 없다.
func ensureGroup(ctx context.Context, client *redis.Client, stream string) error {
	err := client.XGroupCreateMkStream(ctx, stream, contract.GroupExec, "0").Err()
	if err != nil && !isBusyGroup(err) {
		return err
	}
	return nil
}

func isBusyGroup(err error) bool {
	return err != nil && err.Error() == "BUSYGROUP Consumer Group name already exists"
}

// ack 는 **취소되지 않는 ctx 로** 한다 (#415). 종료 중에도 반드시 나가야 하는 호출이다.
// **어느 스트림에서 온 것인지 받아야 한다** (#639). 큐가 여럿이 된 뒤로,
// 고정된 이름으로 ack 하면 **다른 큐의 작업이 영원히 PEL 에 남는다.**
func (c *Consumer) ack(ctx context.Context, stream, id string) {
	ackCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), ackTimeout)
	defer cancel()
	if err := c.redis.XAck(ackCtx, stream, contract.GroupExec, id).Err(); err != nil {
		c.log.Error("ack 실패", "stream", stream, "messageId", id, "error", err)
	}
}

/*
reclaimLoop 은 죽은 소비자가 놓고 간 실행 작업을 되찾는다 (#415).

소비자 이름이 파드 이름이라 **배포하면 그 이름은 영영 돌아오지 않는다.** 유예 종료를
갖춰도 프로세스가 강제로 죽는 경우는 남는다.

다만 실행 작업은 **응답 스트림에 TTL 이 있다**(5분). 그보다 늦게 회수하면 채점기는
이미 기다리기를 그만두었을 수 있다 — 그래도 다시 실행하는 편이 낫다. 그 채점 작업
자체가 채점기 쪽 회수로 다시 오기 때문이다.
*/
func (c *Consumer) reclaimLoop(readCtx, workCtx context.Context) {
	ticker := time.NewTicker(reclaimInterval)
	defer ticker.Stop()
	for {
		select {
		case <-readCtx.Done():
			return
		case <-ticker.C:
			// 차선마다 따로 본다 (#639) — 한 스트림만 보면 나머지의 놓친 작업이 영영 남는다.
			for _, stream := range c.streams {
				c.reclaim(readCtx, workCtx, stream)
			}
		}
	}
}

func (c *Consumer) reclaim(readCtx, workCtx context.Context, stream string) {
	pending, err := c.redis.XPendingExt(readCtx, &redis.XPendingExtArgs{
		Stream: stream,
		Group:  contract.GroupExec,
		// 지금 누가 실행 중인 것을 빼앗지 않는다.
		Idle:  reclaimMinIdle,
		Start: "-",
		End:   "+",
		Count: reclaimBatch,
	}).Result()
	if err != nil {
		if !errors.Is(err, redis.Nil) && readCtx.Err() == nil {
			c.log.Error("밀린 실행 작업 조회 실패", "stream", stream, "error", err)
		}
		return
	}

	for _, entry := range pending {
		if entry.RetryCount > maxDeliveries {
			c.log.Error("여러 번 실패한 실행 작업을 포기합니다",
				"stream", stream, "messageId", entry.ID, "deliveries", entry.RetryCount)
			metrics.Reclaimed(metrics.OutcomeDropped)
			c.ack(workCtx, stream, entry.ID)
			continue
		}

		messages, err := c.redis.XClaim(readCtx, &redis.XClaimArgs{
			Stream:   stream,
			Group:    contract.GroupExec,
			Consumer: c.consumerName,
			MinIdle:  reclaimMinIdle,
			Messages: []string{entry.ID},
		}).Result()
		if err != nil {
			if readCtx.Err() == nil {
				c.log.Error("밀린 실행 작업 회수 실패", "stream", stream, "messageId", entry.ID, "error", err)
			}
			continue
		}

		for _, message := range messages {
			c.log.Warn("놓친 실행 작업을 다시 실행합니다",
				"stream", stream, "messageId", message.ID, "idle", entry.Idle,
				"deliveries", entry.RetryCount, "from", entry.Consumer)
			metrics.Reclaimed(metrics.OutcomeReclaimed)
			c.handle(workCtx, stream, message)
		}
	}
}
