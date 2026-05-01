# CoWall — Revamp & Feature Plan

**Last updated:** 2026-04-29  
**Status legend:** `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

---

## Phase 1 — Stabilization
> Fix crashes and unblock basic use. No new features.

- [x] Delete dead files: `MainActivity.kt`, `SendCapturedImageActivity.kt`, `activity_main.xml`
- [x] Flatten navigation stack: Splash → ChatRoom (returning user) or Splash → OnboardingActivity (first launch)
  - Use `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK` on all terminal navigations
  - No back-stack entry behind `ChatRoomActivity`
- [x] Fix `onResume()` duplicate message bug in `ChatRoomActivity` — removed intent re-processing entirely
- [x] Fix `Uri.parse(data.extras?.getString(...))` NPE in `ChatRoomActivity.onActivityResult` — guard with null check
- [x] Fix hardcoded `"jon dee"` name in `CreateOrJoinRoom.lookForPartner()` — reads from SharedPreferences
- [x] Guard `valueEventListener` uninitialized crash in `CreateOrJoinRoom` — using nullable `partnerListener`
- [x] Replace `addValueEventListener` with `addListenerForSingleValueEvent` in `getPartnerUserName()`
- [x] Fix `runBlocking` in `ChatRoomViewModel` — replaced with `viewModelScope.launch`
- [x] Replace `GlobalScope.launch` in `DatabasesRepositoryImpl` — addressed via ViewModel scope
- [x] Replace `Coroutines.io {}` wrapper — ViewModels now use `viewModelScope` directly
- [x] Add `timestamp: Long` field to `UserChat` and `MessageModel`; populate with `System.currentTimeMillis()` on write
- [x] Enable `FirebaseDatabase.getInstance().setPersistenceEnabled(true)` in `FireBaseConnector.initializeConnection`
- [x] Remove Firebase listener in `FireBaseConnector.lookForUpdates()` on service stop — `stopListening()` method added, called in `RunningService.onDestroy()`
- [x] Fix permission handling — use `READ_MEDIA_IMAGES` on API 33+, `READ_EXTERNAL_STORAGE` below
- [x] Remove unused `READ_MEDIA_VIDEO` permission from manifest
- [x] Add API 26 guard before starting `RunningService` (returns early if API < 26)
- [x] Null-check `BitmapFactory.decodeFile()` result before calling `setWallpaper()`
- [x] Fix Toast missing `.show()` in `RunningService` — replaced with `Log.w`
- [ ] Fix `startActivityForResult` deprecation in `CameraActivity` — migrate to `registerForActivityResult` (deferred: non-crash issue)
- [x] Reuse single `GPUImage` instance — not needed; `EditImageActivity` already creates one per session which is correct
- [x] `ChatRoomActivity` now calls `FireBaseConnector.setUniqueIds()` on entry so IDs are always fresh

---

## Phase 2 — Chat Screen Revamp
> Complete visual and functional redesign of the chat screen.

- [x] Add Glide dependency (`com.github.bumptech.glide:glide:4.16.0`)
- [x] Add design system palette to `colors.xml` (bg_primary, bg_surface, bg_card, accent, text_primary/secondary, bubble_sent/received, etc.)
- [x] Add `Theme.CoWall.PhotoView` and `RoundedSquare` style to `themes.xml`
- [x] Redesign `sent.xml`
  - Image: 240×200dp, scaleType=centerCrop, RoundedSquare shape
  - Text-only mode: `textViewSend` shown, image hidden
  - Timestamp bottom-right, 10sp secondary color
  - Delivery status icon
- [x] Redesign `receive.xml`
  - Same image sizing as sent
  - Sender name above bubble in accent color
  - Timestamp bottom-right
- [x] Redesign `activity_chat_room.xml`
  - Removed broken `imagePreview` overlay
  - Added caption input bar at bottom (`EditText` + camera FAB)
  - Added empty state view: icon + "Send your first photo"
  - RecyclerView height 0dp between toolbar and input bar
  - Added `input_background.xml` drawable (rounded, dark)
- [x] Updated `MessageModel` to include `caption`, `timestamp`, `status: MessageStatus`
- [x] Updated `MessageAdapter`
  - Glide for image loading with placeholder
  - Timestamps formatted with `SimpleDateFormat`
  - Sender name on received bubbles
  - Tap image → `PhotoViewActivity`
  - `notifyItemRangeInserted` for batch adds
  - Removed `OnItemClickLongListener` (replaced by tap-to-fullscreen)
- [x] Created `PhotoViewActivity` — full-screen image tap-to-close
- [x] Created `activity_photo_view.xml` — full-screen dark background
- [x] Auto-scroll `RecyclerView` to bottom on new message (`stackFromEnd = true` + `scrollToPosition`)
- [x] `ChatRoomActivity` rewritten — clean lifecycle, no duplicates, caption wired to outgoing message
- [x] `PhotoViewActivity` registered in `AndroidManifest.xml`

---

## Phase 3 — UX Enhancement: User Feedback & Messaging
> Add loading states, progress indicators, success/error messages across all screens.
> **Priority: HIGH** — Biggest impact on perceived quality with least code change.

### 3A. Feedback Utilities
- [x] Create `utilities/UiHelpers.kt`
  - `Activity.showSnackbar(message, duration, actionLabel?, action?)` — Material Snackbar wrapper
  - `Activity.showErrorSnackbar(message)` — red-tinted Snackbar for errors
  - `Activity.showSuccessSnackbar(message)` — green-tinted Snackbar for success
  - `Activity.showLoadingDialog(message): AlertDialog` — non-cancellable Material loading dialog
- [x] Create `res/layout/dialog_loading.xml` — centered ProgressBar + message TextView
- [x] Add upload state colors to `colors.xml` (`success_green`, `error_red`, `warning_amber`)

### 3B. ChatRoomActivity Feedback
- [x] **Image upload progress**: Show a Snackbar with indeterminate progress when `uploadImageToDrive()` starts  
- [x] **Upload success**: Show "Photo sent to {partnerName}!" Snackbar on Drive upload completion
- [x] **Upload failure**: Show "Failed to send photo. Tap to retry." error Snackbar with retry action
- [x] **Wallpaper set confirmation**: When partner's image arrives, show "Wallpaper updated!" Snackbar
- [x] Add `UploadCallback` interface to `FireBaseConnector.uploadImageToDrive()` — `onProgress()`, `onSuccess()`, `onFailure(error)`
- [ ] Show upload progress overlay on the sent message bubble (semi-transparent + spinning indicator)
- [x] Disable camera FAB while upload is in progress to prevent double-sends

### 3C. CreateOrJoinRoom Feedback
- [x] **Name validation**: Inline error on empty name ("Please enter your name") with `TextInputLayout` error state
- [x] **Room code validation**: Inline error for invalid code format (non-8-digit)
- [x] **Join loading**: Show loading dialog "Joining room..." when entering partner's code
- [ ] **Join failure**: Show error Snackbar if room code doesn't exist in Firebase
- [x] **Room created**: Show brief Snackbar "Room created! Share your code with your partner"
- [x] **Copy code**: Tap room code → copies to clipboard + Snackbar "Code copied!"
- [ ] **Waiting animation**: Replace static waiting screen with Lottie/animated dots + pulsing room code

### 3D. LoginActivity Feedback
- [x] **Sign-in failure detail**: Replace generic "Sign-in failed" with specific error message based on `ApiException.statusCode`
- [x] **Network error**: Detect no connectivity → show "No internet connection" with retry button
- [x] **Progress state**: Disable sign-in button AND show loading text "Signing in..." (not just a hidden spinner)

### 3E. CameraActivity Feedback
- [ ] **Permission denied**: Show explanation dialog "Camera access is required to capture photos for your partner" with "Open Settings" button
- [ ] **Capture feedback**: Brief flash overlay animation when photo is captured
- [ ] **Gallery empty**: Handle case where no images are available

### 3F. EditImageActivity Feedback
- [ ] **Filter applying**: Show brief shimmer/progress when switching filters on large images
- [ ] **Save progress**: Show loading dialog "Preparing image..." during save
- [ ] **Save success**: Brief checkmark animation before finishing

---

## Phase 4 — UX Enhancement: Screen Redesign & New Screens
> Modernize existing screens, add missing screens.
> **Priority: MEDIUM** — Visual polish + missing functionality.

### 4A. SplashScreen Redesign
- [x] Add fade-in animation for logo + tagline (300ms delay, 500ms duration)
- [x] Add scale-up animation on the background image
- [x] Replace hardcoded `#932150` with theme color reference
- [x] Add connectivity check on splash — if offline, show "No connection" with retry instead of proceeding to broken state

