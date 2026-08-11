package sandbox

import (
	"fmt"
	"os"
)

// 지원하는 런타임 구현.
const (
	// RuntimeEngineAPI 는 OCI 호환 엔진 API 로 런타임과 이야기한다. 로컬 개발 환경과
	// 엔진 API 를 노출하는 노드에서 쓴다.
	RuntimeEngineAPI = "engine-api"
	// RuntimeContainerd 는 containerd 를 CRI 로 직접 다룬다. 아직 구현하지 않았다 (#45).
	RuntimeContainerd = "containerd"
)

// New 는 설정된 런타임에 맞는 샌드박스를 만든다.
//
// 구현을 이름으로 고르게 한 이유는, 운영 노드의 런타임이 로컬 개발 환경과 다를 수
// 있는데 그 차이가 첫 제출에서야 드러나면 곤란하기 때문이다. 알 수 없는 이름이나
// 아직 없는 구현은 **기동 시점에** 실패한다.
func New(runtime, seccompProfilePath string) (Sandbox, error) {
	switch runtime {
	case "", RuntimeEngineAPI:
		// **켤 수 없는 것을 켠 것처럼 두지 않는다** (#130). 재매핑은 컨테이너별 spec 으로
		// 거는 것이고, 엔진 API 에서는 daemon 설정이라 여기서 할 수 있는 것이 없다.
		// 조용히 무시하면 재매핑이 걸린 줄 알고 운영한다.
		if os.Getenv(usernsOffsetEnv) != "" {
			return nil, fmt.Errorf(
				"%s 는 %s 런타임에서만 쓸 수 있습니다 — 엔진 API 에서는 daemon 의 --userns-remap 설정입니다",
				usernsOffsetEnv, RuntimeContainerd)
		}
		return NewContainerSandbox(seccompProfilePath)
	case RuntimeContainerd:
		// 소켓 경로는 환경마다 다르다. 비우면 기본 경로를 쓴다 (#68).
		return NewContainerdSandbox(os.Getenv("CONTAINERD_ADDRESS"), seccompProfilePath)
	default:
		return nil, fmt.Errorf(
			"알 수 없는 샌드박스 런타임 %q — %s 또는 %s 여야 합니다",
			runtime, RuntimeEngineAPI, RuntimeContainerd)
	}
}
