package judging

import contract "github.com/shinkeonkim/codekr/libs/gocontract"

// VerdictOf 는 실행 결과 하나를 테스트케이스 판정으로 옮긴다.
// 정답 비교는 실행이 정상으로 끝난 경우에만 의미가 있다.
//
// 비교 방식과 오차는 작업(JudgeJob)에서 온다 (#279). 빈 방식은 정확 일치다.
func VerdictOf(result contract.ExecResult, expectedOutput, comparison string, epsilon float64) contract.Verdict {
	switch result.Status {
	case contract.StatusOK:
		if OutputMatchesWithin(result.Stdout, expectedOutput, comparison, epsilon) {
			return contract.VerdictAccepted
		}
		return contract.VerdictWrongAnswer
	case contract.StatusTimeLimitExceeded:
		return contract.VerdictTimeLimitExceeded
	case contract.StatusMemoryLimitExceeded:
		return contract.VerdictMemoryLimitExceeded
	case contract.StatusCompileError:
		return contract.VerdictCompileError
	case contract.StatusOutputLimitExceeded:
		return contract.VerdictOutputLimitExceeded
	case contract.StatusRuntimeError:
		return contract.VerdictRuntimeError
	default:
		return contract.VerdictSystemError
	}
}

// Summary 는 테스트케이스 판정들을 제출 단위 결과로 집계한다.
type Summary struct {
	Verdict      contract.Verdict
	PassedCount  int
	TotalCount   int
	MaxRuntimeMs int
	MaxMemoryKb  int
	/*
		Score·MaxScore 는 부분 점수다 (#473). 묶음이 없으면 둘 다 0 이다.

		**랭킹에는 반영되지 않는다** — 만점만 "풀었다" 로 본다. 여기 남기는 이유는
		화면이 "어디까지 왔는지" 를 보여 주기 위함이고, 그것이 부분 점수의 값 대부분이다.
	*/
	Score    int
	MaxScore int
}

// Accumulator 는 테스트케이스 결과를 하나씩 받아 최종 집계를 만든다.
type Accumulator struct {
	total   int
	passed  int
	maxTime int
	maxMem  int
	// firstFailure 는 처음 만난 실패 판정이다. 최종 판정은 이 값으로 정한다.
	firstFailure contract.Verdict
	// groups 는 묶음 번호 → 점수 (#473). 비어 있으면 부분 점수가 없는 문제다.
	groups map[int]int
	// failed 는 이미 틀린 묶음이다. **묶음은 하나만 틀려도 0점이다** (IOI 관례).
	failed map[int]bool
}

// NewAccumulator 는 전체 테스트케이스 수를 알고 집계를 시작한다.
func NewAccumulator(total int) *Accumulator {
	return &Accumulator{total: total, groups: map[int]int{}, failed: map[int]bool{}}
}

/*
WithGroups 는 부분 점수 묶음을 알려 준다 (#473).

묶음이 없으면 지금까지와 똑같이 동작한다 — 기존 문제의 채점이 달라지면 안 된다.
*/
func (a *Accumulator) WithGroups(groups []contract.JudgeTestcaseGroup) *Accumulator {
	for _, group := range groups {
		a.groups[group.GroupNo] = group.Score
	}
	return a
}

// AddInGroup 은 묶음에 속한 케이스의 결과를 반영한다 (#473).
func (a *Accumulator) AddInGroup(groupNo int, verdict contract.Verdict, runtimeMs, memoryKb int) {
	if verdict != contract.VerdictAccepted && groupNo != 0 {
		a.failed[groupNo] = true
	}
	a.Add(verdict, runtimeMs, memoryKb)
}

// GroupFailed 는 그 묶음이 이미 틀렸는지 알려 준다.
//
// **틀린 묶음의 남은 케이스는 돌리지 않는다.** 어차피 그 묶음은 0점이고, 전부 돌리면
// 채점 시간이 몇 배가 된다 — 다른 묶음은 그대로 돈다.
func (a *Accumulator) GroupFailed(groupNo int) bool { return groupNo != 0 && a.failed[groupNo] }

// Add 는 테스트케이스 하나의 결과를 반영한다.
func (a *Accumulator) Add(verdict contract.Verdict, runtimeMs, memoryKb int) {
	if verdict == contract.VerdictAccepted {
		a.passed++
	} else if a.firstFailure == "" {
		a.firstFailure = verdict
	}
	if runtimeMs > a.maxTime {
		a.maxTime = runtimeMs
	}
	if memoryKb > a.maxMem {
		a.maxMem = memoryKb
	}
}

// Summarize 는 집계 결과를 돌려준다.
func (a *Accumulator) Summarize() Summary {
	verdict := contract.VerdictAccepted
	if a.firstFailure != "" {
		verdict = a.firstFailure
	}
	score, maxScore := 0, 0
	for groupNo, groupScore := range a.groups {
		maxScore += groupScore
		if !a.failed[groupNo] {
			score += groupScore
		}
	}
	return Summary{
		Verdict:      verdict,
		PassedCount:  a.passed,
		TotalCount:   a.total,
		MaxRuntimeMs: a.maxTime,
		MaxMemoryKb:  a.maxMem,
		Score:        score,
		MaxScore:     maxScore,
	}
}
