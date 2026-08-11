// Package runtimes 는 지원 언어/버전 정의를 읽어 들인다.
// 정의 파일은 api 와 공유하는 infra/runtimes/runtimes.yaml 하나뿐이다 (ADR-0003).
package runtimes

import (
	"fmt"
	"os"

	"gopkg.in/yaml.v3"
)

// Definition 은 런타임 하나의 실행 방법을 서술한다.
type Definition struct {
	ID         string   `yaml:"id"`
	Label      string   `yaml:"label"`
	Image      string   `yaml:"image"`
	SourceFile string   `yaml:"sourceFile"`
	Compile    []string `yaml:"compile"`
	Run        []string `yaml:"run"`
	// Harness 는 이 런타임이 필요로 하는 실행 스크립트 이름이다 (#60).
	Harness string `yaml:"harness"`
	// User 는 컨테이너 안에서 코드를 돌릴 UID:GID 다 (#60). 비우면 기본 계정.
	//
	// PostgreSQL 의 initdb 는 UID 가 /etc/passwd 에 없으면 거부하므로, SQL 런타임은
	// 이미지에 있는 계정(70:70)을 써야 한다.
	User string `yaml:"user"`
	// Template 은 실행에 쓰이지 않지만, "기본 템플릿이 실제로 컴파일·실행되는가"를
	// 검증하는 데 필요해 함께 읽는다.
	Template string `yaml:"template"`
}

// NeedsCompile 은 실행 전 컴파일 단계가 필요한지 알려준다.
func (d Definition) NeedsCompile() bool { return len(d.Compile) > 0 }

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
