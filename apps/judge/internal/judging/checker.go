package judging

import (
	"context"
	"log/slog"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

/*
스페셜 저지 — **출제자가 쓴 코드가 판정한다** (#452).

정답이 여럿인 문제(조건을 만족하는 아무 배치·임의 순서·"성질이 맞는가")는 기대값과
견줄 수가 없다. 그때는 견주는 대신 **물어본다.**

**채점 코드도 남의 코드다.** 무한 루프에 빠지거나 메모리를 다 먹을 수 있어서 샌드박스
안에서 돌고, 제한은 **문제의 제한과 별개**다 — 문제가 1초라고 채점 코드도 1초일 이유가
없고, 반대로 채점 코드가 무거워서 사용자가 TLE 를 받으면 그것은 거짓말이다.
*/
const (
	// 채점 코드는 파이썬으로 고정한다. 늘리는 것은 나중에 할 수 있고 줄이는 것은 못 한다.
	checkerRuntimeID = "python:3.13"
	// 문제의 제한과 별개다. 판정하는 일이 사용자 프로그램보다 오래 걸릴 이유는 없다.
	checkerTimeLimitMs   = 5000
	checkerMemoryLimitMb = 256

	/*
		판정은 **종료 코드**로 받는다.

		표준 출력에 사유를 적게 하는 방법도 있었지만, 그 사유가 곧 **정답을 흘리는
		경로**가 된다 — "3번째 줄의 합이 12여야 하는데 11" 은 답의 일부다. 사유는
		로그에만 남기고 사용자에게는 판정만 준다.
	*/
	checkerAccepted     = 0
	checkerWrongAnswer  = 1
	checkerInputFile    = "input.in"
	checkerOutputFile   = "output.out"
	checkerExpectedFile = "expected.out"
)

// CheckWithCode 는 채점 코드를 돌려 판정을 얻는다 (#452).
//
// **채점 코드가 죽으면 오답이 아니다.** 출제자의 실수를 사용자 기록에 남기지 않는다 —
// #60 이 SQL 에서 이미 같은 판단을 했다.
func CheckWithCode(
	ctx context.Context,
	executor ExecutorClient,
	log *slog.Logger,
	job contract.JudgeJob,
	testcase contract.JudgeTestcase,
	submitted string,
) contract.Verdict {
	if job.Checker == "" {
		// 견줄 기대값도 없고 물어볼 코드도 없다. 이것은 출제자의 실수다.
		log.Error("스페셜 저지인데 채점 코드가 없습니다", "submissionId", job.SubmissionID)
		return contract.VerdictSystemError
	}

	result, err := executor.Run(ctx, contract.ExecJob{
		RuntimeID:  checkerRuntimeID,
		SourceCode: job.Checker,
		/*
			**표준 입력으로 넘기지 않는다.** 셋(입력·제출 출력·정답 출력)을 한 스트림에
			넣으면 경계를 약속해야 하고, 그 약속을 출제자가 매번 지켜야 한다.
			파일로 주면 채점 코드는 열어 읽기만 하면 된다 — SQL(#60)이 스키마를 넘기는
			방식과 같다.
		*/
		ExtraFiles: map[string]string{
			checkerInputFile:    testcase.Input,
			checkerOutputFile:   submitted,
			checkerExpectedFile: testcase.ExpectedOutput,
		},
		TimeLimitMs:   checkerTimeLimitMs,
		MemoryLimitMb: checkerMemoryLimitMb,
	})
	if err != nil {
		log.Error("채점 코드 실행 요청 실패", "submissionId", job.SubmissionID, "error", err)
		return contract.VerdictSystemError
	}

	switch {
	case result.Status == contract.StatusOK && result.ExitCode == checkerAccepted:
		return contract.VerdictAccepted

	case result.Status == contract.StatusRuntimeError && result.ExitCode == checkerWrongAnswer:
		// **약속된 오답이다.** 채점 코드가 "틀렸다" 로 끝낸 것이지 죽은 것이 아니다.
		return contract.VerdictWrongAnswer

	default:
		/*
			나머지는 전부 **출제자의 실수**다 — 문법 오류, 무한 루프, 메모리 초과,
			약속하지 않은 종료 코드.

			사유는 여기 로그에만 남긴다. 화면에 실으면 채점 코드의 내용이 새고,
			그것은 정답의 일부일 수 있다 (#421 이 하네스에서 한 걱정과 같다).
		*/
		log.Error("채점 코드가 판정을 돌려주지 못했습니다",
			"submissionId", job.SubmissionID, "seq", testcase.Seq,
			"status", result.Status, "exitCode", result.ExitCode,
			"stderr", excerpt(result.Stderr))
		return contract.VerdictSystemError
	}
}
