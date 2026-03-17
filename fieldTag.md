**FIELDTAG**

Industrial Field Photo Documentation App

**MVP Architecture & Design Specification**

| **Platform**<br><br>Android (Kotlin / Jetpack Compose)<br><br>**Connectivity Model**<br><br>Offline-first, no cloud required for MVP<br><br>**Target Users**<br><br>1-10 person industrial / construction field teams | **Primary Deliverable**<br><br>PDF reports exported locally, shared via Android share sheet<br><br>**Design Priority**<br><br>Reliability and UX above all else<br><br>**Version**<br><br>1.0 MVP Specification |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |

# **1\. Problem & Market Context**

Field technicians across every industrial and construction vertical rely on smartphones to document site work, but ad-hoc camera roll workflows create costly chaos: photos mixed with personal images, lost context, hours spent labeling and assembling reports, and broken workflows when connectivity fails.

**The Core Pain Loop**

Technician takes photos → saves to camera roll → returns to office → spends 1-2 hours sorting, renaming, emailing → reviewer can't tell which photo belongs to which asset → re-visit required. This loop repeats on every job.

## **1.1 Photo Types Encountered in the Field**

The app must handle the full spectrum of industrial field photography:

| **Photo Type**              | **Purpose**                                     | **OCR / Scan Challenge**                                       |
| --------------------------- | ----------------------------------------------- | -------------------------------------------------------------- |
| Asset tag / nameplate       | Link photo set to a specific piece of equipment | Metal corrosion, glare, stencil paint, varied fonts            |
| Barcode / QR code           | Fast asset identification where tags exist      | Distance, angle, low light - but highly reliable when readable |
| Equipment wide shot         | Show asset in context / location on site        | None - but requires geo/time metadata to associate correctly   |
| Equipment detail / defect   | Document condition, damage, or issue close-up   | None - scale reference often missing                           |
| Meter / gauge reading       | Capture exact value without transcription error | Analog dial vs digital display - ML Kit handles digital well   |
| Before / during / after set | Prove baseline, work in progress, completion    | Temporal grouping critical - photos may span hours             |
| Safety / environment        | Access routes, hazards, PPE, site conditions    | Wide variety - context tags more important than OCR            |
| Wiring / terminations       | Document hidden work before cover-up            | Dense visual - no OCR needed, notes field critical             |
| Serial number plate         | Warranty, parts ordering, compliance records    | Often embossed or recessed - high glare, low contrast          |

## **1.2 Why Existing Tools Fall Short for Small Teams**

| **Tool Category**           | **Example**        | **Gap for Small Industrial Teams**                                                 |
| --------------------------- | ------------------ | ---------------------------------------------------------------------------------- |
| Construction suites         | Procore, Fieldwire | Complex setup, enterprise pricing, mobile UX burdened by full PM scope             |
| Photo-first contractor apps | CompanyCam         | \$79+/mo minimum (3 users), per-user model penalizes tiny crews                    |
| CMMS platforms              | MaintainX, UpKeep  | \$75+/user/mo for full mobile tier - overkill when only photos are needed          |
| Field ops platforms         | SiteCapture        | \$110+/mo base, assumes formalized processes and dedicated admins                  |
| Niche work-photo apps       | Work Photo Pro     | Closest fit but still per-user SaaS; no industrial tag/item pair as core primitive |
| Default camera roll         | (Built-in)         | Zero structure, hours of manual work, photos lost, no offline-sync story           |

# **2\. Data Model**

Four-level hierarchy reflecting the updated workflow: a Project contains one P&ID document, which yields a list of Instruments, each of which accumulates Photos and Videos taken on site.

**Hierarchy**

Project → PidDocument (the imported P&ID PDF) → Instrument (each parsed tag) → Media (photos + videos)

Key change from original design: Instruments are no longer discovered by photographing tags in the field. They are pre-populated by parsing the P&ID PDF before the site visit. The field tech's job is to find each instrument on the list, photograph it, and mark it complete.

## **2.1 Entity Definitions**

**Project**

| **Field**         | **Type**   | **Notes**                                             |
| ----------------- | ---------- | ----------------------------------------------------- |
| id                | UUID       | Primary key                                           |
| name              | String     | User-assigned - typically the job number or site name |
| created_at        | Timestamp  |                                                       |
| location_name     | String?    | Optional - user-typed or reverse-geocoded             |
| gps_lat / gps_lng | Double?    | Captured at project creation if location permitted    |
| status            | Enum       | ACTIVE \| COMPLETE \| ARCHIVED                        |
| notes             | String?    | Free-text field for the overall job                   |
| export_last_at    | Timestamp? | When the project was last exported                    |

**PidDocument**

Represents the imported P&ID PDF. One project can have multiple P&ID sheets (each PDF page is one sheet). The parsed instrument list is derived from this entity.

| **Field**        | **Type**   | **Notes**                                                                                          |
| ---------------- | ---------- | -------------------------------------------------------------------------------------------------- |
| id               | UUID       | Primary key                                                                                        |
| project_id       | UUID FK    | Foreign key → Project                                                                              |
| file_path        | String     | Path to original PDF in app internal storage                                                       |
| file_name        | String     | Original filename - shown in UI for reference                                                      |
| page_count       | Int        | Number of pages / sheets in the PDF                                                                |
| parse_status     | Enum       | PENDING \| PROCESSING \| COMPLETE \| FAILED \| NEEDS_REVIEW                                        |
| parsed_at        | Timestamp? | When parsing completed                                                                             |
| instrument_count | Int        | Total instruments extracted - shown on project home                                                |
| raw_text_json    | String     | Full extracted text with bounding box coordinates, stored as JSON - source of truth for re-parsing |
| parse_warnings   | String?    | JSON array of warnings - e.g. pages with low text density, possible scanned content                |

**Instrument**

One row per instrument tag extracted from the P&ID. This is the central entity the field tech works against on site.

| **Field**       | **Type**   | **Notes**                                                                                             |
| --------------- | ---------- | ----------------------------------------------------------------------------------------------------- |
| id              | UUID       | Primary key                                                                                           |
| project_id      | UUID FK    | Denormalized for fast project-level queries                                                           |
| pid_document_id | UUID FK    | Foreign key → PidDocument                                                                             |
| pid_page_number | Int        | Which PDF page this instrument appears on                                                             |
| tag_id          | String     | The canonical tag - e.g. 'LIT-5219', 'PV-5218'. Source of truth from P&ID text.                       |
| tag_prefix      | String     | ISA function letters - e.g. 'LIT', 'PV', 'FIT'. Parsed from tag_id.                                   |
| tag_number      | String     | Loop/instrument number - e.g. '5219'. Parsed from tag_id.                                             |
| instrument_type | String?    | Human-readable type derived from ISA prefix - e.g. 'Level Indicating Transmitter'                     |
| pid_x / pid_y   | Float?     | Normalised coordinates (0.0-1.0) of tag position on the P&ID page - for dot overlay on diagram viewer |
| field_status    | Enum       | NOT_STARTED \| IN_PROGRESS \| COMPLETE \| CANNOT_LOCATE                                               |
| notes           | String?    | Tech notes added on site                                                                              |
| sort_order      | Int        | Order within project - default is P&ID page order, top-to-bottom                                      |
| created_at      | Timestamp  | When this instrument record was created (during parse)                                                |
| completed_at    | Timestamp? | When field_status was set to COMPLETE                                                                 |

