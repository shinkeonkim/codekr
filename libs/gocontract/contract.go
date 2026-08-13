// Package contract 는 api / judge / executor 가 큐로 주고받는 메시지 정의를 담는다.
// 세 서비스가 같은 정의를 보게 하려고 별도 모듈로 분리했다 (docs/02_도메인_모델.md 5장).
package contract

// Redis 키. Stream 은 작업 큐, Channel 은 실시간 이벤트 브로드캐스트에 쓴다.
const (
	StreamExec        = "codekr:exec"
	GroupJudge        = "judge-workers"
	GroupExec         = "exec-workers"
	ChannelEvents     = "codekr:events"
	ReplyStreamPfx    = "codekr:exec:res:"
	MessagePayloadKey = "payload"

	// StreamMaxLength 는 스트림이 무한정 커지지 않도록 두는 근사 상한이다.
	StreamMaxLength = 10000
)

// 채점 큐는 우선순위 등급마다 스트림을 나눈다 (#102).
//
// Redis Streams 는 우선순위를 기본 지원하지 않는다. 한 스트림에 우선순위 필드를 실어도
// 소비자가 앞에서부터 읽을 수밖에 없어 의미가 없다. 스트림을 나누면 **어느 것을 먼저
// 읽을지** 소비자가 정할 수 있다.
//
// 등급이 메시지가 아니라 **스트림에 있다는 점이 중요하다.** 메시지 안의 값이면
// 조작 가능성을 따져야 하지만, 어느 스트림에 넣을지는 발행자(api)만 정한다.
const (
	StreamJudgeHigh   = "codekr:judge:high"
	StreamJudgeNormal = "codekr:judge:normal"
	StreamJudgeLow    = "codekr:judge:low"
	// StreamJudgeContest 는 대회 제출 전용이다 (#62). 전용 워커만 읽는다.
	StreamJudgeContest = "codekr:judge:contest"
)

// JudgeStreamsByPriority 는 **높은 등급부터** 나열한다. 소비자는 이 순서로 시도한다.
func JudgeStreamsByPriority() []string {
	return []string{StreamJudgeHigh, StreamJudgeNormal, StreamJudgeLow}
}

// 채점 차선 (#62).
//
// **등급이 아니라 차선이다.** 같은 워커가 등급만 나눠 읽으면, 대회 제출이 몰릴 때
// 평소 제출이 그만큼 밀린다 — 등급 순서를 어떻게 정하든 워커 수가 유한하기 때문이다.
// 격리는 **워커를 나눠야** 생긴다.
const (
	LaneGeneral = "general"
	LaneContest = "contest"
)

/*
JudgeConcurrencyKey 는 그 차선의 **워커 수를 담아 두는 자리**다 (#390).

`JUDGE_CONCURRENCY` 는 기동할 때 한 번 읽는 값이라, 바꾸려면 배포를 다시 해야 했다.
**늘리려는 상황은 대개 큐가 밀린 때인데, 그때 재시작하는 것은 나쁘다** — 진행 중인
채점이 끊긴다.

Redis 에 두는 이유: 이미 큐로 쓰고 있고(ADR-0002), 파드가 여럿이어도 **전부가 같은
값을 본다.** 채점기마다 엔드포인트를 두면 모든 파드에 따로 보내야 한다.

값이 없으면 채점기는 기동할 때 읽은 값을 그대로 쓴다 — **설정이 비면 기능만 꺼지는**
이 저장소의 규칙(#115)과 같다.
*/
func JudgeConcurrencyKey(lane string) string {
	if lane == "" {
		lane = LaneGeneral
	}
	return "codekr:judge:concurrency:" + lane
}

// JudgeStreamsFor 는 그 차선의 워커가 읽을 스트림을 돌려준다.
//
// 알 수 없는 차선은 일반으로 읽지 않고 **빈 목록**을 준다 — 오타 하나로 대회 워커가
// 일반 큐를 먹어 치우는 것보다, 아무것도 처리하지 않아 즉시 드러나는 편이 낫다.
func JudgeStreamsFor(lane string) []string {
	switch lane {
	case "", LaneGeneral:
		return JudgeStreamsByPriority()
	case LaneContest:
		return []string{StreamJudgeContest}
	default:
		return nil
	}
}

