package com.ssafy.e102.domain.route.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.e102.domain.route.entity.BusStop;

public interface BusStopRepository extends JpaRepository<BusStop, Long> {

	List<BusStop> findAllByActiveTrue();

	List<BusStop> findAllByBstopIdIn(Collection<String> bstopIds);
}
