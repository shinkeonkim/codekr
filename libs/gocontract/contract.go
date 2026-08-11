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
)

// JudgeStreamsByPriority 는 **높은 등급부터** 나열한다. 소비자는 이 순서로 시도한다.
func JudgeStreamsByPriority() []string {
	return []string{StreamJudgeHigh, StreamJudgeNormal, StreamJudgeLow}
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
	KindQuiz       = "QUIZ"
	KindManual     = "MANUAL"
)

// JudgeJob 은 api 가 채점 큐에 넣는 작업이다.
type JudgeJob struct {
	SubmissionID int64 `json:"submissionId"`
	ProblemID    int64 `json:"problemId"`
	// 빈 값이면 KindJudgeStdio 다. KindOf 로 읽는다.
	Kind          string          `json:"kind"`
	RuntimeID     string          `json:"runtimeId"`
	SourceCode    string          `json:"sourceCode"`
	TimeLimitMs   int             `json:"timeLimitMs"`
	MemoryLimitMb int             `json:"memoryLimitMb"`
	Testcases     []JudgeTestcase `json:"testcases"`
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
}
