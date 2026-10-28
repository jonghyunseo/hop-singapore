# Android 검증 기록

## 0.3.0 — 2026-10-28

기본 지도를 MapLibre Native + OpenStreetMap 상세 지도로 교체하고, 변경한 지도·탐색 흐름을 중심으로 점검했다. 0.2.0의 파일 가져오기 전체 UI 검사는 반복하지 않았다.

- 단위 테스트 9개, 실패 0: 기존 Core 4개 + Import 3개 + 길찾기 URL의 현재 위치/역 출발 구분·허용하지 않는 모드 제외 2개.
- `:app:assembleRelease`, 필수 `lintVitalRelease`, `apksigner verify` 성공. `versionName=0.3.0`, `versionCode=4`.
- 전용 `hop_api36` Android 16/API 36 에뮬레이터에서 0.2.0 위에 `adb install -r` 성공. 기존 저장 장소 유지.
- 실제 OSM 도로·건물·상점명 타일과 MRT·저장 장소 라벨이 함께 보이는 것을 캡처로 확인. 별도 API 키 없이 실행.
- 오프라인 MRT 검색 Maxwell → TE18 역 카드. 온라인 National Gallery 검색 → 이름/주소가 있는 결과 확인.
- 지도 중심 주변 카페 결과 20곳, 지도 핀과 거리순 목록 확인. Privé Cafe 저장 → 강제 종료·재실행 후 유지 확인.
- 도보·대중교통·자동차와 공유 액션 시트 확인. 경로 URL은 단위 테스트로 검증했으며 Google 지도 앱 안의 실제 안내를 실행한 것은 아님.
- 에뮬레이터에 싱가포르 GPS 좌표를 주입해 내 위치 표시 및 ‘내 위치 추적 끄기’ 상태 확인. 지도 드래그로 추적 종료 확인. 물리 기기 GPS 정확도 검증은 아님.
- Wi-Fi/모바일 데이터 중지 후 오프라인 상태 표시, Changi Airport 로컬 검색과 CG2 역 카드 확인. 검증 후 연결 복원. 캐시하지 않은 공항의 상세 타일이 오프라인에서 없는 것도 확인.
- 세로/가로 실제 화면 확인. 안내 문구와 출처 겹침 제거, 가로에서 위치/레이어/전체 보기 버튼을 가로로 배치해 확대·축소 버튼과 분리.
- AndroidRuntime 오류 로그에서 크래시 없음.

증거: 로컬 `app/build/test-results/testDebugUnitTest/TEST-app.hop.singapore.*Test.xml`, `docs/images/android-map-v030.png`, `android-search-v030.png`, `android-nearby-v030.png`, `android-landscape-v030.png`. GitHub Release의 APK와 SHA-256 파일. 기존 배포판과 같은 서명 인증서를 사용한다.

상세 지도·검색 서버의 가용성, 모든 상점의 등록 여부, 전체 기기/대형 화면/TalkBack 동작을 포괄 보장하지 않는다. 앱 내 이동 경로 계산·내비게이션은 구현하지 않았으며 외부 Google 지도를 연다.

## 0.2.0 — 2026-10-28

실제 Kotlin/Compose 네이티브 APK를 대상으로 핵심 계산, 변경된 화면, 파일 가져오기만 점검했다. 테스트용으로 만든 Google 저장 목록 형식의 파일을 사용했으며 개인 Google 계정 전체 내보내기를 검증한 것은 아니다.

- 단위 테스트 7개, 실패 0: 기존 거리·역 순위·반경·공유 좌표·도메인 4개 + Takeout 설명문/따옴표·줄바꿈 메모/좌표 없는 항목 유지, 카메라 좌표·해외 장소 제외, ZIP 다중 목록 읽기 3개.
- `:app:assembleRelease`, 필수 `lintVitalRelease`, `apksigner verify` 성공. `versionName=0.2.0`, `versionCode=3`.
- Android 16/API 36 전용 `hop_api36` 에뮬레이터에서 기존 0.1.1 release 위에 `adb install -r` 성공. 기존 장소 유지.
- 지도 Canvas UI bounds: 세로 `[0,0][1080,2400]`, 가로 `[0,0][2400,1080]`. 전체 화면 지도 위에 검색·하차역 카드·메뉴 표시. Raffles Place 430m와 도보 길찾기 확인.
- 가까운 역 3곳 비교 시트에 Raffles Place/Esplanade/Bayfront와 도보 버튼 표시. 초기 반쯤 펼쳐짐 때문에 버튼이 내려가 있던 부분은 완전히 펼치는 시트로 수정 후 재확인.
- Android 시스템 파일 선택기로 Takeout 형식 ZIP 열기: 전체 3곳 중 위치 있는 2곳 선택, 좌표 없는 1곳 유지, 관련 없는 HTML 파일 제외.
- 선택 저장 후 미확인 장소가 검토 화면에 남음. National Gallery Singapore 검색 결과를 선택하고 위치를 확인해 가져오기 완료.
- 앱 강제 종료·재실행 후 Singapore Flyer와 National Gallery Singapore 확인. 테스트용 파일의 중복 제외와 같은 위치 보완 경로를 확인.
- 라이트/다크, 세로/가로 화면을 실제 캡처해 검토. 가로 화면의 지도 출처 표시가 카드에 겹치는 부분을 오른쪽으로 이동.

핵심 로직 테스트는 프로젝트의 Windows JVM 경로 문제를 피하기 위해 `-Djava.library.path=C:\Windows\System32` 옵션으로 실행했다. 시스템 설정은 변경하지 않았다. UI 안내 문구·시트 높이·가로 출처 배치의 후속 조정에는 유효한 로직 테스트 결과를 재사용했다.

## 증거

- `app/build/test-results/testDebugUnitTest/TEST-app.hop.singapore.CoreTest.xml` 및 `TEST-app.hop.singapore.ImportTest.xml` (로컬 빌드 출력).
- `docs/images/android-map.png`, `android-saved.png`, `android-import.png`, `android-import-help.png`, `android-dark.png`: 에뮬레이터 실제 캡처.
- GitHub Release의 `hop-0.2.0.apk` 및 SHA-256 파일. 전용 배포 서명 인증서 SHA-256은 `1f7fb8b9bd25d26ba228b41724a305a83fec9f2fa43726e74176da6e224221f6`로 0.1.1과 동일하다.

## 이전 버전에서 확인하고 재사용한 범위

0.1.0/0.1.1에서 반경 800m/400m의 장소 수, ACTION_SEND 수신·저장·중복 제외, 방문 체크와 재실행 복원, GeoJSON 가져오기, 서명된 release 설치를 확인했다. 해당 기능의 전체 회귀 검사를 반복하지 않았다. `scripts/smoke_android.py`는 초기 6개 장소 상태의 디버그 APK에 대한 기존 핵심 흐름 스크립트다.

## 한계

- 물리 기기, 다양한 제조사, 태블릿, 최대 글자 크기, TalkBack 전체 검수는 하지 않았다.
- 모든 Google Takeout 변형, 대형 ZIP, 모든 단축 URL, Geocoder 장애 조합은 포괄 검증하지 않았다. 이름 검색 결과는 사용자가 확인해야 한다.
- 파일 가져오기를 구현했으며 Google 계정 자동 동기화, 공유 목록 링크 전체 읽기는 지원하지 않는다. 검토 목록은 앱 프로세스 종료 시 다시 파일에서 열어야 한다.
- 역 대표점 기준 직선거리다. 실제 도보거리, 출구별 접근성, 실시간 열차 운행은 포함하지 않는다.
