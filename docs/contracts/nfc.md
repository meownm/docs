# NFC API Contract

## Endpoints

### POST `/nfc` (and `/api/nfc`)
**Purpose:** store NFC scan result + face photo.

**Request JSON (standard format):**
```json
{
  "passport": { "document_number": "123456789", "date_of_birth": "900101", "date_of_expiry": "300101" },
  "face_image_b64": "base64(jpeg)"
}
```

**Request JSON (raw NFC format):**
```json
{
  "mrz_keys": { "document_number": "123456789", "date_of_birth": "900101", "date_of_expiry": "310101" },
  "dg2_raw_b64": "base64(dg2_raw_bytes)",
  "dg1_raw_b64": "base64(dg1_raw_bytes)",
  "format": "raw"
}
```

**Notes:**
- MRZ data can be provided via `passport`, `mrz`, or `mrz_keys` (all accepted).
- Face image can be provided via `face_image_b64` (extracted JPEG/JP2) or `dg2_raw_b64` (raw DG2 from NFC chip).
- When using `dg2_raw_b64`, the server extracts the facial image (JPEG/JP2) from the ICAO 9303 DG2 biometric structure.
- `document_number` is normalized with trim + remove ALL whitespace (spaces removed) + uppercase.
- Dates inside MRZ are accepted as `YYMMDD`, `YYYYMMDD`, or `YYYY-MM-DD` and are normalized to `YYMMDD`.
- If `passport.mrz` exists, dates inside that object are normalized too.
- Invalid JP2 payloads (including missing decoder/conversion failure) return 422 with `expected JPEG or JP2 convertible to JPEG`.
- JPEG payloads remain valid even if a small tail follows the EOI marker (up to 64 bytes); unknown formats are rejected and not saved.
- `dg1_raw_b64` and `format` fields are accepted but not required (for forward compatibility).

**Response JSON (200):**
```json
{
  "scan_id": "uuid",
  "face_image_url": "/api/nfc/<scan_id>/face.jpg",
  "passport": { "document_number": "123456789", "date_of_birth": "900101", "date_of_expiry": "300101" }
}
```

**Errors (422):**
```json
{ "detail": "Invalid passport" }
```
```json
{ "detail": "Invalid face_image_b64: ..." }
```
```json
{ "detail": "Invalid face_image_b64: expected JPEG or JP2 convertible to JPEG" }
```
```json
{ "detail": "Invalid dg2_raw_b64: ..." }
```
```json
{ "detail": "Invalid dg2_raw_b64: could not extract face image from DG2" }
```

### GET `/nfc/{scan_id}/face.jpg` (and `/api/nfc/{scan_id}/face.jpg`)
Returns stored face image bytes (JPEG).

## SSE Contract

### Event: `nfc_scan_success`
Published on `/events` and `/api/events` after a successful NFC upload.

**Event data JSON:**
```json
{
  "type": "nfc_scan_success",
  "scan_id": "uuid",
  "face_image_url": "/api/nfc/<scan_id>/face.jpg",
  "passport": { "document_number": "123456789", "date_of_birth": "900101", "date_of_expiry": "300101" }
}
```