**Media**

Photos and videos attached to an instrument. Replaces the previous Photo entity - videos are now a first-class media type.

| **Field**         | **Type**  | **Notes**                                                                       |
| ----------------- | --------- | ------------------------------------------------------------------------------- |
| id                | UUID      | Primary key                                                                     |
| instrument_id     | UUID?     | Nullable - null means media is in project-level ungrouped bucket                |
| project_id        | UUID FK   | Denormalized for fast project-level queries                                     |
| type              | Enum      | PHOTO \| VIDEO                                                                  |
| role              | Enum      | OVERVIEW \| DETAIL \| NAMEPLATE \| BEFORE \| DURING \| AFTER \| SAFETY \| OTHER |
| file_path         | String    | Path in app internal storage                                                    |
| thumbnail_path    | String    | Pre-generated thumbnail for list rendering (first frame for video)              |
| duration_ms       | Int?      | Video duration - null for photos                                                |
| captured_at       | Timestamp | From EXIF if available, fallback to file modified time                          |
| exif_missing      | Boolean   | Warning flag - timestamp reliability indicator                                  |
| gps_lat / gps_lng | Double?   | From EXIF or live GPS at capture time                                           |
| source            | Enum      | LIVE_CAPTURE \| BATCH_IMPORT                                                    |
| notes             | String?   | Per-media note or voice-to-text annotation                                      |
| sort_order        | Int       | User-controlled ordering within an instrument                                   |

## **2.2 The Ungrouped Bucket**

Every project retains an ungrouped bucket - media with instrument_id = null. This covers media captured before an instrument is selected, or imported photos that couldn't be matched to an instrument. Shown as a count badge on the project home screen.

# **3\. Core Feature Modules**

The workflow has three distinct phases: (1) Import and parse the P&ID PDF before going to site, (2) Use the instrument list on site to capture media against each tag, (3) Export everything after the job. Each phase is designed to work fully offline.

## **3.1 P&ID Import & Parsing**

This is the most technically critical module. The user imports a P&ID PDF - the app extracts all instrument tags from the text layer, presents them for review, and builds the instrument list that drives the entire field workflow.

**Why text layer extraction - not image OCR**

Your P&IDs have a text layer (the PDF has selectable text, as visible from the uploaded example showing tags like 'LIT 5219', 'PV 5218', 'FIT 5221'). This means we can extract text directly from the PDF without OCR - it is faster, 100% accurate on the text that exists, and works on any device without ML models. Image-based P&ID OCR (YOLO + computer vision) is only needed when the PDF is a scanned raster image with no text layer. That is a separate, significantly harder problem. For MVP: target text-layer PDFs only. Add scanned PDF support later as a premium feature.

**P&ID Parsing Pipeline**

Step 1 - Ingest PDF: User selects PDF via Android SAF file picker (gallery, USB, Drive, Dropbox all supported). App copies to internal storage. Original is preserved. Step 2 - Text extraction: Use PdfBox Android (Apache, free, offline) to extract all text with bounding box coordinates per page. Each text run has: content, page number, x/y position, width/height. Step 3 - ISA tag detection: Apply regex pattern to all extracted text strings to find instrument tags. Pattern matches ISA 5.1 convention (see 3.2 below). Each match yields a candidate tag. Step 4 - Deduplication: Some tags appear twice (bubble + label). Deduplicate by tag_id - keep the one with the highest confidence position score (inside a circle or box bounds = instrument bubble). Step 5 - Review screen: Present all detected tags as a flat list, grouped by P&ID page. User can: confirm all, delete false positives, manually add missing tags, edit any tag ID. Step 6 - Commit: Confirmed tags become Instrument records in Room DB, linked to the PidDocument. sort_order is set by page number then Y-position (top to bottom, left to right).

## **3.2 ISA 5.1 Tag Recognition - Regex Pattern**

Instrument tags on P&IDs follow the ISA 5.1 standard. The pattern is: one or more capital letters (function code) followed by a hyphen or space, followed by a number (loop number), with an optional suffix letter. Examples from your uploaded P&ID: LIT-5219, PV-5218, FIT-5221, LIC-5219, TIT-5234, HS-5222, DT-5220.

**ISA Tag Regex Pattern**

Primary pattern: ^\[A-Z\]{1,5}\[-\\s\]?\\d{3,6}\[A-Z\]?\$ Breakdown: \[A-Z\]{1,5} - 1 to 5 capital letters (function code: LIT, PV, FIT, TIT, HS, etc.) \[-\\s\]? - optional hyphen or space separator \\d{3,6} - 3 to 6 digit loop/instrument number \[A-Z\]? - optional suffix letter (A, B, C for multiple instances) Common ISA prefixes the parser will recognise: Flow: FI, FIT, FIC, FE, FV, FC, FF Level: LI, LIT, LIC, LV, LC Pressure: PI, PIT, PIC, PV, PT Temperature: TI, TIT, TIC, TE, TV, TT Analysis: AI, AIT Discrete/Digital: DI, DT, DE Hand/Manual: HS, HC, HV Vibration: VI, VS Non-instrument text to exclude (false positive filter): Pipe specs: '4x3', '8x6', '6-BL-245' (contain x or start with digit) Equipment tags: '542-10-18' (no letter prefix, or equipment naming convention) Valve tags: 'V-25', 'V-49' (single V prefix - these are hand valves, not instruments - include as separate Valve entity or exclude per user preference) Drawing refs: 'P2', 'S1', 'F.O.' (too short, single letters)

## **3.3 P&ID Diagram Viewer**

After parsing, the user can view the P&ID with instrument locations overlaid as interactive dots. This is the navigation tool for the field - tech taps a dot on the diagram to open that instrument's capture screen.

**Diagram Viewer Implementation**

Render: Display the PDF page as a high-resolution bitmap (rasterise at 2x screen density using PdfRenderer - built into Android, no library needed). Overlay: Draw coloured dots at pid_x / pid_y coordinates for each instrument: Grey dot = NOT_STARTED Yellow dot = IN_PROGRESS (has some media but not marked complete) Green dot = COMPLETE Red dot = CANNOT_LOCATE Interaction: Pan and pinch-zoom (standard Compose gesture handling). Tap a dot → bottom sheet slides up showing tag ID, instrument type, media count, and 'Open' button. Coordinate storage: pid_x / pid_y are stored as normalised floats (0.0-1.0 relative to page dimensions). This makes them resolution-independent - the overlay works regardless of zoom level or device screen size. Fallback: If a tag could not be spatially located (parsed from text but coordinates unknown), it appears in the list view only - not on the diagram. User can manually pin it by long-pressing on the diagram.

