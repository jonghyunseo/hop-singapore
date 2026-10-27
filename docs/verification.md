# Android MVP 검증 — 2026-10-28

## GitHub 배포 0.1.1

- `:app:assembleRelease` 및 필수 release lint 통과. `versionCode=2`, `versionName=0.1.1`, `minSdk=26`, `targetSdk=36`.
- `apksigner verify` 통과. release APK의 `debuggable` 플래그 없음. arm64-v8a / armeabi-v7a / x86 / x86_64를 포함한 단일 APK.
- 전용 테스트 에뮬레이터에서 개발판을 제거하고 실제 서명된 release APK를 설치·실행. Raffles Place 430m, 도보 버튼, 플로팅 메뉴 확인. AndroidRuntime 오류 로그 없음.
- 배포 서명 인증서 SHA-256: `1f7fb8b9bd25d26ba228b41724a305a83fec9f2fa43726e74176da6e224221f6`.
- 아래 4개 핵심 로직 테스트와 공유·저장 흐름은 같은 기능 코드의 개발 빌드에서 확인한 증거를 재사용했다. 이번 배포에서는 버전·서명 설정과 배포 문서를 변경했다.
- 공개 배포 파일은 GitHub Releases에서 제공한다. 아래 개발용 APK·내부 테스트 출력 경로는 로컬 검증 기록이며 저장소에 바이너리로 포함하지 않는다.

검증 대상은 실제 네이티브 Android APK입니다. 브라우저 모형의 결과와 구분합니다. 사용자의 요청에 따라 전체 테스트 체계 확대 대신 빌드·핵심 계산·핵심 사용자 흐름에 집중했습니다.

## 통과한 확인

- `:app:assembleDebug` 성공. 개발용 서명 APK 생성과 `apksigner verify` 통과.
- `:app:testDebugUnitTest` 4개 성공, 실패 0: 거리/정렬/환승역 중복, 반경 필터, 공유 좌표 해석, 허용 도메인/싱가포르 범위.
- Android 16/API 36의 전용 에뮬레이터에 APK 설치·실행 성공.
- 첫 화면에서 멀라이언 → Raffles Place 430m 표시. 도보 확인 버튼과 플로팅 메뉴를 함께 확인.
- 역별 화면: Maxwell 반경 800m에서 3곳, 400m에서 2곳.
- ACTION_SEND로 Singapore Flyer 좌표 링크 수신 → 위치 검토 → 저장. 장소 수 6 → 7.
- 같은 공유를 반복해도 7곳 유지.
- 방문 표시 후 강제 종료·재실행해 장소 7곳과 방문 상태 유지.
- 공유 화면을 간소화한 최종 빌드에서 저장 버튼 고정 표시와 중복 방지를 재확인.
- Android 시스템 파일 선택기로 샘플 GeoJSON 2곳을 검토·가져오기. 중복 Flyer는 제외하고 National Gallery 1곳만 추가되어 총 8곳.
- 라이트 지도·역별·공유 화면과 다크 지도 화면을 실제 캡처하여 확인. AndroidRuntime 오류 로그 없음.

Windows 시스템 경로의 잘못된 따옴표로 테스트 JVM 시작이 한 차례 실패했습니다. 시스템 경로를 수정하지 않고 `-Djava.library.path=C:\Windows\System32`를 주어 실행했고, 최종 4개 테스트가 통과했습니다.

## 범위와 한계

- 물리 기기·다양한 제조사·태블릿·최대 글자 크기·TalkBack 전체 검수는 하지 않았습니다.
- 온라인 Geocoder, 실제 Google Maps 앱이 생성하는 모든 단축 링크, GPS의 실제 현장 정확도, 상세 지도 네트워크 장애 조합은 포괄 검증하지 않았습니다. 실패 시 지도 핀/수동 좌표와 간략 지도로 진행할 수 있습니다.
- GeoJSON의 정상 가져오기 경로는 확인했습니다. 모든 Takeout 변형, CSV 경계 사례, ZIP은 이번 최소 점검 범위에 포함하지 않았으며 ZIP은 지원하지 않습니다.
- 지도는 역 대표점 기준 직선거리입니다. 출구별 접근성, 실제 도보 최단 경로, 실시간 운영 장애를 보장하지 않습니다.
- APK는 개인 테스트용 개발 서명입니다. Play Store 출시 검수나 운영 서버·Google 즐겨찾기 자동 동기화는 범위 밖입니다.

## 증거

- `smoke-results.json`: 핵심 사용자 흐름 결과.
- `app/build/test-results/testDebugUnitTest/TEST-app.hop.singapore.CoreTest.xml`: 테스트 4개 결과.
- `docs/images/android-map.png`, `android-stations.png`, `android-share.png`, `android-dark.png`: 실제 에뮬레이터 화면.
- `hop-0.1.0-debug.apk`: 최종 설치 파일.
- `hop-0.1.0-debug.apk.sha256`: 파일 해시.
