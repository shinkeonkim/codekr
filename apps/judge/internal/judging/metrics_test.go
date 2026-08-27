package judging

import (
	"context"
	"errors"
	"strings"
	"testing"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/common/expfmt"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

/*
채점이 끝나면 지표가 실제로 는다 (#678).

**기본 레지스트리에서 긁어서 본다.** `promauto` 가 등록한 곳이 거기고, `/metrics` 가
내주는 것도 거기다. 우리가 만든 레지스트리에 등록해 확인하면 "등록은 됐지만
`/metrics` 에는 안 나온다" 를 그대로 통과시킨다 — #668 이 짚은, 실패할 수 없는 시험이
되는 자리다.
*/
func scrape(t *testing.T, name string) map[string]float64 {
	t.Helper()

	families, err := prometheus.DefaultGatherer.Gather()
	if err != nil {
		t.Fatalf("지표 수집 실패: %v", err)
	}

	found := map[string]float64{}
	for _, family := range families {
		if family.GetName() != name {
			continue
		}
		for _, metric := range family.GetMetric() {
			labels := make([]string, 0, len(metric.GetLabel()))
			for _, label := range metric.GetLabel() {
				labels = append(labels, label.GetName()+"="+label.GetValue())
			}
			key := strings.Join(labels, ",")
			switch {
			case metric.GetCounter() != nil:
				found[key] = metric.GetCounter().GetValue()
			case metric.GetHistogram() != nil:
				found[key] = float64(metric.GetHistogram().GetSampleCount())
			}
		}
	}
	return found
}

func TestJudgeCountsVerdict(t *testing.T) {
	key := "runtime=python:3.12,verdict=ACCEPTED"
	before := scrape(t, contract.MetricJudgeVerdicts)[key]

	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK, Stdout: "3\n"}}}
	newTestService(executor, &recordingSink{}).Judge(context.Background(), newJob(1))

	if after := scrape(t, contract.MetricJudgeVerdicts)[key]; after != before+1 {
		t.Fatalf("%s 가 늘지 않았습니다: %v → %v", contract.MetricJudgeVerdicts, before, after)
	}
}

/*
**조기 종결도 세어야 한다.** 유형을 모를 때·제약이 잘못됐을 때는 테스트케이스를 하나도
돌리지 않고 `SYSTEM_ERROR` 로 닫는데, 그 길이 넷이라 하나만 빠뜨려도 티가 안 난다 —
정작 가장 알고 싶은 판정이 그것이다.
*/
func TestJudgeCountsEarlyExitVerdicts(t *testing.T) {
	key := "runtime=python:3.12,verdict=SYSTEM_ERROR"
	before := scrape(t, contract.MetricJudgeVerdicts)[key]

	unknown := newJob(1)
	unknown.Kind = "JUDGE_NOT_A_KIND"
	newTestService(&stubExecutor{}, &recordingSink{}).Judge(context.Background(), unknown)

	badLimits := newJob(1)
	badLimits.TimeLimitMs = 0
	newTestService(&stubExecutor{}, &recordingSink{}).Judge(context.Background(), badLimits)

	unavailable := newTestService(&stubExecutor{err: errors.New("실행기 없음")}, &recordingSink{})
	unavailable.Judge(context.Background(), newJob(1))

	if after := scrape(t, contract.MetricJudgeVerdicts)[key]; after != before+3 {
		t.Fatalf("조기 종결이 세어지지 않았습니다: %v → %v (기대 %v)", before, after, before+3)
	}
}

func TestJudgeObservesDurationByKind(t *testing.T) {
	key := "kind=" + contract.KindJudgeStdio
	before := scrape(t, contract.MetricJudgeDuration)[key]

	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK, Stdout: "3\n"}}}
	newTestService(executor, &recordingSink{}).Judge(context.Background(), newJob(1))

	if after := scrape(t, contract.MetricJudgeDuration)[key]; after != before+1 {
		t.Fatalf("%s 표본이 늘지 않았습니다: %v → %v", contract.MetricJudgeDuration, before, after)
	}
}

// 텍스트 노출 형식에 실제로 실리는지까지 본다. Prometheus 가 읽는 것이 이 형식이다.
func TestMetricsAreExposedInTextFormat(t *testing.T) {
	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK, Stdout: "3\n"}}}
	newTestService(executor, &recordingSink{}).Judge(context.Background(), newJob(1))

	families, err := prometheus.DefaultGatherer.Gather()
	if err != nil {
		t.Fatalf("지표 수집 실패: %v", err)
	}

	var out strings.Builder
	encoder := expfmt.NewEncoder(&out, expfmt.NewFormat(expfmt.TypeTextPlain))
	for _, family := range families {
		if err := encoder.Encode(family); err != nil {
			t.Fatalf("인코딩 실패: %v", err)
		}
	}

	for _, name := range []string{contract.MetricJudgeVerdicts, contract.MetricJudgeDuration} {
		if !strings.Contains(out.String(), name) {
			t.Errorf("%s 가 /metrics 본문에 없습니다", name)
		}
	}
}
