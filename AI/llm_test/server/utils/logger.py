import logging
import time
from functools import wraps

# 로거 설정
logging.basicConfig(
    level=logging.INFO,
    format='time=%(asctime)s level=%(levelname)s logger=%(name)s message="%(message)s"',
    datefmt='%Y-%m-%d %H:%M:%S'
)


def get_logger(name):
    return logging.getLogger(name)


def log_performance(func):
    """성능 측정 데코레이터"""
    logger = get_logger(func.__name__)

    @wraps(func)
    def wrapper(*args, **kwargs):
        start_time = time.time()
        logger.debug(f"Started")

        try:
            result = func(*args, **kwargs)
            elapsed = time.time() - start_time
            logger.debug(f"Completed in {elapsed:.2f}s")
            return result
        except Exception as e:
            elapsed = time.time() - start_time
            logger.error(f"Failed after {elapsed:.2f}s: {str(e)}")
            raise

    return wrapper
