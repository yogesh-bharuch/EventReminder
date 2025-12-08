📡 EventReminder Sync Module — Architecture & Design Guide

A robust bidirectional, offline-capable, multi-device synchronization engine for syncing Room database entities with Firestore.

This module provides:

Incremental sync (fast & cost-efficient)

Conflict resolution (LATEST_UPDATED_WINS)

Soft delete propagation

Offline support

Multi-device consistency

Firestore cost optimization

🧠 Overview

The sync system keeps Room (local DB) and Firestore (cloud DB) aligned using updatedAt timestamps (epoch millis).

Each device tracks the last successful sync and only exchanges changes made after the last sync.

The engine is generic and supports syncing any entity that can:

Be identified by an ID

Provide an updatedAt timestamp

Be mapped to/from Firestore

The Reminder entity is the first example integrated.

🧱 Architecture Components
Room <--> DAO Adapter <--> SyncEngine <--> Firestore
^                ^
|                |
EntitySyncConfig  SyncMetadata

📁 File Responsibility Breakdown
1. EntitySyncConfig.kt

Defines how a single entity (e.g., reminders) is synchronized.

Includes:

Firestore collection reference

Mapping Local → Remote (toRemote)

Mapping Remote → Local (fromRemote)

Getter for local ID

Getter for updatedAt

Getter for isDeleted

Conflict resolution strategy

DAO adapter reference

2. ReminderSyncConfig.kt

Creates a concrete EntitySyncConfig for the EventReminder entity.

Responsibilities:

Converts updatedAt millis ↔ Firestore numeric field

Handles soft deletion

Handles type-safe parsing of Firestore fields

Defines the Firestore collection "Reminders"

3. SyncDaoAdapter.kt

Generic interface that abstracts Room operations required for syncing:

getLocalsChangedAfter()

upsertAll()

markDeletedByIds()

getLocalUpdatedAt()

Allows SyncEngine to remain entity-agnostic.

4. ReminderSyncDaoAdapter.kt

Implements SyncDaoAdapter for EventReminder.

Responsibilities:

Fetch all items including soft-deleted

Insert new or updated reminders

Mark reminders deleted

Retrieve updatedAt for conflict resolution

5. SyncEngine.kt (Core Engine)

The heart of the system. Performs two major operations:

🔼 Local → Remote Sync (Push)

Reads sync metadata (lastLocalSyncAt)

Fetches Room items where:

updatedAt > lastLocalSyncAt


Sends changes to Firestore (in batch)

Updates lastLocalSyncAt

Soft-deleted items send tombstones:

{ "isDeleted": true, "updatedAt": <millis> }

🔽 Remote → Local Sync (Pull)

Reads metadata (lastRemoteSyncAt)

Fetches all Firestore docs for the user (limited to 500)

Filters client-side:

remote.updatedAt > lastRemoteSyncAt


Conflict resolution:

LATEST_UPDATED_WINS


Upserts or soft-deletes in Room

Updates lastRemoteSyncAt

6. SyncMetadataEntity & SyncMetadataDao

Stores timestamps of last successful sync:

lastLocalSyncAt

lastRemoteSyncAt

This powers incremental sync (not full sync each time).

7. SyncModule.kt (DI)

Provides:

Firestore instance

UserIdProvider

EntitySyncConfig for reminders

Global SyncConfig (list of all entities)

SyncEngine instance

8. SyncWorker.kt

Background WorkManager job that runs:

syncEngine.syncAll()


Allows syncing even when the user does not open the app.

🔄 Conflict Resolution (LATEST_UPDATED_WINS)

Every change assigns a new:

updatedAt = System.currentTimeMillis()


When syncing remote → local:

if remoteUpdatedAt > localUpdatedAt:
apply remote


This ensures:

Last writer wins

Multi-device consistency

No merge conflicts

Deterministic behavior

🗑 Soft Delete Propagation

Instead of deleting rows:

isDeleted = true
updatedAt = now()


Sync pushes deletion to Firestore, and Firestore propagates deletion to other devices.

This prevents "ghost records" and supports undo/restore functionality.

🚀 Performance & Cost Optimizations
✔ Reduce Firestore billing

