package sandbox

import "testing"

func TestNewRejectsUnknownRuntime(t *testing.T) {
	if _, err := New("podman"); err == nil {
		t.Fatal("알 수 없는 런타임 이름은 기동 시점에 거부해야 합니다")
	}
}

// containerd 구현이 생기기 전까지는 조용히 엔진 API 로 넘어가면 안 된다.
// 그러면 운영 노드에서 잘못된 런타임에 붙은 채로 뜬다.
func TestNewRejectsUnimplementedContainerd(t *testing.T) {
	if _, err := New(RuntimeContainerd); err == nil {
		t.Fatal("구현되지 않은 런타임은 명시적으로 실패해야 합니다")
	}
}
