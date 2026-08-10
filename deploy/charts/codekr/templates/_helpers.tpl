{{/* 이름과 라벨을 한 곳에서 만든다 — 템플릿마다 문자열을 반복하지 않게. */}}
{{- define "codekr.labels" -}}
app.kubernetes.io/name: codekr
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
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
