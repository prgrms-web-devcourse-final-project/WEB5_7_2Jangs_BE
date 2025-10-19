
# <img width="80" height="80" alt="image" src="https://github.com/user-attachments/assets/3548a1ef-6de9-43d3-a1b7-80c905f28d72" /> TEAM 이장님들 - Docsa
  
## 🔗 배포 링크
https://docsa.o-r.kr

## 🙌 프로젝트 소개
Docsa는 문서의 변경 이력과 다양한 버전을 효율적으로 관리할 수 있는 문서 버전 관리 시스템입니다.
이 프로젝트의 목표는 Git을 모르는 사용자도 손쉽게 버전 관리를 활용하여 문서를 편집하고 운용할 수 있도록 돕는 것입니다.
<br><br>
사용자는 그래프로 구현된 시각적인 UI를 통해 문서를 직접 편집하고, 다양한 버전 흐름을 한눈에 확인하며 관리할 수 있습니다.
Docsa는 문서의 변경 사항을 기록(commit) 단위로 추적하고, 버전(branch) 를 분기하거나, 서로 다른 버전을 병합(merge) 할 수 있는 강력한 기능을 제공합니다. <br><br>
이러한 기능은 editor.js 기반의 블록 단위 저장 방식을 통해 구현되며, 변경된 블록만을 감지하여 데이터베이스에 저장하고 이를 조합하여 기록함으로써 저장 효율성과 자원 활용도를 극대화합니다.

## 📌 주요 기능
>*Git의 개념과 용어를 모르는 일반인 유저를 위해, Docsa에서는 일반적인 Git의 개념과 대응되는 용어를 다음과 같이 재정의합니다. <br>
>(기록: commit, 버전: branch, 병합하기: merge)

### 문서 버전 관리

- 문서는 기록,저장과 버전을 가진 최상위 단위입니다. 문서마다 모든 변경 사항을 기록 단위로 저장하여, 원하는 시점의 기록을 조회 (checkout) 할 수 있으며, 메인화면에서 생성과 삭제가 가능합니다.

### 버전(branch) 분기 및 병합

- 하나의 문서에서 여러 버전을 생성하여 자유롭게 분기해나갈 수 있으며, 브랜치 간 병합을 통해 작업 내용을 통합할 수 있습니다. main버전 이외 버전 단위의 삭제도 가능합니다.

### 저장(Save) 및 기록(Commit) 시스템

- 작성 중인 내용을  저장할 수 있는 ‘저장하기’ 기능과, 특정 시점의 변경 사항을 확정하는 ‘기록하기’ 기능을 사용할 수 있습니다. 기록마다 제목과 설명을 추가할 수 있으며, 저장 또는 기록을 단일 삭제할 수도 있습니다.

### 두 기록 비교하기

- ‘비교하기’를 통해 현재 보고있는 기록과 다른 기록을 문단 단위로 비교할 수 있습니다.

### 시각화된 버전 그래프 UI

- 기록과 저장,브랜치 간의 관계를 트리 구조 그래프로 시각화하여, 문서의 버전 흐름을 직관적으로 확인할 수 있습니다.

### 실시간 문서 편집

- Docsa의 에디터를 통해 직접 문서를 작성 및 수정할 수 있으며, 블록 기반 편집과 마크다운 문법을 지원합니다.



## 👩‍💻 팀 이장님 소개
<table align="center"> <thead> <tr> <th style="text-align:center;">팀원</th> <th style="text-align:center;">역할</th> <th style="text-align:left;">담당 업무</th> </tr> </thead> <tbody> <tr> <td align="center"> <a href="https://github.com/sleepyhoon"> <img src="https://avatars.githubusercontent.com/u/101882530?v=4" width="60"><br/> <sub><b>한승훈</b></sub> </a> </td> <td align="center"><b>PO</b></td> <td> - 프론트 개발자님과 소통<br> - 프로젝트 일정 관리<br> - 저장 관련 API 구현 </td> </tr> <tr> <td align="center"> <a href="https://github.com/heets-blue"> <img src="https://avatars.githubusercontent.com/u/89324994?v=4" width="60"><br/> <sub><b>배문성</b></sub> </a> </td> <td align="center"><b>BE 팀장</b></td> <td> - 문서 관련 API 구현<br> - 이종간 트랜잭션 삭제 로직 설계 및 구현 <br> - CI/CD 및 인프라 구축</td> </tr> <tr> <td align="center"> <a href="https://github.com/Jeongmin39"> <img src="https://avatars.githubusercontent.com/u/80705450?v=4" width="60"><br/> <sub><b>한정민</b></sub> </a> </td> <td align="center"><b>AWS 관리자</b></td> <td> - 인증 및 사용자 관련 API 구현<br> - AWS 인프라 운영<br> - Docker 기반 배포<br> - 모니터링 시스템 구축 </td> </tr> <tr> <td align="center"> <a href="https://github.com/2ternal"> <img src="https://avatars.githubusercontent.com/u/26919446?v=4" width="60"><br/> <sub><b>권우철</b></sub> </a> </td> <td align="center"><b>BE 팀원</b></td> <td> - 기록(커밋) 관련 API 구현<br> - 병합기능(머지) API 구현<br> - 이종간 트랜잭션 삭제 로직 설계 </td> </tr> <tr> <td align="center"> <a href="https://github.com/ky1nonly"> <img src="https://avatars.githubusercontent.com/u/117032989?v=4" width="60"><br/> <sub><b>이예원</b></sub> </a> </td> <td align="center"><b>BE 팀원</b></td> <td> - 버전(브랜치) 관련 API 구현<br> - 그래프 조회 API 구현 </td> </tr> </tbody> </table>

