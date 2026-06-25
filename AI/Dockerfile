FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    AI_HOST=0.0.0.0 \
    AI_PORT=5000

WORKDIR /app

COPY llm_test/server/requirements.txt /tmp/server-requirements.txt
COPY requirements.docker.txt /tmp/docker-requirements.txt
RUN pip install --no-cache-dir -r /tmp/server-requirements.txt -r /tmp/docker-requirements.txt

COPY llm_test/server ./llm_test/server

WORKDIR /app/llm_test/server

EXPOSE 5000

CMD ["sh", "-c", "gunicorn --bind ${AI_HOST}:${AI_PORT} --error-logfile - --capture-output app:app"]
