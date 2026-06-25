package com.ssafy.e102.domain.report.exception;

import com.ssafy.e102.global.exception.BusinessException;

public class HazardReportException extends BusinessException {

	public HazardReportException(HazardReportErrorCode errorCode) {
		super(errorCode);
	}

	public HazardReportException(HazardReportErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public HazardReportException(HazardReportErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}
}
