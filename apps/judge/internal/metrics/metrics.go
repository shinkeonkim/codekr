/*
Package metrics 는 채점기가 내보내는 지표를 한곳에 등록한다 (#678).

**지금까지 `/metrics` 에는 `go_goroutines` 뿐이었다.** `promhttp.Handler()` 는 기본
레지스트리를 그대로 내주는데, 우리가 등록한 것이 하나도 없었다. 그래서 대시보드를
만들어도 "파드가 살아 있다" 까지밖에 못 그렸다.

**등록을 한 파일에 모은다.** 흩어 두면 이름이 어디서 나오는지 찾아야 하고, 라벨을
하나 더 붙이는 것이 얼마나 위험한지(카디널리티)를 판단할 자리가 없어진다.
*/
package metrics

import (
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

var (
	verdicts = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: contract.MetricJudgeVerdicts,
		Help: "판정 수. 분포가 기울면 문제나 런타임이 깨진 것이다.",
	}, []string{"verdict", "runtime"})

	duration = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Name:    contract.MetricJudgeDuration,
		Help:    "작업 하나를 받아 판정을 낼 때까지의 시간(초).",
		Buckets: contract.JudgeDurationBuckets(),
	}, []string{"kind"})
)

/*
Completed 는 판정 하나가 끝났음을 기록한다.

**두 지표를 한 함수로 묶는 이유**: 따로 두면 한쪽만 부르는 경로가 생긴다. 실제로
채점 서비스에는 유형을 모를 때·하네스가 없을 때·제약이 잘못됐을 때 각각 조기 종결하는
길이 있어서, 부르는 자리가 넷이다.

`kind` 와 `runtime` 을 한 지표에 함께 두지 않는다. 곱하면 12×25 = 300 계열이 되고,
정작 알고 싶은 것은 **유형별 소요**와 **런타임별 판정 분포**로 서로 다르다.
*/
func Completed(kind, runtime, verdict string, elapsed time.Duration) {
	verdicts.WithLabelValues(verdict, runtime).Inc()
	duration.WithLabelValues(kind).Observe(elapsed.Seconds())
}
