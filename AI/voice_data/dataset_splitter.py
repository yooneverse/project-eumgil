"""
dataset_splitter.py
train / dev / test 화자 단위 분리

기준:
- train 98% / dev 1% / test 1%
- 같은 화자가 여러 split에 걸치지 않도록 화자 단위로 먼저 분리
- 경상도 / 구음장애 각각 독립 분리 후 합산
- 랜덤 시드 고정
"""

import logging
from collections import defaultdict
from typing import Dict, List, Tuple

import numpy as np

logger = logging.getLogger(__name__)

GYEONGSANG_SOURCES = {"gyeongsang_st", "gyeongsang_say", "gyeongsang_talk"}
DYSARTHRIA_SOURCES = {"dysarthria_neuro", "dysarthria_speech", "dysarthria_larynx"}


class DatasetSplitter:
    def __init__(self, train_ratio: float = 0.98, seed: int = 42):
        self.train_ratio = train_ratio
        self.dev_ratio = (1.0 - train_ratio) / 2
        self.test_ratio = (1.0 - train_ratio) / 2
        self.seed = seed
        self.rng = np.random.RandomState(seed)

    def split(
        self, records: List[Dict]
    ) -> Tuple[List[Dict], List[Dict], List[Dict]]:
        """records를 (train, dev, test)로 분리해 반환."""
        gy_recs = [r for r in records if r.get("source", "") in GYEONGSANG_SOURCES]
        dy_recs = [r for r in records if r.get("source", "") in DYSARTHRIA_SOURCES]
        other_recs = [
            r
            for r in records
            if r.get("source", "") not in GYEONGSANG_SOURCES
            and r.get("source", "") not in DYSARTHRIA_SOURCES
        ]

        gy_train, gy_dev, gy_test = self._speaker_split(gy_recs, "gyeongsang")
        dy_train, dy_dev, dy_test = self._speaker_split(dy_recs, "dysarthria")

        # 기타 데이터는 랜덤 분리
        ot_train, ot_dev, ot_test = self._random_split(other_recs)

        train = gy_train + dy_train + ot_train
        dev = gy_dev + dy_dev + ot_dev
        test = gy_test + dy_test + ot_test

        self._log_stats(train, dev, test)
        return train, dev, test

    # ------------------------------------------------------------------ #

    def _speaker_split(
        self, records: List[Dict], name: str
    ) -> Tuple[List[Dict], List[Dict], List[Dict]]:
        """화자 단위로 분리."""
        if not records:
            return [], [], []

        by_speaker: Dict[str, List[Dict]] = defaultdict(list)
        for r in records:
            by_speaker[r.get("speaker", "unknown")].append(r)

        speakers = sorted(by_speaker.keys())
        rng = np.random.RandomState(self.seed)
        rng.shuffle(speakers)

        n = len(speakers)
        n_test = max(1, round(n * self.test_ratio))
        n_dev = max(1, round(n * self.dev_ratio))
        n_train = n - n_dev - n_test

        # 화자 수가 너무 적으면 최소 1명씩 보장
        if n_train < 1:
            n_train = 1
        if n_dev < 1 and n > 2:
            n_dev = 1
        if n_test < 1 and n > 1:
            n_test = 1

        train_spk = set(speakers[:n_train])
        dev_spk = set(speakers[n_train : n_train + n_dev])
        test_spk = set(speakers[n_train + n_dev :])

        train = [r for r in records if r.get("speaker") in train_spk]
        dev = [r for r in records if r.get("speaker") in dev_spk]
        test = [r for r in records if r.get("speaker") in test_spk]

        logger.info(
            f"{name} 분리: 화자 {n}명 → train={len(train_spk)}명({len(train)}건) "
            f"dev={len(dev_spk)}명({len(dev)}건) test={len(test_spk)}명({len(test)}건)"
        )
        return train, dev, test

    def _random_split(
        self, records: List[Dict]
    ) -> Tuple[List[Dict], List[Dict], List[Dict]]:
        """화자 정보 없는 데이터를 랜덤 분리."""
        if not records:
            return [], [], []
        rng = np.random.RandomState(self.seed)
        indices = np.arange(len(records))
        rng.shuffle(indices)

        n = len(records)
        n_test = max(1, round(n * self.test_ratio))
        n_dev = max(1, round(n * self.dev_ratio))
        n_train = n - n_dev - n_test

        train = [records[i] for i in indices[:n_train]]
        dev = [records[i] for i in indices[n_train : n_train + n_dev]]
        test = [records[i] for i in indices[n_train + n_dev :]]
        return train, dev, test

    # ------------------------------------------------------------------ #

    def _log_stats(
        self, train: List[Dict], dev: List[Dict], test: List[Dict]
    ):
        total = len(train) + len(dev) + len(test)
        if total == 0:
            return
        logger.info(
            f"최종 분리: train={len(train)}({len(train)/total*100:.1f}%) "
            f"dev={len(dev)}({len(dev)/total*100:.1f}%) "
            f"test={len(test)}({len(test)/total*100:.1f}%) "
            f"합계={total}"
        )

        # 화자 겹침 검증
        train_spk = {r.get("speaker") for r in train}
        dev_spk = {r.get("speaker") for r in dev}
        test_spk = {r.get("speaker") for r in test}
        overlap_td = train_spk & dev_spk
        overlap_tt = train_spk & test_spk
        overlap_dt = dev_spk & test_spk
        if overlap_td or overlap_tt or overlap_dt:
            logger.warning(
                f"화자 겹침 발생: train∩dev={len(overlap_td)}, "
                f"train∩test={len(overlap_tt)}, dev∩test={len(overlap_dt)}"
            )
        else:
            logger.info("화자 겹침 없음 ✓")