<div align="center">
  
### 🤝 팀 컨벤션
[Git 컨벤션](https://github.com/prgrms-web-devcourse-final-project/WEB5_7_2Jangs_BE/wiki/%F0%9F%91%A9%E2%80%8D%F0%9F%92%BB-%ED%8C%80-%EC%BB%A8%EB%B2%A4%EC%85%98#git-%EC%BB%A8%EB%B2%A4%EC%85%98)<br>
[Code 컨벤션](https://github.com/prgrms-web-devcourse-final-project/WEB5_7_2Jangs_BE/wiki/%F0%9F%91%A9%E2%80%8D%F0%9F%92%BB-%ED%8C%80-%EC%BB%A8%EB%B2%A4%EC%85%98#code-%EC%BB%A8%EB%B2%A4%EC%85%98)<br>
[package 구조](https://github.com/prgrms-web-devcourse-final-project/WEB5_7_2Jangs_BE/wiki/%F0%9F%91%A9%E2%80%8D%F0%9F%92%BB-%ED%8C%80-%EC%BB%A8%EB%B2%A4%EC%85%98#%ED%8C%A8%ED%82%A4%EC%A7%80-%EA%B5%AC%EC%A1%B0)<br>

</div>

## 🌐 시스템 아키텍처
<img width="2000" height="1000" alt="이장님들-System-arch" src="https://github.com/user-attachments/assets/4ea1be39-6fea-479c-a9cb-03e717522fff" />



<div align="center">
  
### Backend

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring MVC](https://img.shields.io/badge/Spring%20MVC-%236DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-%236DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-%236DB33F?style=for-the-badge&logo=spring&logoColor=white)

![MySQL](https://img.shields.io/badge/MySQL-005C84?style=for-the-badge&logo=mysql&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-4EA94B?style=for-the-badge&logo=mongodb&logoColor=white)
![Caffeine Cache](https://img.shields.io/badge/Caffeine%20Cache-F7DF1E?style=for-the-badge&logo=caffeine&logoColor=black)

![AWS EC2](https://img.shields.io/badge/AWS%20EC2-FF9900?style=for-the-badge&logo=amazonaws&logoColor=white)
![AWS RDS](https://img.shields.io/badge/AWS%20RDS-527FFF?style=for-the-badge&logo=amazonrds&logoColor=white)
![Nginx](https://img.shields.io/badge/Nginx-009639?style=for-the-badge&logo=nginx&logoColor=white)

![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=for-the-badge&logo=prometheus&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana-F46800?style=for-the-badge&logo=grafana&logoColor=white)

![JUnit5](https://img.shields.io/badge/JUnit5-25A162?style=for-the-badge&logo=java&logoColor=white)
![Mockito](https://img.shields.io/badge/Mockito-FFDE57?style=for-the-badge&logo=java&logoColor=black)
![Postman](https://img.shields.io/badge/Postman-FF6C37?style=for-the-badge&logo=postman&logoColor=white)


### Collaboration & Tools

![Slack](https://img.shields.io/badge/Slack-4A154B?style=for-the-badge&logo=slack&logoColor=white)
![Notion](https://img.shields.io/badge/Notion-000000?style=for-the-badge&logo=notion&logoColor=white)
![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-000000?style=for-the-badge&logo=intellijidea&logoColor=white)
![Git](https://img.shields.io/badge/Git-F05032?style=for-the-badge&logo=git&logoColor=white)
![GitHub Projects](https://img.shields.io/badge/GitHub%20Projects-181717?style=for-the-badge&logo=github&logoColor=white)

</div>

## 📚 Swagger API 문서

<div align="center">
  <img src="https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black" alt="Swagger Badge"/>
</div>

<p align="center">
  🧪 API 명세서 및 테스트를 위한 Swagger 문서입니다.
</p>

<div align="center">
  🔗 <a href="https://api.docsa.o-r.kr/swagger-ui.html"><strong>배포 서버 Swagger 문서 보기</strong></a><br/>
  🔗 <a href="https://ky1nonly.github.io/docsa_swagger/"><strong>(서버 중단시 조회용) GitHub Pages Swagger 문서 보기</strong></a>
</div>

<br>
  
## 📲 애플리케이션 UI

🔗 [서비스 배포 주소](https://docsa.o-r.kr/)<br>
🎥 [시연 영상](https://www.youtube.com/watch?v=-1J7JvvATXw&t=2s)

### [UI 스크린샷 및 상세 설명](https://github.com/prgrms-web-devcourse-final-project/WEB5_7_2Jangs_BE/wiki/%F0%9F%92%BB-UI-%EC%8A%A4%ED%81%AC%EB%A6%B0%EC%83%B7-%EB%B0%8F-%EC%83%81%EC%84%B8-%EC%84%A4%EB%AA%85) 


[대표 사진]
<img width="1658" height="891" alt="image" src="https://github.com/user-attachments/assets/2873f6bb-33c2-4d79-91e8-80cbf6e3b54f" />
<img width="1646" height="927" alt="image" src="https://github.com/user-attachments/assets/12121c14-ccb3-4167-b633-11cfbb134fa8" />
<img width="1652" height="927" alt="화면 캡처 2025-08-03 184713" src="https://github.com/user-attachments/assets/be0454ed-4350-4ae7-962c-608acbea41a3" />



## 📄 ERD
### [데이터 모델 도출 과정](https://github.com/prgrms-web-devcourse-final-project/WEB5_7_2Jangs_BE/wiki/%F0%9F%93%84-%EB%8D%B0%EC%9D%B4%ED%84%B0-%EB%AA%A8%EB%8D%B8-%EB%8F%84%EC%B6%9C-%EA%B3%BC%EC%A0%95) 
<img width="1700" height="806" alt="docsa-몽고도입 후" src="https://github.com/user-attachments/assets/fa0367db-218b-4553-891f-23c9615e73ca" />

## 📄 Flow Chart
<img width="1468" height="1021" alt="이장님들-페이지-1 drawio" src="https://github.com/user-attachments/assets/1204efd2-94d9-42db-a7fd-c79af0efeace" />



## ⚡ 기술적 이슈
*정리 후 이슈마다 블록 링크 걸 예정
- [비즈니스 로직 구현 방식 상세](https://github.com/prgrms-web-devcourse-final-project/WEB5_7_2Jangs_BE/wiki/%E2%9A%A1-%EA%B8%B0%EC%88%A0%EC%A0%81-%EC%9D%B4%EC%8A%88#%EB%B9%84%EC%A6%88%EB%8B%88%EC%8A%A4-%EB%A1%9C%EC%A7%81-%EA%B5%AC%ED%98%84-%EC%84%A4%EB%AA%85)
  
## 🎇 트러블 슈팅
*정리 후 이슈마다 블록 링크 걸 예정
- [RDB-MongoDB간 트랜잭션 분리](https://github.com/prgrms-web-devcourse-final-project/WEB5_7_2Jangs_BE/wiki/%F0%9F%8E%87-%ED%8A%B8%EB%9F%AC%EB%B8%94-%EC%8A%88%ED%8C%85#rdb--mongodb-%ED%8A%B8%EB%9E%9C%EC%9E%AD%EC%85%98-%EB%B6%84%EB%A6%AC)
- [간선 CASCADE 삭제 시도시 오류와 해결](https://github.com/prgrms-web-devcourse-final-project/WEB5_7_2Jangs_BE/wiki/%F0%9F%8E%87-%ED%8A%B8%EB%9F%AC%EB%B8%94-%EC%8A%88%ED%8C%85#%EC%97%B0%EA%B4%80%EA%B4%80%EA%B3%84-cascade-%EC%98%A4%EB%A5%98-%EB%B0%8F-%ED%95%B4%EA%B2%B0)


## ❓ QnA
### [답변 정리 링크](https://github.com/prgrms-web-devcourse-final-project/WEB5_7_2Jangs_BE/wiki/%E2%9D%93-QnA) 
*추가 예정

1. 질문 1
2. 질문 2
3. 질문 3




