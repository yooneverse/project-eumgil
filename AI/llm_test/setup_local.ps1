# 테스트 앱 로컬 환경 세팅 스크립트
# 실행: .\setup_local.ps1

$ROOT       = $PSScriptRoot
$LOCAL_PROP = "$ROOT\FE\local.properties"

$SRC_BASE   = "C:\Users\SSAFY\Desktop\final\S14P31E102\FE\app"
$DST_BASE   = "$ROOT\FE\app"

$FILES = @(
    @{ src = "$SRC_BASE\src\main\assets\models\sense_voice\model.int8.onnx"; dst = "$DST_BASE\src\main\assets\models\sense_voice\model.int8.onnx" },
    @{ src = "$SRC_BASE\src\main\assets\models\sense_voice\tokens.txt";       dst = "$DST_BASE\src\main\assets\models\sense_voice\tokens.txt"       },
    @{ src = "$SRC_BASE\src\main\assets\models\vad\silero_vad.onnx";          dst = "$DST_BASE\src\main\assets\models\vad\silero_vad.onnx"          },
    @{ src = "$SRC_BASE\libs\sherpa-onnx-1.13.0.aar";                         dst = "$DST_BASE\libs\sherpa-onnx-1.13.0.aar"                         }
)

# ── 1. IP 설정 ──────────────────────────────────────────────────────────────
$content = Get-Content $LOCAL_PROP -Raw
if ($content -match 'server\.url=(.+)') {
    $currentUrl = $Matches[1].Trim()
} else {
    $currentUrl = "없음"
}

Write-Host ""
Write-Host "[ 서버 IP 설정 ]"
Write-Host "현재 값: $currentUrl"
$input = Read-Host "새 IP 입력 (엔터 = 현재 값 유지)"

if ($input -ne "") {
    $newUrl = "http://$($input.Trim()):5000"
    if ($content -match 'server\.url=.+') {
        $content = $content -replace 'server\.url=.+', "server.url=$newUrl"
    } else {
        $content = $content.TrimEnd() + "`nserver.url=$newUrl`n"
    }
    Set-Content $LOCAL_PROP $content -NoNewline
    Write-Host "server.url -> $newUrl"
} else {
    Write-Host "변경 없음"
}

# ── 2. 모델 파일 복사 ────────────────────────────────────────────────────────
Write-Host ""
Write-Host "[ 모델 파일 복사 ]"

foreach ($f in $FILES) {
    if (-not (Test-Path $f.src)) {
        Write-Host "  [SKIP] 원본 없음: $($f.src)"
        continue
    }
    $dir = Split-Path $f.dst -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    Copy-Item $f.src $f.dst -Force
    Write-Host "  [OK]   $(Split-Path $f.dst -Leaf)"
}

Write-Host ""
Write-Host "완료. Android Studio에서 Sync & Build 하세요."
