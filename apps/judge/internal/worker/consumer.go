// Package worker 는 채점 큐를 소비해 채점 서비스에 넘긴다.
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
	log          *slog.Logger
}

// NewConsumer 는 채점 큐 소비자를 만든다.
func NewConsumer(client *redis.Client, judger Judger, consumerName string, concurrency int, log *slog.Logger) *Consumer {
	return &Consumer{redis: client, judger: judger, consumerName: consumerName, concurrency: concurrency, log: log}
}

// Start 는 concurrency 만큼의 소비 루프를 띄우고 ctx 가 끝날 때까지 돈다.
func (c *Consumer) Start(ctx context.Context) error {
	// 등급마다 스트림이 따로 있다 (#102). 하나라도 그룹이 없으면 그 등급을 못 읽는다.
	for _, stream := range contract.JudgeStreamsByPriority() {
		if err := ensureGroup(ctx, c.redis, stream, contract.GroupJudge); err != nil {
			return err
		}
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
	cycle := 0
	for ctx.Err() == nil {
		cycle++
		if c.pickOne(ctx, cycle) {
			continue
		}
		// 어느 등급에도 일이 없다. 아무 데나 들어오면 깨어나도록 한 번만 블로킹으로 기다린다.
		c.waitForAny(ctx)
	}
}

/*
pickOne 은 등급 순서대로 훑어 **처음 발견한 작업 하나**를 처리한다.

등급마다 따로, 논블로킹으로 읽는 이유: 여러 스트림을 한 번에 블로킹으로 읽으면 Redis 가
"먼저 들어온 것" 을 주기 때문에 등급이 무시된다. 순서는 우리가 정해야 한다.
*/
func (c *Consumer) pickOne(ctx context.Context, cycle int) bool {
	for _, stream := range streamOrder(cycle) {
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
				c.handle(ctx, result.Stream, message)
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
func (c *Consumer) waitForAny(ctx context.Context) {
	args := []string{}
	args = append(args, contract.JudgeStreamsByPriority()...)
	for range contract.JudgeStreamsByPriority() {
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
			c.handle(ctx, result.Stream, message)
		}
	}
}

func (c *Consumer) handle(ctx context.Context, stream string, message redis.XMessage) {
	// 채점을 마쳤든 파싱에 실패했든 ack 한다 — 재시도해도 같은 결과다.
	// ack 는 **읽어 온 그 스트림에** 해야 한다.
	defer c.redis.XAck(ctx, stream, contract.GroupJudge, message.ID)

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
