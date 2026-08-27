// Package dispatch 는 실행기와의 큐 통신을 감싼다.
// 채점기는 실행기의 주소를 알지 못하고, 오직 큐로만 이야기한다 (ADR-0002).
package dispatch

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// Executor 는 실행 작업을 큐에 넣고 응답 스트림에서 결과를 기다린다.
type Executor struct {
	redis   *redis.Client
	timeout time.Duration
	/*
		어느 실행 큐로 보낼지 (#639).

		**작업마다가 아니라 프로세스마다 정해진다.** 대회 채점기는 대회 큐로, 일반
		채점기는 일반 큐로 보낸다 — 메시지 안의 값이 아니라 **보내는 쪽이 누구인가**로
		갈리므로 조작할 자리가 없다. 채점 큐(#102)가 등급을 스트림에 둔 것과 같은 이유다.
	*/
	stream string
}

// NewExecutor 는 실행기 디스패처를 만든다.
//
// [lane] 은 이 채점기 프로세스의 차선이다 (#62, #639). 빈 값이면 일반으로 본다.
// timeout 은 결과를 기다리는 최대 시간이다.
func NewExecutor(client *redis.Client, timeout time.Duration, lane string) *Executor {
	return &Executor{redis: client, timeout: timeout, stream: contract.ExecStreamFor(lane)}
}

// Run 은 실행 작업을 보내고 결과가 올 때까지 기다린다.
// 기다리는 동안 실행기가 죽으면 timeout 이 지나고 오류를 돌려준다.
func (e *Executor) Run(ctx context.Context, job contract.ExecJob) (contract.ExecResult, error) {
	job.JobID = newJobID()
	job.ReplyStream = contract.ReplyStreamPfx + job.JobID

	// 응답 스트림은 결과를 읽은 뒤 지운다. 못 읽고 끝나도 실행기 쪽 TTL 이 정리한다.
	defer e.redis.Del(context.WithoutCancel(ctx), job.ReplyStream)

	if err := e.publish(ctx, job); err != nil {
		return contract.ExecResult{}, err
	}
	return e.await(ctx, job.ReplyStream)
}

func (e *Executor) publish(ctx context.Context, job contract.ExecJob) error {
	encoded, err := json.Marshal(job)
	if err != nil {
		return fmt.Errorf("실행 작업 직렬화 실패: %w", err)
	}
	return e.redis.XAdd(ctx, &redis.XAddArgs{
		Stream: e.stream,
		// 스트림은 자동으로 줄어들지 않는다. 근사 트리밍으로 상한을 둔다 (ADR-0002).
		MaxLen: contract.StreamMaxLength,
		Approx: true,
		Values: map[string]any{contract.MessagePayloadKey: string(encoded)},
	}).Err()
}

func (e *Executor) await(ctx context.Context, replyStream string) (contract.ExecResult, error) {
	ctx, cancel := context.WithTimeout(ctx, e.timeout)
	defer cancel()

	streams, err := e.redis.XRead(ctx, &redis.XReadArgs{
		Streams: []string{replyStream, "0"},
		Count:   1,
		Block:   e.timeout,
	}).Result()
	if err != nil {
		return contract.ExecResult{}, fmt.Errorf("실행 결과 대기 실패: %w", err)
	}
	if len(streams) == 0 || len(streams[0].Messages) == 0 {
		return contract.ExecResult{}, fmt.Errorf("실행 결과가 비어 있습니다")
	}

	payload, ok := streams[0].Messages[0].Values[contract.MessagePayloadKey].(string)
	if !ok {
		return contract.ExecResult{}, fmt.Errorf("실행 결과 형식이 올바르지 않습니다")
	}

	var result contract.ExecResult
	if err := json.Unmarshal([]byte(payload), &result); err != nil {
		return contract.ExecResult{}, fmt.Errorf("실행 결과 파싱 실패: %w", err)
	}
	return result, nil
}

func newJobID() string {
	buf := make([]byte, 12)
	if _, err := rand.Read(buf); err != nil {
		return fmt.Sprintf("job-%d", time.Now().UnixNano())
	}
	return hex.EncodeToString(buf)
}
