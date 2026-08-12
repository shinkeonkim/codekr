package sandbox

import (
	"os"
	"strings"
)

/*
평문 HTTP 로 부를 레지스트리 (#251).

**클러스터 안의 레지스트리는 TLS 를 걸지 않는 것이 보통이다.** 인증서를 붙일 이유가
없기 때문이다 — 트래픽이 노드 밖으로 나가지 않고, 앞단에 이미 인그레스가 TLS 를 맡는다.
그래서 `zot.registry.svc.cluster.local:5000` 같은 주소는 https 로 부르면 실패한다.

바깥 주소를 쓰면 클러스터 안에서 인터넷을 한 바퀴 돌아 다시 들어온다. 그것을 피하려면
안쪽 주소를 써야 하고, 안쪽 주소를 쓰려면 이것이 필요하다.

**호스트를 하나하나 적게 한다.** "사설 대역이면 평문" 같은 추측을 넣지 않는다 — 그러면
의도하지 않은 곳까지 평문으로 부르게 되고, 그 사실이 설정 어디에도 남지 않는다.
*/
const plainHTTPEnv = "EXECUTOR_REGISTRY_PLAIN_HTTP"

// plainHTTPHosts 는 평문으로 부를 호스트 집합이다. 쉼표로 여럿 적을 수 있다.
func plainHTTPHosts() map[string]struct{} {
	raw := strings.TrimSpace(os.Getenv(plainHTTPEnv))
	if raw == "" {
		return nil
	}

	hosts := make(map[string]struct{})
	for _, host := range strings.Split(raw, ",") {
		if trimmed := strings.TrimSpace(host); trimmed != "" {
			hosts[trimmed] = struct{}{}
		}
	}
	return hosts
}
