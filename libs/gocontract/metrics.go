package contract

/*
지표 이름을 계약으로 둔다 (#678).

**대시보드가 이 이름에 매인다.** 이름을 바꾸면 그래프가 조용히 빈다 — 패널은 그대로
그려지고 선만 사라지므로, 보는 사람은 "채점이 없었나 보다" 로 읽는다. 큐 키(#62)를
여기 둔 것과 같은 이유다.

**라벨은 `runtime` 하나로 통일한다.** 작업이 실제로 들고 다니는 값이 런타임 id
(`python:3.12`)고, 거기서 "언어" 를 뽑는 것은 잃는 변환이다 — 이미지 pull 때문에 첫
실행만 튀는 것은 **버전까지 봐야** 보인다. 언어별로 보고 싶으면 대시보드에서
`label_replace` 로 자르면 된다. 반대는 못 한다.

`runtimes.yaml` 이 25개라 카디널리티는 닫혀 있다. **`problem_id` 는 절대 라벨이 아니다** —
문제가 수백 개고 계속 는다.

**차선(lane)은 라벨로 두지 않는다.** judge 와 judge-contest 는 파드가 다르고,
PodMonitor 가 `app.kubernetes.io/component` 를 지표에 붙인다(#677). 여기서 또 붙이면
같은 것을 두 곳이 말하게 된다.
*/
const (
	// MetricJudgeVerdicts 는 판정 수다. 분포가 갑자기 기울면 문제나 런타임이 깨진 것이다.
	MetricJudgeVerdicts = "codekr_judge_verdicts_total"
	// MetricJudgeDuration 은 작업 하나를 받아 판정을 낼 때까지다.
	MetricJudgeDuration = "codekr_judge_duration_seconds"
	// MetricExecDuration 은 코드 1회 실행이다. 채점 한 건 안에서 여러 번 돈다.
	MetricExecDuration = "codekr_exec_duration_seconds"
	// MetricExecReclaims 는 죽은 소비자가 놓고 간 작업을 되찾거나 포기한 수다 (#415).
	MetricExecReclaims = "codekr_exec_reclaims_total"
)

/*
JudgeDurationBuckets 는 채점 한 건의 소요 구간이다.

기본 버킷(0.005~10)을 그대로 쓰면 **양쪽 끝이 모두 안 보인다.** 조용할 때 채점은 1초
안쪽이고(실측: 실행 다섯 건이 171~224ms), 밀리면 대회 중앙 2.6s·평소 5.3s 까지 간다.
그리고 위쪽은 **180초에서 잘린다** — 그 시각을 넘긴 제출은 `SYSTEM_ERROR` 로 닫힌다
(ADR-0004). 마지막 버킷을 180 에 두어야 "닫히기 직전" 이 보인다.
*/
func JudgeDurationBuckets() []float64 {
	return []float64{0.1, 0.25, 0.5, 1, 2.5, 5, 10, 30, 60, 180}
}

/*
ExecDurationBuckets 는 1회 실행의 소요 구간이다.

채점보다 훨씬 짧아서 아래쪽이 촘촘해야 한다. 대신 위쪽은 시간 제한(대개 1~5초)에
컴파일과 이미지 pull 이 얹히는 정도까지만 보면 된다.
*/
func ExecDurationBuckets() []float64 {
	return []float64{0.05, 0.1, 0.2, 0.5, 1, 2, 5, 10, 30}
}
