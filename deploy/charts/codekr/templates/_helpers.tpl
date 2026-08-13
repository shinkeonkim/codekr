{{/* 이름과 라벨을 한 곳에서 만든다 — 템플릿마다 문자열을 반복하지 않게. */}}
{{- define "codekr.labels" -}}
app.kubernetes.io/name: codekr
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
남길 리비전 수 (#320).

**되돌리기는 git 에서 한다** — 배포 태그 파일의 이력이 곧 배포 이력이고
(`docs/09_배포_가이드.md` §5.1), `kubectl rollout undo` 로 되돌려 봐야 ArgoCD 가
selfHeal 로 매니페스트 상태로 되돌려 놓는다. 그러니 이력이 많을 이유가 없다.

그래도 **0 은 아니다.** 롤아웃 중 문제가 생겼을 때 앞 ReplicaSet 이 즉시 사라지면
손으로 급히 되돌릴 수단까지 없어진다. 3 은 "git 을 못 쓰는 상황에서 두어 번은
되돌릴 수 있다" 는 뜻이다.

기본값 10 을 그대로 두면 빨리 찬다 — 앱 다섯이 **하나의 태그를 공유**하고 CD 가
머지마다 그 태그를 바꾸므로(#246), 웹만 고친 머지에도 다섯이 전부 새 리비전을
만든다. 열 번 머지하면 ReplicaSet 이 쉰 개다.
*/}}
{{- define "codekr.revisionHistoryLimit" -}}
revisionHistoryLimit: 3
{{- end }}

{{- define "codekr.image" -}}
{{ .root.Values.global.registry }}/codekr-{{ .name }}:{{ .root.Values.global.imageTag }}
{{- end }}

{{/* 모든 앱이 공유하는 최소 권한 보안 컨텍스트. 실행기만 예외다(아래 주석 참고). */}}
{{- define "codekr.securityContext" -}}
runAsNonRoot: true
runAsUser: 10001
runAsGroup: 10001
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: ["ALL"]
{{- end }}

{{/*
실행기는 다른 네임스페이스에 둘 수 있다. 런타임 소켓을 마운트하는 hostPath 가
Pod Security 의 baseline 을 위반하므로, 그 네임스페이스만 privileged 로 열고
나머지 워크로드는 baseline 아래에 남기기 위함이다 (docs/07 잔여 위험 R1).
*/}}
{{- define "codekr.executorNamespace" -}}
{{ .Values.executor.namespace | default .Release.Namespace }}
{{- end }}

{{/* Redis 주소. 실행기가 네임스페이스를 넘어올 수 있으므로 항상 FQDN 으로 쓴다. */}}
{{- define "codekr.redisAddr" -}}
codekr-redis.{{ .Release.Namespace }}.svc.cluster.local:6379
{{- end }}

{{/*
비공개 레지스트리에서 이미지를 받을 때 쓰는 시크릿. 값이 없으면 아무것도 렌더링하지
않으므로 공개 이미지에는 영향이 없다.
*/}}
{{- define "codekr.imagePullSecrets" -}}
{{- with .Values.global.imagePullSecrets }}
imagePullSecrets:
{{- range . }}
  - name: {{ . }}
{{- end }}
{{- end }}
{{- end }}

{{/*
사용자가 접근하는 주소들. 공개(터널)와 내부(Traefik+Tailscale) 둘 다다.
비어 있는 값과 중복은 걸러낸다 — 둘을 같게 설정할 수도 있다.
*/}}
{{- define "codekr.hosts" -}}
{{- list .Values.ingress.publicHost .Values.ingress.internalHost | compact | uniq | toJson }}
{{- end }}

{{/*
종료 유예 (#415).

**하던 채점·실행을 마칠 시간을 준다.** 프로세스는 SIGTERM 을 받으면 새 작업을 그만 받고
하던 것을 끝내는데, 그 시간(`CODEKR_DRAIN_SECONDS`)보다 파드가 기다리는 시간이 짧으면
어차피 SIGKILL 로 끊긴다 — 그래서 항상 드레인보다 크게 잡는다.

기본 30초로는 채점 한 건도 못 끝낸다.
*/}}
{{- define "codekr.terminationGrace" -}}
terminationGracePeriodSeconds: {{ add .Values.workers.drainSeconds 30 }}
{{- end }}
