# Git 문제 실행 하네스 (#654).
#
# **채점 모델이 Redis(#455)와 같다.** 제출이 명령의 연속이고 남는 것은 상태다.
# 그래서 정답 명령을 돌린 저장소와 제출을 돌린 저장소에서 **같은 확인 명령**을 돌려
# 그 출력을 견준다.
#
# 작업 디렉터리에 있을 수 있는 파일:
#   seed.git      (선택) 시작 저장소를 만드는 명령. 문제가 소유한다
#   answer.git    정답 명령. expected 쪽에서 돈다
#   verify.git    끝난 뒤를 읽는 명령. **양쪽에서 같은 것**이 돈다
#   commands.git  제출. actual 쪽에서 돈다
#
# ## 해시가 재현되게 만드는 것이 이 하네스의 절반이다
#
# 커밋 해시는 **작성자·커미터의 이름·메일·시각**을 함께 해싱한다. 그대로 두면 같은
# 명령이 매번 다른 해시를 내고, **같은 답이 때에 따라 틀린다** — #605 가 Redis 만료에서
# 겪은 것과 같은 종류다. 그래서 네 값을 고정한다. 고정하면 재현된다(실측 확인).
#
# 그래도 **확인 명령으로 커밋 해시를 그대로 찍는 것은 권하지 않는다.** 메시지 한 글자만
# 달라도 해시가 달라져, 같은 결과에 이른 다른 풀이가 틀린 답이 된다. 트리 해시(`%T`)와
# 그래프 모양(`--graph`, `%p`)은 내용만 보므로 그런 일이 없다.
#
# ## 네트워크는 "막힌다" 가 아니라 "없다" 로 답하게 한다
#
# 샌드박스가 egress 를 끊지만(ADR-0003) 그러면 `clone` 이 **타임아웃**으로 실패한다 —
# 시간 제한을 다 쓰고 나서야, 그리고 사용자에게는 "느리다" 로 보인다.
# `protocol.allow=never` 로 git 이 **즉시** 거부하게 한다.
set -eu

# **HOME 을 작업 디렉터리로 옮긴다** (#709).
#
# 아래 `git config --global` 은 `~/.gitconfig` 를 쓰는데, 샌드박스는 rootfs 가 읽기
# 전용이고 이 이미지의 `HOME` 은 `/` 다 — 그대로 두면 **첫 설정에서 죽는다.**
#
#     error: could not lock config file //.gitconfig: Read-only file system
#
# `set -eu` 라 거기서 끝나고, 사용자 명령은 돌지도 못한 채 SYSTEM_ERROR 가 된다.
# **하네스만 따로 돌리면 root 라서 그냥 되므로**, 샌드박스 옵션을 준 채로 돌려야
# 드러난다. 그렇게 드러났다.
export HOME=/work

export GIT_AUTHOR_NAME=codekr GIT_AUTHOR_EMAIL=codekr@codekr.kr
export GIT_COMMITTER_NAME=codekr GIT_COMMITTER_EMAIL=codekr@codekr.kr
export GIT_AUTHOR_DATE="2020-01-01T00:00:00+00:00"
export GIT_COMMITTER_DATE="2020-01-01T00:00:00+00:00"
# 편집기를 여는 명령(`rebase -i`, `commit` without -m)이 매달리지 않게 한다.
# 열리면 시간 제한까지 기다렸다가 죽고, 사용자는 왜인지 모른다.
export GIT_EDITOR=true GIT_SEQUENCE_EDITOR=true GIT_PAGER=cat GIT_TERMINAL_PROMPT=0

git config --global init.defaultBranch main
git config --global advice.detachedHead false
git config --global protocol.allow never
git config --global user.name codekr
git config --global user.email codekr@codekr.kr
git config --global safe.directory '*'

# 저장소 하나를 새로 만든다.
init_repo() {
    dir="/work/$1"
    rm -rf "$dir" && mkdir -p "$dir" && cd "$dir"
    git init -q .
}

