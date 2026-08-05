# 공용 Gateway 운영 구조 정리 설계

## 목표

Docsa 운영 Nginx와 인증서 갱신 책임을 `home-gateway`로 이전하고, 스테이징 Nginx는 Gateway가 갱신한 인증서를 읽도록 통일한다. 기존 서버 구성은 날짜별 롤백 백업에 보존한 뒤 활성 경로와 컨테이너만 정리한다.

## 저장소 책임

- Docsa: 애플리케이션·DB·모니터링과 스테이징 Nginx를 관리한다.
- home-gateway: 외부 80/443, 운영 Docsa·DevChat 라우팅, 모든 인증서 갱신을 관리한다.
- Docsa의 생존 확인과 인증서 만료 확인 스크립트는 유지한다.

## 인증서 흐름

Let's Encrypt의 HTTP-01 요청은 공용 Gateway의 `/srv/gateway/certbot/www`가 처리한다. 인증서는 `/srv/gateway/certbot/etc`에만 갱신하며, `gateway-nginx`와 `docsa-nginx-stg`가 같은 인증서를 읽는다. 갱신 후 두 컨테이너를 reload한다.

## 서버 전환과 롤백

전환 전에 기존 Compose, Nginx 설정, 인증서, 사용자 crontab을 날짜별 롤백 디렉터리에 복사한다. 새 스테이징 Nginx와 인증서 갱신을 검증한 뒤 중지된 운영 Docsa Nginx·Certbot 컨테이너와 활성 경로의 구 인증서 복사본을 정리한다.

root crontab은 서버 생존 확인, Gateway 인증서 갱신, 인증서 만료 확인만 실행한다. 일반 사용자 crontab은 root 갱신 작업이 설치된 것을 확인한 다음 제거한다.

## 검증 범위

- Compose 렌더링 및 셸 문법
- 인증서 갱신 dry-run
- Gateway, Docsa 운영, Docsa 스테이징 health 및 HTTPS 인증서
- 전체 애플리케이션 테스트는 실행하지 않는다.
