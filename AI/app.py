from __future__ import annotations

import os
from typing import Any

from flask import Flask, jsonify, request
from flask_cors import CORS


app = Flask(__name__)
CORS(app)


@app.get("/health")
def health() -> tuple[dict[str, str], int]:
    return {"status": "healthy", "service": "ai-intent"}, 200


@app.post("/api/intent")
def parse_intent() -> tuple[Any, int]:
    body = request.get_json(silent=True) or {}
    text = str(body.get("text", "")).strip()

    if not text:
        return jsonify({"error": "text is required"}), 400

    return jsonify(
        {
            "intent": "UNKNOWN",
            "parameters": {},
            "source": "placeholder",
            "text": text,
        }
    ), 200


if __name__ == "__main__":
    app.run(
        host=os.getenv("AI_HOST", "0.0.0.0"),
        port=int(os.getenv("AI_PORT", "5000")),
    )
