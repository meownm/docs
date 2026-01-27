import logging

from app.logging_setup import DEFAULT_REQUEST_ID, log_event


def test_log_event_injects_request_id_for_control_plane(caplog):
    logger = logging.getLogger("test.control")
    with caplog.at_level(logging.INFO):
        log_event(logger, "hello", plane="control", request_id=None)

    assert caplog.records
    record = caplog.records[-1]
    assert record.plane == "control"
    assert record.request_id == DEFAULT_REQUEST_ID


def test_log_event_injects_request_id_for_data_plane(caplog):
    logger = logging.getLogger("test.data")
    with caplog.at_level(logging.INFO):
        log_event(logger, "world", plane="data", request_id="req-123")

    record = caplog.records[-1]
    assert record.plane == "data"
    assert record.request_id == "req-123"


def test_log_event_prefers_extra_request_id(caplog):
    logger = logging.getLogger("test.extra")
    with caplog.at_level(logging.INFO):
        log_event(
            logger,
            "payload",
            plane="data",
            request_id="req-ignored",
            extra={"request_id": "req-extra"},
        )

    record = caplog.records[-1]
    assert record.request_id == "req-extra"
