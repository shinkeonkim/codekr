package contract

import "testing"

func TestValidateLimitsAcceptsRangeBoundaries(t *testing.T) {
	if err := ValidateLimits(MinTimeLimitMs, MinMemoryLimitMb); err != nil {
		t.Errorf("최솟값은 허용되어야 합니다: %v", err)
	}
	if err := ValidateLimits(MaxTimeLimitMs, MaxMemoryLimitMb); err != nil {
		t.Errorf("최댓값은 허용되어야 합니다: %v", err)
	}
}

func TestValidateLimitsRejectsOutOfRange(t *testing.T) {
	cases := []struct {
		name          string
		timeLimitMs   int
		memoryLimitMb int
	}{
		{"시간 0", 0, 256},
		{"시간 초과", MaxTimeLimitMs + 1, 256},
		{"메모리 0", 2000, 0},
		{"메모리 초과", 2000, MaxMemoryLimitMb + 1},
		{"둘 다 음수", -1, -1},
	}

	for _, c := range cases {
		if err := ValidateLimits(c.timeLimitMs, c.memoryLimitMb); err == nil {
			t.Errorf("%s: 거부되어야 합니다", c.name)
		}
	}
}
