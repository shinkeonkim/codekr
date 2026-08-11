package sandbox

// 비공개 레지스트리에서 런타임 이미지를 받기 위한 자격증명 (#171).

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

/*
자격증명 파일의 위치를 정하는 환경 변수.

**`DOCKER_CONFIG` 를 쓴다.** 제품명이 아니라 규약이다 — 쿠버네티스의 `imagePullSecrets`
(`kubernetes.io/dockerconfigjson`)가 이 형식이므로, 같은 시크릿을 실행기에 파일로
마운트하고 이 변수로 가리키면 된다.

kubelet 이 파드 이미지를 받을 때 쓰는 `imagePullSecrets` 는 **실행기에게 보이지 않는다.**
실행기는 자기가 직접 런타임 이미지를 받으므로 자기 몫의 자격증명이 따로 필요하다.
*/
const dockerConfigEnv = "DOCKER_CONFIG"

// registryCredentials 는 호스트별 자격증명이다. 비어 있으면 익명으로 받는다.
type registryCredentials map[string]dockerAuth

type dockerAuth struct {
	username string
	password string
}

// docker config.json 에서 우리가 읽는 부분만.
type dockerConfigFile struct {
	Auths map[string]struct {
		Username string `json:"username"`
		Password string `json:"password"`
		Auth     string `json:"auth"`
	} `json:"auths"`
}

/*
loadRegistryCredentials 는 자격증명 파일을 읽는다.

**파일이 없으면 오류가 아니다.** 공개 이미지만 쓰는 환경이 정상이고, 그때 이 파일을
요구하면 로컬 개발이 막힌다. 반대로 **파일이 있는데 망가졌으면 오류다** — 조용히 익명으로
넘어가면 비공개 이미지에서 403 을 받고 원인을 이미지 쪽에서 찾게 된다.
*/
func loadRegistryCredentials() (registryCredentials, error) {
	dir := os.Getenv(dockerConfigEnv)
	if dir == "" {
		return nil, nil
	}
	path := filepath.Join(dir, "config.json")
	raw, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, fmt.Errorf("레지스트리 자격증명을 읽지 못했습니다 (%s): %w", path, err)
	}
	return parseDockerConfig(raw, path)
}

func parseDockerConfig(raw []byte, path string) (registryCredentials, error) {
	var config dockerConfigFile
	if err := json.Unmarshal(raw, &config); err != nil {
		return nil, fmt.Errorf("레지스트리 자격증명 형식이 잘못됐습니다 (%s): %w", path, err)
	}

	creds := registryCredentials{}
	for host, entry := range config.Auths {
		auth := dockerAuth{username: entry.Username, password: entry.Password}
		// `auth` 는 "사용자:비밀번호" 를 base64 로 담은 것이다. 둘 다 있으면 이쪽이 이긴다
		// — kubernetes.io/dockerconfigjson 이 만드는 형태가 이것이다.
		if entry.Auth != "" {
			decoded, err := base64.StdEncoding.DecodeString(entry.Auth)
			if err != nil {
				return nil, fmt.Errorf("%s 의 auth 값을 풀지 못했습니다 (%s)", host, path)
			}
			username, password, found := strings.Cut(string(decoded), ":")
			if !found {
				return nil, fmt.Errorf("%s 의 auth 값에 ':' 가 없습니다 (%s)", host, path)
			}
			auth = dockerAuth{username: username, password: password}
		}
		if auth.username == "" && auth.password == "" {
			// 빈 항목은 없는 것과 같다. 남겨 두면 "자격증명이 있는데도 401" 로 보인다.
			continue
		}
		creds[normalizeAuthHost(host)] = auth
	}
	return creds, nil
}

/*
normalizeAuthHost 는 자격증명 파일의 키를 resolver 가 묻는 호스트 이름에 맞춘다.

docker 는 Docker Hub 를 `https://index.docker.io/v1/` 로 적는 관례가 있는데, resolver 는
`registry-1.docker.io` 로 묻는다. 그대로 두면 Hub 자격증명이 영영 쓰이지 않는다.
*/
func normalizeAuthHost(host string) string {
	trimmed := strings.TrimPrefix(strings.TrimPrefix(host, "https://"), "http://")
	trimmed = strings.TrimSuffix(trimmed, "/")
	trimmed = strings.TrimSuffix(trimmed, "/v1")
	if trimmed == "index.docker.io" || trimmed == "docker.io" {
		return "registry-1.docker.io"
	}
	return trimmed
}

// lookup 은 resolver 가 호스트마다 부르는 함수다. 모르는 호스트는 익명으로 둔다.
func (c registryCredentials) lookup(host string) (username, password string, err error) {
	auth, ok := c[host]
	if !ok {
		return "", "", nil
	}
	return auth.username, auth.password, nil
}
