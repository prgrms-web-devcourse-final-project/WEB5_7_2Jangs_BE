# Docsa Monitoring Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Docsa 운영과 스테이징의 중복 모니터링을 한 스택으로 통합하고 스테이징 모니터링 데이터 볼륨을 삭제한다.

**Architecture:** 운영 Compose가 공용 관측 스택을 소유한다. 운영 Prometheus는 `docsa_monitoring_net`을 통해 스테이징 앱과 MySQL Exporter도 수집하며 로그는 한 Promtail이 project label로 Docsa만 선별한다.

**Tech Stack:** Docker Compose, Prometheus, Grafana, Loki, Promtail, cAdvisor, Node Exporter, Bash

## Global Constraints

- `stg_mysql_data`와 `mailpit_data`는 삭제하지 않는다.
- 스테이징 모니터링 볼륨은 통합 target 검증 후 즉시 삭제한다.
- 운영과 스테이징 애플리케이션은 모니터링 장애에 의존하지 않는다.
- 모든 커밋 메시지는 한글 Conventional Commit 형식을 사용한다.

---

### Task 1: 통합 구성 계약

**Files:**
- Modify: `infra/tests/shared_gateway_contract_test.sh`
- Modify: `infra/docker-compose.yml`
- Modify: `infra/docker-compose.stg.yml`
- Modify: `infra/prometheus/prometheus.yml`
- Modify: `infra/promtail/config.yml`
- Modify: `infra/loki/config.yml`
- Modify: `infra/nginx/nginx.stg.conf`

**Interfaces:**
- Produces: `docsa_monitoring_net`, 환경별 Prometheus job, 단일 Promtail project filter

- [ ] **Step 1: 실패하는 통합 계약 테스트 작성**

  스테이징 중복 서비스 부재, 공유 네트워크, 환경별 target, 로그 필터, 보존 기간과 Grafana redirect를 검사한다.

- [ ] **Step 2: 테스트 실패 확인**

  Run: `bash infra/tests/shared_gateway_contract_test.sh`

  Expected: 기존 스테이징 모니터링 서비스와 누락된 공유 설정 때문에 실패한다.

- [ ] **Step 3: 최소 Compose 및 설정 변경**

  운영 관측 스택을 유지하고 스테이징 중복 여섯 서비스를 제거한다. 앱, MySQL Exporter와 Prometheus를 공유 네트워크로 연결한다.

- [ ] **Step 4: 테스트와 Compose 렌더링 확인**

  Run: `bash infra/tests/shared_gateway_contract_test.sh`

  Run on server secrets: `docker compose -f infra/docker-compose.yml config --quiet && docker compose -f infra/docker-compose.stg.yml config --quiet`

- [ ] **Step 5: 커밋**

  Run: `git commit -m "chore: Docsa 모니터링 스택 통합"`

### Task 2: MySQL slow log 분리

**Files:**
- Modify: `infra/docker-compose.yml`
- Modify: `infra/docker-compose.stg.yml`
- Modify: `infra/promtail/config.yml`
- Modify: `infra/tests/shared_gateway_contract_test.sh`

**Interfaces:**
- Produces: `/mnt/mysql-logs/prod/slow.log`, `/mnt/mysql-logs/staging/slow.log`

- [ ] **Step 1: 실패하는 slow log 경로 테스트 작성**
- [ ] **Step 2: 테스트 실패 확인**
- [ ] **Step 3: 운영 및 스테이징 bind mount와 Promtail job 분리**
- [ ] **Step 4: 계약 테스트 통과 확인**
- [ ] **Step 5: `fix: 운영과 스테이징 MySQL slow log 경로 분리` 커밋**

### Task 3: 홈서버 마이그레이션

**Files:**
- Deploy: `/srv/docsa`

**Interfaces:**
- Consumes: 병합된 Docsa staging 커밋
- Produces: 단일 Docsa 관측 스택

- [ ] **Step 1: 기존 컨테이너, 볼륨과 target 이름 재확인**
- [ ] **Step 2: `docsa_monitoring_net` 생성 및 서버 pull**
- [ ] **Step 3: slow log 디렉터리 생성과 MySQL 컨테이너 순차 재생성**
- [ ] **Step 4: 운영 모니터링과 스테이징 앱 및 Exporter 반영**
- [ ] **Step 5: 운영 및 스테이징 target과 외부 서비스 검증**
- [ ] **Step 6: 스테이징 중복 모니터링 컨테이너 제거**
- [ ] **Step 7: `stg_prom_data`, `stg_loki_data`, `stg_grafana_data` 실제 볼륨 이름 확인 후 삭제**
- [ ] **Step 8: MySQL 및 Mailpit 볼륨 보존과 Git 상태 확인**
