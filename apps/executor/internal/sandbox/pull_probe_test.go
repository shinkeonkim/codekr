package sandbox

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"testing"

	remoteerrors "github.com/containerd/containerd/v2/core/remotes/errors"
	"github.com/containerd/errdefs"
)

/*
다시 해도 안 되는 실패를 가른다 (#743).

레지스트리는 0초 만에 401 을 답했는데 실행기는 30분을 다 쓰고 `context deadline exceeded`
로 끝냈다. **그 문구는 왜 못 받았는지 아무것도 말하지 않는다.**
*/

func TestPermanentFailuresAreRecognised(t *testing.T) {
	cases := map[string]error{
		"자격증명이 안 통함": errdefs.ErrUnauthenticated,
		"권한 없음":      errdefs.ErrPermissionDenied,
		"없는 이미지":     errdefs.ErrNotFound,
		"401 응답":     remoteerrors.ErrUnexpectedStatus{StatusCode: 401},
		"403 응답":     remoteerrors.ErrUnexpectedStatus{StatusCode: 403},
		"404 응답":     remoteerrors.ErrUnexpectedStatus{StatusCode: 404},
		// 감싸도 알아봐야 한다 — 실제로는 여러 겹을 지나서 온다.
		"감싼 401": fmt.Errorf("이미지를 받지 못했습니다: %w", remoteerrors.ErrUnexpectedStatus{StatusCode: 401}),
	}
	for name, err := range cases {
		if !permanentPullFailure(err) {
			t.Errorf("%s 은 기다려도 안 되는 실패입니다: %v", name, err)
		}
	}
}

func TestTransientFailuresKeepWaiting(t *testing.T) {
	// **레지스트리가 잠깐 죽은 것은 기다릴 값어치가 있다** (#734 가 다루는 경우).
	// 여기서 끊으면 그 재시도가 아무 뜻이 없어진다.
	cases := map[string]error{
		"시간 초과":     context.DeadlineExceeded,
		"연결 거부":     errors.New("dial tcp 10.103.105.152:5000: connect: connection refused"),
		"500 응답":    remoteerrors.ErrUnexpectedStatus{StatusCode: 500},
		"503 응답":    remoteerrors.ErrUnexpectedStatus{StatusCode: 503},
		"429 응답":    remoteerrors.ErrUnexpectedStatus{StatusCode: 429},
		"아무 일도 없었음": nil,
	}
	for name, err := range cases {
		if permanentPullFailure(err) {
			t.Errorf("%s 은 다시 해 볼 값어치가 있습니다: %v", name, err)
		}
	}
}

func TestRefusalSaysWhichImageAndWhy(t *testing.T) {
	// `context deadline exceeded` 로 덮이지 않게, 무엇이 왜 안 됐는지 남긴다.
	err := pullRefused("zot.example:5000/mongo:7", remoteerrors.ErrUnexpectedStatus{StatusCode: 401})

	if !errors.As(err, new(remoteerrors.ErrUnexpectedStatus)) {
		t.Fatalf("원인이 감싸여 남아야 합니다: %v", err)
	}
	for _, want := range []string{"mongo:7", "내주지 않습니다"} {
		if !strings.Contains(err.Error(), want) {
			t.Fatalf("%q 가 없습니다: %v", want, err)
		}
	}
}