## **3.4 On-Site Instrument Capture**

The tech is on site with the instrument list already populated. The workflow is: find the instrument → tap it → capture media → mark complete → move to next.

**Field Capture Flow**

1\. Tech opens project → sees instrument list (or diagram view) 2. Taps an instrument (e.g. 'LIT-5219 - Level Indicating Transmitter') 3. Instrument detail screen opens: tag ID, type, completion status, media grid (empty initially) 4. Taps camera FAB → in-app CameraX screen opens, already associated to this instrument 5. Takes photos and/or videos - all auto-saved to this instrument, never to gallery 6. Each capture: user optionally assigns a role (Overview / Detail / Nameplate / Before / After) - default is 'Detail', role picker is one tap 7. When done: taps 'Mark Complete' - instrument status → COMPLETE, green dot on diagram 8. If instrument physically not found: taps 'Cannot Locate' - status → CANNOT_LOCATE, red dot 9. Back to instrument list - next item is highlighted

Note: on-device ML Kit OCR is retained as a supplementary tool. If the tech photographs a nameplate and taps 'Read Tag', OCR runs and fills a notes field with the extracted text. It no longer drives asset creation - the P&ID tag is already the source of truth. OCR is now a verification assist, not the primary workflow.

## **3.5 Project Management**

- Home screen: project list with progress indicator per project (e.g. '14/32 instruments complete')
- Create project: name + optional notes → immediately prompts to import P&ID PDF
- Project detail: two tabs - LIST VIEW (instruments sorted by page/position, filterable by status) and DIAGRAM VIEW (P&ID with dot overlay)
- Progress summary: count of complete / in-progress / not-started / cannot-locate - shown at top of project detail
- Archive project: preserves all data and exports
- No account required for MVP - fully local

# **4\. UX Design Principles**

These are constraints, not guidelines - every screen must satisfy them before shipping.

| **Principle**                          | **Implementation Rule**                                                                                                                                             |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| OCR is always a suggestion             | No OCR result is ever committed without a visible user confirmation tap. Zero silent auto-commits.                                                                  |
| 'Tag not found' is a first-class state | The button to mark a tag as unfindable is always visible and prominent - never buried in a menu. It creates a valid Asset, not an error.                            |
| Nothing is lost on crash               | Every photo write is transactional: file saved to disk before DB record written. Room WAL mode enabled. On relaunch, any orphaned photos are surfaced for recovery. |
| No login wall                          | App is fully functional without an account. Optional sync/backup introduced later - never as a blocker.                                                             |
| Batch review is tactile                | Group review cards are swipeable. Merge = drag one card onto another. Split = long-press a photo and drag out. No modal dialogs for these operations.               |
| Import sources are equal               | Gallery, USB/folder, and cloud storage (Drive/Dropbox via SAF) all feed the same import pipeline with identical UX. No import source is a second-class citizen.     |
| Offline is the default                 | The app never shows a spinner waiting for network. All actions complete locally first. If cloud sync is added later, it is a background-only operation.             |
| Unassigned photos are visible          | The Ungrouped bucket is shown on the project home screen with a count badge. It is never hidden or auto-dismissed.                                                  |
| PDF export is always available         | Export button is accessible from the project detail screen regardless of whether all assets have confirmed tags. Partial jobs export cleanly.                       |
| Large tap targets on site              | All primary action buttons minimum 56dp. Workers use gloves. No precision-required gestures for critical actions.                                                   |

## **4.1 Screen Map**

**Navigation Structure**

Home (Project List) └── Create Project └── Project Detail ├── P&ID Import │ ├── File Picker (PDF via SAF) │ ├── Parse Progress Screen │ └── Tag Review Screen (confirm / edit / add / delete instruments) ├── List View (instruments, filterable by status) │ └── Instrument Detail │ ├── Live Camera (CameraX) │ │ └── Role Picker Sheet (Overview / Detail / Nameplate / etc.) │ ├── Media Viewer / Reorder │ └── Notes + Status (Complete / Cannot Locate) ├── Diagram View (P&ID page with dot overlay) │ └── Instrument Bottom Sheet → opens Instrument Detail ├── Ungrouped Media Bucket │ └── Assign to existing instrument └── Export └── Format picker → PDF / CSV / JSON → Android Share Sheet

# **5\. Technology Stack**

## **5.1 Android Application**

| **Layer**               | **Technology**                 | **Rationale**                                                                                                                        |
| ----------------------- | ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ |
| Language                | Kotlin                         | Modern Android standard; full Jetpack support; coroutines for async PDF parsing                                                      |
| UI Framework            | Jetpack Compose                | Faster iteration, gesture handling (pan/zoom on P&ID viewer), better state management                                                |
| Camera                  | CameraX                        | Lifecycle-aware, reliable across OEMs, ImageCapture API for photo + video                                                            |
| PDF text extraction     | PdfBox Android (Apache)        | Free, offline, extracts text with bounding box coordinates from text-layer PDFs - this is the primary instrument discovery mechanism |
| PDF page rendering      | Android PdfRenderer (built-in) | Rasterise P&ID pages to Bitmap for the diagram viewer - no library, no license, built into Android 5+                                |
| OCR (supplementary)     | ML Kit Text Recognition v2     | Used for nameplate verification assist on site - no longer the primary tag discovery method. Optional feature.                       |
| Barcode (supplementary) | ML Kit Barcode Scanning        | Same SDK - for sites that use QR/barcode asset tags alongside or instead of P&IDs                                                    |
| Local database          | Room (SQLite)                  | Offline-first, typed queries, WAL mode for crash safety, migration versioning                                                        |
| Image / video storage   | App-scoped internal storage    | Media never appears in gallery; scoped storage API; no external permissions on Android 10+                                           |
| File import             | Storage Access Framework (SAF) | System picker for PDF, gallery, USB, cloud providers - one uniform API                                                               |
| Async / concurrency     | Kotlin Coroutines + Flow       | PDF parsing, OCR, DB writes all non-blocking on IO dispatcher                                                                        |
| Dependency injection    | Hilt                           | Clean separation of PidParser, InstrumentRepository, MediaRepository                                                                 |
| Image / video display   | Coil                           | Kotlin-native, Compose-compatible, thumbnail caching, video frame extraction                                                         |
| PDF export              | Android PdfDocument + Canvas   | No AGPL licensing issues, no APK weight, fully offline. Used for final job report export.                                            |
| Navigation              | Compose Navigation             | Type-safe, back stack correct, handles deep link into instrument detail from notification                                            |

## **5.2 No Backend Required for MVP**

