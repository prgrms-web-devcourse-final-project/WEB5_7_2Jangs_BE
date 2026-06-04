# Logging Hook과 검증 재현성 계획

## 목적

AI 에이전트의 작업을 블랙박스로 두지 않고, 어떤 요청과 도구 실행이 코드 변경과 검증으로 이어졌는지 작업 단위로 추적할 수 있도록 한다.

LLM은 같은 프롬프트에도 다른 결과를 만들 수 있으므로 동일한 코드 생성 결과를 재현하는 것을 목표로 하지 않는다. 대신 다른 사람이 작업 시작 시점과 계획, 실행한 검증을 확인하고 같은 검증 절차를 다시 수행할 수 있는 상태를 목표로 한다.

## 목표 표현

- `추적 가능성`: 변경 원인과 도구 실행 흐름을 확인할 수 있다.
- `검증 재현성`: 같은 Git 기준점에서 동일한 테스트와 검증 명령을 다시 수행할 수 있다.
- `판단 근거`: AI 제안을 채택하거나 기각한 이유를 PR에서 확인할 수 있다.

다음 표현은 사용하지 않는다.

- 같은 프롬프트로 동일한 결과물을 재현했다.
- AI 생성 코드의 정확성을 보장했다.
- Logging Hook만으로 개발 안정성을 보장했다.

## 대상 Hook

Codex 프로젝트 설정의 `.codex/hooks.json`에서 다음 이벤트를 우선 사용한다.

| Hook | 기록 목적 |
| --- | --- |
| `UserPromptSubmit` | 작업 요청과 작업 ID 연결 |
| `PreToolUse` | 실행 전 도구 이름과 입력 요약 기록 |
| `PostToolUse` | 실행 결과와 성공 여부 기록 |
| `Stop` | 작업 종료 시 Git 변경과 검증 결과 요약 |

초기 구현에서는 `Bash`와 `apply_patch`만 기록한다. 모든 MCP 도구와 파일 읽기까지 기록하면 로그가 과도하게 커지고 비밀 정보 노출 위험이 높아진다.

## 저장 구조

```text
.codex/
  hooks.json
  hooks/
    log_ai_event.py
    redact_ai_log.py
    summarize_ai_log.py
ai/
  logs/
    .gitkeep
  summaries/
    .gitkeep
```

- `ai/logs/*.jsonl`: 원본 실행 로그. Git에 커밋하지 않는다.
- `ai/summaries/*.md`: 사람이 검토한 작업 요약. 필요한 경우 PR에 첨부하거나 커밋한다.

## 로그 스키마

```json
{
  "taskId": "DOCSA-203",
  "timestamp": "2026-06-04T14:00:00+09:00",
  "event": "PostToolUse",
  "tool": "Bash",
  "inputSummary": "./gradlew test",
  "success": true,
  "cwd": "/workspace/docsa",
  "baseCommit": "ddca378",
  "changedFiles": [
    "src/main/java/io/ejangs/docsa/domain/doc/app/DocQueryService.java"
  ]
}
```

원본 파일 내용과 전체 대화 내용은 기록하지 않는다.

## 비밀 정보 보호

로그 저장 전에 다음 내용을 마스킹하거나 제외한다.

- 비밀번호, API Key, Access Token, Refresh Token
- `.env`와 인증서 파일 내용
- 데이터베이스 연결 문자열
- 개인정보와 사용자 입력 데이터
- 명령 출력에 포함된 비밀 정보

마스킹에 실패할 가능성이 있으므로 원본 로그는 기본적으로 Git에 커밋하지 않는다.

## 작업 흐름 연결

1. 작업 시작 시 Issue 또는 PR 번호를 `taskId`로 지정한다.
2. Hook이 작업 시작 Git commit과 도구 실행을 JSONL로 기록한다.
3. 구현 전 계획과 사람의 승인 내용은 Issue 또는 PR에 남긴다.
4. 구현 후 `summarize_ai_log.py`가 변경 파일과 검증 명령을 요약한다.
5. 사람은 요약을 검토하고 채택과 기각 판단, 남은 리스크를 PR에 작성한다.
6. 리뷰 결과를 반영한 뒤 동일한 검증 명령을 다시 실행한다.

## 단계별 적용 계획

### 1단계: 수동 기록

- 현재 PR 템플릿으로 AI 사용 범위와 검증 결과를 기록한다.
- 최소 3건의 PR에서 반복해 필요한 로그 항목을 확인한다.

### 2단계: 도구 실행 Logging Hook

- `Bash`와 `apply_patch`의 `PreToolUse`, `PostToolUse` 로그를 JSONL로 저장한다.
- 비밀 정보 마스킹 테스트를 작성한다.
- 로그 파일을 `.gitignore`에 추가한다.

### 3단계: 작업 요약 자동화

- 작업 시작 commit과 종료 diff를 연결한다.
- 실행한 테스트 명령과 성공 여부를 Markdown으로 요약한다.
- PR의 `AI 보조 작업 기록` 작성에 활용한다.

### 4단계: 효과 검증

- 로그로 잘못된 명령이나 변경 원인을 추적한 사례를 남긴다.
- 다른 사람이 요약 문서만 보고 검증 명령을 다시 실행할 수 있는지 확인한다.
- 불필요한 로그와 비밀 정보 노출 위험을 줄인다.

## 완료 조건

- Hook이 실제 Codex 작업에서 실행된다.
- `Bash`와 `apply_patch` 도구 실행이 작업 ID와 함께 기록된다.
- 비밀 정보 마스킹 테스트가 통과한다.
- 작업 시작 commit, 변경 파일, 검증 명령을 연결한 요약이 생성된다.
- 최소 3건의 PR에서 요약과 사람의 판단 기록을 함께 남긴다.
- 로그를 이용해 변경 원인 또는 검증 누락을 찾은 사례가 1건 이상 있다.

## 이력서 문장 기준

2단계 완료 후:

> Codex의 Pre/Post Tool Use Hook을 구성해 AI 에이전트의 도구 실행 이력을 작업 단위로 기록하고, 변경 원인을 추적할 수 있도록 했습니다.

4단계 완료 후:

> AI 에이전트의 도구 실행 이력을 수집하는 Logging Hook과 PR 요약 절차를 구축했습니다. 작업 시작 commit과 변경 파일, 테스트 결과를 연결해 AI 보조 작업의 추적 가능성과 검증 재현성을 높였습니다.
