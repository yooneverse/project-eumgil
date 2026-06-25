package com.ssafy.e102.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

	HttpStatus getHttpStatus();

	String getStatus();

	String getMessage();
}
