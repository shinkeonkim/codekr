package worker

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"sync/atomic"
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"
	"github.com/redis/go-redis/v9"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

/*
배포하면 채점 중이던 제출이 SYSTEM_ERROR 로 끝나던 결함 (#415).

**SIGTERM 은 "새 작업을 그만 받아라" 이지 "하던 일을 끊어라" 가 아니다.** 전에는
진행 중인 채점의 ctx 가 그 자리에서 끊겼고, 같은 ctx 로 하는 ack 까지 거부되어
메시지가 PEL 에 남았다. 3분 뒤 API 의 안전망이 그 제출을 실패로 확정했다.
*/

// slowJudger 는 채점이 오래 걸리는 상황을 흉내 낸다. 중간에 ctx 가 끊기면 그것을 기록한다.
type slowJudger struct {
	started  chan struct{}
	duration time.Duration
	finished atomic.Bool
	cut      atomic.Bool
	handled  atomic.Int32
}

func (j *slowJudger) Judge(ctx context.Context, _ contract.JudgeJob) {
	j.handled.Add(1)
	select {
	case j.started <- struct{}{}:
	default:
	}
	select {
	case <-time.After(j.duration):
		j.finished.Store(true)
	case <-ctx.Done():
		// 여기로 오면 하던 채점이 끊긴 것이다 — 고치려던 바로 그 동작이다.
		j.cut.Store(true)
	}
}

func newTestConsumer(t *testing.T, judger Judger, name string) (*Consumer, *redis.Client) {
	t.Helper()
	server := miniredis.RunT(t)
	client := redis.NewClient(&redis.Options{Addr: server.Addr()})
	t.Cleanup(func() { _ = client.Close() })

	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	return NewConsumer(client, judger, name, 1, "general", log), client
}

func pushJob(t *testing.T, client *redis.Client, stream string) {
	t.Helper()
	payload, err := json.Marshal(contract.JudgeJob{SubmissionID: 1})
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

func pendingCount(t *testing.T, client *redis.Client, stream string) int64 {
	t.Helper()
	result, err := client.XPending(context.Background(), stream, contract.GroupJudge).Result()
	if err != nil {
		t.Fatalf("PEL 조회 실패: %v", err)
	}
	return result.Count
}

func TestShutdownFinishesRunningJudge(t *testing.T) {
	judger := &slowJudger{started: make(chan struct{}, 1), duration: 300 * time.Millisecond}
	consumer, client := newTestConsumer(t, judger, "pod-a")
	stream := contract.JudgeStreamsFor("general")[0]
	pushJob(t, client, stream)

	ctx, stop := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- consumer.Start(ctx, 5*time.Second) }()

	<-judger.started
	// 채점이 도는 중에 종료 신호가 온다 — 배포가 하는 일이 정확히 이것이다.
	stop()

	select {
	case <-done:
	case <-time.After(5 * time.Second):
		t.Fatal("종료가 끝나지 않았습니다")
	}

	if judger.cut.Load() {
		t.Error("하던 채점이 끊겼습니다 — SIGTERM 은 새 작업을 그만 받으라는 뜻이어야 한다")
	}
	if !judger.finished.Load() {
		t.Error("채점이 끝까지 가지 않았습니다")
	}
	// **ack 가 나가야 한다.** 끊긴 ctx 로 ack 하면 거부되어 PEL 에 남는다.
	if got := pendingCount(t, client, stream); got != 0 {
		t.Errorf("PEL 에 %d건이 남았습니다. 끊긴 ctx 로 ack 한 것이다", got)
	}
}

func TestShutdownCutsAfterDrain(t *testing.T) {
	// 유예에도 끝이 있어야 한다 — 파드는 어차피 SIGKILL 을 보낸다.
	judger := &slowJudger{started: make(chan struct{}, 1), duration: 10 * time.Second}
	consumer, client := newTestConsumer(t, judger, "pod-b")
	pushJob(t, client, contract.JudgeStreamsFor("general")[0])

	ctx, stop := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- consumer.Start(ctx, 200*time.Millisecond) }()

	<-judger.started
	stop()

	select {
	case <-done:
	case <-time.After(5 * time.Second):
		t.Fatal("유예가 지나도 종료되지 않았습니다")
	}
	if !judger.cut.Load() {
		t.Error("유예가 지났는데도 채점을 끊지 않았습니다")
	}
}

