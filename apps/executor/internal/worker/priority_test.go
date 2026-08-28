package worker

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"testing"

	"github.com/alicebob/miniredis/v2"
	"github.com/redis/go-redis/v9"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

/*
실행 큐의 차선 순서 (#639).

**채점기만 나누는 것으로는 부족했다.** 대회 전용 채점기(#62)와 전용 큐가 있어도 그
뒤의 실행 큐가 한 벌이라, 평소 제출이 몰리면 대회도 함께 밀렸다 — 실측으로 대회 판정
중앙값이 27~28초까지 늘었다(조용할 때 14~17초).

여기서 지켜야 할 것이 둘이다: **대회가 먼저 나간다**, 그리고 **평소가 굶지 않는다.**
*/

func TestStreamOrderPutsContestFirst(t *testing.T) {
	streams := contract.ExecStreamsByPriority()

	if got := streamOrder(streams, 1)[0]; got != contract.StreamExecContest {
		t.Fatalf("대회가 먼저여야 합니다: %s", got)
	}
	// 옛 큐는 배포가 굴러가는 동안만 쓰이므로 맨 뒤다.
	if got := streamOrder(streams, 1)[2]; got != contract.StreamExec {
		t.Fatalf("옛 큐가 맨 뒤여야 합니다: %s", got)
	}
}

/*
**대회가 몇 시간씩 이어진다.** 늘 대회부터 읽으면 그동안 평소 제출은 영원히 돌지
않는다 — "잠깐이면 괜찮다" 가 성립하지 않는 자리다.
*/
func TestStreamOrderGivesGeneralATurn(t *testing.T) {
	streams := contract.ExecStreamsByPriority()

	if got := streamOrder(streams, starvationInterval)[0]; got == contract.StreamExecContest {
		t.Fatalf("가끔은 대회가 먼저가 아니어야 합니다: %s", got)
	}
	// 그 밖에는 늘 대회부터다.
	for _, cycle := range []int{1, 2, starvationInterval - 1, starvationInterval + 1} {
		if got := streamOrder(streams, cycle)[0]; got != contract.StreamExecContest {
			t.Fatalf("cycle %d: 대회가 먼저여야 합니다: %s", cycle, got)
		}
	}
}

/** 스트림이 하나뿐이면 뒤집어도 같다 — 순서를 바꿀 것이 없다. */
func TestStreamOrderHandlesSingleStream(t *testing.T) {
	if got := streamOrder([]string{"only"}, starvationInterval); len(got) != 1 || got[0] != "only" {
		t.Fatalf("하나뿐이면 그대로여야 합니다: %v", got)
	}
}

// ── 실제 Redis 로 ────────────────────────────────────────────────────────────

func newTestConsumer(t *testing.T) (*Consumer, *redis.Client) {
	t.Helper()
	server := miniredis.RunT(t)
	client := redis.NewClient(&redis.Options{Addr: server.Addr()})
	t.Cleanup(func() { _ = client.Close() })

	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	return NewConsumer(client, nil, "test-executor", 1, log), client
}

func pushExecJob(t *testing.T, client *redis.Client, stream, jobID string) {
	t.Helper()
	payload, err := json.Marshal(contract.ExecJob{JobID: jobID})
	if err != nil {
		t.Fatalf("작업 직렬화 실패: %v", err)
	}
	if err := client.XAdd(context.Background(), &redis.XAddArgs{
		Stream: stream,
		Values: map[string]any{contract.MessagePayloadKey: string(payload)},
	}).Err(); err != nil {
		t.Fatalf("작업 넣기 실패: %v", err)
	}
}

