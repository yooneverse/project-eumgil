## 모델 파일 배치 안내

다음 파일들을 아래 경로에 배치하세요.

=== SenseVoice Small int8 ===
gradle이 빌드 전(preBuild)에 자동 다운로드합니다 — 수동 배치 불필요.
출처: https://huggingface.co/stonebed/SenseVoice_busan_finetuning (model.int8.onnx, tokens.txt)
task: ./gradlew :app:downloadSttModels (없을 때만 받음, 멱등)

배치 위치(자동):
  assets/models/sense_voice/model.int8.onnx
  assets/models/sense_voice/tokens.txt

=== Silero VAD ===
다운로드: https://github.com/k2-fsa/sherpa-onnx/releases (silero_vad.onnx 검색)

배치 위치:
  assets/models/vad/silero_vad.onnx

모델 파일이 없으면 음성 입력 기능이 비활성화됩니다.
