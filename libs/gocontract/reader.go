package contract

import (
	"bytes"
	"io"
)

// newReader 는 바이트 슬라이스를 스트림 디코더에 물릴 수 있게 감싼다.
func newReader(raw []byte) io.Reader { return bytes.NewReader(raw) }
