# NFC API Contract

## Endpoints

### POST `/nfc` (and `/api/nfc`)
**Purpose:** store NFC scan result + face photo.

**Request JSON:**
```json
{
  "passport": { "document_number": "123456789", "date_of_birth": "900101", "date_of_expiry": "300101" },
  "face_image_b64": "base64(jpeg)"
}
```

**Notes:**
- `passport` must be a non-empty object.
- `face_image_b64` must be a non-empty base64 string of a JPEG (JP2 allowed only if convertible to JPEG).
- `document_number` is normalized with trim + remove whitespace + uppercase.
- Dates inside `passport` are accepted as `YYMMDD`, `YYYYMMDD`, or `YYYY-MM-DD` and are normalized to `YYMMDD`.
- If `passport.mrz` exists, dates inside that object are normalized too.
- Invalid JP2 payloads (including missing decoder/conversion failure) return 422 with `expected JPEG or JP2 convertible to JPEG`.
- JPEG payloads remain valid even if a small tail follows the EOI marker (up to 64 bytes); unknown formats are rejected and not saved.

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
