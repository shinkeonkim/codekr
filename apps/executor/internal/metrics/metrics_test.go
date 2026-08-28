package metrics

import (
	"strings"
	"testing"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/common/expfmt"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

/*
갓 띄운 상태에서도 회수 지표가 0 으로 나온다 (#697).

**"한 건도 없다" 와 "아무도 안 내보낸다" 가 같은 모양이면 안 된다.** `dropped` 는
가장 보고 싶은 0 이다 — 그만큼의 제출이 판정 없이 닫혔다는 뜻이기 때문이다(ADR-0004).

아무것도 부르지 않은 상태를 그대로 본다. 이 파일에서 `Reclaimed` 를 부르면 시험이
시험 자신을 확인하게 된다.
*/
func TestReclaimCounterStartsAtZero(t *testing.T) {
	families, err := prometheus.DefaultGatherer.Gather()
	if err != nil {
		t.Fatalf("지표 수집 실패: %v", err)
	}

	var out strings.Builder
	encoder := expfmt.NewEncoder(&out, expfmt.NewFormat(expfmt.TypeTextPlain))
	for _, family := range families {
		if family.GetName() == contract.MetricExecReclaims {
			if err := encoder.Encode(family); err != nil {
				t.Fatalf("인코딩 실패: %v", err)
			}
		}
	}

	for _, outcome := range []string{OutcomeReclaimed, OutcomeDropped} {
		want := contract.MetricExecReclaims + `{outcome="` + outcome + `"} 0`
		if !strings.Contains(out.String(), want) {
			t.Errorf("갓 띄운 상태에 %q 가 없습니다. 실제:\n%s", want, out.String())
		}
	}
}
