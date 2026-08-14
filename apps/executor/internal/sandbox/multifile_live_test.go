package sandbox

import (
	"context"
	"strings"
	"testing"

	"github.com/shinkeonkim/codekr/apps/executor/internal/runtimes"
)

/*
여러 파일로 낸 제출 (#457).

**언어마다 파일을 잇는 방식이 다르다.** 파이썬은 import, 자바는 클래스, C++ 는 각각을
컴파일해 링크한다. 실행기가 하는 일은 파일을 놓는 것뿐이지만, **놓기만 해서는 되지
않는 언어**가 있다 — 컴파일 명령이 형제 파일을 집어야 한다.

그래서 이 시험은 언어마다 **두 파일짜리 프로그램**을 실제로 돌린다. 정의 파일의 컴파일
명령이 형제 파일을 빠뜨리면 여기서 드러난다.
*/
func TestLiveMultiFileSubmissionRunsPerLanguage(t *testing.T) {
	box := newLiveSandbox(t)
	registry := loadSharedRegistry(t)

	cases := []struct {
		runtimeID string
		files     map[string]string
	}{
		{"python:3.13", map[string]string{
			"main.py":   "from helper import add\nprint(add(1, 2))\n",
			"helper.py": "def add(a, b):\n    return a + b\n",
		}},
		{"javascript:22", map[string]string{
			"main.js":   "const { add } = require('./helper');\nconsole.log(add(1, 2));\n",
			"helper.js": "module.exports.add = (a, b) => a + b;\n",
		}},
		{"cpp:17", map[string]string{
			"main.cpp":   "#include <iostream>\nint add(int, int);\nint main(){ std::cout << add(1,2) << std::endl; }\n",
			"helper.cpp": "int add(int a, int b){ return a + b; }\n",
		}},
		{"c:17", map[string]string{
			"main.c":   "#include <stdio.h>\nint add(int, int);\nint main(){ printf(\"%d\\n\", add(1,2)); }\n",
			"helper.c": "int add(int a, int b){ return a + b; }\n",
		}},
		{"java:21", map[string]string{
			"Main.java":   "public class Main { public static void main(String[] a){ System.out.println(Helper.add(1,2)); } }\n",
			"Helper.java": "class Helper { static int add(int a, int b){ return a + b; } }\n",
		}},
		{"go:1.26", map[string]string{
			"main.go":   "package main\n\nimport \"fmt\"\n\nfunc main() { fmt.Println(add(1, 2)) }\n",
			"helper.go": "package main\n\nfunc add(a, b int) int { return a + b }\n",
		}},
	}

	for _, testcase := range cases {
		t.Run(testcase.runtimeID, func(t *testing.T) {
			t.Parallel()
			definition, ok := registry.Find(testcase.runtimeID)
			if !ok {
				t.Skipf("정의 파일에 %s 가 없습니다", testcase.runtimeID)
			}

			outcome, err := box.Run(context.Background(), specFor(definition, testcase.files))
			if err != nil {
				t.Fatalf("실행 실패: %v", err)
			}
			if strings.TrimSpace(outcome.Stdout) != "3" {
				t.Fatalf("두 파일짜리 풀이가 3 을 내지 못했습니다.\nstdout=%q\nstderr=%q",
					outcome.Stdout, outcome.Stderr)
			}
		})
	}
}

// **문제가 소유하는 자료를 제출이 덮어쓸 수 없다** (#60, #457).
//
// 덮어쓸 수 있으면 스키마나 정답을 바꿔 놓고 통과하는 길이 열린다.
func TestLiveMultiFileCannotOverwriteProblemFiles(t *testing.T) {
	box := newLiveSandbox(t)
	registry := loadSharedRegistry(t)
	definition, ok := registry.Find("python:3.13")
	if !ok {
		t.Skip("정의 파일에 python:3.13 이 없습니다")
	}

	spec := specFor(definition, map[string]string{
		"main.py":   "print(3)\n",
		"input.txt": "덮어쓰기 시도\n",
	})
	if _, err := box.Run(context.Background(), spec); err == nil {
		t.Fatal("예약된 이름을 쓴 제출이 그대로 실행되었습니다")
	}
}

// 진입점이 빠진 제출은 **돌리지 않는다** — 빈 파일을 짠 결과가 판정으로 남지 않게.
func TestLiveMultiFileRequiresEntryPoint(t *testing.T) {
	box := newLiveSandbox(t)
	registry := loadSharedRegistry(t)
	definition, ok := registry.Find("python:3.13")
	if !ok {
		t.Skip("정의 파일에 python:3.13 이 없습니다")
	}

	spec := specFor(definition, map[string]string{"helper.py": "def add(a, b): return a + b\n"})
	if _, err := box.Run(context.Background(), spec); err == nil {
		t.Fatal("진입점 없는 제출이 그대로 실행되었습니다")
	}
}

func specFor(definition runtimes.Definition, files map[string]string) Spec {
	return Spec{
		Image:                definition.Image,
		SourceFile:           definition.SourceFile,
		SourceFiles:          files,
		Compile:              definition.Compile,
		Run:                  definition.Run,
		Harness:              definition.Harness,
		User:                 definition.User,
		TimeLimitMs:          10000,
		MemoryLimitMb:        512,
		CompileTimeoutMs:     60000,
		CompileMemoryLimitMb: 1024,
		MaxOutputBytes:       65536,
	}
}
