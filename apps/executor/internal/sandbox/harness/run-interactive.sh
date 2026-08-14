# 인터랙티브 문제 실행 하네스 (#474).
#
# **도는 중에 주고받는다.** 스페셜 저지(#452)는 끝난 뒤 출력을 받아 판정하지만, 여기서는
# 채점 코드와 제출이 **동시에 돌면서** 서로에게 쓴다 — 그 차이가 전부다.
#
# 작업 디렉터리에 있을 수 있는 파일:
#   interactor.py  출제자가 쓴 채점 코드. 대화를 주관하고 **종료 코드로 판정**한다
#   case.txt       이 케이스의 숨은 값 (테스트케이스의 입력)
#   main.py        제출
#
# ## 왜 FIFO 가 아니라 파이프인가
#
# 처음에는 `mkfifo` 로 팠다. **좁힌 seccomp 프로파일(#48)이 그것을 막는다** —
# `mknod` 계열이 허용 목록에 없다. 로컬(Docker 기본 프로파일)에서는 되고 CI·운영
# (containerd + 좁힌 프로파일)에서는 안 되는, 가장 늦게 드러나는 종류의 차이였다.
#
# 보통의 파이프(`pipe2`)는 그 목록에 있고, 어차피 컨테이너가 파이썬이므로 프로세스를
# 띄우고 잇는 일도 파이썬에게 맡긴다.
#
# ## 버퍼링이 가장 흔한 오답 원인이다
#
# 대부분의 언어가 표준 출력을 줄이 아니라 블록으로 모은다. **제출자가 flush 를 안 하면
# 영원히 안 도착한다** — 사용자 잘못이지만 사용자가 알기 어렵다. 그래서 둘 다
# `-u`(버퍼 없음)로 돌린다.
#
# ## 종료 코드가 판정이다 (#452 의 규약을 넓힌다)
#
#   0  정답
#   1  오답
#   3  **교착** — 제출이 아무것도 보내지 않고 끝났다. "시간 초과" 와 다른 말을 해야 한다
#   그 밖  출제자의 코드가 잘못됐다 (사용자 잘못이 아니다)
set -u

python3 -u - <<'PY'
import subprocess
import sys

with open("/work/.interactor.err", "w") as err:
    judge = subprocess.Popen(
        [sys.executable, "-u", "/work/interactor.py"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=err,
    )
    solver = subprocess.Popen(
        [sys.executable, "-u", "/work/main.py"],
        # 서로의 입출력을 맞물린다. 채점 코드가 쓰면 제출이 읽고, 그 반대도 같다.
        stdin=judge.stdout, stdout=judge.stdin, stderr=subprocess.DEVNULL,
    )
    # **부모가 쥔 쪽을 놓는다.** 안 놓으면 쓰는 쪽이 남아 있는 셈이라 한쪽이 끝나도
    # 상대에게 EOF 가 가지 않고, 아무것도 안 보낸 제출이 교착으로 갈리지 않는다.
    judge.stdout.close()
    judge.stdin.close()

    solver.wait()
    # 제출이 먼저 죽어도 채점 코드의 판정을 쓴다 — 무엇이 틀렸는지는 그쪽만 안다.
    sys.exit(judge.wait())
PY
status=$?

# 채점 코드가 남긴 말은 그대로 사용자에게 간다 — 무엇이 틀렸는지 그것만 안다.
cat /work/.interactor.err >&2 2>/dev/null || true
exit "$status"
