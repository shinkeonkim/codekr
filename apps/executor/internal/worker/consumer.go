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

// Start 는 concurrency 만큼의 소비 루프를 띄우고 ctx 가 끝날 때까지 돈다.
func (c *Consumer) Start(ctx context.Context) error {
	if err := ensureGroup(ctx, c.redis, contract.StreamExec, contract.GroupExec); err != nil {
		return err
	}

	var wg sync.WaitGroup
	for i := 0; i < c.concurrency; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			c.loop(ctx)
		}()
	}
	wg.Wait()
	return nil
}

func (c *Consumer) loop(ctx context.Context) {
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
				c.handle(ctx, message)
			}
		}
	}
}

func (c *Consumer) handle(ctx context.Context, message redis.XMessage) {
	// 메시지를 해석하지 못해도 ack 한다 — 재처리해도 같은 결과이므로 큐만 막는다.
	defer c.redis.XAck(ctx, contract.StreamExec, contract.GroupExec, message.ID)

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
