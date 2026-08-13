package worker

import "strings"

/*
scrub 은 오류 출력을 사용자가 읽을 수 있는 것으로 만든다 (#445, #421).

두 가지를 한다.

 1. **경로를 지운다.** `/work/solution.py:12` → `solution.py:12`. 컨테이너 안의 경로는
    사용자에게 뜻이 없고, 그 자체가 우리 내부 구조다.

 2. **하네스에서 난 줄은 사실만 남긴다.** 하네스에는 정답의 일부나 테스트 방식이 들어갈
    수 있어서 그 코드가 보이면 문제가 무너진다.

**지우지 않고 되돌린다.** 하네스 줄을 통째로 버리면 진짜 원인이 감춰지고, 그러면
"왜 안 되는지 모르는 상태" 가 된다 — 그것은 이 기능을 쓸 수 없게 만든다. 그래서
**났다는 사실과 오류의 종류는 남긴다.**
*/
func scrub(output, harnessFile string) string {
	if output == "" {
		return output
	}
	cleaned := strings.ReplaceAll(output, workDirPrefix, "")
	if harnessFile == "" {
		return cleaned
	}

	lines := strings.Split(cleaned, "\n")
	result := make([]string, 0, len(lines))
	// swallow 는 지금 지우는 프레임 안인지, announced 는 그 프레임을 이미 알렸는지다.
	swallow, announced := false, false
	for _, line := range lines {
		switch {
		case strings.Contains(line, loaderMarker):
			/*
				하네스를 읽어 들이는 **우리 쪽 장치**다 (`python -c ...`). 사용자에게는
				뜻이 없고 진짜 원인은 늘 그 아래에 함께 찍힌다 — 알릴 것이 없으므로
				조용히 지운다. 하네스와 달리 **감추는 내용이 없다.**
			*/
			swallow = true

		case strings.Contains(line, harnessFile):
			// 하네스를 가리키는 줄. **연달아 나와도 한 번만 알린다.**
			if !announced {
				result = append(result, harnessNotice)
				announced = true
			}
			swallow = true

		case swallow && isContinuation(line) && !isFrameHeader(line):
			/*
				**파이썬 트레이스백은 파일 줄 다음에 그 소스를 그대로 찍는다.**

				```
				  File "main.py", line 5, in <module>
				      print(solve(a, b))     ← 이 줄이 하네스의 코드다
				```

				파일 줄만 지우면 정작 코드가 그대로 나간다. 그래서 그 프레임에 딸린
				들여쓴 줄까지 함께 지운다 — 사용자 프레임의 들여쓴 줄은 `swallow` 가
				아니므로 그대로 남는다.
			*/

		default:
			result = append(result, line)
			swallow, announced = false, false
		}
	}
	return strings.Join(result, "\n")
}

/*
isFrameHeader 는 **새 프레임의 첫 줄**인지 본다.

들여쓰기만으로 가르면 안 된다 — 파이썬은 `  File "solution.py", line 3` 처럼 **사용자
프레임의 머리도 들여쓴다.** 그것을 앞 프레임에 딸린 줄로 보면 정작 원인을 지운다
(시험이 그것을 잡았다).

지금은 파이썬 형식만 안다. 언어가 늘면 그 언어의 형식을 여기에 더한다 — #421 이
"언어별로 다르게 어긋나는 것은 그때 본다" 로 정했다.
*/
func isFrameHeader(line string) bool {
	return strings.Contains(line, `File "`)
}

// isContinuation 은 앞 줄에 딸린 줄인지 본다. 트레이스백의 소스·화살표 줄이 그렇다.
func isContinuation(line string) bool {
	trimmed := strings.TrimLeft(line, " \t")
	return trimmed != line && trimmed != ""
}

const (
	workDirPrefix = "/work/"
	// 하네스를 읽어 실행하는 로더가 트레이스백에 남기는 표식.
	loaderMarker = "<string>"
	// **무엇이 났는지는 남기고 어디였는지만 감춘다.**
	harnessNotice = "  (문제의 실행 코드에서 오류가 났습니다 — 구현한 함수의 이름과 인자를 확인하세요)"
)