/*
**대회가 나중에 들어와도 먼저 나간다.**

이것이 이 변경의 요점이다. 큐가 하나였을 때는 들어온 순서대로였고, 그래서 평소 제출이
몰리면 대회가 그 뒤에 줄을 섰다.
*/
func TestPickOneTakesContestBeforeGeneral(t *testing.T) {
	consumer, client := newTestConsumer(t)
	ctx := context.Background()
	for _, stream := range consumer.streams {
		if err := ensureGroup(ctx, client, stream, contract.GroupExec); err != nil {
			t.Fatalf("그룹 생성 실패: %v", err)
		}
	}

	// 평소 제출이 **먼저** 들어온다.
	pushExecJob(t, client, contract.StreamExecGeneral, "general-1")
	pushExecJob(t, client, contract.StreamExecContest, "contest-1")

	first := readOne(t, consumer, client, 1)
	if first != contract.StreamExecContest {
		t.Fatalf("대회를 먼저 읽어야 합니다: %s", first)
	}
	second := readOne(t, consumer, client, 2)
	if second != contract.StreamExecGeneral {
		t.Fatalf("그다음이 평소여야 합니다: %s", second)
	}
}

/** 굶주림 방지 차례에는 평소가 먼저다 — 대회가 쌓여 있어도. */
func TestPickOneGivesGeneralATurnOnStarvationCycle(t *testing.T) {
	consumer, client := newTestConsumer(t)
	ctx := context.Background()
	for _, stream := range consumer.streams {
		if err := ensureGroup(ctx, client, stream, contract.GroupExec); err != nil {
			t.Fatalf("그룹 생성 실패: %v", err)
		}
	}
	pushExecJob(t, client, contract.StreamExecGeneral, "general-1")
	pushExecJob(t, client, contract.StreamExecContest, "contest-1")

	if got := readOne(t, consumer, client, starvationInterval); got != contract.StreamExec {
		// 뒤집으면 옛 큐가 맨 앞이고, 거기가 비었으므로 그다음인 평소가 나온다.
		if got != contract.StreamExecGeneral {
			t.Fatalf("굶주림 차례에는 대회가 먼저가 아니어야 합니다: %s", got)
		}
	}
}

/*
readOne 은 그 차례에 **어느 스트림에서** 읽혔는지 돌려준다.

`pickOne` 을 그대로 쓰지 않는 이유: 거기서 바로 실행까지 하는데 이 시험이 보려는 것은
**순서**뿐이다. 읽는 규칙(`streamOrder`)은 같은 것을 쓴다.
*/
func readOne(t *testing.T, consumer *Consumer, client *redis.Client, cycle int) string {
	t.Helper()
	for _, stream := range streamOrder(consumer.streams, cycle) {
		results, err := client.XReadGroup(context.Background(), &redis.XReadGroupArgs{
			Group:    contract.GroupExec,
			Consumer: consumer.consumerName,
			Streams:  []string{stream, ">"},
			Count:    1,
			Block:    -1,
		}).Result()
		if err != nil || len(results) == 0 || len(results[0].Messages) == 0 {
			continue
		}
		return results[0].Stream
	}
	t.Fatal("읽은 것이 없습니다")
	return ""
}

/*
**옛 큐를 계속 읽는다** (#639).

배포는 파드마다 굴러가므로 그 사이에 **옛 채점기가 `codekr:exec` 로 넣고 새 실행기가
새 큐만 읽는** 순간이 생긴다. 그러면 그 제출은 응답을 못 받고 시스템 오류가 된다.
*/
func TestConsumerStillReadsLegacyStream(t *testing.T) {
	consumer, _ := newTestConsumer(t)

	found := false
	for _, stream := range consumer.streams {
		if stream == contract.StreamExec {
			found = true
		}
	}
	if !found {
		t.Fatalf("옛 큐도 읽어야 합니다: %v", consumer.streams)
	}
}

func TestExecStreamForRoutesByLane(t *testing.T) {
	if got := contract.ExecStreamFor(contract.LaneContest); got != contract.StreamExecContest {
		t.Fatalf("대회 차선은 대회 큐로: %s", got)
	}
	for _, lane := range []string{contract.LaneGeneral, "", "오타"} {
		// **모르는 차선은 일반으로 보낸다.** 빈 값을 주면 채점이 통째로 멈춘다 —
		// 잘못 넣는 것보다 나쁜 결과다.
		if got := contract.ExecStreamFor(lane); got != contract.StreamExecGeneral {
			t.Fatalf("차선 %q: 일반 큐여야 합니다: %s", lane, got)
		}
	}
}
