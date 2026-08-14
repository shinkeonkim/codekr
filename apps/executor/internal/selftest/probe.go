package selftest

import (
	"fmt"

	"github.com/shinkeonkim/codekr/apps/executor/internal/runtimes"
)

// ProbeRuntimeID 는 검사를 실어 나를 런타임이다. 파이썬은 표준 라이브러리만으로 커널
// 경계를 들여다볼 수 있어서 검사 코드가 짧아진다.
const ProbeRuntimeID = "python:3.12"

/*
ShellRuntimeID 는 셸 검사를 실어 나를 런타임이다 (#456).

셸 문제가 들어오면서 **검사가 파이썬만 전제하면 안 되게** 됐다. 같은 상자라도 이미지가
다르면 안에 들어 있는 것이 다르고, 셸은 프로세스를 만드는 방법이 훨씬 짧다.
*/
const ShellRuntimeID = "bash:5"

// Probe 는 검사를 실을 런타임이다.
//
// **이미지를 여기에 적어 두지 않는다** (#218). 전에는 `python:3.12-alpine` 이라고
// 박아 두었는데, 그러면 정의 파일이 가리키는 것과 이 노드에 있는 것이 어긋나도
// 검사는 전부 통과한다. 실제로 다이제스트를 고정한 뒤(#96) 채점이 전부 SYSTEM_ERROR
// 가 됐는데 `--self-test` 는 초록이었다 (PR #217).
//
// 검사 코드와 판정은 그대로다 — 두 샌드박스 구현을 같은 잣대로 보는 것이 이 검사의
// 목적이기 때문이다 (#68). 바뀌는 것은 **어느 이미지로 싣는가** 하나뿐이고, 그것도
// 양쪽이 같은 정의 파일에서 온다.
type Probe struct {
	Image      string
	SourceFile string
	Run        []string
}

/*
Probes 는 검사들이 쓰는 런타임을 **전부** 푼다 (#456).

검사마다 런타임이 다를 수 있어서 하나만 풀어 두면 셸 검사가 파이썬으로 돈다 —
그러면 통과해도 아무것도 확인한 것이 없다.
*/
func Probes(registry *runtimes.Registry, registryPrefix string) (map[string]Probe, error) {
	probes := make(map[string]Probe)
	for _, check := range Checks() {
		id := check.runtimeID()
		if _, done := probes[id]; done {
			continue
		}
		probe, err := probeFor(registry, registryPrefix, id)
		if err != nil {
			return nil, err
		}
		probes[id] = probe
	}
	return probes, nil
}

func (c Check) runtimeID() string {
	if c.RuntimeID == "" {
		return ProbeRuntimeID
	}
	return c.RuntimeID
}

// ProbeFrom 은 정의 파일에서 검사용 런타임을 꺼낸다 (#218).
//
// registry 접두와 다이제스트까지 포함한 **실제로 받아올 참조**를 만든다 — 채점이 쓰는
// 것과 같은 경로여야 어긋남이 검사에 잡힌다.
func ProbeFrom(registry *runtimes.Registry, registryPrefix string) (Probe, error) {
	return probeFor(registry, registryPrefix, ProbeRuntimeID)
}

func probeFor(registry *runtimes.Registry, registryPrefix, runtimeID string) (Probe, error) {
	definition, ok := registry.Find(runtimeID)
	if !ok {
		return Probe{}, fmt.Errorf("검사용 런타임 %s 가 정의 파일에 없습니다", runtimeID)
	}
	return Probe{
		Image:      definition.ImageRef(registryPrefix),
		SourceFile: definition.SourceFile,
		Run:        definition.Run,
	}, nil
}