All data is stored locally on-device. The app ships with zero server dependency. This is a deliberate architectural decision for three reasons:

- Industrial sites commonly have no connectivity - offline-first is not a feature, it is the baseline requirement
- No backend means no authentication, no API keys, no server costs, no outage risk - dramatically simpler MVP to build and maintain
- The export story (PDF via Android share sheet) covers the delivery workflow without cloud sync

**Future Cloud Sync - Design Constraint**

When cloud sync is added in a future version, it must be designed as a background-only, additive layer. The local Room database remains the source of truth at all times. Sync is eventually consistent. The app never blocks on sync status. Recommended approach: Firebase Firestore with offline persistence enabled, or Supabase (Postgres) with a sync queue. Decision deferred to post-MVP.

## **5.3 PDF Export Architecture**

PDF is generated entirely on-device using Android's native PdfDocument API. The output is written to a temp file in app cache, then delivered via FileProvider URI to the Android share sheet - compatible with email, Drive, Dropbox, WhatsApp, or any app the user has installed.

PDF report structure per project:

- Cover page: project name, date, location, total asset count, total photo count, export timestamp
- Asset pages: one section per asset - tag ID (or 'Tag Not Found'), asset notes, tag photo, then item photos in 2-column grid
- Ungrouped section: photos not assigned to any asset, shown at end with timestamp and any notes
- Photo metadata: each photo shows capture time and role label (Before / After / Tag / etc.)

**PDF Performance Note**

Photos must be downsampled before writing to PDF - full-resolution images from modern phones (12MP+) will produce unusable file sizes. Target 1200px on the long edge for report photos, 400px for thumbnails. Use Bitmap.createScaledBitmap() with inSampleSize calculation before Canvas.drawBitmap(). A 50-photo report should produce a PDF under 15MB.

# **6\. Reliability Design**

Reliability is the #1 product requirement. A field tech losing photos mid-job has real financial and reputational consequences. Every decision below exists to prevent data loss.

## **6.1 Crash-Safe Photo Write**

The write sequence for every photo is strictly ordered to ensure nothing is lost if the app crashes, the battery dies, or the OS kills the process:

**Write Sequence (must not be reordered)**

1\. Write image bytes to disk (app internal storage) → fsync() 2. Write thumbnail to disk → fsync() 3. Insert Photo record into Room database (single transaction) 4. Only now: dismiss the capture loading indicator and return to camera On next app launch: scan for image files with no corresponding DB record → surface as 'Recovered Photos' in Ungrouped bucket.

## **6.2 Room Database Configuration**

- WAL (Write-Ahead Logging) mode enabled - concurrent reads during writes, crash recovery to last committed transaction
- All migrations versioned and tested - no destructive schema changes without explicit migration
- Room database backed up to Android auto-backup (cloud backup if user has Google account) - no action required from user
- Foreign key constraints enforced - orphaned photos cannot exist in DB without an asset reference being valid

## **6.3 Batch Import Resilience**

- Import runs in a WorkManager worker - survives app backgrounding and device rotation
- Progress persisted to DB at each step - if interrupted, import resumes from last checkpoint on relaunch
- Original photos are never moved or deleted during import - only copies are made into app storage
- If OCR fails on any photo, import continues - failure is logged and shown in review screen, never silently discarded

## **6.4 Storage Management**

A 50-job project history with 20 photos each at 1.5MB average = ~1.5GB. This is a real concern on mid-range Android devices. The app handles this proactively:

- Storage usage displayed on settings screen - total size, per-project breakdown
- Archive project compresses thumbnails to 200px (originals retained) - typical 40% size reduction
- Export + delete workflow: user can export a project to PDF, verify it, then delete the project's photos from app storage - PDF becomes the archive
- No automatic deletion ever - user controls all cleanup

# **7\. MVP Scope & Build Order**

Ship in this order. Each phase is independently useful and testable with real field techs.

| **Phase**                             | **Features**                                                                                                                                                                                                              | **Definition of Done**                                                                                              |
| ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| Phase 1 P&ID Import & Instrument List | Project create/list, PDF import via SAF, PdfBox text extraction, ISA regex tag detection, tag review screen (confirm/edit/add/delete), Instrument records in Room DB, instrument list view with status badges             | User can import a P&ID PDF, review extracted tags, and have a clean instrument list ready before going to site      |
| Phase 2 Diagram Viewer                | PdfRenderer page rasterisation, pan/zoom Compose gesture handling, status dot overlay (grey/yellow/green/red), tap dot → instrument bottom sheet, manual pin for unlocated tags                                           | User can navigate the P&ID visually on their phone and see which instruments are complete                           |
| Phase 3 On-Site Capture               | CameraX in-app camera scoped to instrument, photo + video capture, role picker, Mark Complete / Cannot Locate actions, progress summary on project screen, ungrouped media bucket                                         | Tech can go to site, open each instrument, capture media, and mark completion - entire workflow without camera roll |
| Phase 4 Export                        | PDF report generation (cover page, per-instrument sections with media grid, cannot-locate summary), CSV export (instrument list with status and media counts), JSON export (full structured data), Android share sheet    | Tech can export a complete field report immediately after the job in any required format                            |
| Phase 5 Polish & Batch Import         | Batch photo import with time-gap clustering (for techs who used camera roll first), storage management, recovered media on launch, voice-to-text notes, media reordering, scanned P&ID warning with manual entry fallback | App passes 2-week field trial with zero data loss and handles the camera-roll-first legacy workflow                 |

## **7.1 Explicitly Out of Scope for MVP**

- User accounts or authentication
- Cloud sync or multi-device access
- Multi-user collaboration on same project
- Scanned / raster P&ID parsing (no text layer) - this requires YOLO + OCR pipeline, significant ML work
- P&ID connectivity / line tracing (which instruments are connected to which pipes)
- iOS version
- AI-generated instrument descriptions or summaries
- Annotation / markup tools on the P&ID diagram
- Custom report templates
- Integration with CMMS, ERP, or any external system
- Push notifications

## **7.2 Key Technical Risks & Mitigations**

| **Risk**                                                | **Likelihood**                                         | **Mitigation**                                                                                                                                                                                                                                 |
| ------------------------------------------------------- | ------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| P&ID PDF has no text layer (raster/scanned)             | Medium - older drawings, scanned revisions             | Detect at import: check PdfBox text output - if < 10 text tokens per page, show warning: 'This PDF may be a scanned image. Tag extraction requires a text-layer PDF.' Prompt manual entry. Add scanned PDF support as a later premium feature. |
| ISA regex misses site-specific tag formats              | Medium - non-standard naming at some facilities        | Review screen allows manual add/edit. Parser confidence shown per tag. Allow user to save custom tag patterns per project for re-use.                                                                                                          |
| P&ID coordinate extraction unreliable for dot placement | Medium - PDF coordinate systems vary by authoring tool | Treat pid_x/pid_y as best-effort. Tags without reliable coordinates excluded from diagram view, shown in list only. Manual pin gesture always available.                                                                                       |
| PDF parsing performance on large multi-page P&IDs       | Low-medium - large P&IDs can be 50+ pages              | Run PdfBox parsing in WorkManager background job with progress bar. Chunked page processing. Cancel/resume supported.                                                                                                                          |
| PdfRenderer OOM on high-res P&ID pages                  | Low - P&IDs are often large format (A0/E-size)         | Cap render resolution at 2x screen density. For very large pages, tile rendering: render visible viewport only, load adjacent tiles on scroll.                                                                                                 |
| Media write crash during capture                        | Low - same risk as before                              | File to disk → fsync → DB record. Orphaned media recovered on next launch.                                                                                                                                                                     |

