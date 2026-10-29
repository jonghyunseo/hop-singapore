# 데이터와 외부 서비스

## OpenStreetMap

지도·도로·수역·공원·예시 장소 좌표는 © OpenStreetMap contributors. Open Database License(ODbL): https://www.openstreetmap.org/copyright

앱의 `app/src/main/assets/singapore.json`은 도심 OSM 벡터와 MRT 자료를 담은 배포 데이터입니다. 도로·공원·수역 등 OSM 기반 데이터에는 ODbL이 적용됩니다. 원본 제공처는 OpenStreetMap과 아래 sgraildata 저장소이며, 지도 데이터의 출처 표시는 앱 지도와 설정에서 확인할 수 있습니다.

상세 지도 타일: https://tile.openstreetmap.org/{z}/{x}/{y}.png

사용 정책: https://operations.osmfoundation.org/policies/tiles/

앱 고유 User-Agent, 화면 범위만의 요청, 서버 캐시 헤더를 따르는 100MB 디스크 캐시(헤더가 없으면 7일), 만료 후 조건부 요청을 적용했습니다. 공개 타일 서버의 가용성은 보장되지 않습니다. 배포 규모가 커지면 적절한 지도 공급자로 전환해야 합니다.

## Singapore rail data

cheeaun/sgraildata: https://github.com/cheeaun/sgraildata

역 대표점·코드·간소화된 노선 형상. 원자료 설명에 따라 선 형상은 실제 터널 선형과 정확히 일치한다고 보장하지 않으며 출구 안내에 사용하지 않습니다. 앱 자료는 MRT 142개 역, 6개 노선으로 구성되어 있고 LRT는 제외합니다. 미개통 TEL Stage 5 및 지연 개통 역은 이 스냅샷의 추천 후보에 없습니다.

확인일 2026-10-28. Circle Line 6의 Keppel, Cantonment, Prince Edward Road와 변경된 CC33/CC34 코드를 포함합니다. CCL6 개통 사실은 LTA 공식 자료(2026-07-12)와 대조했습니다:
https://www.lta.gov.sg/content/ltagov/en/upcoming_projects/rail_expansion/circle_line_6.html

공식 운영 현황: https://www.lta.gov.sg/content/ltagov/en/getting_around/public_transport/rail_network.html

## Android 및 Google 지도

앱 코드의 Kotlin/AndroidX/Material 의존성은 각 배포물의 라이선스를 따릅니다. 공유 수신은 Android 표준 ACTION_SEND, 온라인 이름 검색의 보조 수단으로 Android Geocoder, 도보·대중교통·자동차 길찾기는 Google Maps URL을 사용합니다. Google 지도 화면을 임베드하거나 Google 계정의 저장 목록을 읽지 않습니다.

https://developer.android.com/reference/android/location/Geocoder
https://developers.google.com/maps/documentation/urls/guide

## MapLibre Native

지도 엔진: MapLibre Native Android 13.7.1. BSD 2-Clause 라이선스.

- 소스와 저작권: https://github.com/maplibre/maplibre-native
- 배포 라이선스 전문: [docs/licenses/MapLibre-Native.txt](docs/licenses/MapLibre-Native.txt)
- Android 사용 안내: https://maplibre.org/maplibre-native/android/examples/getting-started/

지도 스타일은 앱 안에서 구성하며 별도 데모 지도·글꼴 서버를 사용하지 않습니다. 역·저장 장소 라벨은 기기 글꼴로 생성합니다.

## Photon

장소 검색·주변 장소·역지오코딩: https://photon.komoot.io

Photon은 OpenStreetMap 기반 검색 서비스입니다. 앱은 명시적인 검색·카테고리 선택·핀 선택에만 호출하고 1초 간격 제한, 24시간 캐시를 적용합니다. 공개 서버의 응답 수와 가용성은 보장되지 않습니다.

- 프로젝트 및 사용 조건: https://github.com/komoot/photon
- API: https://github.com/komoot/photon/blob/master/docs/api-v1.md

검색 결과의 지리 데이터는 © OpenStreetMap contributors, ODbL입니다. 앱 내 지도와 검색 결과에서 출처를 표시합니다.

## 공유 원문 보기

AndroidX Browser 1.10.0의 Custom Tabs로 사용자가 선택한 http/https 링크를 엽니다. 링크의 원본 콘텐츠는 해당 서비스의 권한·이용 조건을 따릅니다. 앱은 공유된 제목/텍스트/링크를 로컬에 저장하며 원본 페이지를 배경에서 수집하거나 계정 데이터를 자동으로 읽지 않습니다.

https://developer.android.com/jetpack/androidx/releases/browser
