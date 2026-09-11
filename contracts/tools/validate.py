#!/usr/bin/env python3
"""Validate AutoCam contracts: schemas compile, examples/sequences/fixtures match, invalid cases fail."""

from __future__ import annotations

import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator
from jsonschema.exceptions import SchemaError, ValidationError
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT202012

ROOT = Path(__file__).resolve().parents[2]
CONTRACTS = ROOT / "contracts"
SCHEMAS = CONTRACTS / "schemas"
EXAMPLES = CONTRACTS / "golden" / "examples"
SEQUENCES = CONTRACTS / "golden" / "sequences"
FIXTURES = CONTRACTS / "golden" / "fixtures"
INVALID = CONTRACTS / "golden" / "invalid"

REQUIRED_SCHEMAS = [
    "OpenSessionRequest.schema.json",
    "CameraSession.schema.json",
    "CaptureParams.schema.json",
    "SetZoom.schema.json",
    "GuideUser.schema.json",
    "ViewfinderFrame.schema.json",
    "CompositionGuide.schema.json",
    "OverlayPrimitive.schema.json",
    "StillResult.schema.json",
    "ApplyCrop.schema.json",
    "VideoOptions.schema.json",
    "VideoSession.schema.json",
    "VideoClipResult.schema.json",
    "ZoomBlendProfile.schema.json",
    "DeviceProfile.schema.json",
    "FilterRecommendation.schema.json",
    "EngineEvent.schema.json",
    "CommandEnvelope.schema.json",
    "SetPhysicalLensHint.schema.json",
    "GoldenSequence.schema.json",
    "Fixture.schema.json",
    "_defs.schema.json",
]

CLOSED_OPS = [
    "openSession",
    "closeSession",
    "setCaptureParams",
    "setZoom",
    "setPhysicalLensHint",
    "guideUser",
    "captureStill",
    "startVideo",
    "stopVideo",
    "applyCrop",
    "loadDeviceProfile",
    "saveZoomCalibration",
]


def load_json(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8"))


def build_registry() -> Registry:
    registry = Registry()
    for path in sorted(SCHEMAS.glob("*.schema.json")):
        data = load_json(path)
        resource = Resource.from_contents(data, default_specification=DRAFT202012)
        registry = registry.with_resource(path.name, resource)
        schema_id = data.get("$id")
        if isinstance(schema_id, str):
            registry = registry.with_resource(schema_id, resource)
    alias = CONTRACTS / "command_envelope.schema.json"
    if alias.exists():
        data = load_json(alias)
        resource = Resource.from_contents(data, default_specification=DRAFT202012)
        registry = registry.with_resource(alias.name, resource)
        schema_id = data.get("$id")
        if isinstance(schema_id, str):
            registry = registry.with_resource(schema_id, resource)
    return registry


def validator_for(registry: Registry, schema_name: str) -> Draft202012Validator:
    schema = load_json(SCHEMAS / schema_name)
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema, registry=registry)


def fail(errors: list[str], msg: str) -> None:
    errors.append(msg)


def check_required_files(errors: list[str]) -> None:
    for name in REQUIRED_SCHEMAS:
        if not (SCHEMAS / name).exists():
            fail(errors, f"missing required schema {name}")
    alias = CONTRACTS / "command_envelope.schema.json"
    if not alias.exists():
        fail(errors, "missing contracts/command_envelope.schema.json")
    if not (CONTRACTS / "openapi.yaml").exists():
        fail(errors, "missing contracts/openapi.yaml")


def check_closed_ops(errors: list[str]) -> None:
    defs = load_json(SCHEMAS / "_defs.schema.json")
    ops = defs["$defs"]["op"]["enum"]
    if ops != CLOSED_OPS:
        fail(errors, f"op enum mismatch: {ops} != {CLOSED_OPS}")


def check_no_leica(errors: list[str]) -> None:
    needle = "lei" + "ca"
    for path in CONTRACTS.rglob("*"):
        if path.suffix.lower() not in {".json", ".yaml", ".yml", ".md"}:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        if needle in text.lower():
            fail(errors, f"forbidden trademark token in {path.relative_to(ROOT)}")


def check_examples(registry: Registry, errors: list[str]) -> None:
    if not EXAMPLES.is_dir():
        fail(errors, "missing golden/examples")
        return
    for path in sorted(EXAMPLES.glob("*.json")):
        schema_name = f"{path.stem}.schema.json"
        if not (SCHEMAS / schema_name).exists():
            fail(errors, f"no schema for example {path.name}")
            continue
        instance = load_json(path)
        try:
            validator_for(registry, schema_name).validate(instance)
        except (ValidationError, SchemaError) as exc:
            fail(errors, f"{path.relative_to(ROOT)}: {exc.message}")


