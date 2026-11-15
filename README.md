# 가는 길 30분

판교에서 분당구청으로 가는 Pleos Connect 시연 앱입니다. APK를 설치하면 **가는 길 30분**으로 표시됩니다.

## 에뮬레이터 시연

1. Pleos Connect 에뮬레이터를 P단에 두고 현재 위치를 판교 시연 시작점(위도 `37.4127212`, 경도 `127.094681`)으로 설정합니다.
2. **예시 음성으로 시작**을 누릅니다. 순정 내비가 열리면 화면 하단의 **가는 길 30분** 앱 아이콘을 눌러 추천을 확인합니다.
3. **마감 변경 시연**을 누르면 판교환경생태학습원의 입장 마감이 시연값 16:45로 바뀝니다. 분당구청 경로와 순정 내비 예상 주행 시간은 유지됩니다. **마감 복원**으로 공식 운영 시간 스냅샷인 17:00을 다시 적용합니다.
4. **경로 변경 시연**을 누르고 순정 내비가 열리면 앱으로 돌아옵니다. 동일한 최종 목적지로 우회한 새 ETA, 추천 장소, 변경 사유를 확인합니다.
5. 추천을 읽고 **경유지 추가**를 누릅니다. 순정 내비에서 경유지와 분당구청을 확인할 수 있습니다. 앱으로 돌아오면 순정 내비의 확인 결과를 표시합니다.

음성은 동봉된 합성 PCM을 `SpeechToText` 3.3.1의 `ON_DEVICE` 모드와 `Locale.KOREA`로 처리합니다. NaviHelper 3.0.5에서 분당구청 최종 목적지, 순서가 일치하는 경유지 목록과 추천 장소의 POI ID를 확인한 뒤 추가 완료를 표시합니다. 주행 중 조작은 차량 기어와 UX 제한 상태를 읽어 차단합니다.

추천은 2026년 11월 15일 운영 시간 스냅샷, 지정된 출발점, OSRM 구간 추정값과 순정 내비 주행 시간을 사용합니다. 최초 추천 시 학습원 입장 예상이 16:58이 되도록 시연 기준 시각을 정해 같은 시연이 끝날 때까지 고정합니다. 실내 체류 30분, 주차 후 이동 3분, 출차 준비 3분을 적용합니다. 마감 변경 시연은 APK에 내장된 값이며 실시간 운영 변경, 주차 만차 및 실제 입차는 시연 범위 밖입니다.

## 로컬 빌드

Windows에서 Android SDK Platform 35, Android SDK Build Tools와 JDK 21이 있는 환경으로 빌드합니다. Android SDK 경로는 `ANDROID_HOME` 또는 추적에서 제외된 `local.properties`의 `sdk.dir`로 지정합니다.

```powershell
.\gradlew.bat :app:assembleDebug
```

결과는 `app/build/outputs/apk/debug/app-debug.apk`에 생성되며 Android 디버그 서명을 사용합니다.

추천 계산을 독립 실행하려면 소스와 테스트를 같은 디렉터리로 컴파일합니다.

```powershell
$stop30TestDir = Join-Path $env:TEMP 'pleos-stop-30-test-classes'
New-Item -ItemType Directory -Force $stop30TestDir | Out-Null
javac -encoding UTF-8 -d $stop30TestDir app/src/main/java/app/pleos/stop30/StopScenario.java tests/StopScenarioTest.java
java -classpath $stop30TestDir app.pleos.stop30.StopScenarioTest
```

## 운영 시간과 API 자료

- [판교환경생태학습원 이용 안내](https://ppark.seongnam.go.kr:10013/info/info)
- [교보문고 판교점](https://store.kyobobook.co.kr/store-info/069) · [현대백화점 판교점 주차](https://home.ehyundai.com/newPortal/DP/WC/WC000000_V.do?branchCd=B00148000)
- [Pleos 음성 데이터 전송](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/sending-audio-data) · [Pleos 경유지 포함 경로 요청](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/route-with-waypoints)

## 시연 APK

- 파일은 [artifacts/pleos-stop-30-demo.apk](artifacts/pleos-stop-30-demo.apk)에 있습니다.
- Pleos Connect 에뮬레이터 P단에서 검증한 Android 디버그 서명 APK입니다.
- SHA-256: 2a15d64df952d68db7ed0004326239832a9cd51ef470f747e17421b47b0d4d32

APK를 저장한 뒤 Pleos Connect 에뮬레이터에 설치합니다.

```powershell
adb install -r .\artifacts\pleos-stop-30-demo.apk
```
