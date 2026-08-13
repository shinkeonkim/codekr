// Package runtimes 는 지원 언어/버전 정의를 읽어 들인다.
// 정의 파일은 api 와 공유하는 infra/runtimes/runtimes.yaml 하나뿐이다 (ADR-0003).
package runtimes

import (
	"fmt"
	"os"
	"strings"

	"gopkg.in/yaml.v3"
)

// Definition 은 런타임 하나의 실행 방법을 서술한다.
type Definition struct {
	ID    string `yaml:"id"`
	Label string `yaml:"label"`
	Image string `yaml:"image"`
	// Digest 는 이미지 다이제스트다 (#96). 비어 있으면 태그로만 받는다.
	//
	// **태그는 다시 붙을 수 있다.** python:3.13-alpine 이 어제와 오늘 다른 이미지일 수
	// 있고, 그러면 우리가 검증한 것과 다른 것이 실행 노드에 들어간다 — 격리가 이미지
	// 내용에 의존하는 이 프로젝트에서는 검증 결과의 의미가 약해진다.
	Digest     string   `yaml:"digest"`
	SourceFile string   `yaml:"sourceFile"`
	Compile    []string `yaml:"compile"`
	Run        []string `yaml:"run"`
	// Harness 는 이 런타임이 필요로 하는 실행 스크립트 이름이다 (#60).
	//
	// **문제가 제공하는 하네스(#421)와 다른 것이다.** 이쪽은 저장소가 들고 있는
	// 실행 스크립트(예: SQL 의 `run-sql.sh`)이고, 그쪽은 출제자가 쓴 코드다.
	Harness string `yaml:"harness"`
	// FunctionHarness 는 **함수형 문제**(#421)를 이 런타임에서 어떻게 돌리는지다.
	//
	// 없으면 이 런타임으로는 함수형 문제를 낼 수 없다 — 그리고 그것이 곧 문제의
	// 허용 언어가 된다 (#419).
	FunctionHarness *FunctionHarness `yaml:"functionHarness"`
	// User 는 컨테이너 안에서 코드를 돌릴 UID:GID 다 (#60). 비우면 기본 계정.
	//
	// PostgreSQL 의 initdb 는 UID 가 /etc/passwd 에 없으면 거부하므로, SQL 런타임은
	// 이미지에 있는 계정(70:70)을 써야 한다.
	User string `yaml:"user"`
	// Template 은 실행에 쓰이지 않지만, "기본 템플릿이 실제로 컴파일·실행되는가"를
	// 검증하는 데 필요해 함께 읽는다.
	Template string `yaml:"template"`
}

/*
FunctionHarness 는 하네스와 사용자 코드를 **파일로 나눠** 놓고 돌리는 방법이다 (#421).

`{{USER_CODE}}` 같은 자리에 끼워 넣지 않는다 — 문자열을 이어 붙이면 사용자 코드의
줄 번호가 하네스 길이만큼 밀린다.
*/
type FunctionHarness struct {
	// File 은 하네스를 저장할 이름이다.
	File string `yaml:"file"`
	/*
		SourceFile 은 **함수형 문제일 때** 사용자 코드를 저장할 이름이다.

		평소의 `SourceFile` 과 달라야 하는 언어가 있다 — 파이썬은 실행 진입점이
		`main.py` 인데 하네스가 그 자리를 가져가므로, 사용자 코드는 `solution.py` 로
		가서 **import 되는 쪽**이 된다.
	*/
	SourceFile string `yaml:"sourceFile"`
	// Run 은 하네스를 돌리는 명령이다. `Run` 을 대신한다.
	Run []string `yaml:"run"`
}

// SupportsFunctionHarness 는 이 런타임으로 함수형 문제를 낼 수 있는지다 (#421).
func (d Definition) SupportsFunctionHarness() bool {
	return d.FunctionHarness != nil &&
		d.FunctionHarness.File != "" &&
		d.FunctionHarness.SourceFile != "" &&
		d.FunctionHarness.File != d.FunctionHarness.SourceFile &&
		len(d.FunctionHarness.Run) > 0
}

// NeedsCompile 은 실행 전 컴파일 단계가 필요한지 알려준다.
func (d Definition) NeedsCompile() bool { return len(d.Compile) > 0 }

// ImageRef 는 실제로 받아올 이미지 참조를 만든다 (#96).
//
// registry 가 있으면 그 앞에 붙인다 — 미러를 쓰는 노드와 원본을 쓰는 로컬이
// **같은 정의 파일**을 쓰게 하기 위함이다.
//
// 다이제스트가 있으면 태그 대신 다이제스트로 고정한다. 미러링은 매니페스트를 그대로
// 복사하므로 원본과 미러의 다이제스트가 같다 — 어느 쪽에서 받아도 같은 것이 온다.
func (d Definition) ImageRef(registry string) string {
	image := d.Image
	if registry != "" {
		image = strings.TrimSuffix(registry, "/") + "/" + image
	}
	if d.Digest == "" {
		return image
	}
	// 태그와 다이제스트가 함께 있으면 다이제스트가 이긴다. 태그는 사람이 읽는 용도로 남긴다.
	if index := strings.LastIndex(image, ":"); index > strings.LastIndex(image, "/") {
		image = image[:index]
	}
	return image + "@" + d.Digest
}

// Registry 는 런타임 ID 로 정의를 찾아준다.
type Registry struct {
	byID map[string]Definition
}

type fileFormat struct {
	Runtimes []Definition `yaml:"runtimes"`
}

// Load 는 YAML 정의 파일을 읽어 레지스트리를 만든다.
func Load(path string) (*Registry, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("런타임 정의 파일 읽기 실패 (%s): %w", path, err)
	}
	return LoadFromBytes(raw)
}

// LoadFromBytes 는 이미 읽어 둔 YAML 내용으로 레지스트리를 만든다.
func LoadFromBytes(raw []byte) (*Registry, error) {
	var parsed fileFormat
	if err := yaml.Unmarshal(raw, &parsed); err != nil {
		return nil, fmt.Errorf("런타임 정의 파싱 실패: %w", err)
	}
	if len(parsed.Runtimes) == 0 {
		return nil, fmt.Errorf("런타임 정의가 비어 있습니다")
	}

	byID := make(map[string]Definition, len(parsed.Runtimes))
	for _, def := range parsed.Runtimes {
		if def.ID == "" || def.Image == "" || len(def.Run) == 0 {
			return nil, fmt.Errorf("런타임 정의가 불완전합니다: %+v", def)
		}
		byID[def.ID] = def
	}
	return &Registry{byID: byID}, nil
}

// Find 는 ID 에 해당하는 런타임을 찾는다.
func (r *Registry) Find(id string) (Definition, bool) {
	def, ok := r.byID[id]
	return def, ok
}

// Images 는 등록된 모든 런타임 이미지 목록을 돌려준다 (사전 pull 확인용). 중복은 제거한다.
func (r *Registry) Images() []string {
	seen := make(map[string]struct{}, len(r.byID))
	images := make([]string, 0, len(r.byID))
	for _, def := range r.byID {
		if _, ok := seen[def.Image]; ok {
			continue
		}
		seen[def.Image] = struct{}{}
		images = append(images, def.Image)
	}
	return images
}

// All 은 등록된 모든 런타임 정의를 돌려준다.
func (r *Registry) All() []Definition {
	all := make([]Definition, 0, len(r.byID))
	for _, def := range r.byID {
		all = append(all, def)
	}
	return all
}
