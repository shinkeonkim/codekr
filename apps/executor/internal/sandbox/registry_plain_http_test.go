package sandbox

import "testing"

// 평문으로 부를 호스트 목록 (#251).
//
// **비어 있으면 아무것도 평문이 아니다.** 기본값이 "평문 허용" 이면, 설정을 빠뜨린
// 환경에서 조용히 TLS 없이 나가게 된다.
func TestPlainHTTPHosts(t *testing.T) {
	tests := []struct {
		name  string
		env   string
		hosts []string
		other []string
	}{
		{name: "비어 있으면 없음", env: "", other: []string{"zot.registry.svc.cluster.local:5000"}},
		{
			name:  "호스트 하나",
			env:   "zot.registry.svc.cluster.local:5000",
			hosts: []string{"zot.registry.svc.cluster.local:5000"},
			// 포트가 다르면 다른 호스트다 — 주소를 그대로 맞춰야 한다.
			other: []string{"zot.registry.svc.cluster.local", "registry.shinkeonkim.com"},
		},
		{
			name:  "쉼표와 공백",
			env:   " a.local:5000 , b.local:5000 ",
			hosts: []string{"a.local:5000", "b.local:5000"},
			other: []string{"c.local:5000"},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Setenv(plainHTTPEnv, test.env)
			hosts := plainHTTPHosts()

			for _, host := range test.hosts {
				if _, ok := hosts[host]; !ok {
					t.Errorf("%s 가 평문 목록에 있어야 합니다: %v", host, hosts)
				}
			}
			for _, host := range test.other {
				if _, ok := hosts[host]; ok {
					t.Errorf("%s 는 평문 목록에 없어야 합니다: %v", host, hosts)
				}
			}
		})
	}
}
