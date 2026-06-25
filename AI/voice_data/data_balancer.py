"""
data_balancer.py
데이터 불균형 보정

처리 내용:
1. 구음장애 연령 불균형 보정 (21~30세 → 목표 20%)
2. 장애 유형 비율 유지하며 다운샘플링
3. 경상도:구음장애 = 7:3 비율로 최종 믹싱
"""

import logging
from collections import defaultdict
from typing import Dict, List, Optional, Tuple

import numpy as np

logger = logging.getLogger(__name__)

# 장애 유형별 목표 비율 (원본 데이터 분포 기준)
DISEASE_TYPE_RATIO = {"01": 0.2082, "02": 0.3035, "03": 0.4883}

# 구음장애 연령대별 목표 비율
AGE_GROUP_TARGET = {
    "21-30": 0.20,  # 원본 44% → 20%로 다운샘플
}


def _age_group(age: int) -> str:
    """연령 → 연령대 문자열."""
    if age <= 0:
        return "unknown"
    decade = (age // 10) * 10
    return f"{decade + 1}-{decade + 10}"


class DataBalancer:
    def __init__(self, seed: int = 42):
        self.seed = seed
        self.rng = np.random.RandomState(seed)

    # ------------------------------------------------------------------ #
    #  퍼블릭 API                                                           #
    # ------------------------------------------------------------------ #

    def balance(
        self,
        gyeongsang_records: List[Dict],
        dysarthria_records: List[Dict],
    ) -> List[Dict]:
        """경상도 + 구음장애 records를 받아 불균형 보정 후 합산 반환."""
        logger.info(
            f"밸런싱 전: 경상도={len(gyeongsang_records)}, 구음장애={len(dysarthria_records)}"
        )

        dys_balanced = self._balance_dysarthria(dysarthria_records)
        gy_balanced = self._balance_gyeongsang(gyeongsang_records, len(dys_balanced))

        result = gy_balanced + dys_balanced
        self.rng.shuffle(result)
        logger.info(
            f"밸런싱 후: 경상도={len(gy_balanced)}, 구음장애={len(dys_balanced)}, "
            f"합계={len(result)}"
        )
        return result

    # ------------------------------------------------------------------ #
    #  구음장애 불균형 보정                                                  #
    # ------------------------------------------------------------------ #

    def _balance_dysarthria(self, records: List[Dict]) -> List[Dict]:
        if not records:
            return []

        # 1단계: 연령 불균형 보정 (21~30세 다운샘플)
        records = self._fix_age_imbalance(records)

        # 2단계: 장애 유형 비율 유지
        records = self._fix_type_ratio(records)

        return records

    def _fix_age_imbalance(self, records: List[Dict]) -> List[Dict]:
        """21~30세 데이터를 전체의 20%로 다운샘플."""
        by_age: Dict[str, List[Dict]] = defaultdict(list)
        for r in records:
            grp = _age_group(int(r.get("age", 0)))
            by_age[grp].append(r)

        target_group = "21-30"
        if target_group not in by_age:
            return records

        target_count = int(
            sum(len(v) for k, v in by_age.items() if k != target_group)
            / (1 - AGE_GROUP_TARGET[target_group])
            * AGE_GROUP_TARGET[target_group]
        )
        current_count = len(by_age[target_group])

        if current_count <= target_count:
            logger.info(f"연령 보정 불필요: {target_group} {current_count}개")
            return records

        sampled = self.rng.choice(by_age[target_group], target_count, replace=False).tolist()
        logger.info(
            f"연령 보정: {target_group} {current_count} → {target_count}개"
        )

        result = []
        for k, v in by_age.items():
            if k == target_group:
                result.extend(sampled)
            else:
                result.extend(v)
        return result

    def _fix_type_ratio(self, records: List[Dict]) -> List[Dict]:
        """장애 유형 비율 유지하며 최소 유형에 맞춰 다운샘플."""
        by_type: Dict[str, List[Dict]] = defaultdict(list)
        for r in records:
            by_type[r.get("disease_type", "unknown")].append(r)

        # 비율이 없는 유형은 건너뜀
        valid_types = [t for t in by_type if t in DISEASE_TYPE_RATIO]
        if len(valid_types) < 2:
            return records

        # 각 유형별로 목표 비율 달성 시 필요한 전체 수 계산
        # → 최소값 기준으로 전체 타겟 결정
        totals = []
        for t in valid_types:
            ratio = DISEASE_TYPE_RATIO[t]
            n = len(by_type[t])
            totals.append(n / ratio)
        total_target = int(min(totals))

        result = []
        for t in valid_types:
            target_n = max(1, int(total_target * DISEASE_TYPE_RATIO[t]))
            current = by_type[t]
            if len(current) > target_n:
                sampled = self.rng.choice(current, target_n, replace=False).tolist()
                logger.info(f"타입 보정: disease_type={t} {len(current)} → {target_n}")
            else:
                sampled = current
            result.extend(sampled)

        # 비율 없는 유형은 그대로 추가
        for t, v in by_type.items():
            if t not in DISEASE_TYPE_RATIO:
                result.extend(v)

        return result

    # ------------------------------------------------------------------ #
    #  경상도 다운샘플                                                       #
    # ------------------------------------------------------------------ #

    def _balance_gyeongsang(self, records: List[Dict], dys_count: int) -> List[Dict]:
        """경상도:구음장애 = 7:3 비율에 맞춰 경상도 다운샘플."""
        if dys_count == 0:
            logger.info("구음장애 데이터 없음 → 경상도 전체 사용")
            return records

        target_gy = int(dys_count * 7 / 3)
        if len(records) <= target_gy:
            logger.info(f"경상도 다운샘플 불필요: {len(records)} ≤ {target_gy}")
            return records

        # 소스 유형별 비율 유지하며 다운샘플
        by_source: Dict[str, List[Dict]] = defaultdict(list)
        for r in records:
            by_source[r.get("source", "gyeongsang")].append(r)

        current_total = len(records)
        result = []
        for src, recs in by_source.items():
            src_target = max(1, int(target_gy * len(recs) / current_total))
            if len(recs) > src_target:
                sampled = self.rng.choice(recs, src_target, replace=False).tolist()
                logger.info(f"경상도 소스 보정: {src} {len(recs)} → {src_target}")
            else:
                sampled = recs
            result.extend(sampled)

        logger.info(f"경상도 다운샘플: {len(records)} → {len(result)}")
        return result

    # ------------------------------------------------------------------ #
    #  통계 출력                                                            #
    # ------------------------------------------------------------------ #

    def stats(self, records: List[Dict]) -> Dict:
        """records 통계 딕셔너리 반환."""
        total = len(records)
        total_dur = sum(r.get("duration", 0) for r in records)
        by_source: Dict[str, int] = defaultdict(int)
        by_gender: Dict[str, int] = defaultdict(int)
        by_age: Dict[str, int] = defaultdict(int)

        for r in records:
            by_source[r.get("source", "unknown")] += 1
            by_gender[r.get("gender", "unknown")] += 1
            if r.get("age"):
                by_age[_age_group(int(r["age"]))] += 1
            elif r.get("birth_year"):
                try:
                    age = 2024 - int(r["birth_year"])
                    by_age[_age_group(age)] += 1
                except (TypeError, ValueError):
                    pass

        return {
            "total": total,
            "total_hours": round(total_dur / 3600, 2),
            "by_source": dict(by_source),
            "by_gender": dict(by_gender),
            "by_age_group": dict(sorted(by_age.items())),
        }
