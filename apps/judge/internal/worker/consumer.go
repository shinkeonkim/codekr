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
	if err := ensureGroup(ctx, c.redis, contract.StreamJudge, contract.GroupJudge); err != nil {
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
			Group:    contract.GroupJudge,
			Consumer: c.consumerName,
			Streams:  []string{contract.StreamJudge, ">"},
			Count:    1,
			Block:    2 * time.Second,
		}).Result()

		if err != nil {
			if errors.Is(err, redis.Nil) || ctx.Err() != nil {
				continue
			}
			c.log.Error("채점 큐 읽기 실패", "error", err)
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
	// 채점을 마쳤든 파싱에 실패했든 ack 한다 — 재시도해도 같은 결과다.
	defer c.redis.XAck(ctx, contract.StreamJudge, contract.GroupJudge, message.ID)

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
