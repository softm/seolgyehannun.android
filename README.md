# seolgyehannun.android

**설계한눈 Android WebView 앱**

- 웹 서비스: <https://seolgyehannun.designboard.net/>
- 패키지: `net.softm.seolgyehannun`
- Android 8.0(API 26) 이상
- compileSdk / targetSdk: 36
- JDK 17

## 광고

공통 AdMob 구조를 사용한다.

- 하단 네이티브 광고
- 앱 시작 75초 후, 이후 180초 쿨다운을 둔 전면 광고(세션당 최대 3회)
- 뒤로가기 종료 확인 다이얼로그의 300×250 배너
- UMP 동의 요청 및 광고 개인정보 설정
- `AdMobNative/1` User-Agent로 웹 AdFit 중복 노출 방지

`debug` 빌드는 Google 공식 테스트 광고 ID만 사용하고, `release` 빌드는 설계한눈 전용 광고 단위를 사용한다.

## 빌드

```bash
gradle :app:assembleDebug
```

운영 빌드는 키스토어 환경 변수가 있을 때만 서명한다.

- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## Play 등록자료

- [2026-11-03 거부 대응 및 재심사 절차](docs/PLAY-RESUBMISSION-20261103.md)
- [512px 스토어 아이콘](store/ko-KR/icon-512.png)
- 검증: `python tools/store_assets.py --check`
- 실제 기기 캡처: `python tools/capture_phone.py --help`
- 스크린샷과 release 설치 검증은 아직 필요하며, 재심사는 소유자 승인 후에만 제출합니다.
