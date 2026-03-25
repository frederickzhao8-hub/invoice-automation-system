import os
import time

from flask import Flask, jsonify

app = Flask(__name__)

# Simulate a short warm-up period so the readiness probe has something real to check.
START_TIME = time.time()
STARTUP_DELAY_SECONDS = int(os.getenv("STARTUP_DELAY_SECONDS", "5"))
SERVICE_NAME = os.getenv("SERVICE_NAME", "hello-service")
APP_VERSION = os.getenv("APP_VERSION", "v1")
PORT = int(os.getenv("PORT", "5000"))


def is_ready() -> bool:
    return (time.time() - START_TIME) >= STARTUP_DELAY_SECONDS


@app.get("/")
def root():
    return jsonify(
        {
            "service": SERVICE_NAME,
            "version": APP_VERSION,
            "message": "Hello service is running.",
        }
    )


@app.get("/hello")
def hello():
    return jsonify(
        {
            "service": SERVICE_NAME,
            "version": APP_VERSION,
            "message": "Hello from the Kubernetes demo.",
        }
    )


@app.get("/health/live")
def liveness():
    # Liveness only answers whether the process is alive.
    return jsonify({"status": "alive", "service": SERVICE_NAME}), 200


@app.get("/health/ready")
def readiness():
    # Readiness waits until the application has finished warming up.
    if not is_ready():
        return (
            jsonify(
                {
                    "status": "starting",
                    "service": SERVICE_NAME,
                    "ready": False,
                }
            ),
            503,
        )

    return jsonify({"status": "ready", "service": SERVICE_NAME, "ready": True}), 200


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=PORT)