# **8\. Future Scaling Considerations**

These are not MVP concerns, but the architecture above is designed not to foreclose them. Decisions made now that keep these options open:

| **Future Feature**              | **Why the MVP Architecture Supports It**                                                                                                             |
| ------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Cloud sync / multi-device       | Room as local source of truth + UUIDs as primary keys = clean sync story. Add a sync layer without touching the core data model.                     |
| Multi-user / team projects      | Project and Asset entities have no user ownership baked in. Adding a user_id and shared_with\[\] field is a non-breaking migration.                  |
| iOS version                     | All business logic (OCR, clustering, PDF) can be extracted to shared Kotlin Multiplatform modules. Compose Multiplatform for UI is a viable path.    |
| Guided photo checklists         | Asset entity has asset_type field already. Checklist templates keyed by asset_type slot in without schema changes.                                   |
| CMMS / ERP integration          | tag_id_confirmed is the canonical key that maps to asset records in any external system. Export as JSON alongside PDF - same data, different format. |
| AI photo descriptions           | Photo entity has a notes field and a role enum. An AI-generated description is just another notes write - the schema already accommodates it.        |
| Custom report templates         | PDF generation is isolated in a single ExportService class. Template selection is a parameter to that service - no architectural change needed.      |
| GPS-based auto-project grouping | gps_lat/gps_lng already captured on Project and Photo. Clustering by location proximity uses the same algorithm as time-gap clustering.              |

**FieldTag MVP Architecture Specification**

Prepared March 2026 · All decisions reflect offline-first, reliability-first design constraints

# **9\. Backend Architecture**

The MVP app runs fully offline with no backend required. The backend exists solely to power optional premium features - cloud sync and AI tagging. It is additive and never a dependency for core functionality.

## **9.1 Backend Recommendation: Supabase**

Supabase is the correct choice for this app over Firebase for three concrete reasons:

| **Dimension**          | **Supabase**                                                    | **Firebase**                                                                   | **Winner** |
| ---------------------- | --------------------------------------------------------------- | ------------------------------------------------------------------------------ | ---------- |
| Data model fit         | PostgreSQL - relational, foreign keys, SQL joins, transactions  | NoSQL Firestore - document model, denormalization required for relational data | Supabase   |
| Credit system          | Atomic SQL transaction: deduct credit + log entry in one query  | Multi-document write with risk of partial failure without careful transactions | Supabase   |
| Pricing predictability | \$25/month flat Pro tier - no surprise bills                    | Pay-per-read/write - can spike unexpectedly with AI job volume                 | Supabase   |
| Vendor lock-in         | Open-source, standard Postgres, self-hostable - migrate anytime | Proprietary, no password hash export, migration costs 2-4 engineer weeks       | Supabase   |
| Android auth           | OAuth via Supabase Auth - Google Sign-In supported natively     | Firebase Auth - also excellent, marginally tighter Google integration          | Tie        |
| AI job queue           | Postgres table as queue + Edge Functions - simple and reliable  | Cloud Functions - also works but adds GCP dependency                           | Supabase   |

## **9.2 Supabase Schema (Backend Tables)**

**users**

| **Column**     | **Type**          | **Notes**                                          |
| -------------- | ----------------- | -------------------------------------------------- |
| id             | UUID (PK)         | Matches Supabase Auth UID - single source of truth |
| email          | TEXT              | From Google Sign-In or email/password auth         |
| display_name   | TEXT?             | From Google profile                                |
| credit_balance | INTEGER DEFAULT 0 | Current spendable credits - never goes below 0     |
| created_at     | TIMESTAMPTZ       |                                                    |
| last_seen_at   | TIMESTAMPTZ       | Updated on each app session sync                   |

**credit_transactions**

Immutable ledger - credits are never updated in-place, only appended. Balance is derived from this table if needed for audit.

| **Column**          | **Type**        | **Notes**                                                                      |
| ------------------- | --------------- | ------------------------------------------------------------------------------ |
| id                  | UUID (PK)       |                                                                                |
| user_id             | UUID FK → users |                                                                                |
| type                | ENUM            | PURCHASE \| AI_TAG_SINGLE \| AI_TAG_BATCH \| AI_TAG_PROJECT \| REFUND \| PROMO |
| amount              | INTEGER         | Positive = credit added, negative = credit spent                               |
| play_order_id       | TEXT?           | Google Play order ID for purchase verification                                 |
| play_purchase_token | TEXT?           | For server-side Play verification and idempotency                              |
| ai_job_id           | UUID?           | FK to ai_jobs - links spend to the job that consumed it                        |
| created_at          | TIMESTAMPTZ     |                                                                                |

**ai_jobs**

| **Column**      | **Type**        | **Notes**                                                     |
| --------------- | --------------- | ------------------------------------------------------------- |
| id              | UUID (PK)       |                                                               |
| user_id         | UUID FK → users |                                                               |
| scope           | ENUM            | SINGLE \| MULTI \| PROJECT                                    |
| status          | ENUM            | QUEUED \| PROCESSING \| COMPLETE \| FAILED \| REFUNDED        |
| photo_ids       | UUID\[\]        | Array of photo IDs from local Room DB to process              |
| project_id      | UUID            | Client-side project ID - for result routing on device         |
| credits_charged | INTEGER         | Credits deducted when job was queued                          |
| result_payload  | JSONB?          | AI results - see Section 11 for schema                        |
| error_message   | TEXT?           | If status = FAILED                                            |
| created_at      | TIMESTAMPTZ     |                                                               |
| completed_at    | TIMESTAMPTZ?    |                                                               |
| photo_urls      | TEXT\[\]?       | Signed Supabase Storage URLs - populated when photos uploaded |

## **9.3 Backend Services**

