package sandbox

import (
	"testing"
	"time"
)

/*
미리 받기와 제출은 **기다리는 사람이 다르다** (#737).

전에는 하나를 둘이 나눠 썼고, 그래서 `gcc:13` 처럼 5분을 넘기는 이미지는 어느 쪽으로도
받을 수 없었다 — 미리 받기가 5분에 취소되고, 제출도 5분에 취소되고, 채점기는 그보다
먼저 60초에 포기했다. 운영에서 `elapsedMs` 가 정확히 300002 로 찍혔다.

**값이 아니라 관계를 지킨다.** 얼마가 적당한지는 실측이 정하지만, 미리 받기가 제출보다
짧아지는 순간 이 자리는 다시 무너진다.
*/
func TestWarmBudgetIsLongerThanSubmitBudget(t *testing.T) {
	if warmPullTimeout <= submitPullTimeout {
		t.Fatalf("미리 받기(%s)가 제출(%s)보다 짧거나 같습니다. "+
			"미리 받기는 아무도 안 기다리므로 더 길어야 합니다",
			warmPullTimeout, submitPullTimeout)
	}
}

func TestSubmitBudgetStaysShortEnoughForAPerson(t *testing.T) {
	// **사람이 화면을 보고 있다.** 여기가 길어지면 그만큼 멈춰 있는 것으로 보인다.
	if submitPullTimeout > 10*time.Minute {
		t.Fatalf("제출 경로의 받기 예산이 너무 깁니다: %s", submitPullTimeout)
	}
}
