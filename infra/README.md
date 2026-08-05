# Docsa 인프라

Docsa Compose는 애플리케이션, 데이터베이스, 모니터링과 스테이징 Nginx를 관리합니다. 운영 80/443 라우팅과 인증서 갱신은 별도 [home-gateway](https://github.com/lunarbae628/home-gateway) 저장소가 담당합니다.

## 트래픽 구조

    api.docsa.o-r.kr:443
      → gateway-nginx
      → docsa-app:8080

    stg.api.docsa.o-r.kr:8443
      → docsa-nginx-stg
      → docsa-app-stg:8080

운영 docsa-app은 docsa_docsa_net을 통해 공용 Gateway와 연결됩니다. 스테이징 Nginx는 /srv/gateway/certbot/etc를 읽어 Gateway가 갱신한 인증서를 사용합니다.

## Compose 책임

- docker-compose.yml: 운영 애플리케이션, MySQL, 메트릭, 로그 및 대시보드
- docker-compose.stg.yml: 스테이징 애플리케이션, MySQL, Nginx, Mailpit 및 관측 구성
- deploy.sh: 브랜치 이미지 태그를 적용하고 애플리케이션 health를 확인

운영 Nginx와 Certbot 서비스는 공용 Gateway로 이전했으므로 이 저장소에서 다시 실행하지 않습니다.

## 모니터링 cron

Docsa 저장소에는 서비스 생존 확인과 인증서 만료 확인만 유지합니다.

    0 */6 * * * /usr/bin/env bash /srv/docsa/infra/scripts/check_server_alive.sh >> /var/log/check_server_alive.log 2>> /var/log/check_server_alive.err
    0 3 * * * /usr/bin/env bash /srv/docsa/infra/scripts/check_cert_expiry.sh >> /var/log/check_cert_expiry.log 2>> /var/log/check_cert_expiry.err

인증서 갱신 cron과 롤백 절차는 home-gateway README를 따릅니다.