| **Service**           | **Technology**                          | **Purpose**                                                                                                      |
| --------------------- | --------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Auth                  | Supabase Auth                           | Google Sign-In OAuth + email/password fallback. Issues JWTs used for all API calls.                              |
| Database              | Supabase Postgres                       | Users, credits, jobs, transaction ledger. Row-Level Security ensures users only access their own data.           |
| Photo storage         | Supabase Storage                        | Temporary upload bucket for AI processing only. Photos deleted after job completes - not permanent cloud backup. |
| Purchase verification | Supabase Edge Function: verify-purchase | Receives Play purchase token from Android → calls Google Play Developer API → credits user atomically.           |
| AI job processor      | Supabase Edge Function: process-ai-job  | Triggered by DB insert on ai_jobs. Downloads photos, calls AI APIs, writes results, updates job status.          |
| Result polling        | Supabase Realtime subscription          | Android app subscribes to its own ai_jobs row - gets pushed update when status changes. No polling needed.       |

# **10\. Authentication Design**

## **10.1 Auth Strategy**

The app supports three user states. The transition from guest to signed-in must be frictionless - no data loss, no forced re-setup.

| **State**               | **Capabilities**                                                                    | **Data Lives**                                |
| ----------------------- | ----------------------------------------------------------------------------------- | --------------------------------------------- |
| Guest (no account)      | Full offline capture, batch import, PDF export, on-device ML Kit OCR                | Room DB on device only                        |
| Signed In (free)        | All guest features + credit balance sync, AI tag preview unlocked, purchase credits | Room DB + Supabase (synced)                   |
| Signed In (has credits) | All above + AI tagging: single photo, multi-select, entire project                  | Room DB + Supabase + temp Storage for AI jobs |

## **10.2 Android Auth Implementation**

Primary: Google Sign-In via Android Credential Manager API. This is the Android equivalent of Sign in with Apple - one tap for any user already signed into their Google account, which is effectively all Android users. Secondary: email/password via Supabase Auth. SMS OTP is not recommended as primary - carrier delivery failures are common on industrial sites.

**Credential Manager Flow**

1\. User taps 'Sign In' → app calls CredentialManager.getCredential() 2. Android shows native bottom sheet with user's Google account(s) - no webview, no redirect 3. User taps their account → Google ID token returned to app 4. App sends ID token to Supabase Auth (signInWithIdToken) 5. Supabase verifies with Google, creates/finds user, returns JWT 6. App stores JWT securely in Android EncryptedSharedPreferences 7. All subsequent Supabase API calls include JWT in Authorization header

## **10.3 Guest-to-Signed-In Migration**

When a guest user signs in, local data is not deleted or replaced. The migration is purely additive:

- Room DB remains the local source of truth - nothing changes on device
- user_id is stamped on the local Room records for future sync identification
- Credit balance is fetched from Supabase and displayed in UI
- Any guest projects remain fully accessible - they are now associated with the signed-in account
- No re-entry of any data required

## **10.4 Soft Gate for AI Tagging (Guest Users)**

When a guest taps any AI tagging entry point, they see a preview screen - not an error or a hard wall:

**Soft Gate Preview Flow**

1\. Guest taps 'AI Tag' on any photo or project 2. Preview screen loads instantly (no network call) - shows a pre-tagged demo photo stored in the app bundle 3. Demo shows: classified photo type badge (e.g. 'Defect'), auto-generated asset note, suggested equipment type 4. Animated 'shimmer' effect shows this is a live result, not static marketing 5. CTA: 'Unlock AI Tagging - Sign in to purchase credits' 6. Tapping CTA → Credential Manager Google Sign-In sheet 7. After sign-in → credit purchase flow (see Section 11) The demo photo is chosen to showcase a genuinely hard case - worn nameplate, unclear photo - where AI tagging visibly outperforms on-device ML Kit. This is the honest version of the upsell.

# **11\. Billing & Credit System**

## **11.1 Google Play Billing (Mandatory)**

All in-app credit purchases must go through Google Play Billing - Google Play policy requires this for digital goods purchased within an Android app. Google takes a 15% service fee (30% for first year if not qualifying for reduced rate). Credit packs are implemented as consumable in-app products, which can be purchased multiple times.

## **11.2 Credit Pack Design**

| **Pack** | **Credits** | **Suggested Price** | **Cost Per Tag** | **Play SKU** |
| -------- | ----------- | ------------------- | ---------------- | ------------ |
| Starter  | 10 credits  | \$2.99              | \$0.30/tag       | credits_10   |
| Standard | 50 credits  | \$9.99              | \$0.20/tag       | credits_50   |
| Pro      | 150 credits | \$24.99             | \$0.17/tag       | credits_150  |

## **11.3 Credit Costs Per Operation**

| **Operation**                       | **Credits**   | **Rationale**                                                          |
| ----------------------------------- | ------------- | ---------------------------------------------------------------------- |
| AI tag - single photo               | 1 credit      | One API call, fast turnaround, low cost                                |
| AI tag - multi-select (per photo)   | 1 credit each | Same cost regardless of batch - batch just queues multiple single jobs |
| AI tag - entire project (per photo) | 1 credit each | Volume is handled by batch processing on backend, same unit cost       |

Rationale for flat per-photo pricing: it is the most transparent model for a field tech audience. No confusing 'project tokens' or tiered batch discounts - one photo costs one credit, always. Credits are non-expiring.

## **11.4 Purchase Verification Flow**

**Server-Side Purchase Verification (Required by Google)**

1\. Android app: user taps credit pack → Play Billing Library launches purchase sheet 2. User completes purchase → Play returns purchaseToken + orderId to app 3. App immediately sends token to Supabase Edge Function: POST /verify-purchase 4. Edge Function calls Google Play Developer API: purchases.products.get(purchaseToken) 5. Validates: purchaseState = 1 (purchased), not already processed (idempotency check on play_purchase_token) 6. Atomic SQL transaction: INSERT credit_transaction (type=PURCHASE, amount=+N) + UPDATE users SET credit_balance = credit_balance + N 7. Edge Function acknowledges the purchase via Play API (required within 3 days or Google auto-refunds) 8. Edge Function calls consumeAsync signal back to app 9. App receives updated credit balance, shows confirmation toast CRITICAL: Credits are never granted client-side. The app only updates its displayed balance after server confirmation. If verification fails or times out, the purchase is not consumed - Google will auto-refund within 3 days.

## **11.5 Credit Display & UX**

- Credit balance shown as a persistent chip in the top bar when signed in - always visible
- Before any AI tagging operation, the app shows the credit cost and current balance, with a confirm tap required
- If balance is insufficient, user sees current balance, cost of operation, and a direct 'Buy Credits' button - no error message, immediate path to purchase
- After a successful AI job, credits deducted are shown in a toast: 'AI tagging complete - 3 credits used, 47 remaining'
- Credit transaction history accessible from profile screen - full ledger with timestamps

# **12\. AI Tagging Pipeline**

AI tagging is an async cloud operation. The user submits a job, continues working offline, and receives results when connectivity allows. Results are always presented as editable suggestions - the same confirmation-first UX as on-device OCR.

## **12.1 What AI Tagging Does**

