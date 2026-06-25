## sherpa-onnx AAR 배치 방법

sherpa-onnx는 JitPack을 통해 배포되지 않습니다.
GitHub Releases에서 AAR을 직접 다운로드하여 이 폴더에 배치해야 합니다.

=== 다운로드 URL ===
https://github.com/k2-fsa/sherpa-onnx/releases

=== 다운로드 방법 ===
1. 위 URL에서 최신 릴리즈로 이동
2. Assets 섹션에서 "sherpa-onnx-android-*.aar" 파일 다운로드
   예: sherpa-onnx-android-1.10.30.aar
3. 다운로드한 .aar 파일을 이 폴더(app/libs/)에 복사

=== 버전 확인 ===
1.10.x 이상 권장 (SenseVoice Small int8 지원)

배치 후 Android Studio에서 "Sync Project with Gradle Files" 실행