### 4B. LoginActivity Redesign
- [x] Add onboarding info cards above sign-in button (3 feature highlights):
  - "📸 Capture & share photos" 
  - "🖼️ Auto-set partner's wallpaper"
  - "💕 Stay connected, always"
- [ ] Add app icon/logo image at top
- [ ] Add "How it works" expandable section below sign-in
- [ ] Add terms/privacy footer text
- [ ] Animate content on entry (slide up from bottom)

### 4C. CreateOrJoinRoom Redesign → `OnboardingActivity`
- [ ] Split into 2-step flow using ViewPager2:
  - **Step 1**: Enter your name (large friendly input, "What should your partner call you?")
  - **Step 2**: Create or Join (tab layout)
    - **Create tab**: Shows your room code as large copyable card, "Share this code with your partner" instruction, "Waiting for partner..." state with animation
    - **Join tab**: 8-digit code input with segmented boxes, "Join Room" button
- [ ] Add gradient background matching brand colors
- [ ] Add progress dots at bottom showing current step
- [ ] Add back button to go from step 2 → step 1

### 4D. SettingsActivity (New Screen)
- [x] **Profile section**: User name (editable), Google account email (read-only), profile picture from Google
- [x] **Wallpaper Target**: `MaterialButtonToggleGroup` — Lock Screen / Home Screen / Both
  - Persist to `SharedPreferences("wallpaperTarget")`
  - Update `FireBaseConnector.setWallpaper()` to use correct `WallpaperManager` flags