def check_sequences(registry: Registry, errors: list[str]) -> None:
    seq_v = validator_for(registry, "GoldenSequence.schema.json")
    env_v = validator_for(registry, "CommandEnvelope.schema.json")
    expected = {
        "set_zoom.json",
        "guide_user_pan_zoom.json",
        "capture_apply_crop.json",
        "worked_example_uw_to_main.json",
    }
    found = {p.name for p in SEQUENCES.glob("*.json")}
    missing = expected - found
    if missing:
        fail(errors, f"missing golden sequences: {sorted(missing)}")
    for path in sorted(SEQUENCES.glob("*.json")):
        data = load_json(path)
        try:
            seq_v.validate(data)
        except ValidationError as exc:
            fail(errors, f"{path.relative_to(ROOT)}: {exc.message}")
            continue
        envelopes: list[object] = []
        if "send" in data:
            envelopes.append(data["send"])
        for step in data.get("steps") or []:
            if "send" in step:
                envelopes.append(step["send"])
        for env in envelopes:
            try:
                env_v.validate(env)
            except ValidationError as exc:
                fail(errors, f"{path.relative_to(ROOT)} envelope: {exc.message}")
        if path.name == "guide_user_pan_zoom.json":
            check_guide_trajectory(data, errors)


def check_guide_trajectory(data: dict, errors: list[str]) -> None:
    subject = data.get("subject0") or {}
    send = (data.get("send") or {}).get("body") or {}
    step = float(data.get("stepPerFrame") or 0.25)
    nx = float(subject["nx"])
    ny = float(subject["ny"])
    pan_nx = float(send["panNx"])
    pan_ny = float(send["panNy"])
    for point in data.get("expectSubjectTrajectory") or []:
        frame = int(point["frame"])
        expect_nx = nx + pan_nx * step * frame
        expect_ny = ny + pan_ny * step * frame
        if abs(float(point["nx"]) - expect_nx) > 1e-9 or abs(float(point["ny"]) - expect_ny) > 1e-9:
            fail(
                errors,
                "guide_user_pan_zoom.json trajectory "
                f"frame {frame}: got ({point['nx']}, {point['ny']}), "
                f"expected ({expect_nx}, {expect_ny})",
            )


def check_fixtures(registry: Registry, errors: list[str]) -> None:
    expected = {
        "still_object_table",
        "still_food",
        "still_building",
        "still_person",
    }
    found = {p.parent.name for p in FIXTURES.glob("*/fixture.json")}
    missing = expected - found
    if missing:
        fail(errors, f"missing fixtures: {sorted(missing)}")
    fixture_v = validator_for(registry, "Fixture.schema.json")
    for path in sorted(FIXTURES.glob("*/fixture.json")):
        data = load_json(path)
        try:
            fixture_v.validate(data)
        except ValidationError as exc:
            fail(errors, f"{path.relative_to(ROOT)}: {exc.message}")
        if data.get("id") != path.parent.name:
            fail(errors, f"{path.relative_to(ROOT)} id must equal directory name")


def check_invalid(registry: Registry, errors: list[str]) -> None:
    if not INVALID.is_dir():
        fail(errors, "missing golden/invalid")
        return
    files = list(INVALID.glob("*.json"))
    if not files:
        fail(errors, "golden/invalid must contain at least one negative fixture")
        return
    for path in sorted(files):
        schema_name = f"{path.name.split('.', 1)[0]}.schema.json"
        if not (SCHEMAS / schema_name).exists():
            fail(errors, f"invalid fixture {path.name} has no schema {schema_name}")
            continue
        instance = load_json(path)
        try:
            validator_for(registry, schema_name).validate(instance)
        except ValidationError:
            continue
        fail(errors, f"{path.relative_to(ROOT)} was expected to fail {schema_name}")


def main() -> int:
    errors: list[str] = []
    check_required_files(errors)
    check_closed_ops(errors)
    check_no_leica(errors)
    try:
        registry = build_registry()
        for name in REQUIRED_SCHEMAS:
            Draft202012Validator.check_schema(load_json(SCHEMAS / name))
        check_examples(registry, errors)
        check_sequences(registry, errors)
        check_fixtures(registry, errors)
        check_invalid(registry, errors)
    except SchemaError as exc:
        fail(errors, f"schema compile: {exc.message}")
        registry = None  # noqa: F841

    if errors:
        print(f"contracts: FAIL ({len(errors)})")
        for item in errors:
            print(f"  - {item}")
        return 1
    print("contracts: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