// Verdict 는 테스트케이스 및 제출 단위의 판정 값이다.
type Verdict string

// 판정 값. 채점기가 정하고 api 가 그대로 저장·노출한다.
const (
	VerdictAccepted            Verdict = "ACCEPTED"
	VerdictWrongAnswer         Verdict = "WRONG_ANSWER"
	VerdictTimeLimitExceeded   Verdict = "TIME_LIMIT_EXCEEDED"
	VerdictMemoryLimitExceeded Verdict = "MEMORY_LIMIT_EXCEEDED"
	VerdictRuntimeError        Verdict = "RUNTIME_ERROR"
	VerdictCompileError        Verdict = "COMPILE_ERROR"
	VerdictOutputLimitExceeded Verdict = "OUTPUT_LIMIT_EXCEEDED"
	VerdictSystemError         Verdict = "SYSTEM_ERROR"
)

// ExecStatus 는 코드 1회 실행의 결과 상태다. Verdict 와 값 공간을 공유하되
// 정답 비교(WRONG_ANSWER)는 실행기의 관심사가 아니므로 StatusOK 로만 구분한다.
type ExecStatus string

// 실행 결과 상태. 실행기가 관찰한 사실만 담는다.
const (
	StatusOK                  ExecStatus = "OK"
	StatusTimeLimitExceeded   ExecStatus = "TIME_LIMIT_EXCEEDED"
	StatusMemoryLimitExceeded ExecStatus = "MEMORY_LIMIT_EXCEEDED"
	StatusRuntimeError        ExecStatus = "RUNTIME_ERROR"
	StatusCompileError        ExecStatus = "COMPILE_ERROR"
	StatusOutputLimitExceeded ExecStatus = "OUTPUT_LIMIT_EXCEEDED"
	StatusSystemError         ExecStatus = "SYSTEM_ERROR"
)

// ExecJob 은 judge(또는 api 의 단발 실행)가 실행기에 보내는 작업이다.
type ExecJob struct {
	JobID         string `json:"jobId"`
	RuntimeID     string `json:"runtimeId"`
	SourceCode    string `json:"sourceCode"`
	Stdin         string `json:"stdin"`
	TimeLimitMs   int    `json:"timeLimitMs"`
	MemoryLimitMb int    `json:"memoryLimitMb"`
	ReplyStream   string `json:"replyStream"`
	// ExtraFiles 는 작업 디렉터리에 함께 풀 파일이다 (#60).
	//
	// SQL 문제의 스키마·시드·정답 쿼리처럼 **문제가 소유하는 자료**를 싣는다.
	// 이름에 경로를 쓸 수 없고 샌드박스 예약 이름과 겹칠 수 없다 — 실행기가 거부한다.
	ExtraFiles map[string]string `json:"extraFiles,omitempty"`
	/*
		HarnessSource 는 **보이지 않는 코드**다 (#421).

		함수만 구현하는 문제에서 사용자 코드를 부르는 쪽이다. 차 있으면 실행기가 런타임
		정의의 `functionHarness` 대로 파일을 나눠 놓는다 — **사용자 코드와 한 파일로
		합치지 않는다.** 합치면 줄 번호가 어긋나 오류가 어디인지 알 수 없다.
	*/
	HarnessSource string `json:"harnessSource,omitempty"`
	/*
		SourceFiles 는 **사용자가 쓴 파일들**이다 (#457).

		`SourceCode` 와 나눠 둔 이유: 둘은 주인이 다르다. 하나는 제출이고 하나는 문제가
		소유하는 자료(`ExtraFiles`)이며, 섞으면 "이 파일을 사용자가 고칠 수 있는가" 를
		실행기가 알 수 없게 된다.

		비어 있으면 지금까지처럼 `SourceCode` 하나를 `SourceFile` 이름으로 놓는다.
		차 있으면 그 파일들을 놓고, **진입점(`SourceFile`)도 그 안에 있어야 한다** —
		없으면 무엇을 컴파일·실행할지가 없다.
	*/
	SourceFiles map[string]string `json:"sourceFiles,omitempty"`
}

