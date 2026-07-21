# 음악 인식 프로젝트 Task 목록

기준 문서: [`mds/실행 순서 작성.md`](../../mds/실행%20순서%20작성.md)

아래 Task는 순서대로 수행한다. 이전 Task의 완료 조건을 통과해야 다음 Task를 시작할 수 있다.

| 순서 | Task | 목표 | 대응 실행 순서 |
|---:|---|---|---|
| 1 | [TASK-01 기반 설계와 테스트 골격](./TASK-01-기반-설계와-테스트-골격.md) | 교체 가능한 음악 인식 코어와 자동 테스트 기반 구성 | 01~03 |
| 2 | [TASK-02 휴대폰 음악 인식 PoC](./TASK-02-휴대폰-음악-인식-PoC.md) | 휴대폰 마이크로 곡을 인식하고 제목·가수 표시 | 04~07 |
| 3 | [TASK-03 ACRCloud 프록시와 앱 adapter](./TASK-03-ACRCloud-프록시와-앱-adapter.md) | ACRCloud Node proxy와 Android adapter 구현 | 08~13 |
| 4 | [TASK-04 UI와 앱 통합](./TASK-04-UI와-앱-통합.md) | Music UI·factory·route·권한 통합 | 14~17 |
| 5 | [TASK-05 운영 설정과 실기기 E2E](./TASK-05-운영-설정과-실기기-E2E.md) | 운영 설정·실기기 E2E·출시 조건 확인 | 18~20 |

체크리스트는 [`docs/checklist`](../checklist/README.md)에 있다.