# 명령 파일 하나를 한 줄씩 흘려 넣는다.
#
# **한 줄이 명령 하나다.** `sh` 로 통째로 돌리지 않는 이유: 그러면 제출이 셸 스크립트가
# 되어 `git` 이 아닌 것도 할 수 있다. 여기서 묻는 것은 git 이다.
#
# ## 제한은 파일마다 다르다 (#716)
#
# **시드와 정답은 문제가 소유한다.** 어드민이 쓰고 사용자는 못 건드린다 — SQL 문제가
# 스키마·시드를 자유롭게 쓰는 것과 같다(#60). 거기까지 `git` 만 받으면 **작업 트리에
# 파일을 만들 방법이 없어**, 스테이징·복원·stash 처럼 실무에서 실제로 쓰는 상황을
# 하나도 낼 수 없다.
#
# **시드는 제출 쪽 저장소에서도 문제가 소유한다.** 그래서 신뢰는 저장소가 아니라
# **파일**에 붙는다 — 저장소 단위로 걸었더니 제출 쪽 시드가 자기 규칙에 걸렸다.
#
# ## 죽지 않고 값을 돌려준다 (#715)
#
# 전에는 명령이 실패하면 `exit 1` 이었다. 셸 함수 안의 `exit` 는 **스크립트 전체를**
# 끝내므로, 제출이 실패하면 `--- codekr:actual` 이 아예 안 나왔다 — 견줄 것이 없어
# 채점기가 판정을 못 내고 사용자는 "채점 실패" 를 봤다. **제출이 실패하는 것은 정상적인
# 오답 경로다.** 누가 실패했는지는 부르는 쪽이 정한다.
#
#   $1  명령 파일
#   $2  trusted 이면 셸을 연다 (문제 소유). 아니면 git 명령만 받는다
run_file() {
    file="$1"
    trusted="$2"
    [ -f "$file" ] || return 0
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in
            ''|'#'*) continue ;;
        esac
        # **git 명령만 받는다.** 다른 것이 오면 무엇이 막혔는지 말한다 —
        # 조용히 건너뛰면 사용자는 자기 명령이 돌았다고 믿는다.
        if [ "$trusted" != "trusted" ]; then
            case "$line" in
                git\ *) ;;
                *) echo "git 명령만 쓸 수 있습니다: $line" >&2; return 1 ;;
            esac
        fi
        # shellcheck disable=SC2086
        eval "$line" || { echo "명령이 실패했습니다: $line" >&2; return 1; }
    done <"$file"
}

if [ ! -f /work/verify.git ]; then
    echo "문제에 확인 명령이 없습니다." >&2
    exit 1
fi

# 기대: 시드 + 정답. **둘 다 문제가 소유하므로 셸을 연다** (#716).
#
# 여기서 실패하면 **문제가 깨진 것**이다 — 채점 오류로 끝낸다. 사용자가 할 수 있는
# 일이 없고, 오답으로 처리하면 아무도 못 푸는 문제가 조용히 남는다.
init_repo expected
# **stderr 는 삼키지 않는다.** 문제가 깨졌을 때 무엇이 잘못됐는지 어드민이 볼 수
# 있어야 한다 — 전에는 둘 다 버려서 아무 메시지 없이 끝났다.
if ! { run_file /work/seed.git trusted && run_file /work/answer.git trusted; } >/dev/null; then
    echo "문제의 시드나 정답 명령이 실패했습니다." >&2
    exit 1
fi
echo "--- codekr:expected"
cd /work/expected && sh /work/verify.git

# 실제: 같은 시드 + 제출.
init_repo actual
if ! run_file /work/seed.git trusted >/dev/null; then
    echo "문제의 시드 명령이 실패했습니다." >&2
    exit 1
fi

# **제출은 git 명령만 받고, 실패해도 계속한다** (#715).
#
# 명령이 실패하면 저장소가 기대한 상태가 안 될 뿐이고, 그것을 견주면 오답으로 나온다 —
# 그것이 맞는 판정이다. 여기서 끝내면 견줄 것이 없어 채점 오류가 된다.
#
# **제출의 stderr 는 사용자에게 보인다.** 무엇이 잘못됐는지 볼 수 있는 것이 그것뿐이다.
run_file /work/commands.git untrusted >/dev/null || true

echo "--- codekr:actual"
# 확인 명령도 실패할 수 있다 — 제출이 커밋 하나 없는 상태로 두면 `rev-list` 가 죽는다.
# 그때는 빈 출력으로 견주고, 기대값과 달라 오답이 된다.
cd /work/actual && sh /work/verify.git || true