// JudgeTestcaseGroup 은 부분 점수 묶음 하나다 (#473).
type JudgeTestcaseGroup struct {
	GroupNo int `json:"groupNo"`
	// Score 는 이 묶음을 **다 맞혔을 때** 받는 점수다.
	Score int `json:"score"`
}

// ExecResult 는 실행기가 응답 스트림으로 돌려주는 결과다.
type ExecResult struct {
	JobID     string     `json:"jobId"`
	Status    ExecStatus `json:"status"`
	ExitCode  int        `json:"exitCode"`
	Stdout    string     `json:"stdout"`
	Stderr    string     `json:"stderr"`
	RuntimeMs int        `json:"runtimeMs"`
	MemoryKb  int        `json:"memoryKb"`
	Truncated bool       `json:"truncated"`
}

// JudgeTestcase 는 채점 작업에 실려 오는 테스트케이스다.
// judge 가 DB 를 읽지 않도록 필요한 값을 모두 담아 보낸다 (ADR-0004).
type JudgeTestcase struct {
	/*
		GroupNo 는 이 케이스가 속한 묶음이다 (#473). 0 이면 묶음이 없다.

		묶음은 대개 제약 조건이다 — "N ≤ 1,000", "N ≤ 100,000". **묶음 안을 다 맞혀야
		그 점수를 받는다** (IOI 관례) — 케이스마다 점수를 주면 묶음의 뜻이 없어진다.
	*/
	GroupNo        int    `json:"groupNo,omitempty"`
	ID             int64  `json:"id"`
	Seq            int    `json:"seq"`
	Input          string `json:"input"`
	ExpectedOutput string `json:"expectedOutput"`
}

// 문제 유형 (#59). 유형마다 풀이 입력·실행 환경·정답 표현·비교 방식이 다르다.
//
// **빈 값은 KindJudgeStdio 로 읽는다.** 이 필드가 없던 시절에 큐에 들어간 작업이
// 남아 있을 수 있고, 그것들은 전부 stdin/stdout 채점이다.
const (
	KindJudgeStdio = "JUDGE_STDIO"
	KindJudgeSQL   = "JUDGE_SQL"
	KindJudgeNoSQL = "JUDGE_NOSQL"
	// KindJudgeInteractive 는 **도는 중에 주고받는** 문제다 (#474).
	KindJudgeInteractive = "JUDGE_INTERACTIVE"
	// KindJudgeFunction 은 **함수만 구현하는** 문제다 (#421).
	KindJudgeFunction = "JUDGE_FUNCTION"
	KindQuiz          = "QUIZ"
	KindManual        = "MANUAL"
)

