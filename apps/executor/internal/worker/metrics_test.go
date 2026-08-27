package worker

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"strings"
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/redis/go-redis/v9"
	"github.com/shinkeonkim/codekr/apps/executor/internal/metrics"
	"github.com/shinkeonkim/codekr/apps/executor/internal/sandbox"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

/*
실행기 지표가 **실제 경로에서** 는다 (#678).

`metrics.Executed` 를 직접 부르는 시험은 아무것도 증명하지 않는다 — 그 함수가 도는지가
아니라 **소비 경로가 그것을 부르는지**가 문제다. 그래서 큐에 작업을 넣고 소비시킨다.
*/
func sampleCount(t *testing.T, name, labels string) float64 {
	t.Helper()

	families, err := prometheus.DefaultGatherer.Gather()
	if err != nil {
		t.Fatalf("지표 수집 실패: %v", err)
	}
	for _, family := range families {
		if family.GetName() != name {
			continue
		}
		for _, metric := range family.GetMetric() {
			pairs := make([]string, 0, len(metric.GetLabel()))
			for _, label := range metric.GetLabel() {
				pairs = append(pairs, label.GetName()+"="+label.GetValue())
			}
			if strings.Join(pairs, ",") != labels {
				continue
			}
			if metric.GetCounter() != nil {
				return metric.GetCounter().GetValue()
			}
			return float64(metric.GetHistogram().GetSampleCount())
		}
	}
	return 0
}

func TestHandleObservesExecDuration(t *testing.T) {
	before := sampleCount(t, contract.MetricExecDuration, "runtime=python:3.12")

	server := miniredis.RunT(t)
	client := redis.NewClient(&redis.Options{Addr: server.Addr()})
	t.Cleanup(func() { _ = client.Close() })

	runner := newTestRunner(t, &stubSandbox{outcome: sandbox.Outcome{Stdout: "3\n"}})
	consumer := NewConsumer(client, runner, "test-executor", 1, slog.New(slog.NewTextHandler(io.Discard, nil)))

	payload, err := json.Marshal(contract.ExecJob{JobID: "job-1", RuntimeID: "python:3.12", TimeLimitMs: 1000, MemoryLimitMb: 128})
	if err != nil {
		t.Fatalf("작업 직렬화 실패: %v", err)
	}
	consumer.handle(context.Background(), contract.StreamExecGeneral,
		redis.XMessage{ID: "1-1", Values: map[string]any{contract.MessagePayloadKey: string(payload)}})

	if after := sampleCount(t, contract.MetricExecDuration, "runtime=python:3.12"); after != before+1 {
		t.Fatalf("%s 표본이 늘지 않았습니다: %v → %v", contract.MetricExecDuration, before, after)
	}
}

/*
**포기한 작업이 세어진다.**

이것이 이 지표를 만든 이유다. 여러 번 실패한 작업은 ack 하고 버려지는데, 그 제출은
채점기의 응답을 영영 못 받아 180초 뒤 `SYSTEM_ERROR` 로 닫힌다(ADR-0004). 지금까지
그것은 로그 한 줄이었고, **몇 건인지 세어 본 적이 없다.**

배달 횟수는 `XCLAIM` 마다 는다. `maxDeliveries` 를 넘기려고 네 번 물어 온 뒤,
miniredis 의 시계를 감아 `reclaimMinIdle` 을 지나가게 한다.
*/
func TestReclaimCountsDroppedJob(t *testing.T) {
	before := sampleCount(t, contract.MetricExecReclaims, "outcome="+metrics.OutcomeDropped)

	server := miniredis.RunT(t)
	client := redis.NewClient(&redis.Options{Addr: server.Addr()})
	t.Cleanup(func() { _ = client.Close() })

	ctx := context.Background()
	stream := contract.StreamExecGeneral
	if err := ensureGroup(ctx, client, stream); err != nil {
		t.Fatalf("그룹 생성 실패: %v", err)
	}
	pushExecJob(t, client, stream, "doomed")

	// 한 번 읽어 PEL 에 올린다.
	entries, err := client.XReadGroup(ctx, &redis.XReadGroupArgs{
		Group: contract.GroupExec, Consumer: "dead-executor",
		Streams: []string{stream, ">"}, Count: 1,
	}).Result()
	if err != nil {
		t.Fatalf("읽기 실패: %v", err)
	}
	id := entries[0].Messages[0].ID

	// 배달 횟수를 maxDeliveries 너머로 올린다.
	for range maxDeliveries + 1 {
		if err := client.XClaim(ctx, &redis.XClaimArgs{
			Stream: stream, Group: contract.GroupExec, Consumer: "dead-executor",
			MinIdle: 0, Messages: []string{id},
		}).Err(); err != nil {
			t.Fatalf("배달 횟수 올리기 실패: %v", err)
		}
	}

	/*
		**`FastForward` 가 아니라 `SetTime` 이다.** 전자는 PEL 의 유휴 시각을 안 움직여서
		`XPENDING ... IDLE` 필터가 아무것도 못 고른다(실측: 네 번 claim 뒤에도 `Idle:0s`).
		시계를 통째로 옮기면 `Idle:11m0s` 로 잡힌다.
	*/
	server.SetTime(time.Now().Add(reclaimMinIdle + time.Minute))

	consumer := NewConsumer(client, nil, "test-executor", 1, slog.New(slog.NewTextHandler(io.Discard, nil)))
	consumer.reclaim(ctx, ctx, stream)

	if after := sampleCount(t, contract.MetricExecReclaims, "outcome="+metrics.OutcomeDropped); after != before+1 {
		t.Fatalf("포기가 세어지지 않았습니다: %v → %v", before, after)
	}
}

// 되찾은 쪽도 세어진다. 값이 둘인 라벨은 한쪽만 확인하면 반만 지킨 것이다.
func TestReclaimCountsRecoveredJob(t *testing.T) {
	before := sampleCount(t, contract.MetricExecReclaims, "outcome="+metrics.OutcomeReclaimed)

	server := miniredis.RunT(t)
	client := redis.NewClient(&redis.Options{Addr: server.Addr()})
	t.Cleanup(func() { _ = client.Close() })

	ctx := context.Background()
	stream := contract.StreamExecGeneral
	if err := ensureGroup(ctx, client, stream); err != nil {
		t.Fatalf("그룹 생성 실패: %v", err)
	}
	pushExecJob(t, client, stream, "orphan")

	// 한 번만 읽는다 — 배달 횟수가 maxDeliveries 안이라 포기하지 않고 되찾는다.
	if _, err := client.XReadGroup(ctx, &redis.XReadGroupArgs{
		Group: contract.GroupExec, Consumer: "dead-executor",
		Streams: []string{stream, ">"}, Count: 1,
	}).Result(); err != nil {
		t.Fatalf("읽기 실패: %v", err)
	}
	server.SetTime(time.Now().Add(reclaimMinIdle + time.Minute))

	runner := newTestRunner(t, &stubSandbox{outcome: sandbox.Outcome{Stdout: "3\n"}})
	consumer := NewConsumer(client, runner, "test-executor", 1, slog.New(slog.NewTextHandler(io.Discard, nil)))
	consumer.reclaim(ctx, ctx, stream)

	if after := sampleCount(t, contract.MetricExecReclaims, "outcome="+metrics.OutcomeReclaimed); after != before+1 {
		t.Fatalf("되찾음이 세어지지 않았습니다: %v → %v", before, after)
	}
}