| **Capability**                 | **How**                                                                                                      | **Output Field**                                           |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------- |
| Enhanced OCR on worn/hard tags | Claude Vision or Google Cloud Vision API - better than ML Kit on corroded, embossed, low-contrast nameplates | tag_id_suggested (overwrites or supplements ML Kit result) |
| Photo type classification      | Vision model classifies each photo: Before / During / After / Tag / Defect / Meter / Safety / Wiring / Other | role (enum, replaces or confirms existing)                 |
| Asset notes generation         | Vision model describes visible equipment condition, notable features, readable specs - 1-3 sentences         | ai_notes (new field, separate from user notes)             |
| Equipment type suggestion      | Vision model identifies equipment category from photo: Pump, Valve, Panel, Motor, Pipe, Gauge, etc.          | asset_type (suggestion for the Asset entity)               |

## **12.2 Three Submission Modes**

**Single Photo**

User long-presses any photo → context menu → 'AI Tag this photo' Cost shown: 1 credit. Confirm → job submitted. Result returned to that specific photo only.

**Multi-Select**

User enters multi-select mode on Asset or Ungrouped bucket → selects 2+ photos → 'AI Tag selected (N credits)' Each photo is one credit. Total cost shown before confirm. Results returned per photo.

**Entire Project**

From project detail screen → overflow menu → 'AI Tag entire project' App counts all photos in project, shows total credit cost. Confirm → entire project queued as one batch job. This is the high-value workflow: a tech comes back from a job, dumps all photos via batch import, then taps one button to have everything classified and noted.

## **12.3 Job Lifecycle**

**End-to-End Flow**

1\. App deducts credits optimistically (pending confirmation) - shows 'AI job queued' 2. App uploads selected photos to Supabase Storage (temporary signed URLs, 1hr expiry) 3. App inserts row into ai_jobs with status=QUEUED and photo_urls\[\] 4. Supabase Realtime subscription on ai_jobs row is active on device 5. Edge Function triggered by DB insert → downloads photos → calls Vision API → parses results 6. Edge Function updates ai_jobs: status=COMPLETE, result_payload=JSON 7. Realtime push reaches Android app (even if app was backgrounded - WorkManager poll as fallback) 8. App writes results into local Room DB as suggested fields 9. Photos deleted from Supabase Storage 10. User sees 'AI results ready' notification → opens review screen If job FAILS: credits are refunded via INSERT credit_transaction (type=REFUND). Photos deleted from storage. User notified with error summary.

## **12.4 AI Result Schema (result_payload JSONB)**

Each item in the results array corresponds to one photo_id submitted:

**result_payload structure**

{ "job_id": "uuid", "results": \[ { "photo_id": "uuid", "tag_id_suggested": "TAG-4821-B", "tag_confidence": 0.94, "role_suggested": "DEFECT", "role_confidence": 0.88, "asset_type_suggested": "Pump", "ai_notes": "Centrifugal pump showing significant corrosion on inlet flange. Nameplate partially obscured but serial visible. Appears to be 2-inch nominal inlet.", "processing_ms": 1240 } \], "total_photos": 12, "failed_photos": \[\], "model_used": "claude-3-5-sonnet" }

## **12.5 AI Result Review UX**

Results are never auto-applied. The review screen presents each photo with its AI suggestions as an editable card:

- Tag ID: show AI suggestion vs existing ML Kit result - user selects one or types override
- Photo role: shown as a pill selector (Before / After / Tag / Defect / etc.) with AI suggestion pre-selected
- Asset type: shown as a chip with AI suggestion - tap to edit
- AI notes: shown in an editable text field - user can keep, edit, or delete
- 'Apply All' button to accept all suggestions at once for speed
- 'Skip' per photo to leave it unchanged

Low-confidence results (< 0.70) are highlighted in amber - the same visual language as on-device OCR uncertainty.

# **13\. Android Permissions**

This section covers every permission the app requires, why it is needed, the exact in-app rationale string to show users, the legal obligations it triggers, and the real-world job site risks the user and their employer must be aware of.

## **13.1 Permission Requirements**

| **Permission**              | **Android Declaration**                             | **When Requested**                          | **Required For**                                                                                                |
| --------------------------- | --------------------------------------------------- | ------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Camera                      | CAMERA (dangerous - runtime)                        | First time user opens in-app camera         | CameraX live capture - in-app camera for tag and item photos                                                    |
| Photo library read          | READ_MEDIA_IMAGES (dangerous - runtime)             | First time user opens batch import          | Batch import from gallery. Note: Google Play now requires a declaration form for broad access - see 13.3        |
| Location (coarse)           | ACCESS_COARSE_LOCATION (dangerous - runtime)        | On first project creation, with explanation | GPS coordinates on photos and projects for context. Optional - user can decline and app still works fully       |
| Location (fine)             | ACCESS_FINE_LOCATION (dangerous - runtime)          | Same prompt as coarse, requested together   | More accurate GPS for photo metadata. Optional - same as above                                                  |
| Internet                    | INTERNET (normal - auto-granted)                    | Never shown to user                         | Supabase API calls for auth, credits, AI job upload. Not used when offline.                                     |
| Network state               | ACCESS_NETWORK_STATE (normal - auto-granted)        | Never shown to user                         | Check connectivity before attempting cloud sync or AI job submission - prevents silent failures offline         |
| Notifications (Android 13+) | POST_NOTIFICATIONS (dangerous - runtime)            | When user first submits an AI job           | Push notification when async AI tagging job completes. User can decline - they can check status manually in app |
| Foreground service          | FOREGROUND_SERVICE (normal - auto-granted)          | Never shown to user                         | Keeps batch import WorkManager job alive when app is backgrounded - prevents import cancellation                |
| Storage write (legacy)      | WRITE_EXTERNAL_STORAGE (only declared for API < 29) | Not shown on modern devices                 | PDF export on very old Android versions. Not required on Android 10+. Use MediaStore API on modern devices.     |

## **13.2 In-App Permission Rationale Strings**

Android requires showing an explanation before requesting dangerous permissions if the user previously denied them. These strings should be used for both the initial contextual explanation shown before the system dialog, and the re-request rationale:

**Camera**

**Rationale String - Camera**

"FieldTag uses your camera to photograph asset tags and equipment directly inside the app. Photos are saved to the app only - they won't appear in your personal photo gallery." Re-request (if denied): "Camera access is required to take photos within the app. You can also import existing photos from your gallery instead. To enable, go to Settings > Apps > FieldTag > Permissions."

**Photo Library**

**Rationale String - Photo Library**

"FieldTag needs access to your photos to import images you've already taken into a project. Only photos you select will be used - we don't scan or access your entire library automatically." Re-request (if denied): "Photo library access is needed to import existing photos. Alternatively, use the in-app camera going forward. To enable, go to Settings > Apps > FieldTag > Permissions."

**Location**

**Rationale String - Location**

