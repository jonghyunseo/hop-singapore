# 데이터와 외부 서비스

## OpenStreetMap

지도·도로·수역·공원·예시 장소 좌표는 © OpenStreetMap contributors. Open Database License(ODbL): https://www.openstreetmap.org/copyright

앱의 `app/src/main/assets/singapore.json`은 도심 OSM 벡터와 MRT 자료를 담은 배포 데이터입니다. 도로·공원·수역 등 OSM 기반 데이터에는 ODbL이 적용됩니다. 원본 제공처는 OpenStreetMap과 아래 sgraildata 저장소이며, 지도 데이터의 출처 표시는 앱 지도와 설정에서 확인할 수 있습니다.

상세 지도 타일: https://tile.openstreetmap.org/{z}/{x}/{y}.png

사용 정책: https://operations.osmfoundation.org/policies/tiles/

앱 고유 User-Agent, 화면 범위만의 요청, 최소 7일 디스크 캐시, 만료 후 조건부 요청을 적용했습니다. 공개 타일 서버의 가용성은 보장되지 않습니다. 배포 규모가 커지면 적절한 지도 공급자로 전환해야 합니다.

## Singapore rail data

cheeaun/sgraildata: https://github.com/cheeaun/sgraildata

역 대표점·코드·간소화된 노선 형상. 원자료 설명에 따라 선 형상은 실제 터널 선형과 정확히 일치한다고 보장하지 않으며 출구 안내에 사용하지 않습니다. 앱 자료는 MRT 142개 역, 6개 노선으로 구성되어 있고 LRT는 제외합니다. 미개통 TEL Stage 5 및 지연 개통 역은 이 스냅샷의 추천 후보에 없습니다.

확인일 2026-10-28. Circle Line 6의 Keppel, Cantonment, Prince Edward Road와 변경된 CC33/CC34 코드를 포함합니다. CCL6 개통 사실은 LTA 공식 자료(2026-07-12)와 대조했습니다:
https://www.lta.gov.sg/content/ltagov/en/upcoming_projects/rail_expansion/circle_line_6.html

공식 운영 현황: https://www.lta.gov.sg/content/ltagov/en/getting_around/public_transport/rail_network.html

## Android 및 Google 지도

앱 코드의 Kotlin/AndroidX/Material 의존성은 각 배포물의 라이선스를 따릅니다. 공유 수신은 Android 표준 ACTION_SEND, 온라인 이름 검색은 Android Geocoder, 도보 안내는 Google Maps URL을 사용합니다. Google 지도 화면을 임베드하거나 Google 계정의 저장 목록을 읽지 않습니다.

https://developer.android.com/reference/android/location/Geocoder
https://developers.google.com/maps/documentation/urls/guide
