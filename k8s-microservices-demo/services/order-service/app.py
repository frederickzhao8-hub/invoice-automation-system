import os
import time

from flask import Flask, jsonify

app = Flask(__name__)

START_TIME = time.time()
STARTUP_DELAY_SECONDS = int(os.getenv("STARTUP_DELAY_SECONDS", "5"))
SERVICE_NAME = os.getenv("SERVICE_NAME", "order-service")
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
            "message": "Order service is running.",
        }
    )


@app.get("/orders/<order_id>")
def get_order(order_id: str):
    return jsonify(
        {
            "service": SERVICE_NAME,
            "version": APP_VERSION,
            "orderId": order_id,
            "status": "PROCESSING",
            "items": [
                {"sku": "SKU-100", "quantity": 2},
                {"sku": "SKU-200", "quantity": 1},
            ],
        }
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