// JudgeJob 은 api 가 채점 큐에 넣는 작업이다.
type JudgeJob struct {
	SubmissionID int64 `json:"submissionId"`
	ProblemID    int64 `json:"problemId"`
	// 빈 값이면 KindJudgeStdio 다. KindOf 로 읽는다.
	Kind       string `json:"kind"`
	RuntimeID  string `json:"runtimeId"`
	SourceCode string `json:"sourceCode"`
	// SourceFiles 는 여러 파일로 낸 제출이다 (#457). 비면 SourceCode 하나다.
	SourceFiles   map[string]string `json:"sourceFiles,omitempty"`
	TimeLimitMs   int               `json:"timeLimitMs"`
	MemoryLimitMb int               `json:"memoryLimitMb"`
	Testcases     []JudgeTestcase   `json:"testcases"`
	/*
		Groups 는 부분 점수 묶음이다 (#473). 비어 있으면 지금까지처럼 전부 아니면 전무다.

		**랭킹에는 반영되지 않는다** — 만점만 "풀었다" 로 본다. 부분 점수의 교육적 값은
		화면에 보이는 것으로 대부분 얻어지고, 랭킹까지 열면 #57·#58·#84·#105 가 전부
		흔들린다. 값은 남겨 두므로 나중에 열 수 있다.
	*/
	Groups []JudgeTestcaseGroup `json:"groups,omitempty"`
	// Comparison 은 출력 비교 방식 (#279). 빈 값이면 CompareExact 다 —
	// 이 필드가 없던 시절에 큐에 들어간 작업이 남아 있을 수 있고, 전부 정확 일치였다.
	Comparison string `json:"comparison,omitempty"`
	// Epsilon 은 CompareFloat 일 때의 허용 오차. 절대·상대 중 **하나만 만족해도** 맞다.
	Epsilon float64 `json:"epsilon,omitempty"`
	// SQL 은 KindJudgeSQL 일 때만 실린다 (#60).
	//
	// 유형별 자료를 공통 필드에 섞지 않고 블록으로 나눈 이유: 유형이 늘어날 때
	// 쓰이지 않는 필드가 공통 계약에 쌓이면 어느 조합이 유효한지 알 수 없게 된다.
	SQL *JudgeSQLSpec `json:"sql,omitempty"`
	/*
		Checker 는 CompareChecker 일 때 실리는 **출제자의 채점 코드**다 (#452).

		파이썬으로 고정한다 — 언어를 열면 실행기가 언어마다 채점 코드를 돌리는 법을
		알아야 하고, 그것은 하네스(#421)가 이미 겪는 문제다. **늘리는 것은 나중에
		할 수 있고, 줄이는 것은 못 한다.**

		비어 있으면 채점하지 않는다 — 견줄 기대값도 없고 물어볼 코드도 없는 상태라,
		그것은 **오답이 아니라 출제자의 실수**다.
	*/
	/*
		Interactor 는 인터랙티브 문제에서 **대화를 주관하는 출제자의 코드**다 (#474).

		#452 의 채점 코드와 다른 점은 **언제 도는가**다 — 그쪽은 끝난 뒤 출력을 받아
		판정하고, 이쪽은 제출과 동시에 돌며 주고받는다. 판정을 종료 코드로 말하는
		규약은 같고(0 정답 · 1 오답), 거기에 **3 = 교착**이 더해진다.
	*/
	/*
		Harness 는 함수만 구현하는 문제의 **보이지 않는 코드**다 (#421).

		언어마다 다르다 — 문제 × 언어 = 하네스 하나. 하네스를 쓴 언어만 그 문제를 풀 수
		있고, 그것이 곧 #419 의 허용 목록이다.
	*/
	Harness    string `json:"harness,omitempty"`
	Interactor string `json:"interactor,omitempty"`
	Checker    string `json:"checker,omitempty"`
	// NoSQL 은 KindJudgeNoSQL 일 때만 실린다 (#455).
	NoSQL *JudgeNoSQLSpec `json:"nosql,omitempty"`
}

/*
JudgeNoSQLSpec 은 NoSQL 문제의 채점 자료다 (#455).

**채점 모델이 SQL 과 다르다.** SQL 은 쿼리 하나를 던져 결과 집합을 받지만, 제출이
**명령의 연속**이면 남는 것은 결과가 아니라 **상태**다. 그래서 여기서는 상태를 견주는
것이 예외가 아니라 **기본**이다 — `Verify` 가 선택이 아닌 이유다.
*/
type JudgeNoSQLSpec struct {
	// Seed 는 시작 상태를 만드는 명령이다. 관리자로 넣는다. 문제가 소유한다.
	Seed string `json:"seed,omitempty"`
	// Answer 는 정답 명령의 연속이다. 기대 상태를 만든다.
	Answer string `json:"answer"`
	// Verify 는 **끝난 뒤의 상태를 읽는 명령**이다. 양쪽에서 같은 것을 돌려 견준다.
	Verify string `json:"verify"`
	// IgnoreOrder 가 참이면 줄 순서를 맞추지 않고 비교한다.
	//
	// 기본을 참으로 두지 않는 이유: Redis 의 정렬 집합·리스트는 **순서가 자료의 일부**다.
	// SQL 의 행 순서와 반대다.
	IgnoreOrder bool `json:"ignoreOrder,omitempty"`
}

// 출력 비교 방식 (#279, ADR-0010).
//
// **문제 단위다.** 한 문제 안에서 케이스마다 정밀도가 다른 경우는 드물고, 케이스마다
// 두면 어드민이 채워야 할 칸이 케이스 수만큼 늘어난다.
const (
	// CompareExact 는 지금까지의 동작 — 줄 끝 공백과 끝의 빈 줄만 무시하고 정확히 맞춘다.
	CompareExact = "EXACT"
	// CompareFloat 는 토큰마다 숫자로 읽히면 오차 안에서 비교한다.
	CompareFloat = "FLOAT"
	/*
		CompareChecker 는 **출제자가 쓴 코드가 판정한다** (#452).

		정답이 여럿인 문제 — 조건을 만족하는 아무 배치, 임의 순서, "성질이 맞는가" —
		는 기대값과 견줄 수가 없다. 그때는 견주는 대신 **물어본다.**
	*/
	CompareChecker = "CHECKER"
)

