# 설계한눈 Play 재심사 준비 — 2026-11-03

상태: 아이콘 수정 완료. **실기기 스크린샷·서명 AAB·설치 검증 미완료, 제출하지 않음.**

## 확인 결과

| 항목 | 근거 | 조치 / 남은 작업 |
|---|---|---|
| 한국어 스토어 아이콘 | 거부 메일 HI_RES_ICON-7088.png는 녹색 배경의 클립보드 | 기존 디자인을 기준으로 Android vector 수정 |
| 설치 아이콘 | LAUNCHER_ICON-5703.png는 주유기 모양. 현재 vector와 일치 | 일반·원형 adaptive icon이 같은 새 전경 사용 |
| 이름 | 메일 설치 화면과 manifest 모두 설계한눈 | 유지. Console 기본/번역/맞춤 등록정보도 확인 필요 |
| 스크린샷 | APP_SCREENSHOT-2696.png는 PC 소개 페이지·로그인 페이지를 병치 | 제출용에서 교체 필요. 이 저장소에는 Console 원본 등록정보가 없어 직접 삭제하지 않음 |
| 실제 기능 확인 | 운영 웹앱의 `샘플 업무 시작`으로 목록, 필터, 개요의 필지·허가·접수 UI 확인 | 체험 데이터이며 서버 저장/내보내기 정상 동작은 검증하지 않음 |
| 지도 | 이 브라우저에서 `일반지도를 불러오지 못했습니다` 표시 | 정상 지도처럼 합성하지 않음. 실제 기기에서 재확인 |
| Android 실행 | 현재 작업 환경에 SDK·Gradle·adb·에뮬레이터 없음 | 빌드/설치 확인과 실제 모바일 캡처 필요 |

메일: https://mail.google.com/mail/u/?authuser=softmnet%40gmail.com#all/1a19ec1fd382b396
운영 웹앱: https://seolgyehannun.designboard.net/

## 아이콘 단일 원본

`app/src/main/res/drawable/ic_launcher_foreground.xml`과 `values/colors.xml`을 원본으로 사용합니다.
`tools/store_assets.py`는 Android adaptive 108dp 캔버스의 중앙 72dp를 렌더링하여
`store/ko-KR/icon-512.png`를 만듭니다. 기본/원형 런처 모두 같은 전경·배경입니다.
기기별 마스크로 외곽 모양이 달라질 수 있으나 클립보드 문양·색상은 같습니다.
스토어 PNG에는 둥근 모서리나 그림자를 미리 넣지 않았습니다.

```bash
python -m pip install -r requirements-store-assets.txt
python tools/store_assets.py
python tools/store_assets.py --check
```

정적 검사 통과: 이름, manifest 연결, 일반·원형 전경/배경, 안전 영역,
512×512 RGBA PNG, 1MB 이하, 원본과의 재생성 일치.
이 검사는 APK/AAB 내 패키징이나 기기 설치 결과까지 증명하지 않습니다.

## 빌드와 실제 설치 확인

JDK 17, Android SDK 36, 프로젝트 AGP와 호환되는 Gradle 환경에서 실행합니다.
현재 저장소에는 Gradle wrapper가 없습니다.

```bash
gradle :app:assembleDebug
adb devices
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL shell am start -n net.softm.seolgyehannun/.MainActivity
```

서명이 다른 운영 앱이 설치된 기기에는 debug 설치가 실패할 수 있습니다.
기존 앱을 삭제하여 데이터를 잃지 말고 별도 테스트 기기/에뮬레이터를 사용하세요.
홈 화면에서 이름 '설계한눈', 녹색·노란색 클립보드 모양을 확인합니다.
원형/둥근 사각형 마스크에서 문양이 잘리지 않는지 확인합니다.

release 기본값은 versionCode 2, versionName 1.0.1입니다.
Console에서 사용한 가장 큰 versionCode를 확인한 뒤 더 큰 값으로 지정하세요.

