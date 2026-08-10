package dispatch

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/redis/go-redis/v9"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// EventPublisher 는 채점 진행 이벤트를 발행한다.
// api 가 이것을 구독해 결과를 영속화하고 WebSocket 으로 중계한다 (ADR-0004).
type EventPublisher struct {
	redis *redis.Client
}

// NewEventPublisher 는 이벤트 발행자를 만든다.
func NewEventPublisher(client *redis.Client) *EventPublisher {
	return &EventPublisher{redis: client}
}

// Publish 는 이벤트 하나를 채널에 브로드캐스트한다.
func (p *EventPublisher) Publish(ctx context.Context, event contract.Event) error {
	encoded, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("이벤트 직렬화 실패: %w", err)
	}
	return p.redis.Publish(ctx, contract.ChannelEvents, encoded).Err()
}
