"""
pipeline.py
전체 전처리 파이프라인 실행 스크립트

사용 예시:
  # 서브셋 20시간 처리
  python pipeline.py --subset_hours 20

  # 전체 처리
  python pipeline.py

  # 경로 직접 지정
  python pipeline.py \\
      --gyeongsang_dir "C:/Users/SSAFY/Desktop/suyeon/voice_data/korean_saturi" \\
      --dysarthria_dir "C:/Users/SSAFY/Desktop/suyeon/voice_data/hard_voice" \\
      --output_dir "C:/Users/SSAFY/Desktop/suyeon/voice_data/preprocessing_output"

출력 구조:
  C:/Users/SSAFY/Desktop/suyeon/voice_data/preprocessing_output/  ← WAV 세그먼트, 체크포인트, JSONL
  C:/Users/SSAFY/Desktop/suyeon/voice_data/preprocessing_output/results/  ← 리포트
  <작업폴더>/data.jsonl  ← 최종 JSONL (작업 폴더에만 복사)

실행 순서:
  1. GyeongsangProcessor  → 경상도 방언 전처리
  2. DysarthriaProcessor  → 구음장애 전처리 (데이터 있을 때만)
  3. DataBalancer         → 불균형 보정
  4. DatasetSplitter      → train / dev / test 분리
  5. JSONL + wav.scp / text / utt2spk 파일 생성
  6. preprocessing_report.csv 생성 (results/ 폴더)
"""

import argparse
import csv
import json
import logging
import os
import shutil
import sys
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Optional

from data_balancer import DataBalancer
from dataset_splitter import DatasetSplitter
from dysarthria_processor import DysarthriaProcessor
from gyeongsang_processor import GyeongsangProcessor

# ------------------------------------------------------------------ #
#  로깅 설정                                                            #
# ------------------------------------------------------------------ #

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%H:%M:%S",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger("pipeline")


# ------------------------------------------------------------------ #
#  출력 파일 생성                                                        #
# ------------------------------------------------------------------ #


