# Perf 디렉터리 재구성 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 성능 측정 주제를 `results` 아래가 아닌 `perf` 디렉터리 계층에서 먼저 분리하고, 실행 코드·결과·문서 경로를 일관되게 정리한다.

**Architecture:** `perf/<도메인>/<시나리오>/`가 실행 파일과 `results/`를 함께 소유한다. 공용 데이터 생성은 `perf/seed/`에 유지하고, 사람용 판정 문서는 `docs/performance/`에 둔다.

**Tech Stack:** k6 JavaScript, Node.js, Bash, Git ignore rules, Markdown/HTML 문서

## Global Constraints

- 기존 측정 결과는 삭제하지 않고 새 경로로 이동한다.
- raw 결과는 기본 Git 제외, `results/evidence/`만 추적 허용한다.
- `single_delete`는 `perf/delete/single/`로 통합한다.
- 결과 보고서는 `docs/performance/`에 유지한다.
- 커밋과 push는 수행하지 않는다.

---

### Task 1: 읽기 측정 주제 분리

**Files:**
- Move: `perf/read/results/commit-cache/` → `perf/read/commit-cache/results/`
- Move: commit cache 실행/테스트 파일 → `perf/read/commit-cache/`
- Move: `perf/read/results/cqrs/` → `perf/read/cqrs/results/`
- Move: `perf/read/doc_list_benchmark.js` → `perf/read/cqrs/`
- Move: `perf/read/results/graph/` → `perf/read/graph/results/`
- Move: `perf/read/doc_graph_benchmark.js` → `perf/read/graph/`
- Move: `perf/read/results/simple-read-probes/` → `perf/read/simple-read-probes/results/`

- [x] 이동 뒤 commit-cache runner의 기본 결과 경로를 `perf/read/commit-cache/results`로 바꿨다.
- [x] CQRS/graph benchmark의 기본 결과 경로를 각 시나리오 `results/`로 바꿨다.
- [x] README, 테스트, 성능 보고서와 계획 문서의 경로 참조를 갱신했다.
- [x] Node 테스트와 Bash runner 테스트를 실행했다.

### Task 2: 삭제/썸네일 측정 주제 분리

**Files:**
- Move: 기존 `perf/delete/` 실행 파일과 결과 → `perf/delete/bulk/`
- Move: `perf/single_delete/` → `perf/delete/single/`
- Move: preview E2E 파일/결과 → `perf/thumbnail/preview-e2e/`
- Move: workflow 파일/결과 → `perf/thumbnail/workflow/`

- [x] 기본 `RESULT_DIR`과 CLI 사용 예시를 새 경로로 바꿨다.
- [x] 삭제 비교기의 경로 참조를 갱신했다. 공용 정리 스크립트는 결과 경로를 직접 참조하지 않는다.
- [x] 기존 결과를 시나리오별 `results/`로 이동하고 삭제하지 않았다.

### Task 3: 공통 규칙과 ignore 정책

**Files:**
- Modify: `.gitignore`
- Modify: `AGENTS.md`
- Create: `perf/README.md`
- Modify: 각 시나리오 README

- [x] 결과 추적 예외를 `perf/**/results/evidence/`로 한정했다.
- [x] `AGENTS.md`에 `도메인 → 시나리오 → results` 구조와 raw/evidence 규칙을 추가했다.
- [x] `perf/README.md`에 디렉터리 구조와 공용 seed 역할을 기록했다.
- [x] `.DS_Store`를 제거하고 `.gitignore`로 재발을 막았다.

### Task 4: 검증

- [x] `node --test`로 perf Node 테스트를 실행했다.
- [x] commit-cache Bash runner 테스트를 실행했다.
- [x] 이전 runtime 경로 참조가 남지 않았는지 확인했다. 이 계획의 이동 전 경로 설명과 legacy 문서는 제외했다.
- [x] `git diff --check`를 실행했다.
