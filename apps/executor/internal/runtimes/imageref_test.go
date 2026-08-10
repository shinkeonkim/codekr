package runtimes

import "testing"

// 이미지 참조 만들기 (#96).
func TestImageRefWithoutRegistryOrDigest(t *testing.T) {
	d := Definition{Image: "python:3.13-alpine"}

	if got := d.ImageRef(""); got != "python:3.13-alpine" {
		t.Fatalf("원본 그대로여야 합니다: %s", got)
	}
}

func TestImageRefPrefixesRegistry(t *testing.T) {
	d := Definition{Image: "python:3.13-alpine"}

	// 미러를 쓰는 노드와 원본을 쓰는 로컬이 같은 정의 파일을 쓴다.
	if got := d.ImageRef("registry.example.com/"); got != "registry.example.com/python:3.13-alpine" {
		t.Fatalf("레지스트리가 앞에 붙어야 합니다: %s", got)
	}
}

func TestImageRefPinsDigestOverTag(t *testing.T) {
	d := Definition{Image: "python:3.13-alpine", Digest: "sha256:abc"}

	// **태그는 다시 붙을 수 있다.** 다이제스트가 있으면 그것으로 고정한다.
	if got := d.ImageRef(""); got != "python@sha256:abc" {
		t.Fatalf("다이제스트로 고정해야 합니다: %s", got)
	}
	if got := d.ImageRef("registry.example.com"); got != "registry.example.com/python@sha256:abc" {
		t.Fatalf("레지스트리와 다이제스트가 함께 붙어야 합니다: %s", got)
	}
}

// 포트가 붙은 레지스트리 주소의 콜론을 태그로 착각하면 안 된다.
func TestImageRefHandlesRegistryPort(t *testing.T) {
	d := Definition{Image: "python:3.13-alpine", Digest: "sha256:abc"}

	if got := d.ImageRef("localhost:5000"); got != "localhost:5000/python@sha256:abc" {
		t.Fatalf("포트를 태그로 착각했습니다: %s", got)
	}
}
