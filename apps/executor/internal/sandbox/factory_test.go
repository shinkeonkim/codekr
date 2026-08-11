package sandbox

import (
	"os"
	"testing"
)

func TestNewRejectsUnknownRuntime(t *testing.T) {
	if _, err := New("podman", ""); err == nil {
		t.Fatal("알 수 없는 런타임 이름은 기동 시점에 거부해야 합니다")
	}
}

// containerd 구현이 생기기 전까지는 조용히 엔진 API 로 넘어가면 안 된다.
// 그러면 운영 노드에서 잘못된 런타임에 붙은 채로 뜬다.
func TestNewRejectsUnimplementedContainerd(t *testing.T) {
	if _, err := New(RuntimeContainerd, ""); err == nil {
		t.Fatal("구현되지 않은 런타임은 명시적으로 실패해야 합니다")
	}
}

// 프로파일 파일이 없거나 망가졌으면 **기동 시점에** 실패해야 한다.
// 실행할 때마다 읽는 방식이었다면 첫 제출에서야 드러난다.
func TestNewRejectsMissingSeccompProfile(t *testing.T) {
	if _, err := New(RuntimeEngineAPI, "/없는/경로/seccomp.json"); err == nil {
		t.Fatal("없는 프로파일 파일은 기동 시점에 거부해야 합니다")
	}
}

func TestNewRejectsMalformedSeccompProfile(t *testing.T) {
	path := t.TempDir() + "/broken.json"
	if err := os.WriteFile(path, []byte("{ 이건 JSON 이 아니다"), 0o600); err != nil {
		t.Fatalf("준비 실패: %v", err)
	}
	if _, err := New(RuntimeEngineAPI, path); err == nil {
		t.Fatal("망가진 프로파일은 기동 시점에 거부해야 합니다")
	}
}
