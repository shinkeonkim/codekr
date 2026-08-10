package sandbox

import (
	"os"
	"path/filepath"
	"testing"
)

func TestParseDockerConfigReadsBase64Auth(t *testing.T) {
	// kubernetes.io/dockerconfigjson 이 만드는 형태다 — username/password 없이 auth 만 있다.
	raw := []byte(`{"auths":{"ghcr.io":{"auth":"dXNlcjpwYXNz"}}}`)

	creds, err := parseDockerConfig(raw, "테스트")
	if err != nil {
		t.Fatalf("읽지 못했습니다: %v", err)
	}
	username, password, _ := creds.lookup("ghcr.io")
	if username != "user" || password != "pass" {
		t.Errorf("자격증명이 다릅니다: %q / %q", username, password)
	}
}

func TestParseDockerConfigMapsDockerHubKey(t *testing.T) {
	// 파일에는 index.docker.io 로 적히지만 resolver 는 registry-1.docker.io 로 묻는다.
	// 그대로 두면 Hub 자격증명이 영영 쓰이지 않는다.
	raw := []byte(`{"auths":{"https://index.docker.io/v1/":{"username":"u","password":"p"}}}`)

	creds, err := parseDockerConfig(raw, "테스트")
	if err != nil {
		t.Fatalf("읽지 못했습니다: %v", err)
	}
	if username, _, _ := creds.lookup("registry-1.docker.io"); username != "u" {
		t.Errorf("Docker Hub 자격증명을 찾지 못했습니다: %q", username)
	}
}

func TestParseDockerConfigSkipsEmptyEntries(t *testing.T) {
	// 빈 항목을 남기면 "자격증명이 있는데도 401" 로 보인다.
	raw := []byte(`{"auths":{"ghcr.io":{}}}`)

	creds, err := parseDockerConfig(raw, "테스트")
	if err != nil {
		t.Fatalf("읽지 못했습니다: %v", err)
	}
	if len(creds) != 0 {
		t.Errorf("빈 항목이 남았습니다: %v", creds)
	}
}

func TestParseDockerConfigRejectsBrokenFile(t *testing.T) {
	// **조용히 익명으로 넘어가면 안 된다.** 그러면 403 의 원인을 이미지 쪽에서 찾게 된다.
	for name, raw := range map[string][]byte{
		"JSON 이 아님":   []byte(`{`),
		"base64 가 아님": []byte(`{"auths":{"ghcr.io":{"auth":"!!"}}}`),
		"콜론이 없는 auth": []byte(`{"auths":{"ghcr.io":{"auth":"dXNlcg=="}}}`),
	} {
		if _, err := parseDockerConfig(raw, "테스트"); err == nil {
			t.Errorf("%s: 오류를 내야 합니다", name)
		}
	}
}

func TestLoadRegistryCredentialsIsOptional(t *testing.T) {
	// 공개 이미지만 쓰는 환경이 정상이다. 파일을 요구하면 로컬 개발이 막힌다.
	t.Setenv(dockerConfigEnv, t.TempDir())
	creds, err := loadRegistryCredentials()
	if err != nil {
		t.Fatalf("파일이 없는 것은 오류가 아닙니다: %v", err)
	}
	if len(creds) != 0 {
		t.Errorf("빈 결과여야 합니다: %v", creds)
	}

	t.Setenv(dockerConfigEnv, "")
	if creds, err := loadRegistryCredentials(); err != nil || creds != nil {
		t.Errorf("변수가 없으면 아무것도 읽지 않아야 합니다: %v / %v", creds, err)
	}
}

func TestLoadRegistryCredentialsReadsFile(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "config.json")
	if err := os.WriteFile(path, []byte(`{"auths":{"ghcr.io":{"auth":"dXNlcjpwYXNz"}}}`), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv(dockerConfigEnv, dir)

	creds, err := loadRegistryCredentials()
	if err != nil {
		t.Fatalf("읽지 못했습니다: %v", err)
	}
	if username, _, _ := creds.lookup("ghcr.io"); username != "user" {
		t.Errorf("자격증명을 찾지 못했습니다: %q", username)
	}
}

func TestCredentialStateNeverLeaksSecrets(t *testing.T) {
	creds := registryCredentials{"ghcr.io": {username: "user", password: "s3cret"}}

	for ref, want := range map[string]string{
		"ghcr.io/shinkeonkim/codekr-runtime-kotlin:2.2": "있음",
		"docker.io/library/python:3.12-alpine":          "이 호스트에는 없음",
	} {
		if got := credentialState(creds, ref); got != want {
			t.Errorf("%s: %q 가 아니라 %q", ref, want, got)
		}
	}
	if got := credentialState(nil, "ghcr.io/x:1"); got != "없음" {
		t.Errorf("자격증명이 없을 때: %q", got)
	}
}