"FieldTag can attach GPS coordinates to your photos and projects, making it easier to identify which site each job belongs to. This is optional - the app works fully without location access." This permission should never be marked as required. Always present a 'Skip' option with text: "You can enable this later in Settings if you change your mind." Important: Do NOT request background location (ACCESS_BACKGROUND_LOCATION). The app only needs location when actively in use. Background location requires a much stronger justification for Google Play and raises significant privacy concerns.

**Notifications**

**Rationale String - Notifications**

"Allow FieldTag to notify you when your AI tagging job is complete. Jobs can take a few minutes - a notification means you don't have to keep the app open." This permission is only requested when user submits their first AI job, not on app install.

## **13.3 Legal Obligations Triggered by These Permissions**

The following legal requirements are activated by this permission set. This is not legal advice - consult a qualified attorney before publishing the app. These are the minimum obligations based on current law and Google Play policy:

| **Obligation**                       | **Triggered By**                                         | **What You Must Do**                                                                                                                                                                                                                                                                                          |
| ------------------------------------ | -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Privacy Policy (mandatory)           | Camera + Location + Photo Library - any one of these     | Required by Google Play for all apps requesting dangerous permissions. Must be hosted at a public, non-gated URL (not a PDF). Must disclose what data is collected, why, how it's used, and whether it's shared with third parties.                                                                           |
| GDPR compliance                      | Location data + email address (if user signs in)         | If any user is in the EU/UK, GDPR applies regardless of where the app is built or hosted. Must explain legal basis for processing (Legitimate Interest for core function, Consent for optional location). Must support data deletion requests. Users must be able to export or delete their account and data. |
| CCPA compliance (California)         | Any personal data collection including location or email | If users are in California, disclose what data is collected and whether it is sold (it should not be). Include a 'Do Not Sell' mechanism or explicit statement that data is not sold.                                                                                                                         |
| PIPEDA compliance (Canada)           | Email + location from Canadian users                     | Requires Privacy Policy and consent before collecting personal data. Consent must be meaningful - pre-checked boxes or assumed consent are not valid.                                                                                                                                                         |
| Google Play photo/video declaration  | READ_MEDIA_IMAGES permission                             | As of 2025, Google requires a declaration form in Play Console for apps that retain broad photo access. A photo documentation app qualifies as core use - submit the declaration form or updates will be blocked.                                                                                             |
| Play Store Privacy Policy link       | Any dangerous permission                                 | Privacy Policy URL must appear in the Play Store listing AND be accessible from within the app itself (typically in Settings or About).                                                                                                                                                                       |
| Android Privacy Dashboard disclosure | Camera + Location                                        | Android 12+ automatically includes your app in the Privacy Dashboard. The rationale strings in section 13.2 appear here. Users can revoke permissions directly from this dashboard - handle graceful degradation in code.                                                                                     |

## **13.4 Job Site Photography Restrictions - Real-World Risks**

This is critical context for your users. The app itself cannot enforce site rules, but your marketing, onboarding, and Terms of Service should make users aware that photography on industrial sites is governed by multiple overlapping restrictions entirely separate from app permissions.

**⚠ Important Disclaimer for This Section**

The risks below are real and well-documented, but the specifics vary significantly by country, industry, employer, and individual site. This section is informational only. Users of FieldTag are solely responsible for complying with the photography policies of any site they access. Consult legal counsel if operating in regulated industries.

| **Risk Category**                      | **Who It Affects**                                                        | **Details & Mitigation**                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| -------------------------------------- | ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Client / employer NDAs                 | All users - universal risk                                                | Most industrial service contracts include confidentiality clauses. Photographing a client's facility, equipment, processes, or even asset tag IDs may constitute disclosure of confidential information if photos are transmitted outside the engagement. The app's offline-first design and local storage reduces this risk significantly - photos never leave the device without explicit user action. Users should review their contracts before using any cloud sync or AI tagging features. |
| Site-specific no-photography policies  | Common in oil & gas, chemicals, pharma, data centres                      | Many industrial facilities have blanket no-photography policies for security and IP reasons. These are site rules, not laws - violation typically results in removal from site and potential contract termination. The app should not be opened on such sites without explicit written approval from the facility owner. Users are responsible for obtaining photo authorisation before using any camera app on site.                                                                            |
| Nuclear and government facilities      | Any user working near nuclear plants, military, or federal infrastructure | Photography at or near nuclear facilities is federally regulated in the US. The NRC requires facility security clearances, and unauthorised photography of safety-critical systems is prohibited. Similar restrictions apply near military installations under 18 U.S. Code § 795. The app should never be used to photograph restricted areas, safety systems, or classified infrastructure.                                                                                                    |
| Oil refinery / critical infrastructure | Oil & gas, pipeline, water treatment users                                | Post-9/11 security culture means photography near refineries, pipelines, and water infrastructure is treated with heightened suspicion even when technically legal on public property. Contractors on private sites are subject to the site owner's rules. Users working inside these facilities with authorised work orders are generally permitted to document their specific work scope - but should clarify boundaries with the facility contact before beginning.                           |
| GPS metadata as a security concern     | All users - any sensitive site                                            | Photos with embedded GPS coordinates can reveal the precise location of sensitive infrastructure. Even if the photo content is innocuous, the location metadata can be a security concern on classified or restricted sites. The app should include an option in Settings to disable GPS tagging globally or per-project. This should be surfaced prominently in onboarding for users in defence, nuclear, or government-adjacent industries.                                                    |
| AI tagging photo upload                | Premium users - any sensitive site                                        | When users submit photos for AI tagging, images are temporarily uploaded to Supabase cloud storage. This is the most significant data-handling event in the app. Users on sites with strict data residency requirements, NDAs covering digital assets, or photography prohibitions must not use the AI tagging feature for those photos. The upload confirmation screen must make this explicit.                                                                                                 |

## **13.5 Recommended In-App Safeguards**

These features reduce legal exposure for both the app and its users:

- GPS disable toggle - global setting and per-project override to strip location from all captured photos. Presented during onboarding with plain-language explanation of why some sites require this.
- AI tagging upload warning - before any photo leaves the device for AI processing, a one-time modal explains: 'These photos will be temporarily uploaded to our servers for processing and deleted immediately after. Do not use this feature for photos taken under NDA or on restricted sites.' Requires explicit confirmation.
- Export warning for cloud sharing - when user shares PDF via share sheet to a cloud destination (Drive, Dropbox, email), a non-blocking toast: 'Sharing to cloud - ensure this is permitted under your site's data policy.'
- Terms of Service - app ToS should explicitly state that users are responsible for compliance with all applicable site photography policies, NDAs, and local laws. FieldTag is a documentation tool and does not authorise photography anywhere.
- Privacy Policy data deletion - signed-in users must be able to delete their account and all associated data from within the app (required by GDPR and Google Play policy). Implement a 'Delete my account and data' option in Settings that triggers a Supabase account deletion and removes all server-side data.