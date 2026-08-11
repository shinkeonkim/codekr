package sandbox

// user namespace 재매핑 (#130).

import (
	"fmt"
	"os"
	"strconv"

	"github.com/opencontainers/runtime-spec/specs-go"
)

// 재매핑할 UID/GID 폭. 컨테이너 안의 0~65535 를 호스트의 한 구간에 겹쳐 놓는다.
//
// 65536 인 이유: 이미지가 쓰는 UID 는 거의 언제나 이 아래에 있고, `/etc/subuid` 가
// 관례적으로 이 단위로 나눈다.
const usernsRangeSize = 65536

// 재매핑을 켜는 환경 변수. 호스트에서 이 컨테이너들이 쓸 UID 구간의 시작이다.
const usernsOffsetEnv = "CODEKR_USERNS_HOST_OFFSET"

/*
usernsMapping 은 재매핑 설정을 읽는다.

**기본은 꺼짐이다.** 호스트의 어느 구간을 내줄지는 노드마다 다르고, 아무 값이나 고르면
그 구간을 쓰는 실제 계정과 겹칠 수 있다. 겹치면 재매핑이 오히려 권한을 **주는** 셈이 된다.

`CODEKR_USERNS_HOST_OFFSET=100000` 이면 컨테이너의 UID 10001 은 호스트의 110001 이 된다.
그 UID 는 호스트에 존재하지 않는 것이어야 한다 (`/etc/subuid` 로 예약).
*/
func usernsMapping() ([]specs.LinuxIDMapping, bool, error) {
	raw := os.Getenv(usernsOffsetEnv)
	if raw == "" {
		return nil, false, nil
	}
	offset, err := strconv.ParseUint(raw, 10, 32)
	if err != nil {
		return nil, false, fmt.Errorf("%s 는 숫자여야 합니다 (%q): %w", usernsOffsetEnv, raw, err)
	}
	// **0 은 거부한다.** 그것은 재매핑을 켠 것처럼 보이지만 컨테이너의 root 가 호스트의
	// root 가 되는, 켜지 않은 것보다 나쁜 설정이다.
	if offset == 0 {
		return nil, false, fmt.Errorf("%s 는 0 일 수 없습니다 — 재매핑이 되지 않습니다", usernsOffsetEnv)
	}
	return []specs.LinuxIDMapping{{
		ContainerID: 0,
		HostID:      uint32(offset),
		Size:        usernsRangeSize,
	}}, true, nil
}
