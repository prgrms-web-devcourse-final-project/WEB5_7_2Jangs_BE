# Shared Gateway Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Docsa와 home-gateway의 Nginx·인증서 책임을 현재 운영 구조와 일치시키고 홈서버 레거시를 롤백 가능하게 정리한다.

**Architecture:** 공용 Gateway가 인증서와 운영 80/443을 소유한다. 스테이징 Nginx는 Gateway 인증서 저장소를 읽으며 Docsa는 생존·만료 모니터링만 유지한다.

**Tech Stack:** Docker Compose, Nginx, Certbot, Bash, cron

## Global Constraints

- 현재 기능 브랜치의 사용자 변경은 수정하지 않는다.
- 커밋 제목은 `docs:` 또는 `chore:` 접두사를 사용하고 한글로 작성한다.
- 서버 변경 전 날짜별 롤백 백업을 생성한다.
- 전체 백엔드 테스트 없이 인프라 계약과 실제 HTTPS만 검증한다.

---

### Task 1: Docsa Compose 책임 정리

**Files:**
- Modify: `infra/docker-compose.yml`
- Modify: `infra/docker-compose.stg.yml`
- Delete: `infra/nginx/nginx.conf`
- Delete: `infra/scripts/cert_renew.sh`
- Create: `infra/tests/shared_gateway_contract_test.sh`
- Create: `infra/README.md`

- [ ] 실행된 Compose 결과를 검사하는 실패 계약 테스트를 추가한다.
- [ ] 운영 Nginx·Certbot 서비스를 제거하고 스테이징 인증서 마운트를 Gateway 경로로 바꾼다.
- [ ] 계약 테스트와 셸 문법 검사를 통과시킨다.
- [ ] `chore: 공용 Gateway 기준으로 Docsa 인프라 정리`로 커밋한다.

### Task 2: home-gateway 운영 문서와 갱신 스크립트 정리

**Files:**
- Modify: `.gitignore`
- Modify: `scripts/cert_renew.sh`
- Create: `tests/cert_renew_test.sh`
- Create: `README.md`

- [ ] 갱신 명령과 두 Nginx reload를 검증하는 실패 테스트를 추가한다.
- [ ] Certbot의 cron 랜덤 대기를 끄고 README에 구조·cron·롤백을 기록한다.
- [ ] 테스트, Compose 렌더링, 셸 문법을 통과시킨다.
- [ ] `chore: 공용 Gateway 운영과 인증서 갱신 구조 정리`로 커밋한다.

### Task 3: 홈서버 전환

**Files:**
- Deploy: `/srv/docsa/infra/docker-compose.yml`
- Deploy: `/srv/docsa/infra/docker-compose.stg.yml`
- Deploy: `/srv/gateway/scripts/cert_renew.sh`
- Backup: `rollback_dir=/srv/gateway/rollback/docsa-gateway-$(date +%Y%m%d-%H%M%S)`

- [ ] 기존 파일·인증서·사용자 cron을 백업한다.
- [ ] 새 파일을 배포하고 스테이징 Nginx만 재생성한다.
- [ ] root용 crontab 파일을 준비해 사용자가 `sudo crontab`으로 설치할 수 있게 한다.
- [ ] root 설치 확인 후 일반 사용자 crontab을 제거한다.
- [ ] dry-run, HTTPS, 컨테이너 health를 확인한 뒤 레거시 컨테이너와 활성 인증서 복사본을 정리한다.
