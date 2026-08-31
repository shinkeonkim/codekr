package judging

import contract "github.com/shinkeonkim/codekr/libs/gocontract"

/*
실행기에 닿지 못했을 때의 결과 (#741).

**`redis: nil` 이 사용자 화면에 그대로 떴다.** 그것은 Redis 클라이언트가 "아무것도
없다" 를 뜻하는 값이라, 사용자가 알 수 있는 것도 할 수 있는 것도 없다 — 그런데 자기
제출 옆에 붙어 있으니 **자기 코드가 잘못된 줄 안다.**

`Stderr` 를 그대로 보여 주는 것이 **맞는 경우가 대부분**이다. 하네스가 담아 주는
`git 명령만 쓸 수 있습니다` 나 컴파일러 오류는 사용자가 읽어야 할 것이다. 다른 것은
**실행기에 닿지도 못한 경우**뿐이고, 그때의 오류는 우리 인프라의 말이지 사용자의 코드에
대한 말이 아니다.

기술적인 원인은 부르는 쪽이 로그에 남긴다 — 여기서는 사람이 **할 수 있는 일**만 말한다.

여덟 유형이 같은 자리를 갖고 있어 한 곳에서 만든다. 유형이 늘 때 또 빠뜨리지 않게
하려는 것이고, #681 이 제출 번호를 아홉 군데에서 한 곳으로 모은 것과 같은 이유다.
*/
func executorUnreachable() contract.ExecResult {
	return contract.ExecResult{
		Status: contract.StatusSystemError,
		Stderr: UnreachableMessage,
	}
}

// UnreachableMessage 는 화면에 그대로 나간다. **원인이 아니라 할 일을 적는다.**
const UnreachableMessage = "채점 서버가 실행 결과를 받지 못했습니다. 잠시 뒤 다시 제출해 주세요."
