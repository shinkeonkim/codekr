# syntax=docker/dockerfile:1
# 아희 — 공식 이미지가 없어 직접 만든다 (#394).
#
# **정본을 pyaheui 로 정했다.** 구현이 여럿인데(caheui, rust-aheui …) 이것이 PyPI 에
# 올라와 있어 **버전을 숫자로 고정**할 수 있다. 소스에서 빌드하는 구현은 커밋을 고정해도
# 빌드 도구까지 함께 고정해야 하고, 그러면 이미지가 커진다.
#
# JIT 없이 도는 순수 파이썬이라 **느리다** — 실행 제한을 언어별로 따로 준다 (#97).
FROM python:3.13-alpine

# 버전을 고정한다. 태그는 다시 붙을 수 있고, 그때 우리가 검증한 것과 다른 것이
# 실행 노드에 들어간다 (#96 이 이미지에 대해 한 판단과 같다).
ARG AHEUI_VERSION=1.2.5

RUN pip install --no-cache-dir "aheui==${AHEUI_VERSION}"

# 인터프리터가 홈 디렉터리를 건드리지 않도록 작업 디렉터리로 돌려 둔다
# (읽기 전용 rootfs 에서 실패하지 않게 — kotlin 이미지와 같은 이유).
ENV HOME=/work
WORKDIR /work