```bash
# 기존 서명 환경 변수 설정 후, N을 Console 최고 versionCode보다 큰 숫자로 교체
gradle :app:bundleRelease -PPLAY_VERSION_CODE=N
```

`app/build/outputs/bundle/release/app-release.aab`를 사용합니다.
서명 환경 변수가 없으면 이 프로젝트는 release 서명을 수행하지 않습니다.
최종적으로 같은 release를 내부 테스트에 설치해 UI·광고·로그인·핵심 기능을 확인하세요.
테스트/심사 제출 역시 소유자의 승인을 받은 뒤 진행합니다.

## 스크린샷 확보

실제 Android 앱에서 소개 → 체험하기 → 샘플 업무 시작으로 이동합니다.
샘플 데이터임을 알리는 `체험 데이터`/`샘플 업무공간` 표시를 유지하세요.
1080×1920 세로 기기/에뮬레이터에서 2–8장의 독립 화면을 캡처합니다.

| 파일 | 촬영할 실제 화면 | 확인 사항 |
|---|---|---|
| 01-dashboard.png | 업무 목록, 상태·마감 요약 | 텍스트 가독성, 로딩 종료 |
| 02-case-overview.png | 한 업무의 필지·허가 구성·접수 개요 | 모바일에서 상세 영역이 실제로 표시됨 |
| 03-deadlines.png | 해당 업무의 기한 탭 | 실제 표시되는 기능만 포함 |

```bash
python tools/capture_phone.py 01-dashboard --serial DEVICE_SERIAL --sample-data
# 다음 화면을 기기에서 직접 연 후
python tools/capture_phone.py 02-case-overview --serial DEVICE_SERIAL --sample-data
```

`store/captures/`에는 원본 PNG, RGB PNG, 버전·시각·해시 JSON이 기록됩니다.
개인자료 유출을 막기 위해 해당 폴더는 gitignore입니다.
검토한 RGB 파일만 `store/ko-KR/phoneScreenshots/`에 복사하고 다음을 실행하세요.

```bash
python tools/store_assets.py --check --screenshots store/ko-KR/phoneScreenshots
```

규격 검사는 내용의 진실성까지 판정하지 않습니다. 지도 오류, 빈 화면, 잘린 UI,
민감한 실고객 정보, 다른 앱, PC 화면 병치, 가상/합성 UI가 없는지 직접 확인하세요.
현재는 제출용 스크린샷 파일을 만들지 않았습니다.

## Play Console 등록 순서 — 아직 수행하지 않음

1. 설계한눈 `net.softm.seolgyehannun` → 스토어 등록정보 → 한국어(ko-KR).
2. 앱 이름을 `설계한눈`으로 확인하고 고해상도 아이콘을 `store/ko-KR/icon-512.png`로 교체.
3. 기존 PC 소개/로그인 병치 스크린샷을 제거하고 검증한 실제 모바일 화면 2장 이상 등록.
4. 기본·번역·맞춤 스토어 등록정보에서도 동일한 이름/아이콘/실제 기능 표현인지 점검.
5. 새 아이콘이 포함된 서명 AAB를 올리고 내부 테스트 설치 결과와 일치하는지 점검.
6. 로그인 필수 기능은 앱 액세스 항목에서 심사에 필요한 접근 안내를 제공.
7. 게시 개요에서 변경 이미지, 버전, 설명과 모든 보류 변경을 소유자에게 먼저 보여줌.
8. **소유자의 명시적 제출 승인 후에만** 검토를 위해 변경사항 전송.

이미지를 바꾸는 것만으로 기존 AAB의 주유기 아이콘이 바뀌지는 않습니다.
새 Android 리소스가 들어간 AAB도 필요합니다. 정책 통과를 보장하지 않습니다.

공식 규격: https://support.google.com/googleplay/android-developer/answer/9866151?hl=ko
