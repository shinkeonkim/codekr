package judging

import (
	"context"
	"io"
	"log/slog"
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

/*
여러 파일로 낸 제출을 그대로 실어 보낸다 (#457).

**채점기는 파일을 합치지 않는다.** 합치는 방법은 언어마다 다르고(파이썬 import, 자바
클래스, C++ 링크), 그것을 아는 곳은 런타임 정의와 실행기다. 여기서 하는 일은 잃지 않고
넘기는 것뿐이다.
*/
func TestStdioJudgeCarriesSubmittedFiles(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{Status: contract.StatusOK, Stdout: "3\n"}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	job := contract.JudgeJob{
		SubmissionID:  9,
		RuntimeID:     "python:3.13",
		SourceCode:    "print(3)",
		SourceFiles:   map[string]string{"main.py": "from helper import add", "helper.py": "def add(): ..."},
		TimeLimitMs:   2000,
		MemoryLimitMb: 256,
		Testcases:     []contract.JudgeTestcase{{Seq: 1, Input: "", ExpectedOutput: "3\n"}},
	}

	NewStdioJudge(captured, log).Judge(context.Background(), job, func(contract.Event) {})

	if len(captured.job.SourceFiles) != 2 {
		t.Fatalf("제출한 파일을 그대로 실어야 합니다: %+v", captured.job.SourceFiles)
	}
}