- [x] **Room Info**: Room code (copyable), Partner name, "Leave Room" button with confirmation dialog
- [ ] **Notifications**: Toggle for wallpaper update notifications
- [x] **About**: App version, "Rate on Play Store", "Share with friends"
- [x] **Logout**: Sign out of Google + clear SharedPreferences + navigate to Login
- [x] Add Settings gear icon to `ChatRoomActivity` toolbar → opens SettingsActivity
- [x] Register `SettingsActivity` in `AndroidManifest.xml`

### 4E. CameraActivity Polish
- [x] Add back/close button (top-left)
- [x] Add semi-transparent dark gradient at top and bottom for button visibility
- [ ] Add gallery thumbnail preview in bottom-left (last photo from gallery)
- [ ] Animate capture button press (scale down → up)
- [ ] Add "Tap to capture, hold for burst" hint text on first use

### 4F. EditImageActivity Polish
- [x] Replace hardcoded `#0050aa` toolbar with theme color
- [ ] Add "Original" label on the first filter thumbnail
- [ ] Add filter name label below each filter preview
- [ ] Add undo button to revert to original
- [ ] Add image crop option (integrate UCrop library)

---

## Phase 5 — UX Enhancement: Chat Experience
> Richer messaging capabilities.
> **Priority: MEDIUM** — Core feature expansion.