Query limited to .limit(500)

No server-side timestamp filtering (avoids composite indexes)

Only changed records synced

✔ Reduce local writes

Metadata updated only when necessary

✔ Batch Firestore writes

Faster network operations

Lower latency

✔ Client-side timestamp filtering

Lower Firestore CPU cost

Avoid numeric/Timestamp conversion overhead

📦 Adding Sync to Another Entity

To sync a new entity:

Create EntitySyncConfig for that entity

Implement DAO adapter

Add to SyncConfig.entities list

SyncEngine automatically picks it up.

🧪 Recommended Testing Strategy
Device-to-device sync:

Device A → Create

Device B → Sync

Device A → Update

Device C → Sync

Device B → Delete

Device A → Sync

Offline scenarios:

Device A offline → Edit → Sync later

Device B online → Edit → Device A comes online → Resolve conflict

High-volume testing:

100 reminders edit sequence

Multiple deletes + updates

🎯 Final Summary

The EventReminder Sync Module is a high-performance, scalable, and robust realtime syncing system designed with:

Incremental deltas

Conflict resolution

Soft deletion

Offline capability

Firestore cost optimization

Multi-device reliability

It is modular, generic, production-ready, and easy to extend.

If you want, I can also generate:

📘 Flow diagrams (plantUML / mermaid)
🔍 Detailed troubleshooting guide
🧪 Unit test templates
⚙ Integration test scripts
📊 Performance benchmark suggestions
-------------------------------------------------------------------------------------------------------

📘 1. Sync Flow Diagrams (Mermaid + PlantUML)

You can paste these into GitHub README, Notion, Obsidian, or any Mermaid/PlantUML viewer.

🌐 Mermaid — High-Level Sync Flow (Bidirectional)
flowchart TD

A[Start Sync] --> B{User Authenticated?}
B -- No --> X[Exit Sync]
B -- Yes --> C[Load SyncMetadata]

C --> D[Local → Remote Sync]
D --> E[Remote → Local Sync]

E --> F[Update SyncMetadata]
F --> Z[End Sync]

🔄 Mermaid — Local → Remote Sync (Push)
flowchart TD

A[Local→Remote] --> B[Query Room: updatedAt > lastLocalSyncAt]
B --> C{Any changes?}
C -- No --> X[Skip Push]
C -- Yes --> D[Build Firestore Batch]

D --> E[For each changed item]
E --> F{IsDeleted?}
F -- Yes --> G[Write Tombstone to Firestore]
F -- No --> H[Write Updated Record to Firestore]

G --> I[Commit Batch]
H --> I[Commit Batch]

I --> J[Update lastLocalSyncAt]
J --> K[Done]

🔽 Mermaid — Remote → Local Sync (Pull)
flowchart TD

A[Remote→Local] --> B[Query Firestore: where uid=<user> limit 500]
B --> C[For each remote doc]

C --> D[Extract remoteUpdatedAt]
D --> E{remoteUpdatedAt > lastRemoteSyncAt?}
E -- No --> X[Skip Doc]

E -- Yes --> F{Is Tombstone?}
F -- Yes --> G[Local markDeleted(id)]
F -- No --> H[Local upsert(fromRemote)]

G --> I[Track maxRemoteUpdatedAt]
H --> I[Track maxRemoteUpdatedAt]

I --> J[Update lastRemoteSyncAt]
J --> K[Done]

🧩 PlantUML — Full Sync Engine Sequence Diagram
@startuml
actor User
participant SyncEngine
participant SyncMetadataDao
participant Room as R
participant Firestore as F

User -> SyncEngine: syncAll()

SyncEngine -> SyncMetadataDao: get(metadata)
SyncMetadataDao --> SyncEngine: lastLocalSyncAt, lastRemoteSyncAt

== Local → Remote ==
SyncEngine -> R: getLocalsChangedAfter(lastLocalSyncAt)
R --> SyncEngine: changedLocals

alt no local changes
SyncEngine -> SyncEngine: skip push
else
SyncEngine -> F: batch.set(local updates)
F --> SyncEngine: commit OK
SyncEngine -> SyncMetadataDao: update(lastLocalSyncAt)
end

