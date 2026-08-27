/*
Package metrics 는 실행기가 내보내는 지표를 한곳에 등록한다 (#678).

채점기 쪽(`apps/judge/internal/metrics`)과 **같은 모양으로 둔다.** 두 앱이 다른 방식으로
지표를 붙이면 대시보드를 만들 때마다 어느 쪽 규칙인지 확인해야 한다.
*/
package metrics

import (
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// 회수 결과. **포기가 조용히 늘면 제출이 판정 없이 사라진다** — 그것이 이 지표의 목적이다.
const (
	OutcomeReclaimed = "reclaimed"
	OutcomeDropped   = "dropped"
)

var (
	/*
		실행 소요.

		`status` 를 라벨로 두지 않았다. 시간 초과는 늘 제한값에 붙어서 분포를 봐도
		새로 아는 것이 없고, 25개 런타임에 곱하면 계열이 여덟 배가 된다.
		어떤 판정이 났는지는 채점기 쪽 `codekr_judge_verdicts_total` 이 답한다.
	*/
	duration = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Name:    contract.MetricExecDuration,
		Help:    "코드 1회 실행에 걸린 시간(초). 채점 한 건 안에서 여러 번 돈다.",
		Buckets: contract.ExecDurationBuckets(),
	}, []string{"runtime"})

	reclaims = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: contract.MetricExecReclaims,
		Help: "죽은 소비자가 놓고 간 작업을 되찾거나(reclaimed) 포기한(dropped) 수.",
	}, []string{"outcome"})
)

// Executed 는 실행 하나가 끝났음을 기록한다.
func Executed(runtime string, elapsed time.Duration) {
	duration.WithLabelValues(runtime).Observe(elapsed.Seconds())
}

/*
Reclaimed 는 밀린 작업을 되찾거나 포기했음을 기록한다 (#415).

**포기(`dropped`)는 로그에만 남아 있었다.** 여러 번 실패한 작업을 ack 하고 버리는데,
그 제출은 채점기의 응답을 영영 못 받아 180초 뒤 `SYSTEM_ERROR` 로 닫힌다(ADR-0004).
사용자에게는 "채점 실패" 로만 보이고, 우리는 그것이 몇 건인지 세어 본 적이 없다.
*/
func Reclaimed(outcome string) {
	reclaims.WithLabelValues(outcome).Inc()
}
