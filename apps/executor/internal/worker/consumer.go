package worker

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"sync"
	"time"

	"github.com/redis/go-redis/v9"
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
	log          *slog.Logger
}

// NewConsumer 는 실행 큐 소비자를 만든다.
func NewConsumer(client *redis.Client, runner *Runner, consumerName string, concurrency int, log *slog.Logger) *Consumer {
	return &Consumer{redis: client, runner: runner, consumerName: consumerName, concurrency: concurrency, log: log}
}

/*
Start 는 [ctx] 가 끝나면 **새 작업을 그만 받는다.** 하던 실행은 끝까지 간다 (#415).

채점기와 같은 결함을 갖고 있었다 — SIGTERM 이 실행 중인 컨테이너의 ctx 를 끊고,
같은 ctx 로 하는 ack 까지 거부되어 메시지가 PEL 에 남았다. 그러면 채점기는 응답을
받지 못해 그 테스트케이스를 실패로 적는다.

[drain] 은 "끝까지" 의 상한이다. 파드의 `terminationGracePeriodSeconds` 보다 짧아야 한다.
*/
func (c *Consumer) Start(ctx context.Context, drain time.Duration) error {
	if err := ensureGroup(ctx, c.redis, contract.StreamExec, contract.GroupExec); err != nil {
		return err
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
func (c *Consumer) loop(ctx, workCtx context.Context) {
	for ctx.Err() == nil {
		streams, err := c.redis.XReadGroup(ctx, &redis.XReadGroupArgs{
			Group:    contract.GroupExec,
			Consumer: c.consumerName,
			Streams:  []string{contract.StreamExec, ">"},
			Count:    1,
			Block:    2 * time.Second,
		}).Result()

		if err != nil {
			if errors.Is(err, redis.Nil) || ctx.Err() != nil {
				continue
			}
			c.log.Error("실행 큐 읽기 실패", "error", err)
			time.Sleep(time.Second)
			continue
		}

		for _, stream := range streams {
			for _, message := range stream.Messages {
				c.handle(workCtx, message)
			}
		}
	}
}

func (c *Consumer) handle(ctx context.Context, message redis.XMessage) {
	// 메시지를 해석하지 못해도 ack 한다 — 재처리해도 같은 결과이므로 큐만 막는다.
	defer c.ack(ctx, message.ID)

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
func ensureGroup(ctx context.Context, client *redis.Client, stream, group string) error {
	err := client.XGroupCreateMkStream(ctx, stream, group, "0").Err()
	if err != nil && !isBusyGroup(err) {
		return err
	}
	return nil
}

func isBusyGroup(err error) bool {
	return err != nil && err.Error() == "BUSYGROUP Consumer Group name already exists"
}

// ack 는 **취소되지 않는 ctx 로** 한다 (#415). 종료 중에도 반드시 나가야 하는 호출이다.
func (c *Consumer) ack(ctx context.Context, id string) {
	ackCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), ackTimeout)
	defer cancel()
	if err := c.redis.XAck(ackCtx, contract.StreamExec, contract.GroupExec, id).Err(); err != nil {
		c.log.Error("ack 실패", "messageId", id, "error", err)
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
			c.reclaim(readCtx, workCtx)
		}
	}
}

func (c *Consumer) reclaim(readCtx, workCtx context.Context) {
	pending, err := c.redis.XPendingExt(readCtx, &redis.XPendingExtArgs{
		Stream: contract.StreamExec,
		Group:  contract.GroupExec,
		// 지금 누가 실행 중인 것을 빼앗지 않는다.
		Idle:  reclaimMinIdle,
		Start: "-",
		End:   "+",
		Count: reclaimBatch,
	}).Result()
	if err != nil {
		if !errors.Is(err, redis.Nil) && readCtx.Err() == nil {
			c.log.Error("밀린 실행 작업 조회 실패", "error", err)
		}
		return
	}

	for _, entry := range pending {
		if entry.RetryCount > maxDeliveries {
			c.log.Error("여러 번 실패한 실행 작업을 포기합니다",
				"messageId", entry.ID, "deliveries", entry.RetryCount)
			c.ack(workCtx, entry.ID)
			continue
		}

		messages, err := c.redis.XClaim(readCtx, &redis.XClaimArgs{
			Stream:   contract.StreamExec,
			Group:    contract.GroupExec,
			Consumer: c.consumerName,
			MinIdle:  reclaimMinIdle,
			Messages: []string{entry.ID},
		}).Result()
		if err != nil {
			if readCtx.Err() == nil {
				c.log.Error("밀린 실행 작업 회수 실패", "messageId", entry.ID, "error", err)
			}
			continue
		}

		for _, message := range messages {
			c.log.Warn("놓친 실행 작업을 다시 실행합니다",
				"messageId", message.ID, "idle", entry.Idle,
				"deliveries", entry.RetryCount, "from", entry.Consumer)
			c.handle(workCtx, message)
		}
	}
}
