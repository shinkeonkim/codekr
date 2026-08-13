# syntax=docker/dockerfile:1
# 엄랭 (umjunsik-lang) — 공식 이미지가 없어 직접 만든다 (#394).
#
# **파이썬 구현을 고른 이유**: 같은 저장소에 여러 구현이 있는데(go, rust, java, deno …)
# 파이썬 것이 105줄이고 런타임이 이미 이미지에 있다. 컴파일러를 얹으면 이미지가 커지고
# 빌드 도구까지 고정해야 한다.
#
# **커밋으로 고정한다.** 이 저장소는 릴리스 태그가 없다 — 브랜치를 그대로 쓰면
# 어느 날 다른 것이 실행 노드에 들어간다 (#96 과 같은 이유).
FROM python:3.13-alpine

ARG UMJUNSIK_COMMIT=e973f9d22b9803ee86b53d8e60f1b65ab08c7547

RUN apk add --no-cache git \
    && git clone --filter=blob:none --no-checkout \
         https://github.com/rycont/umjunsik-lang.git /tmp/umm \
    && git -C /tmp/umm checkout "${UMJUNSIK_COMMIT}" -- umjunsik-lang-python \
    && mkdir -p /opt/umjunsik \
    && cp /tmp/umm/umjunsik-lang-python/*.py /opt/umjunsik/ \
    && rm -rf /tmp/umm \
    && apk del git

# 패키지로 부를 수 있게 둔다 — `python -m umjunsik <파일>`.
ENV PYTHONPATH=/opt

ENV HOME=/work
WORKDIR /work