### 5A. Text Messaging
- [x] Extend `UserChat` to support `type: "image" | "text" | "reaction"` and `text: String?`
- [x] Update `FireBaseConnector.sendUri()` → `sendChatMessage(type, content)` 
- [x] Replace camera FAB with send button when caption text is non-empty
- [x] Show text-only message bubbles in `MessageAdapter` (already partially supported)
- [x] Add "send text" button (paper plane icon) that appears when `captionInput` has text
- [x] Caption input hint changes: "Type a message or tap 📷" 

### 5B. Image Send Confirmation
- [ ] After selecting/capturing image + applying filter, show confirmation bottom sheet:
  - Image preview (full width)
  - Caption input field
  - "Send to {partnerName}" button
  - "Cancel" button
- [ ] This replaces the current flow where the image is immediately sent on return from EditImageActivity

### 5C. Emoji Reactions
- [ ] Add reaction bar below each received image (5 emojis: ❤️ 😂 😮 😢 🔥)
- [ ] Tap reaction → sends reaction message to Firebase (`type: "reaction", content: "❤️", replyTo: messageId`)
- [ ] Show reaction badges on message bubbles (small emoji + count)
- [ ] Add `reactionTo: String?` field to `UserChat` model
- [ ] Update `MessageAdapter` to render reaction badges

### 5D. Presence & Status Indicators
- [ ] **Online indicator**: Green dot on partner name in toolbar when partner has app open
  - Write `online/{userId}: true/false` to Firebase on `onResume()`/`onPause()`
  - Listen to `online/{partnerId}` in `ChatRoomActivity`
- [ ] **Last seen**: Show "Last seen 2h ago" under partner name when offline
  - Write `lastSeen/{userId}: timestamp` to Firebase on `onPause()`
- [ ] **Typing indicator**: Show "typing..." when partner is entering caption text
  - Write `typing/{roomId}/{userId}: true/false` on text change (with debounce)

### 5E. Notification Improvements  
- [ ] **Rich notification**: When `RunningService` receives new image, show `BigPictureStyle` notification with image preview
- [ ] **Notification tap**: Opens `ChatRoomActivity` directly
- [ ] **Quick reply from notification**: Add `RemoteInput` action for text reply without opening app
- [ ] **Reaction buttons in notification**: 3 reaction buttons (❤️ 😂 🔥) as notification actions

---

## Phase 6 — Onboarding & Settings UI (original Phase 3, merged into Phase 4)
> Superseded by Phase 4C and 4D above.

---

## Phase 7 — Image Editing (Text Overlay + Crop) *(was Phase 4)*
> More editing power beyond GPU filters.

- [ ] Add "Text" tab to `EditImageActivity` tab bar (alongside Filters, Crop)
- [ ] Implement `TextOverlayView` — custom View supporting
  - Draggable, resizable text layers via touch gestures
  - Color picker (predefined palette)
  - Font size slider
  - Bold / Italic toggles
  - Drop shadow toggle
- [ ] Implement `flattenOnBitmap(source: Bitmap): Bitmap` — draws source bitmap onto Canvas, renders all text layers on top, returns merged Bitmap
- [ ] Add ucrop library for basic crop/rotate tab
- [ ] Wire "Save" button: flatten text layers onto filtered image → pass result URI back to `ChatRoomActivity`
- [ ] (Optional) Emoji/sticker picker tab — predefined sticker sheet placed as image layers

---

## Phase 8 — Home Screen Widget
> Show latest received image as a home screen widget.

- [ ] Create `res/xml/cowall_widget_info.xml` — widget metadata (4×2 cells, resize horizontal)
- [ ] Create `res/layout/widget_cowall.xml`
  - Full-bleed `ImageView`
  - Bottom strip: partner name + relative timestamp
  - Row of 5 reaction buttons (❤️ 😂 😮 😢 🔥)
- [ ] Create `CoWallWidget.kt` (`AppWidgetProvider`)
  - `onUpdate()` loads last saved image path from SharedPreferences into `RemoteViews`
  - Each reaction button wired to a `PendingIntent` → `QuickReplyService`
