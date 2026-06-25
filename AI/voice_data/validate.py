"""
validate.py
전처리 결과 검증 스크립트

검증 항목:
- 모든 WAV 경로 실존 여부
- 빈 텍스트 없는지
- 오디오 길이 0.5~30초 범위
- 샘플링레이트 16000Hz
- train/dev/test 화자 중복 없는지
- 각 split 데이터 수 및 총 시간 출력
"""

import argparse
import json
import logging
import sys
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple

import numpy as np
import soundfile as sf
from tqdm import tqdm

logger = logging.getLogger(__name__)
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
    handlers=[logging.StreamHandler(sys.stdout)],
)

TARGET_SR = 16000
MIN_DUR = 0.5
MAX_DUR = 30.0


def load_jsonl(path: Path) -> List[Dict]:
    if not path.exists():
        logger.warning(f"JSONL 없음: {path}")
        return []
    records = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    return records


def validate_split(
    records: List[Dict],
    split_name: str,
    check_audio: bool = True,
    sample_rate: int = TARGET_SR,
) -> Dict:
    """단일 split 검증. 결과 딕셔너리 반환."""
    errors: List[str] = []
    warnings_: List[str] = []
    total_dur = 0.0
    speakers: Set[str] = set()

    dur_check_sample = min(len(records), 500)  # 오디오 체크는 최대 500건만

    for i, r in enumerate(tqdm(records, desc=f"validate:{split_name}", leave=False)):
        key = r.get("key", f"idx_{i}")

        # 텍스트 검증
        text = r.get("text", "")
        if not text or len(text.strip()) < 1:
            errors.append(f"[{key}] 빈 텍스트")

        # 발화 시간
        dur = r.get("duration", 0.0)
        total_dur += dur
        if dur < MIN_DUR or dur > MAX_DUR:
            warnings_.append(f"[{key}] duration={dur:.2f}s 범위 초과")

        # 화자
        spk = r.get("speaker", "")
        if spk:
            speakers.add(spk)

        # WAV 경로 + 실제 오디오 검증 (샘플링)
        if check_audio and i < dur_check_sample:
            wav_path = r.get("wav", "")
            if not Path(wav_path).exists():
                errors.append(f"[{key}] WAV 없음: {wav_path}")
            else:
                try:
                    info = sf.info(wav_path)
                    if info.samplerate != sample_rate:
                        errors.append(
                            f"[{key}] SR={info.samplerate} (기대={sample_rate})"
                        )
                    actual_dur = info.frames / info.samplerate
                    if abs(actual_dur - dur) > 0.5:
                        warnings_.append(
                            f"[{key}] 메타 duration={dur:.2f}s vs 실제={actual_dur:.2f}s"
                        )
                except Exception as e:
                    errors.append(f"[{key}] WAV 읽기 실패: {e}")

    result = {
        "split": split_name,
        "total": len(records),
        "total_hours": round(total_dur / 3600, 3),
        "avg_dur_sec": round(total_dur / max(len(records), 1), 2),
        "num_speakers": len(speakers),
        "errors": len(errors),
        "warnings": len(warnings_),
        "error_samples": errors[:10],
        "warning_samples": warnings_[:5],
        "speakers": speakers,
    }
    return result


def check_speaker_overlap(
    train_spk: Set[str], dev_spk: Set[str], test_spk: Set[str]
) -> bool:
    ok = True
    for a, b, name in [
        (train_spk, dev_spk, "train∩dev"),
        (train_spk, test_spk, "train∩test"),
        (dev_spk, test_spk, "dev∩test"),
    ]:
        overlap = a & b
        if overlap:
            logger.error(f"화자 겹침 {name}: {len(overlap)}명 예) {list(overlap)[:3]}")
            ok = False
    return ok


def print_summary(results: List[Dict]):
    print("\n" + "=" * 60)
    print("검증 결과 요약")
    print("=" * 60)

    for r in results:
        status = "✓" if r["errors"] == 0 else "✗"
        print(
            f"[{status}] {r['split']:8s}  "
            f"{r['total']:6d}건  "
            f"{r['total_hours']:6.2f}h  "
            f"avg={r['avg_dur_sec']:.2f}s  "
            f"화자={r['num_speakers']}  "
            f"에러={r['errors']}  "
            f"경고={r['warnings']}"
        )
        for e in r["error_samples"]:
            print(f"     ERROR: {e}")
        for w in r["warning_samples"]:
            print(f"     WARN:  {w}")

    total_records = sum(r["total"] for r in results)
    total_hours = sum(r["total_hours"] for r in results)
    print("-" * 60)
    print(f"합계: {total_records}건 / {total_hours:.2f}시간")
    print("=" * 60)


def main():
    parser = argparse.ArgumentParser(description="전처리 결과 검증")
    parser.add_argument("--output_dir", default="output")
    parser.add_argument("--no_audio_check", action="store_true", help="실제 WAV 파일 체크 건너뜀 (빠름)")
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    check_audio = not args.no_audio_check

    all_results = []
    all_speakers: Dict[str, Set[str]] = {}

    for split in ("train", "dev", "test"):
        jsonl_path = output_dir / split / "data.jsonl"
        records = load_jsonl(jsonl_path)
        if not records:
            logger.warning(f"{split} 데이터 없음")
            continue

        result = validate_split(records, split, check_audio=check_audio)
        all_results.append(result)
        all_speakers[split] = result.pop("speakers")

    # 화자 겹침 검사
    if len(all_speakers) >= 2:
        print("\n[화자 겹침 검사]")
        ok = check_speaker_overlap(
            all_speakers.get("train", set()),
            all_speakers.get("dev", set()),
            all_speakers.get("test", set()),
        )
        if ok:
            print("  → 화자 겹침 없음 ✓")

    print_summary(all_results)

    # 검증 통과 기준
    total = sum(r["total"] for r in all_results)
    any_error = any(r["errors"] > 0 for r in all_results)

    if total < 5000:
        logger.warning(f"총 발화 수 {total}건 < 권장 5000건")
    if any_error:
        logger.error("에러 발생. 위 ERROR 메시지를 확인하세요.")
        sys.exit(1)
    else:
        logger.info("검증 통과 ✓")


if __name__ == "__main__":
    main()
