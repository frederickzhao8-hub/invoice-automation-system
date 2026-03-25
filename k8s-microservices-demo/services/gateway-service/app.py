import os
import time

import requests
from flask import Flask, jsonify

app = Flask(__name__)

START_TIME = time.time()
STARTUP_DELAY_SECONDS = int(os.getenv("STARTUP_DELAY_SECONDS", "3"))
SERVICE_NAME = os.getenv("SERVICE_NAME", "gateway-service")
APP_VERSION = os.getenv("APP_VERSION", "v1")
PORT = int(os.getenv("PORT", "5000"))
HELLO_SERVICE_URL = os.getenv("HELLO_SERVICE_URL", "http://localhost:5001")
ORDER_SERVICE_URL = os.getenv("ORDER_SERVICE_URL", "http://localhost:5002")
REQUEST_TIMEOUT_SECONDS = float(os.getenv("REQUEST_TIMEOUT_SECONDS", "2.0"))


def is_ready() -> bool:
    return (time.time() - START_TIME) >= STARTUP_DELAY_SECONDS


def call_downstream(url: str) -> dict:
    response = requests.get(url, timeout=REQUEST_TIMEOUT_SECONDS)
    response.raise_for_status()
    return response.json()


@app.get("/")
def root():
    return jsonify(
        {
            "service": SERVICE_NAME,
            "version": APP_VERSION,
            "message": "Gateway service is running.",
            "availableEndpoints": [
                "/api/hello",
                "/api/orders/<order_id>",
                "/api/demo/<order_id>",
            ],
        }
    )


@app.get("/api/hello")
def proxy_hello():
    try:
        hello_payload = call_downstream(f"{HELLO_SERVICE_URL}/hello")
        return jsonify(
            {
                "service": SERVICE_NAME,
                "version": APP_VERSION,
                "downstream": hello_payload,
            }
        )
    except requests.RequestException as exc:
        return (
            jsonify(
                {
                    "service": SERVICE_NAME,
                    "error": "hello-service is unavailable",
                    "details": str(exc),
                }
            ),
            502,
        )


@app.get("/api/orders/<order_id>")
def proxy_order(order_id: str):
    try:
        order_payload = call_downstream(f"{ORDER_SERVICE_URL}/orders/{order_id}")
        return jsonify(
            {
                "service": SERVICE_NAME,
                "version": APP_VERSION,
                "downstream": order_payload,
            }
        )
    except requests.RequestException as exc:
        return (
            jsonify(
                {
                    "service": SERVICE_NAME,
                    "error": "order-service is unavailable",
                    "details": str(exc),
                }
            ),
            502,
        )


@app.get("/api/demo/<order_id>")
def aggregate_demo(order_id: str):
    try:
        hello_payload = call_downstream(f"{HELLO_SERVICE_URL}/hello")
        order_payload = call_downstream(f"{ORDER_SERVICE_URL}/orders/{order_id}")
        return jsonify(
            {
                "service": SERVICE_NAME,
                "version": APP_VERSION,
                "requestPath": "ingress -> gateway-service -> hello-service/order-service",
                "hello": hello_payload,
                "order": order_payload,
            }
        )
    except requests.RequestException as exc:
        return (
            jsonify(
                {
                    "service": SERVICE_NAME,
                    "error": "one or more downstream services are unavailable",
                    "details": str(exc),
                }
            ),
            502,
        )


@app.get("/health/live")
def liveness():
    return jsonify({"status": "alive", "service": SERVICE_NAME}), 200


@app.get("/health/ready")
def readiness():
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
