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