func TestReclaimsAbandonedJob(t *testing.T) {
	/*
		**소비자 이름은 파드 이름이라 배포하면 영영 돌아오지 않는다.** 그 소비자가
		읽어 갔지만 ack 하지 못한 메시지는 PEL 에 남고, 아무도 되찾지 않았다.
		프로세스가 강제로 죽는 경우(OOM·노드 장애)는 유예 종료로도 막을 수 없다.
	*/
	judger := &slowJudger{started: make(chan struct{}, 1), duration: time.Millisecond}
	consumer, client := newTestConsumer(t, judger, "pod-new")
	stream := contract.JudgeStreamsFor("general")[0]
	ctx := context.Background()

	if err := ensureGroup(ctx, client, stream, contract.GroupJudge); err != nil {
		t.Fatalf("그룹 생성 실패: %v", err)
	}
	pushJob(t, client, stream)

	// 죽은 파드가 읽어 가고 ack 하지 못한 상태를 만든다.
	if err := client.XReadGroup(ctx, &redis.XReadGroupArgs{
		Group:    contract.GroupJudge,
		Consumer: "pod-dead",
		Streams:  []string{stream, ">"},
		Count:    1,
	}).Err(); err != nil {
		t.Fatalf("죽은 소비자 흉내 실패: %v", err)
	}
	if got := pendingCount(t, client, stream); got != 1 {
		t.Fatalf("PEL 에 1건이 있어야 합니다: %d", got)
	}

	/*
		miniredis 는 PEL 의 idle 시간을 세지 않는다(항상 0). 그래서 임계값을 0 으로
		두고 회수 경로 자체를 확인한다 — **실제 Redis 에서는 이 값이 회수 여부를
		가르지만, 여기서 볼 것은 "회수한 뒤에 다시 채점하고 ack 하는가" 다.**
	*/
	consumer.reclaimMinIdle = 0

	consumer.reclaimStream(ctx, ctx, stream)

	if judger.handled.Load() != 1 {
		t.Errorf("놓친 작업을 다시 채점하지 않았습니다: %d회", judger.handled.Load())
	}
	if got := pendingCount(t, client, stream); got != 0 {
		t.Errorf("회수한 뒤에도 PEL 에 %d건이 남았습니다", got)
	}
}

func TestReclaimGivesUpAfterTooManyDeliveries(t *testing.T) {
	/*
		**무한히 재시도하지 않는다.** 정말 채점기를 죽이는 제출이 있으면 그것이
		영원히 돌면서 큐를 막는다 — 상한을 넘으면 ack 하고 로그로 남긴다.
	*/
	judger := &slowJudger{started: make(chan struct{}, 1), duration: time.Millisecond}
	consumer, client := newTestConsumer(t, judger, "pod-new")
	stream := contract.JudgeStreamsFor("general")[0]
	ctx := context.Background()

	if err := ensureGroup(ctx, client, stream, contract.GroupJudge); err != nil {
		t.Fatalf("그룹 생성 실패: %v", err)
	}
	pushJob(t, client, stream)

	if err := client.XReadGroup(ctx, &redis.XReadGroupArgs{
		Group:    contract.GroupJudge,
		Consumer: "pod-dead",
		Streams:  []string{stream, ">"},
		Count:    1,
	}).Err(); err != nil {
		t.Fatalf("죽은 소비자 흉내 실패: %v", err)
	}
	// 배달 횟수를 상한 너머로 올린다 — 회수할 때마다 오르는 값이다.
	id := client.XPendingExt(ctx, &redis.XPendingExtArgs{
		Stream: stream, Group: contract.GroupJudge, Start: "-", End: "+", Count: 1,
	}).Val()[0].ID
	for i := 0; i <= maxDeliveries; i++ {
		client.XClaim(ctx, &redis.XClaimArgs{
			Stream: stream, Group: contract.GroupJudge, Consumer: "pod-dead", Messages: []string{id},
		})
	}
	consumer.reclaimMinIdle = 0

	consumer.reclaimStream(ctx, ctx, stream)

	if judger.handled.Load() != 0 {
		t.Error("상한을 넘긴 작업을 또 채점했습니다")
	}
	if got := pendingCount(t, client, stream); got != 0 {
		t.Errorf("포기한 작업을 ack 하지 않아 PEL 에 %d건이 남았습니다", got)
	}
}
