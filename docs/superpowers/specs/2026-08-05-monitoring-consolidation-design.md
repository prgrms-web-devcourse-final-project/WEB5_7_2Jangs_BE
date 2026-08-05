# Docsa 운영 및 스테이징 모니터링 통합 설계

## 배경

Docsa 운영과 스테이징 Compose는 Prometheus, Grafana, Loki, Promtail, cAdvisor와 Node Exporter를 각각 실행한다. 두 cAdvisor와 Node Exporter는 같은 홈서버를 중복 관찰하고, 두 Promtail은 필터 없이 같은 Docker 로그와 syslog를 각각의 Loki에 저장한다.

현재 모니터링 컨테이너는 약 1.86GiB를 사용한다. 스테이징 중복 스택은 약 724MiB를 사용하며 두 Loki 데이터 경로는 각각 약 2.7GiB다. 운영과 스테이징 MySQL도 동일한 호스트 `./mysql/logs` 경로에 slow log를 기록한다.

## 목표

- Prometheus, Grafana, Loki, Promtail, cAdvisor와 Node Exporter를 운영 Compose의 한 세트로 통합한다.
- 운영과 스테이징 애플리케이션 및 MySQL 메트릭을 환경 label로 구분한다.
- Promtail은 Compose project가 `docsa` 또는 `docsa-stg`인 컨테이너 로그만 한 번 수집한다.
- 운영과 스테이징 MySQL slow log 경로를 분리한다.
- Prometheus는 15일, Loki는 7일 보존한다.
- 검증 후 스테이징 Prometheus, Grafana와 Loki 데이터 볼륨을 즉시 삭제한다.

## 통합 구조

```text
Docsa Prometheus
├─ docsa-app:9091
├─ docsa-app-stg:9091
├─ mysqld-exporter:9104
├─ mysqld-exporter-stg:9104
├─ cadvisor:8080
└─ node-exporter:9100

Docsa Promtail
├─ compose_project=docsa
└─ compose_project=docsa-stg

Docsa Grafana
├─ Prometheus
└─ Loki
```

운영 Prometheus와 스테이징 앱 및 MySQL Exporter는 호스트에 게시하지 않는 외부 Docker 네트워크 `docsa_monitoring_net`으로 연결한다. 운영 내부 대상은 기존 `docsa_net`을 유지한다.

## 스테이징 변경

스테이징 Compose에서 다음 서비스를 제거한다.

- `cadvisor`
- `node_exporter`
- `prometheus`
- `loki`
- `promtail`
- `grafana`

스테이징 앱과 MySQL Exporter는 유지하고 `docsa_monitoring_net`에 추가로 참여한다. 스테이징 `/grafana/`는 운영 Grafana 주소로 redirect한다.

## 로그 경계

- 운영 MySQL: `./mysql/logs/prod`
- 스테이징 MySQL: `./mysql/logs/staging`
- 기존 `./mysql/logs/slow.log`는 마이그레이션 백업으로 보존한다.
- Promtail Docker discovery는 `docsa|docsa-stg` project 정규식으로 제한한다.
- MySQL slow log는 운영과 스테이징 경로를 별도 job과 environment label로 수집한다.

## 보존 기간

- Prometheus: `--storage.tsdb.retention.time=15d`
- Loki: `retention_period: 168h`

## 배포와 삭제

1. `docsa_monitoring_net`을 한 번 생성한다.
2. 운영 모니터링 설정을 반영하고 Prometheus를 해당 네트워크에 연결한다.
3. 스테이징 앱과 MySQL Exporter를 해당 네트워크에 연결한다.
4. 운영 Prometheus에서 운영 및 스테이징 target이 모두 UP인지 확인한다.
5. 통합 Grafana와 Loki를 확인한다.
6. 스테이징 중복 모니터링 컨테이너만 제거한다.
7. 정확한 볼륨 이름을 다시 확인한다.
8. 스테이징 Prometheus, Grafana와 Loki 볼륨을 즉시 삭제한다.

삭제 대상은 모니터링 데이터 볼륨 세 개로 제한한다. `stg_mysql_data`와 `mailpit_data`는 삭제하지 않는다.

## 검증

- Compose 계약 테스트에서 스테이징 중복 서비스 제거를 확인한다.
- Prometheus 설정 검사에서 운영 및 스테이징 app과 MySQL job을 확인한다.
- Promtail 설정 검사에서 project filter와 두 slow log 경로를 확인한다.
- 운영 Prometheus target API에서 모든 target이 UP이다.
- 운영 및 스테이징 Docsa health가 정상이다.
- 운영 Grafana health가 정상이다.
- 스테이징 `/grafana/`가 운영 주소로 redirect한다.
- 제거 후 스테이징 MySQL, 앱, Nginx와 Mailpit은 계속 실행된다.

## 롤백

스테이징 모니터링 볼륨은 사용자 요청에 따라 즉시 삭제하므로 과거 스테이징 대시보드, 메트릭과 로그 데이터는 복구하지 않는다. 구성 장애 시 Git 이전 커밋으로 돌아가 스테이징 모니터링 컨테이너를 새 볼륨으로 재생성할 수 있다. MySQL과 Mailpit 볼륨은 삭제 대상이 아니므로 애플리케이션 데이터에는 영향을 주지 않는다.
