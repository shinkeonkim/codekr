package worker

import (
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

func TestStreamOrderPrefersHighPriority(t *testing.T) {
	order := streamOrder(1)

	if order[0] != contract.StreamJudgeHigh {
		t.Fatalf("높은 등급을 먼저 읽어야 합니다: %v", order)
	}
	if order[len(order)-1] != contract.StreamJudgeLow {
		t.Fatalf("낮은 등급이 마지막이어야 합니다: %v", order)
	}
}

func TestStreamOrderGivesLowPriorityATurn(t *testing.T) {
	// 굶주림 방지: 일정 횟수마다 낮은 등급이 먼저 와야 한다.
	order := streamOrder(starvationInterval)

	if order[0] != contract.StreamJudgeLow {
		t.Fatalf("굶주림 방지 차례에는 낮은 등급이 먼저여야 합니다: %v", order)
	}
}

func TestLowPriorityGetsTurnAtBoundedRate(t *testing.T) {
	// 100번 돌면 낮은 등급이 적어도 열 번 가까이 먼저 온다 —
	// "언젠가는" 이 아니라 "몇 번에 한 번" 이 보장되어야 한다.
	turns := 0
	for cycle := 1; cycle <= 100; cycle++ {
		if streamOrder(cycle)[0] == contract.StreamJudgeLow {
			turns++
		}
	}
	if turns < 100/starvationInterval-1 {
		t.Fatalf("낮은 등급 차례가 너무 드뭅니다: %d회", turns)
	}
}

func TestStreamOrderKeepsAllStreams(t *testing.T) {
	// 순서를 뒤집어도 어떤 등급도 빠지면 안 된다.
	for _, cycle := range []int{1, starvationInterval} {
		if len(streamOrder(cycle)) != len(contract.JudgeStreamsByPriority()) {
			t.Fatalf("cycle %d 에서 스트림이 누락됐습니다", cycle)
		}
	}
}