def write_jsonl(records: List[Dict], path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        for r in records:
            entry = {
                "key": r["key"],
                "wav": r["wav"],
                "text": r["text"],
                "source": r.get("source", ""),
                "duration": r.get("duration", 0.0),
            }
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    logger.info(f"JSONL 저장: {path} ({len(records)}건)")


def write_kaldi_files(records: List[Dict], split_dir: Path):
    """wav.scp, text, utt2spk 생성."""
    split_dir.mkdir(parents=True, exist_ok=True)

    with open(split_dir / "wav.scp", "w", encoding="utf-8") as f:
        for r in records:
            f.write(f"{r['key']} {r['wav']}\n")

    with open(split_dir / "text", "w", encoding="utf-8") as f:
        for r in records:
            f.write(f"{r['key']} {r['text']}\n")

    with open(split_dir / "utt2spk", "w", encoding="utf-8") as f:
        for r in records:
            spk = r.get("speaker", r["key"])
            f.write(f"{r['key']} {spk}\n")

    logger.info(f"Kaldi 파일 저장: {split_dir} ({len(records)}건)")


# ------------------------------------------------------------------ #
#  전처리 리포트                                                         #
# ------------------------------------------------------------------ #


def write_report(
    gy_processor: GyeongsangProcessor,
    dy_processor: Optional[DysarthriaProcessor],
    train: List[Dict],
    dev: List[Dict],
    test: List[Dict],
    report_dir: Path,
):
    report_dir.mkdir(parents=True, exist_ok=True)
    report_path = report_dir / "preprocessing_report.csv"

    all_records = train + dev + test
    total_dur = sum(r.get("duration", 0) for r in all_records)

    # 소스별 집계
    by_source: Dict[str, Dict] = defaultdict(lambda: {"count": 0, "duration": 0.0})
    for r in all_records:
        src = r.get("source", "unknown")
        by_source[src]["count"] += 1
        by_source[src]["duration"] += r.get("duration", 0.0)

    # 스킵 사유별 집계
    all_skipped = list(gy_processor.skipped)
    if dy_processor:
        all_skipped.extend(dy_processor.skipped)
    skip_reasons: Dict[str, int] = defaultdict(int)
    for s in all_skipped:
        reason = s.get("reason", "unknown")
        # 앞 부분만 키로 사용 (duration_xxx → duration)
        key = reason.split("_")[0] if "_" in reason else reason
        skip_reasons[key] += 1

    # 화자 수
    speakers = set(r.get("speaker", "") for r in all_records)

    # 성별 분포
    gender_dist: Dict[str, int] = defaultdict(int)
    for r in all_records:
        gender_dist[r.get("gender", "unknown")] += 1

    # 연령대 분포
    age_dist: Dict[str, int] = defaultdict(int)
    for r in all_records:
        if r.get("age"):
            try:
                age = int(r["age"])
                decade = (age // 10) * 10
                grp = f"{decade+1}-{decade+10}"
                age_dist[grp] += 1
            except (TypeError, ValueError):
                pass
        elif r.get("birth_year"):
            try:
                age = 2024 - int(r["birth_year"])
                decade = (age // 10) * 10
                grp = f"{decade+1}-{decade+10}"
                age_dist[grp] += 1
            except (TypeError, ValueError):
                pass

    rows = []

    # 전체 요약
    rows.append(["=== 전체 요약 ===", "", ""])
    rows.append(["항목", "값", "비고"])
    rows.append(["총 발화 수", len(all_records), ""])
    rows.append(["총 오디오 시간 (시간)", round(total_dur / 3600, 2), ""])
    rows.append(["train 발화 수", len(train), f"{len(train)/max(len(all_records),1)*100:.1f}%"])
    rows.append(["dev 발화 수", len(dev), f"{len(dev)/max(len(all_records),1)*100:.1f}%"])
    rows.append(["test 발화 수", len(test), f"{len(test)/max(len(all_records),1)*100:.1f}%"])
    rows.append(["화자 수", len(speakers), ""])
    rows.append(["제외된 발화 수", len(all_skipped), ""])
    rows.append(["", "", ""])

    # 소스별
    rows.append(["=== 소스별 발화 수 ===", "", ""])
    rows.append(["소스", "발화 수", "오디오 시간(h)"])
    for src, info in sorted(by_source.items()):
        rows.append([src, info["count"], round(info["duration"] / 3600, 2)])
    rows.append(["", "", ""])

    # 제외 사유별
    rows.append(["=== 제외 사유별 ===", "", ""])
    rows.append(["사유", "건수", ""])
    for reason, cnt in sorted(skip_reasons.items(), key=lambda x: -x[1]):
        rows.append([reason, cnt, ""])
    rows.append(["", "", ""])

    # 성별 분포
    rows.append(["=== 성별 분포 ===", "", ""])
    rows.append(["성별", "발화 수", "비율(%)"])
    for g, cnt in sorted(gender_dist.items()):
        rows.append([g, cnt, round(cnt / max(len(all_records), 1) * 100, 1)])
    rows.append(["", "", ""])

    # 연령대 분포
    rows.append(["=== 연령대 분포 ===", "", ""])
    rows.append(["연령대", "발화 수", "비율(%)"])
    for grp, cnt in sorted(age_dist.items()):
        rows.append([grp, cnt, round(cnt / max(len(all_records), 1) * 100, 1)])

    with open(report_path, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerows(rows)

    logger.info(f"리포트 저장: {report_path}")


# ------------------------------------------------------------------ #
#  메인                                                                 #
# ------------------------------------------------------------------ #


def main():
    parser = argparse.ArgumentParser(description="SenseVoice 파인튜닝용 데이터 전처리 파이프라인")
    parser.add_argument(
        "--gyeongsang_dir",
        default=r"C:\Users\SSAFY\Desktop\suyeon\voice_data\korean_saturi",
        help="경상도 방언 데이터 최상위 경로 (Training/Validation 폴더 포함)",
    )
    parser.add_argument(
        "--dysarthria_dir",
        default=r"C:\Users\SSAFY\Desktop\suyeon\voice_data\hard_voice",
        help="구음장애 데이터 최상위 경로 (neuro/speech/larynx 포함). 없으면 건너뜀",
    )
    parser.add_argument(
        "--output_dir",
        default=r"C:\Users\SSAFY\Desktop\suyeon\voice_data\preprocessing_output",
        help="전처리 결과 저장 경로",
    )
    parser.add_argument(
        "--results_dir",
        default=r"C:\Users\SSAFY\Desktop\suyeon\voice_data\preprocessing_output\results",
        help="리포트 저장 경로",
    )
    parser.add_argument(
        "--subset_hours",
        type=float,
        default=None,
        help="서브셋 처리 시간 (예: 100). dtype별 한도가 지정되면 무시됨",
    )
    parser.add_argument(
        "--gy_st_hours",
        type=float,
        default=None,
        help="경상도 st_(따라말하기) 처리 시간 한도 (예: 40)",
    )
    parser.add_argument(
        "--gy_say_hours",
        type=float,
        default=None,
        help="경상도 say_(질문답하기) 처리 시간 한도 (예: 40)",
    )
    parser.add_argument(
        "--dys_hours",
        type=float,
        default=None,
        help="구음장애 처리 시간 한도 (예: 20)",
    )
    parser.add_argument("--num_workers", type=int, default=1, help="현재 미사용 (향후 병렬처리 예정)")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--skip_balance",
        action="store_true",
        help="데이터 불균형 보정 건너뜀 (빠른 테스트용)",
    )
    args = parser.parse_args()

    # dtype별 한도 구성
    dtype_hours = {}
    if args.gy_st_hours is not None:
        dtype_hours["st"] = args.gy_st_hours
    if args.gy_say_hours is not None:
        dtype_hours["say"] = args.gy_say_hours
    # dtype_hours가 비어있으면 None으로 (subset_hours 전체 모드)
    gy_dtype_hours = dtype_hours if dtype_hours else None

    output_dir = Path(args.output_dir)
    results_dir = Path(args.results_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    results_dir.mkdir(parents=True, exist_ok=True)

    # 로그 파일도 저장
    log_path = results_dir / "pipeline.log"
    file_handler = logging.FileHandler(log_path, encoding="utf-8")
    file_handler.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s"))
    logging.getLogger().addHandler(file_handler)

    logger.info("=" * 60)
    logger.info("파이프라인 시작")
    logger.info(f"  gyeongsang_dir: {args.gyeongsang_dir}")
    logger.info(f"  dysarthria_dir: {args.dysarthria_dir}")
    logger.info(f"  output_dir:     {output_dir.resolve()}")
    logger.info(f"  results_dir:    {results_dir.resolve()}")
    logger.info(f"  subset_hours:   {args.subset_hours}")
    logger.info(f"  gy_st_hours:    {args.gy_st_hours}")
    logger.info(f"  gy_say_hours:   {args.gy_say_hours}")
    logger.info(f"  dys_hours:      {args.dys_hours}")
    logger.info("=" * 60)

    # ---- 1. 경상도 방언 처리 ------------------------------------------ #
    gy_processor = GyeongsangProcessor(
        data_dir=args.gyeongsang_dir,
        output_dir=str(output_dir),
        subset_hours=args.subset_hours,
        dtype_hours=gy_dtype_hours,
        seed=args.seed,
    )
    gy_records = gy_processor.process()
    interrupted = gy_processor._interrupted

    # ---- 2. 구음장애 처리 ---------------------------------------------- #
    dy_processor: Optional[DysarthriaProcessor] = None
    dy_records: List[Dict] = []

    if not interrupted and args.dysarthria_dir and Path(args.dysarthria_dir).exists():
        dy_processor = DysarthriaProcessor(
            data_dir=args.dysarthria_dir,
            output_dir=str(output_dir),
            subset_hours=args.dys_hours,
            seed=args.seed,
        )
        dy_records = dy_processor.process()
        interrupted = dy_processor._interrupted
    else:
        logger.info("구음장애 데이터 경로 없음 → 건너뜀")

    # ---- 중단 시 현재까지 결과만 저장하고 종료 --------------------------- #
    def _save_partial_and_exit(gy_recs: List[Dict], dy_recs: List[Dict]):
        all_recs = gy_recs + dy_recs
        if not all_recs:
            logger.warning("저장할 데이터가 없습니다.")
            sys.exit(0)
        logger.info(f"중단 시점까지 {len(all_recs)}건 부분 저장 중...")
        splitter = DatasetSplitter(seed=args.seed)
        train_p, dev_p, test_p = splitter.split(all_recs)
        write_jsonl(train_p, output_dir / "train" / "data.jsonl")
        write_jsonl(dev_p, output_dir / "dev" / "data.jsonl")
        write_jsonl(test_p, output_dir / "test" / "data.jsonl")
        write_jsonl(all_recs, output_dir / "data.jsonl")
        write_kaldi_files(train_p, output_dir / "train")
        write_kaldi_files(dev_p, output_dir / "dev")
        write_kaldi_files(test_p, output_dir / "test")
        write_report(gy_processor, dy_processor, train_p, dev_p, test_p, results_dir)
        work_dir_jsonl = Path(__file__).parent / "data.jsonl"
        shutil.copy(output_dir / "data.jsonl", work_dir_jsonl)
        logger.info(
            f"부분 저장 완료. 다음 실행 시 체크포인트에서 이어서 처리됩니다.\n"
            f"  체크포인트: {output_dir / 'checkpoints'}"
        )
        sys.exit(0)

    if interrupted:
        _save_partial_and_exit(gy_records, dy_records)

    # ---- 3. 데이터 불균형 보정 ----------------------------------------- #
    if args.skip_balance or not dy_records:
        logger.info("불균형 보정 건너뜀 (구음장애 없거나 --skip_balance 옵션)")
        all_records = gy_records + dy_records
    else:
        balancer = DataBalancer(seed=args.seed)
        all_records = balancer.balance(gy_records, dy_records)
        stats = balancer.stats(all_records)
        logger.info(f"밸런싱 통계: {stats}")

    if not all_records:
        logger.error("처리된 데이터가 없습니다. 입력 경로와 데이터 구조를 확인하세요.")
        sys.exit(1)

    # ---- 4. train / dev / test 분리 ------------------------------------ #
    splitter = DatasetSplitter(seed=args.seed)
    train, dev, test = splitter.split(all_records)

    # ---- 5. 출력 파일 생성 --------------------------------------------- #
    write_jsonl(train, output_dir / "train" / "data.jsonl")
    write_jsonl(dev, output_dir / "dev" / "data.jsonl")
    write_jsonl(test, output_dir / "test" / "data.jsonl")
    write_jsonl(all_records, output_dir / "data.jsonl")

    write_kaldi_files(train, output_dir / "train")
    write_kaldi_files(dev, output_dir / "dev")
    write_kaldi_files(test, output_dir / "test")

    # ---- 6. 전처리 리포트 ---------------------------------------------- #
    write_report(gy_processor, dy_processor, train, dev, test, results_dir)

    # ---- data.jsonl 작업 폴더에 복사 ----------------------------------- #
    work_dir_jsonl = Path(__file__).parent / "data.jsonl"
    shutil.copy(output_dir / "data.jsonl", work_dir_jsonl)
    logger.info(f"data.jsonl 작업 폴더 복사: {work_dir_jsonl}")

    # ---- 최종 요약 ----------------------------------------------------- #
    total_dur = sum(r.get("duration", 0) for r in all_records)
    logger.info("=" * 60)
    logger.info("파이프라인 완료!")
    logger.info(f"  총 발화: {len(all_records)}건 / {total_dur/3600:.2f}시간")
    logger.info(f"  train={len(train)} / dev={len(dev)} / test={len(test)}")
    logger.info(f"  출력 경로: {output_dir.resolve()}")
    logger.info(f"  리포트:   {results_dir.resolve()}/preprocessing_report.csv")
    logger.info("=" * 60)


if __name__ == "__main__":
    main()