- [ ] Register widget receiver in `AndroidManifest.xml`
- [ ] Update `RunningService`: after setting wallpaper, broadcast `ACTION_APPWIDGET_UPDATE` with image path so widget refreshes
- [ ] Settings toggle (Phase 3) enables/disables widget

---

## Phase 9 — Quick Reply
> React to partner's image without opening the app.

### Tier A — Notification Actions (all Android versions)
- [ ] On new wallpaper received in `RunningService`, build `NotificationCompat` with `BigPictureStyle` showing the image
- [ ] Add 5 reaction action buttons to notification (❤️ 😂 😮 😢 🔥)
- [ ] Add "Reply" action with `RemoteInput` for inline text reply
- [ ] Create `QuickReplyService` — reads reaction/text from Intent, writes to Firebase `roomChat/{roomId}` as a text message, shows brief toast

### Tier B — Widget Reactions (ties into Phase 5)
- [ ] Widget reaction buttons fire `PendingIntent` → `QuickReplyService` (same service as Tier A)
- [ ] Reaction displayed in chat as a text message bubble

### Tier C — Lock Screen Bubble (Android 11+, exploratory)
- [ ] Research `BubbleMetadata` API — floating bubble on lock screen
- [ ] Evaluate feasibility vs notification action approach
- [ ] Implement only if Tier A UX is insufficient

---

## Phase 10 — Pluggable Backend (Future Goal)
> Let users bring their own storage so CoWall doesn't hold their data.

- [ ] Define `CoWallBackend` interface
  ```
  uploadImage(uri, roomId) → imageUrl
  sendMessage(roomId, message)
  observeRoom(roomId, callback)
  stopObserving(roomId)
  ```
- [ ] Refactor `FireBaseConnector` + `DatabasesRepositoryImpl` to implement `CoWallBackend`
- [ ] Onboarding option: "Use CoWall servers" vs "Use my own Firebase"
  - If own Firebase: input fields for Web API key, Database URL, Storage Bucket
  - Store encrypted in SharedPreferences (use `EncryptedSharedPreferences`)
- [ ] Initialize Firebase app dynamically from user config instead of `google-services.json`
- [ ] (Future spike) `GoogleDriveBackend` implementing `CoWallBackend`
  - Images stored via Drive API `Files.create()`
  - Message log as shared Google Sheet
  - User authenticates with their own Google account — no CoWall Firebase needed

---

## Known Issues Log
> Issues identified in audit that don't belong to a specific phase.

| ID | Severity | Status | Description | File |
|----|----------|--------|-------------|------|
| B1 | Medium | Open | `startActivityForResult` deprecated — use `ActivityResultContracts` | `CameraActivity.kt:330` |
| B2 | Medium | Open | Camera rotation hardcoded, ignores device orientation — sideways photos on landscape | `CameraActivity.kt:85-89` |
| B3 | Low | Fixed | `getOrGenerateId("roomId")` called before `userUniqueId` set — now called in sequence | `CreateOrJoinRoom.kt` |
| B4 | Low | Fixed | `orderByChild("timestamp")` query removed — `UserChat` now has timestamp field | `FireBaseConnector.kt` |
| B5 | Low | Fixed | `Toast.makeText()` missing `.show()` — removed entirely | `RunningService.kt` |
| B6 | Low | Fixed | `assert(manager != null)` in production — replaced with real null-safety | `RunningService.kt` |
| B7 | Low | Open | Debug log "Capturing photo jgh" left in production | `CameraActivity.kt:219` |
| B8 | Low | Fixed | Hardcoded tag `"Walld"` — replaced with `"CoWall"` in all new/modified files | Multiple |
| B9 | Low | Open | `getInputStreamFromUri()` never closes stream — resource leak | `EditImageRepositoryImpl.kt:162` |
| B10 | Low | Fixed | `READ_MEDIA_VIDEO` permission declared but never used — removed | `AndroidManifest.xml` |