== Remote → Local ==
SyncEngine -> F: get(uid=userId, limit=500)
F --> SyncEngine: remoteDocs

loop for each doc
SyncEngine -> SyncEngine: extract updatedAt

    alt updatedAt <= lastRemoteSyncAt
        SyncEngine -> SyncEngine: skip doc
    else
        alt isDeleted
            SyncEngine -> R: markDeleted(id)
        else
            SyncEngine -> R: upsert(fromRemote)
        end
    end
end

SyncEngine -> SyncMetadataDao: update(lastRemoteSyncAt)
SyncEngine --> User: Sync Complete
@enduml

🔍 2. Detailed Troubleshooting Guide for Sync Module

A production-ready guide to debug sync issues.

🔧 TROUBLESHOOTING GUIDE — EVENT REMINDER SYNC ENGINE

Use this when sync behaves incorrectly across multiple devices.

🧩 SECTION 1 — Common Symptoms & Fixes
🟥 Symptom: Remote updates do NOT appear on another device
✓ Likely Cause

updatedAt stored as Firestore Timestamp, but client expects Number

lastRemoteSyncAt incorrect

Local record's updatedAt > remoteUpdatedAt (old logic – fixed now)

✓ Fix

Ensure Firestore stores "updatedAt" as numeric epoch millis

Verify that Remote→Local filtering uses:

remoteUpdatedAt > lastRemoteSyncAt

🟥 Symptom: Local changes get overwritten by old remote data
✓ Likely Cause

Wrong conflict strategy

Remote timestamp older but still applied

✓ Fix

Ensure conflict strategy in EntitySyncConfig is:

conflictStrategy = ConflictStrategy.LATEST_UPDATED_WINS

🟥 Symptom: Sync causes duplicate reminders
✓ Likely Cause

getLocalId returns string inconsistent with Firestore document IDs

Using autogenerated Room IDs without syncing back correctly

✓ Fix

Make sure IDs map perfectly:

getLocalId = { it.id.toString() }
fromRemote = { id, ... -> EventReminder(id = id.toLong(), ...) }

🟥 Symptom: Soft delete works only on one device
✓ Likely Cause

isDeleted not stored in Firestore

Tombstone not applied locally

✓ Fix Checklist

Local delete must set:
isDeleted = true + updatedAt = now()

Firestore doc must contain:
{ isDeleted: true, updatedAt: <millis> }

Remote sync must call:
daoAdapter.markDeletedByIds()

🧭 SECTION 2 — Debugging Workflow
✔ Step 1: Check Firestore document

Look for correct fields:

updatedAt: <epoch millis>
isDeleted: true/false
uid: <user>


If updatedAt is a Timestamp → wrong.

✔ Step 2: Check sync_metadata table

Fields must contain:

lastLocalSyncAt = <epoch millis>
lastRemoteSyncAt = <epoch millis>


If lastRemoteSyncAt never updates → Remote→Local logic incorrect.

✔ Step 3: Enable REMOTE_DEBUG Logs

Your SyncEngine already logs every step:

REMOTE_DEBUG E ENTERED syncRemoteToLocal()
REMOTE_DEBUG E PROCESS id=1 remoteUpdatedAt=...
REMOTE_DEBUG E shouldApply=true


Using these logs, verify:

Was remoteUpdatedAt extracted?

Was localUpdatedAt compared?

Was doc skipped or applied?

Was lastRemoteSyncAt updated?

✔ Step 4: Verify DAO behavior

ReminderSyncDaoAdapter must use:

getAllIncludingDeletedOnce()


If deleted records are hidden, the device cannot sync deletions.

✔ Step 5: Force resync

You can reset:

DELETE FROM sync_metadata


This forces a FULL SCAN.

If sync works with full scan but not incrementally, metadata timestamps are wrong.

🚧 SECTION 3 — Common Developer Mistakes (Prevented in Your Code)
❌ Using Firestore Timestamp → Conflicts & filtering failures
❌ Filtering on server-side .whereGreaterThan("updatedAt", lastRemoteSyncAt)
❌ Comparing remoteUpdatedAt versus localUpdatedAt incorrectly
❌ Not syncing tombstones
❌ Not syncing deleted records from Room