// ComparisonOf 는 빈 값을 CompareExact 로 읽는다. 옛 작업과 새 채점기를 잇는다.
func (j JudgeJob) ComparisonOf() string {
	if j.Comparison == "" {
		return CompareExact
	}
	return j.Comparison
}

// JudgeSQLSpec 은 SQL 채점에 필요한 자료다 (#60).
type JudgeSQLSpec struct {
	// Schema 는 스키마와 시드 데이터. 슈퍼유저로 주입한다.
	Schema string `json:"schema"`
	// Answer 는 정답 쿼리. **결과 집합이 아니라 쿼리다** — 시드가 바뀌면 기대 결과도 따라간다.
	Answer string `json:"answer"`
	// IgnoreRowOrder 가 참이면 행 순서를 맞추지 않고 비교한다.
	IgnoreRowOrder bool `json:"ignoreRowOrder"`
	/*
		Verify 는 **끝난 뒤의 상태를 읽는 쿼리**다 (#453).

		`INSERT`·`UPDATE`·`CREATE TABLE` 은 결과 집합이 없다 — 바뀌는 것은 **DB 의
		상태**다. 그래서 비교 대상이 바뀐다: 정답 스크립트를 돌린 DB 와 제출을 돌린 DB
		에서 **각각 이 쿼리를 돌려** 그 결과를 견준다.

		비어 있으면 지금까지처럼 **제출 쿼리의 결과 집합**을 견준다.
	*/
	Verify string `json:"verify,omitempty"`
	/*
		AllowWrite 가 참이면 제출이 **쓸 수 있다** (#453).

		**여는 것도 권한으로 한다.** 문자열 필터로 돌아가지 않는다 — 필터는 우회되지만
		권한은 우회되지 않는다(#60 이 정한 것). 슈퍼유저 권한은 열지 않으므로
		`COPY … FROM PROGRAM`·`pg_read_file`·`pg_authid` 는 그대로 막힌다.
	*/
	AllowWrite bool `json:"allowWrite,omitempty"`
}

// KindOf 는 작업의 문제 유형을 돌려준다. 비어 있으면 stdin/stdout 채점이다.
func (j JudgeJob) KindOf() string {
	if j.Kind == "" {
		return KindJudgeStdio
	}
	return j.Kind
}

// 채점 진행 이벤트 타입.
const (
	EventJudging   = "JUDGING"
	EventTestcase  = "TESTCASE"
	EventCompleted = "COMPLETED"
)

// Event 는 judge 가 발행하고 api 가 구독하는 실시간 진행 이벤트다.
type Event struct {
	Type          string  `json:"type"`
	SubmissionID  int64   `json:"submissionId"`
	Seq           int     `json:"seq,omitempty"`
	Verdict       Verdict `json:"verdict,omitempty"`
	RuntimeMs     int     `json:"runtimeMs,omitempty"`
	MemoryKb      int     `json:"memoryKb,omitempty"`
	PassedCount   int     `json:"passedCount,omitempty"`
	TotalCount    int     `json:"totalCount,omitempty"`
	MaxRuntimeMs  int     `json:"maxRuntimeMs,omitempty"`
	MaxMemoryKb   int     `json:"maxMemoryKb,omitempty"`
	CompileError  string  `json:"compileError,omitempty"`
	StderrExcerpt string  `json:"stderrExcerpt,omitempty"`
	/*
		Score·MaxScore 는 부분 점수다 (#473). 묶음이 없는 문제에서는 둘 다 0 이다.

		**랭킹에는 반영되지 않는다** — 만점만 "풀었다" 로 본다. 이 값이 하는 일은
		화면에 "어디까지 왔는지" 를 보여 주는 것이고, 그것이 부분 점수의 값 대부분이다.
	*/
	Score    int `json:"score,omitempty"`
	MaxScore int `json:"maxScore,omitempty"`
}
