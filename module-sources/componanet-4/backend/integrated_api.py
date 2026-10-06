"""Private Spring Boot adapter. All visual detectors remain in the existing src/ files.
Run from Component 4 root: python -m uvicorn backend.integrated_api:app --host 127.0.0.1 --port 8001
"""
import base64
import hashlib
import hmac
import io
import json
import os
from pathlib import Path
import tempfile
import threading

from fastapi import FastAPI, HTTPException, Request
from starlette.concurrency import run_in_threadpool
from PIL import Image, ImageOps
from src.analysis_pipeline import run_analysis_pipeline, TEMPLATE_FILES
from src.ocr_validation import validate_ocr_payload

app = FastAPI(title="Integrated Component 4", version="step8-1")
_TOKEN = os.environ.get("ALTERATION_API_TOKEN", "")
if len(_TOKEN) < 32:
    raise RuntimeError("Set ALTERATION_API_TOKEN to the shared random token (at least 32 characters).")
_GATE = threading.Lock()
MAX_BODY = 30 * 1024 * 1024
MAX_IMAGE = 20 * 1024 * 1024
Image.MAX_IMAGE_PIXELS = 12_000_000


def authorize(request):
    supplied = request.headers.get("X-Analysis-Token", "")
    if not hmac.compare_digest(supplied, _TOKEN):
        raise HTTPException(401, "Invalid analysis service token")


@app.get("/health")
def health(request: Request):
    authorize(request)
    return {"status": "OK", "supportedTypes": sorted(TEMPLATE_FILES),
            "templatesPresent": all(Path(p).is_file() for p in TEMPLATE_FILES.values())}


def analyse_payload(payload):
    kind = payload.get("documentType")
    if kind not in TEMPLATE_FILES:
        return {"status": "NOT_SUPPORTED", "message": "No configured analysis template for this document type."}
    quality = payload.get("quality", {})
    if quality.get("status") != "PASSED" or not all(
            quality.get(k) is True for k in ("blurPassed", "brightnessPassed", "resolutionPassed")):
        raise HTTPException(422, "All capture quality checks must have passed")
    try:
        raw = base64.b64decode(payload["imageBase64"], validate=True)
    except (KeyError, ValueError, TypeError):
        raise HTTPException(422, "Invalid image encoding")
    if not raw or len(raw) > MAX_IMAGE:
        raise HTTPException(413, "Image size exceeds secure capture limit")
    digest = hashlib.sha256(raw).hexdigest()
    if not hmac.compare_digest(digest, str(payload.get("imageSha256", "")).lower()):
        raise HTTPException(422, "Verified image hash does not match")
    ocr = payload.get("ocr")
    if not isinstance(ocr, dict) or not isinstance(ocr.get("fullText"), str):
        raise HTTPException(422, "Invalid OCR object")
    if not isinstance(ocr.get("lines"), list) or len(ocr["lines"]) > 5000:
        raise HTTPException(422, "Invalid OCR lines")
    if not isinstance(ocr.get("fields"), dict):
        raise HTTPException(422, "Invalid OCR fields")
    if len(json.dumps(ocr)) > 1_000_000:
        raise HTTPException(413, "OCR payload exceeds limit")
    with tempfile.TemporaryDirectory(prefix="leasing-analysis-") as tmp:
        work = Path(tmp)
        # Original bytes are used for hash checks; only a derived working image is oriented.
        with Image.open(io.BytesIO(raw)) as original:
            if original.width * original.height > 12_000_000:
                raise HTTPException(413, "Image pixel count exceeds capture limit")
            with ImageOps.exif_transpose(original) as oriented:
                if ocr.get("imageWidth") != oriented.width or ocr.get("imageHeight") != oriented.height:
                    raise HTTPException(422, "OCR coordinate dimensions do not match the upright image")
                if ocr.get("coordinateSpace") != "EXIF_UPRIGHT_FULL_IMAGE":
                    raise HTTPException(422, "OCR coordinate space mismatch")
                image_path = work / "upright.png"
                oriented.convert("RGB").save(image_path)
        validation = validate_ocr_payload(ocr, kind, image_path)
        output = work / "analysis"
        result = run_analysis_pipeline(image_path, kind, output, ocr_validation=validation)
        if result.get("status") != "COMPLETE":
            raise HTTPException(502, "Analysis pipeline did not complete")
        result["imageSha256"] = digest
        result["ocr"] = ocr
        result["ocr_validation"] = validation
        result["algorithmVersion"] = "component4-step8-1"
        # Return bytes, never expose internal filesystem paths or unprotected image URLs.
        result["highlightedImageBase64"] = base64.b64encode(
            (output / "highlighted_suspicious_areas.png").read_bytes()).decode("ascii")
        result["normalizedImageBase64"] = base64.b64encode(
            (output / "normalized" / "document.png").read_bytes()).decode("ascii")
        result["message"] = ("Possible alteration screening only. Manual review determines the final decision. "
                             "OCR candidates and template alignment must be checked by the reviewer.")
        # Remove temporary filesystem paths from nested diagnostic metadata.
        def sanitize(value):
            if isinstance(value, dict):
                return {k: sanitize(v) for k, v in value.items()}
            if isinstance(value, list):
                return [sanitize(v) for v in value]
            if isinstance(value, str) and str(work) in value:
                return Path(value).name
            return value
        return sanitize(result)


@app.post("/analyze")
async def analyze(request: Request):
    authorize(request)
    body = bytearray()
    async for chunk in request.stream():
        body.extend(chunk)
        if len(body) > MAX_BODY:
            raise HTTPException(413, "Request exceeds limit")
    try:
        payload = json.loads(body)
    except (ValueError, UnicodeError):
        raise HTTPException(400, "Invalid JSON")
    if not isinstance(payload, dict):
        raise HTTPException(400, "Expected JSON object")
    if not _GATE.acquire(blocking=False):
        raise HTTPException(409, "Another document is being analysed; retry after it finishes")
    try:
        return await run_in_threadpool(analyse_payload, payload)
    except HTTPException:
        raise
    except Exception:
        # Avoid returning OCR, document contents, or local paths in errors.
        raise HTTPException(502, "Component 4 analysis failed; check template/configuration and retry")
    finally:
        _GATE.release()