Your final architecture avoids all these issues.

🧲 SECTION 4 — When to Reset Sync Metadata

Reset sync_metadata (only for debugging!) when:

You change updatedAt storage format

Firestore rules changed

Client had corrupted timestamps

Run:

DELETE FROM sync_metadata;


Next sync becomes a full sync.

🟢 SECTION 5 — Contact Points for Further Improvements

Per-field diff sync (reduce payload)

Partial sync by category

Real-time listener for near-instant sync

Sync diagnostics dashboard in-app

🎉 Done!

You now have:

✔ High-quality Mermaid diagrams
✔ High-quality PlantUML sequence diagram
✔ A complete Sync Troubleshooting Guide

Want me to generate:

📗 In-app sync status UI design?
🧪 Unit test template for SyncEngine?
📐 Firestore index configuration guide?

Just ask!
-----------------------------------------------------------------------------------------

🚨 THE MAJOR FLAW: Using System.currentTimeMillis() as the source of truth for conflict resolution

Your entire sync system depends on:

updatedAt = System.currentTimeMillis()
LATEST_UPDATED_WINS


This works on paper but breaks in real-world scenarios because:

❌ 1. Device clocks are not synchronized

Different devices often have:

Time drift

Wrong timezone

Wrong date

No internet (NTP)

User manually changing time

Example:

Device A time = correct
Device B time = 5 minutes ahead

Device B edits a reminder → updatedAt = T+5 minutes
Device A edits the same reminder 10 seconds later → updatedAt = T+10 seconds
→ Sync will treat A's edit as older, even though it's the real latest edit.

Outcome:

💥 Wrong device wins
💥 Incorrect conflict resolution
💥 Ghost overwrites
💥 Lost updates

This WILL happen in real-world multi-device sync.

❌ 2. Firestore + Android have different system clock sources

Even if both devices are correct, Firestore does not guarantee consistency with client clocks.

Unsynced devices will generate timestamps wildly different from others.

❌ 3. Offline edits break ordering

If user edits offline for hours or days:

Device A (online) → several updates

Device B (offline) → update with past timestamp

When Device B syncs:

→ Its update will be treated as older, even though it's logically the final update the user made.

This breaks user expectations.

❌ 4. Millisecond precision does not guarantee monotonicity

Two updates can be generated within the same millisecond, especially on fast devices.

This causes:

LATEST_UPDATED_WINS ties

Random ordering

Non-deterministic results

🧨 Bottom Line: Client clock is NOT a reliable source for sync ordering

Every real production sync system eventually hits this problem.

Examples:

Google Drive uses server timestamps

Slack uses version counters

Notion uses server op logs

Firebase Realtime DB uses server timestamps %TIMESTAMP%

✔ WHAT YOU SHOULD USE INSTEAD
✅ Option 1: Firestore Server Timestamp

When writing:

updatedAt = FieldValue.serverTimestamp()


Then always read numeric millis back.

Pros:

Guaranteed monotonic per document

No device time needed

Works offline (server applies timestamp later)

Cons:

Requires adjusting sync write/read logic

✅ Option 2: Version Counter (Recommended for your architecture)

Each reminder has an integer:

version: Long


Every update increments version:

version = oldVersion + 1
updatedAt remains client timestamp only for UI


Conflict resolution uses:

LATEST_VERSION_WINS


Pros:

Device time irrelevant

Always correct ordering

Deterministic

Easy to merge offline changes

This is how production apps like Linear, Notion, Trello work.

✅ Option 3: Hybrid approach

Store both:

serverUpdatedAt (server timestamp)
clientUpdatedAt (local millis for UI)


Use server for conflict resolution
Use client for display only
This is the most robust system of all.

🎯 Summary — The Flaw & Fix
❗ Major flaw: Using device clock (System.currentTimeMillis) to determine which update wins.
❗ This will cause:

Wrong conflict resolution

Lost updates

Devices becoming “dominant” due to clock drift

Impossible-to-debug sync bugs in the future

✔ Fix: Use Server timestamps or Version counter.