# Car Servicing Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A permanent record of work done on the car — what, when, at what odometer, by which
workshop, and what it cost — with every cost figure derived rather than stored.

**Architecture:** Three tables (`vehicles`, `services`, `service_items`) on migration 11 → 12,
a `VehicleDao` whose summary columns compute the derived figures in SQL, a `VehicleRepository`
mirroring `RestaurantRepository`, one `IndexSource`, and a `feature/vehicle` package of four
screens. Photos, tags, and the workshop's location are spine calls and add no columns.

**Tech Stack:** Kotlin · Room · Hilt · Compose · Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-05-khata-vehicle-design.md`

## Global Constraints

- Money is `Long` paisa. Never floating point. BDT only. (D7)
- Every table carries the house quartet: `uuid` (unique index), `createdAt`, `updatedAt`,
  `deletedAt` nullable. Deletes are soft.
- All day/month/year boundaries are computed in `Asia/Dhaka` via the existing `KhataClock` /
  time helpers, never UTC.
- English interface. Bengali script must render in workshop names, item names, and notes.
- Routine controls live in the bottom third of the screen; nothing routine in a top corner.
- `BackupRepository.SCHEMA_VERSION` must equal the Room `version` at all times.
- No new dependencies. Coil, Haze, `AmountKeypad`, `MoneyText`, `KhataGlass`, `FieldScaffold`
  already exist and are reused as-is.
- Migration SQL is copied **verbatim** from `app/schemas/com.wasif.khata.core.data.KhataDatabase/12.json`
  after a build, never hand-written — Room compares an identity hash at open time.

---

### Task 1: Schema — three tables, the DAO, and the migration

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/data/entity/VehicleEntity.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/entity/ServiceEntity.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/entity/ServiceItemEntity.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/dao/VehicleDao.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/KhataDatabase.kt` (entities, `version = 12`, `vehicleDao()`)
- Modify: `app/src/main/java/com/wasif/khata/core/data/migration/Migrations.kt` (add `MIGRATION_11_12`)
- Modify: `app/src/main/java/com/wasif/khata/core/data/di/DatabaseModule.kt` (register the migration)
- Modify: `app/src/main/java/com/wasif/khata/core/backup/BackupRepository.kt` (`SCHEMA_VERSION = 12`)
- Test: `app/src/test/java/com/wasif/khata/core/data/migration/Migration11To12Test.kt`

**Interfaces:**
- Produces: `VehicleEntity`, `ServiceEntity`, `ServiceItemEntity`; `VehicleDao` with
  `upsert(VehicleEntity): Long`, `upsertService(ServiceEntity): Long`,
  `upsertItem(ServiceItemEntity): Long`, `firstVehicle(): VehicleEntity?`,
  `observeServices(vehicleId: Long): Flow<List<ServiceSummary>>`,
  `serviceSummary(id: Long): Flow<ServiceSummary?>`, `itemsFor(serviceId: Long): List<ServiceItemEntity>`,
  `observeItemsFor(serviceId: Long): Flow<List<ServiceItemEntity>>`,
  `costTotals(vehicleId: Long): Flow<CostTotals>`, `spendByItem(vehicleId: Long): Flow<List<ItemSpend>>`,
  `summariesByIds(ids: List<Long>): List<ServiceSummary>`, `findService(id: Long): ServiceEntity?`,
  `allServiceIdsForIndex(): List<Long>`.

- [ ] **Step 1: Write the three entities**

`VehicleEntity.kt`:

```kotlin
package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row, in practice. The table exists so services have a real parent to hang off:
 * a second car is plausible, and a migration that has to invent a parent for orphaned
 * services later is not. The UI shows no picker (C1).
 */
@Entity(tableName = "vehicles", indices = [Index(value = ["uuid"], unique = true)])
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val registration: String? = null,
    /** The dash reading as of the last time it was entered. Nullable: unknown is not zero. */
    val odometerKm: Int? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

`ServiceEntity.kt`:

```kotlin
package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "services",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["vehicleId"]),
        Index(value = ["servicedAt"]),
    ],
)
data class ServiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val vehicleId: Long,
    /** UTC epoch millis. Bucketed for display in Asia/Dhaka, never UTC. */
    val servicedAt: Long,
    /** Nullable: a job where the dash went unread is still a job, and every derived
     *  figure needing it skips this row rather than reading it as zero. */
    val odometerKm: Int? = null,
    /** The workshop, as a spine place (C5). */
    val placeId: Long? = null,
    /** The bill. Item costs are optional detail beneath it, never its source (C2). */
    val costMinor: Long? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

`ServiceItemEntity.kt`:

```kotlin
package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Free text, no catalogue (C6). "What have brake pads cost me" is a GROUP BY. */
@Entity(
    tableName = "service_items",
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["serviceId"])],
)
data class ServiceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val serviceId: Long,
    val name: String,
    val costMinor: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

- [ ] **Step 2: Write `VehicleDao`**

Follow `RestaurantDao`'s shape: a `data class` of derived columns plus a shared
`SUMMARY_COLUMNS` string.

```kotlin
package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.ServiceEntity
import com.wasif.khata.core.data.entity.ServiceItemEntity
import com.wasif.khata.core.data.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

/**
 * One service with everything the list shows, computed rather than stored: the
 * workshop's name, the item names on one line, and the sum of whatever items carry a
 * cost. Nothing here can drift out of step with the rows underneath it.
 */
data class ServiceSummary(
    val id: Long,
    val servicedAt: Long,
    val odometerKm: Int?,
    val costMinor: Long?,
    val note: String?,
    val placeId: Long?,
    val placeName: String?,
    val mapsUrl: String?,
    /** Comma-joined item names, for the quiet line under each row. Null when none. */
    val itemNames: String?,
    val itemCount: Int,
    /** Sum across items that carry a cost. */
    val itemCostTotal: Long?,
    /** Items that carry no cost. Zero is what lets the "other" line show (§3). */
    val unpricedItemCount: Int,
)

/** Totals for the header. All derived, none stored (C7). */
data class CostTotals(
    val totalMinor: Long?,
    val serviceCount: Int,
    val minOdometerKm: Int?,
    val maxOdometerKm: Int?,
)

data class ItemSpend(val name: String, val totalMinor: Long, val occurrences: Int)

data class YearSpend(val year: String, val totalMinor: Long)

private const val SUMMARY_COLUMNS = """
    SELECT s.id, s.servicedAt, s.odometerKm, s.costMinor, s.note, s.placeId,
      (SELECT p.name FROM places p WHERE p.id = s.placeId) AS placeName,
      (SELECT p.mapsUrl FROM places p WHERE p.id = s.placeId) AS mapsUrl,
      (SELECT GROUP_CONCAT(i.name, ', ') FROM service_items i
       WHERE i.serviceId = s.id AND i.deletedAt IS NULL) AS itemNames,
      (SELECT COUNT(*) FROM service_items i
       WHERE i.serviceId = s.id AND i.deletedAt IS NULL) AS itemCount,
      (SELECT SUM(i.costMinor) FROM service_items i
       WHERE i.serviceId = s.id AND i.deletedAt IS NULL) AS itemCostTotal,
      (SELECT COUNT(*) FROM service_items i
       WHERE i.serviceId = s.id AND i.deletedAt IS NULL AND i.costMinor IS NULL)
        AS unpricedItemCount
    FROM services s
"""

@Dao
interface VehicleDao {

    @Upsert
    suspend fun upsert(vehicle: VehicleEntity): Long

    @Upsert
    suspend fun upsertService(service: ServiceEntity): Long

    @Upsert
    suspend fun upsertItem(item: ServiceItemEntity): Long

    @Query("SELECT * FROM vehicles WHERE deletedAt IS NULL ORDER BY id LIMIT 1")
    suspend fun firstVehicle(): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE deletedAt IS NULL ORDER BY id LIMIT 1")
    fun observeVehicle(): Flow<VehicleEntity?>

    @Query("SELECT * FROM services WHERE id = :id")
    suspend fun findService(id: Long): ServiceEntity?

    @Query(
        SUMMARY_COLUMNS + """
        WHERE s.vehicleId = :vehicleId AND s.deletedAt IS NULL
        ORDER BY s.servicedAt DESC, s.id DESC
        """,
    )
    fun observeServices(vehicleId: Long): Flow<List<ServiceSummary>>

    @Query(SUMMARY_COLUMNS + " WHERE s.id = :id AND s.deletedAt IS NULL")
    fun serviceSummary(id: Long): Flow<ServiceSummary?>

    /** Search results: most recent job first, as everywhere (spine §7). */
    @Query(
        SUMMARY_COLUMNS + """
        WHERE s.id IN (:ids) AND s.deletedAt IS NULL
        ORDER BY s.servicedAt DESC, s.id DESC
        """,
    )
    suspend fun summariesByIds(ids: List<Long>): List<ServiceSummary>

    @Query("SELECT * FROM service_items WHERE serviceId = :serviceId AND deletedAt IS NULL ORDER BY sortOrder, id")
    suspend fun itemsFor(serviceId: Long): List<ServiceItemEntity>

    @Query("SELECT * FROM service_items WHERE serviceId = :serviceId AND deletedAt IS NULL ORDER BY sortOrder, id")
    fun observeItemsFor(serviceId: Long): Flow<List<ServiceItemEntity>>

    /**
     * The odometer span comes back as two numbers rather than one difference: cost per
     * km is null on fewer than two readings and on a zero span, and that is a decision
     * about meaning, not arithmetic. It belongs in Kotlin where it can be tested.
     */
    @Query(
        """
        SELECT SUM(costMinor) AS totalMinor,
               COUNT(*) AS serviceCount,
               MIN(odometerKm) AS minOdometerKm,
               MAX(odometerKm) AS maxOdometerKm
        FROM services WHERE vehicleId = :vehicleId AND deletedAt IS NULL
        """,
    )
    fun costTotals(vehicleId: Long): Flow<CostTotals>

    /**
     * COLLATE NOCASE so "Oil Filter" and "oil filter" are one answer rather than two
     * halves of one. MIN(name) picks a stable spelling to display.
     */
    @Query(
        """
        SELECT MIN(i.name) AS name, SUM(i.costMinor) AS totalMinor, COUNT(*) AS occurrences
        FROM service_items i
        JOIN services s ON s.id = i.serviceId
        WHERE s.vehicleId = :vehicleId AND s.deletedAt IS NULL AND i.deletedAt IS NULL
          AND i.costMinor IS NOT NULL
        GROUP BY i.name COLLATE NOCASE
        ORDER BY totalMinor DESC
        """,
    )
    fun spendByItem(vehicleId: Long): Flow<List<ItemSpend>>

    /**
     * Grouped in Asia/Dhaka, not UTC: '+6 hours' is the offset, and Dhaka has no DST,
     * so the shift is a constant rather than a table lookup.
     */
    @Query(
        """
        SELECT strftime('%Y', datetime(servicedAt / 1000, 'unixepoch', '+6 hours')) AS year,
               SUM(costMinor) AS totalMinor
        FROM services
        WHERE vehicleId = :vehicleId AND deletedAt IS NULL AND costMinor IS NOT NULL
        GROUP BY year ORDER BY year DESC
        """,
    )
    fun spendByYear(vehicleId: Long): Flow<List<YearSpend>>

    @Query("SELECT id FROM services WHERE deletedAt IS NULL")
    suspend fun allServiceIdsForIndex(): List<Long>

    @Query("UPDATE services SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteService(id: Long, now: Long)

    @Query("UPDATE service_items SET deletedAt = :now, updatedAt = :now WHERE serviceId = :serviceId")
    suspend fun softDeleteItemsFor(serviceId: Long, now: Long)
}
```

- [ ] **Step 3: Register in `KhataDatabase`, build, copy the schema into a migration**

In `KhataDatabase.kt`: add `VehicleEntity::class`, `ServiceEntity::class`,
`ServiceItemEntity::class` to `entities`, set `version = 12`, add
`abstract fun vehicleDao(): VehicleDao`.

Run: `./gradlew :app:kspDebugKotlin`
Expected: `app/schemas/com.wasif.khata.core.data.KhataDatabase/12.json` is written.

Then add `MIGRATION_11_12` to `Migrations.kt`, with every `CREATE TABLE` / `CREATE INDEX`
statement copied **verbatim** from `12.json`, in the style of `MIGRATION_10_11`:

```kotlin
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Statements copied verbatim from schemas/12.json, as every migration since
        // 7 -> 8 has been. Room compares its identity hash at open time, so anything
        // adjusted by hand here fails at runtime rather than at compile time.
        //
        // Three tables and nothing else: the workshop is a places id, photos are
        // media_links, and there is no transactionId (C3).
        // ... CREATE TABLE vehicles / services / service_items and their indices
    }
}
```

Register it in `DatabaseModule.kt` alongside the others, and set
`BackupRepository.SCHEMA_VERSION = 12`.

- [ ] **Step 4: Write `Migration11To12Test`**

Copy `Migration11To12Test` from `Migration10To11Test.kt` wholesale, changing `TEST_DB` to
`migration-11-12-test.db`, `createV10Database()` to `createV11Database()` reading `schemaJson(11)`,
and the assertions to insert a vehicle, a service, and an item through the reopened Room database:

```kotlin
@Test
fun `migrates 11 to 12 and the three tables accept rows`() = runTest {
    createV11Database().close()

    val db = Room.databaseBuilder(context, KhataDatabase::class.java, TEST_DB)
        .addMigrations(MIGRATION_11_12)
        .build()

    val dao = db.vehicleDao()
    val vehicleId = dao.upsert(
        VehicleEntity(uuid = "v1", name = "Car", createdAt = 1L, updatedAt = 1L),
    )
    val serviceId = dao.upsertService(
        ServiceEntity(
            uuid = "s1", vehicleId = vehicleId, servicedAt = 1L,
            odometerKm = 42_000, costMinor = 500_00L, createdAt = 1L, updatedAt = 1L,
        ),
    )
    dao.upsertItem(
        ServiceItemEntity(
            uuid = "i1", serviceId = serviceId, name = "Oil filter",
            costMinor = 120_00L, createdAt = 1L, updatedAt = 1L,
        ),
    )

    assertEquals(1, dao.itemsFor(serviceId).size)
    assertEquals(42_000, dao.findService(serviceId)?.odometerKm)
    db.close()
}
```

- [ ] **Step 5: Run everything**

Run: `./gradlew :app:testDebugUnitTest --tests '*Migration*'`
Expected: PASS, including the existing migration tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main app/src/test app/schemas
git commit -m "feat(vehicle): three tables, and nothing the spine already has"
```

---

### Task 2: `VehicleRepository` and the derived figures

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/data/repository/VehicleRepository.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/repository/VehicleRepositoryTest.kt`

**Interfaces:**
- Consumes: `VehicleDao`, `TagRepository`, `SearchIndex`, `KhataClock` from Task 1 and the spine.
- Produces: `ENTITY_VEHICLE_SERVICE = "vehicle_service"`, `ServiceDraft`, `ItemDraft`,
  `VehicleRepository.findOrCreateVehicle(): Long`, `saveService(ServiceDraft, List<ItemDraft>): Long`,
  `costPerKm(CostTotals): Double?`, `otherMinor(ServiceSummary): Long?`, `deleteService(Long)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class VehicleRepositoryTest {
    // Build the in-memory database and repository exactly as RestaurantRepositoryTest does.

    @Test
    fun `findOrCreateVehicle returns the same row on a second call`() = runTest {
        val first = repository.findOrCreateVehicle()
        val second = repository.findOrCreateVehicle()
        assertEquals(first, second)
    }

    @Test
    fun `cost per km needs two readings`() {
        assertNull(costPerKm(CostTotals(500_00L, 1, 42_000, 42_000)))
        assertNull(costPerKm(CostTotals(500_00L, 2, 42_000, 42_000)))   // zero span
        assertNull(costPerKm(CostTotals(500_00L, 2, null, null)))        // no readings
        assertEquals(10.0, costPerKm(CostTotals(500_00L, 2, 42_000, 42_050))!!, 0.001)
    }

    @Test
    fun `other is the remainder only when every item is priced`() {
        val allPriced = summary(costMinor = 500_00L, itemCostTotal = 380_00L, unpriced = 0, itemCount = 2)
        assertEquals(120_00L, otherMinor(allPriced))

        val someUnpriced = summary(costMinor = 500_00L, itemCostTotal = 380_00L, unpriced = 1, itemCount = 2)
        assertNull(otherMinor(someUnpriced))

        val noItems = summary(costMinor = 500_00L, itemCostTotal = null, unpriced = 0, itemCount = 0)
        assertNull(otherMinor(noItems))
    }

    @Test
    fun `saving a service twice leaves the items it holds, not two copies`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        val id = repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1L, costMinor = 500_00L),
            listOf(ItemDraft("Oil filter", 120_00L), ItemDraft("Brake pads", null)),
        )
        repository.saveService(
            ServiceDraft(id = id, vehicleId = vehicleId, servicedAt = 1L, costMinor = 500_00L),
            listOf(ItemDraft("Oil filter", 120_00L)),
        )
        assertEquals(listOf("Oil filter"), dao.itemsFor(id).map { it.name })
    }

    @Test
    fun `spend by item folds case and skips unpriced rows`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1L),
            listOf(ItemDraft("Oil Filter", 100_00L)),
        )
        repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 2L),
            listOf(ItemDraft("oil filter", 150_00L), ItemDraft("Wiper", null)),
        )
        val spend = dao.spendByItem(vehicleId).first()
        assertEquals(1, spend.size)
        assertEquals(250_00L, spend.single().totalMinor)
    }

    @Test
    fun `yearly spend buckets in Dhaka, not UTC`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        // 2025-12-31 22:00 Dhaka == 2025-12-31 16:00 UTC. Both are 2025; the row that
        // separates the two zones is the one just after midnight Dhaka time.
        // 2026-01-01 01:00 Dhaka == 2025-12-31 19:00 UTC -> must bucket as 2026.
        repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1767207600_000L, costMinor = 100_00L),
            emptyList(),
        )
        assertEquals("2026", dao.spendByYear(vehicleId).first().single().year)
    }

    @Test
    fun `a soft-deleted service leaves the totals and the index`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        val id = repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1L, costMinor = 500_00L),
            listOf(ItemDraft("Oil filter", 120_00L)),
        )
        repository.deleteService(id)
        assertNull(dao.costTotals(vehicleId).first().totalMinor)
        assertEquals(emptyList<ItemSpend>(), dao.spendByItem(vehicleId).first())
    }
}
```

- [ ] **Step 2: Run, watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*VehicleRepositoryTest*'`
Expected: FAIL — unresolved reference `VehicleRepository`.

- [ ] **Step 3: Write the repository**

```kotlin
package com.wasif.khata.core.data.repository

/** The one entity type this module links against in the spine's tables. */
const val ENTITY_VEHICLE_SERVICE = "vehicle_service"

/** A service as the editor holds it: no uuid, no timestamps, an id only when editing. */
data class ServiceDraft(
    val id: Long = 0,
    val vehicleId: Long,
    val servicedAt: Long,
    val odometerKm: Int? = null,
    val placeId: Long? = null,
    val costMinor: Long? = null,
    val note: String? = null,
)

/** Free text and an optional cost. No catalogue, so there is nothing else to carry. */
data class ItemDraft(val name: String, val costMinor: Long? = null)

/**
 * Null rather than a number whenever the number would be invented: fewer than two
 * readings, no readings at all, or a span of zero. A made-up cost per km is worse
 * than an absent one, because it looks like an answer.
 */
fun costPerKm(totals: CostTotals): Double? {
    val total = totals.totalMinor ?: return null
    val min = totals.minOdometerKm ?: return null
    val max = totals.maxOdometerKm ?: return null
    val span = max - min
    return if (span <= 0) null else total.toDouble() / span
}

/**
 * The remainder is labour and VAT only when every item carries a cost. With an
 * unpriced item in the list it is partly that item, and showing it would present an
 * unknown as a known.
 */
fun otherMinor(summary: ServiceSummary): Long? {
    if (summary.itemCount == 0 || summary.unpricedItemCount > 0) return null
    val cost = summary.costMinor ?: return null
    val items = summary.itemCostTotal ?: return null
    return (cost - items).takeIf { it > 0 }
}

@Singleton
class VehicleRepository @Inject constructor(
    private val dao: VehicleDao,
    private val searchIndex: SearchIndex,
    private val clock: KhataClock,
) {
    /**
     * Lazy rather than seeded: a fresh install builds its schema from the entities and
     * runs no migration, so seeding in SQL would mean the same row written in two
     * places and eventually in only one.
     */
    suspend fun findOrCreateVehicle(): Long {
        dao.firstVehicle()?.let { return it.id }
        val now = clock.now()
        return dao.upsert(
            VehicleEntity(uuid = UUID.randomUUID().toString(), name = "Car", createdAt = now, updatedAt = now),
        )
    }

    fun observeVehicle(): Flow<VehicleEntity?> = dao.observeVehicle()

    suspend fun saveVehicle(vehicle: VehicleEntity) {
        dao.upsert(vehicle.copy(updatedAt = clock.now()))
    }

    fun observeServices(vehicleId: Long): Flow<List<ServiceSummary>> = dao.observeServices(vehicleId)

    fun observeService(id: Long): Flow<ServiceSummary?> = dao.serviceSummary(id)

    fun observeItems(serviceId: Long): Flow<List<ServiceItemEntity>> = dao.observeItemsFor(serviceId)

    fun costTotals(vehicleId: Long): Flow<CostTotals> = dao.costTotals(vehicleId)

    fun spendByItem(vehicleId: Long): Flow<List<ItemSpend>> = dao.spendByItem(vehicleId)

    fun spendByYear(vehicleId: Long): Flow<List<YearSpend>> = dao.spendByYear(vehicleId)

    suspend fun findService(id: Long): ServiceEntity? = dao.findService(id)

    suspend fun items(serviceId: Long): List<ServiceItemEntity> = dao.itemsFor(serviceId)

    /**
     * The service and its items in one call, because they are one thing the user
     * filled in. Items are rewritten rather than merged: the editor holds the whole
     * list, so saving it twice must leave that list rather than two copies of it --
     * the same move RestaurantRepository.saveVisit makes with dishes.
     */
    suspend fun saveService(service: ServiceDraft, items: List<ItemDraft>): Long {
        val now = clock.now()
        val existing = service.id.takeIf { it > 0 }?.let { dao.findService(it) }
        val id = dao.upsertService(
            ServiceEntity(
                id = service.id,
                uuid = existing?.uuid ?: UUID.randomUUID().toString(),
                vehicleId = service.vehicleId,
                servicedAt = service.servicedAt,
                odometerKm = service.odometerKm,
                placeId = service.placeId,
                costMinor = service.costMinor,
                note = service.note,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        dao.softDeleteItemsFor(id, now)
        items.forEachIndexed { index, item ->
            dao.upsertItem(
                ServiceItemEntity(
                    uuid = UUID.randomUUID().toString(),
                    serviceId = id,
                    name = item.name.trim(),
                    costMinor = item.costMinor,
                    sortOrder = index,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        searchIndex.reindex(ENTITY_VEHICLE_SERVICE, id)
        return id
    }

    suspend fun deleteService(id: Long) {
        val now = clock.now()
        dao.softDeleteItemsFor(id, now)
        dao.softDeleteService(id, now)
        searchIndex.remove(ENTITY_VEHICLE_SERVICE, id)
    }
}
```

- [ ] **Step 4: Run until green**

Run: `./gradlew :app:testDebugUnitTest --tests '*VehicleRepositoryTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat(vehicle): the repository, and every cost figure derived"
```

---

### Task 3: Search

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/search/VehicleServiceIndexSource.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/di/RepositoryModule.kt` (`@IntoSet` binding)
- Modify: `app/src/main/java/com/wasif/khata/feature/search/SearchUiState.kt` (a `services` group)
- Modify: `app/src/main/java/com/wasif/khata/feature/search/SearchViewModel.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/search/SearchScreen.kt`
- Test: `app/src/test/java/com/wasif/khata/core/search/VehicleServiceIndexSourceTest.kt`

**Interfaces:**
- Consumes: `IndexSource`, `SearchIndex`, `ENTITY_VEHICLE_SERVICE`, `VehicleDao`, `PlaceDao`.
- Produces: `VehicleServiceIndexSource`; `SearchUiState.services: List<ServiceSummary>`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `indexed text carries item names, the note, and the workshop`() = runTest {
    val vehicleId = repository.findOrCreateVehicle()
    val placeId = placeDao.upsert(PlaceEntity(uuid = "p1", name = "Navana Workshop", createdAt = 1, updatedAt = 1))
    val id = repository.saveService(
        ServiceDraft(vehicleId = vehicleId, servicedAt = 1L, placeId = placeId, note = "rattling"),
        listOf(ItemDraft("Front brake pads", 300_00L)),
    )

    val text = source.textFor(id)!!
    assertTrue(text.contains("Front brake pads"))
    assertTrue(text.contains("Navana Workshop"))
    assertTrue(text.contains("rattling"))
}

@Test
fun `a deleted service indexes as null`() = runTest {
    val vehicleId = repository.findOrCreateVehicle()
    val id = repository.saveService(ServiceDraft(vehicleId = vehicleId, servicedAt = 1L), emptyList())
    repository.deleteService(id)
    assertNull(source.textFor(id))
}
```

- [ ] **Step 2: Run, watch it fail, then write the source**

```kotlin
/**
 * One indexed row per service, never one per item: "brake" should land you on the job
 * rather than on a fragment inside it -- the same call RestaurantIndexSource makes
 * about visits.
 */
@Singleton
class VehicleServiceIndexSource @Inject constructor(
    private val vehicles: VehicleDao,
    private val places: PlaceDao,
    private val tags: TagDao,
) : IndexSource {
    override val entityType = ENTITY_VEHICLE_SERVICE

    override suspend fun textFor(entityId: Long): String? {
        val row = vehicles.findService(entityId)?.takeIf { it.deletedAt == null } ?: return null
        val place = row.placeId?.let { places.findById(it) }
        val items = vehicles.itemsFor(entityId).map { it.name }
        val tagNames = tags.tagsFor(ENTITY_VEHICLE_SERVICE, entityId).map { it.name }
        return (listOf(row.note, place?.name, place?.address) + items + tagNames)
            .filterNot { it.isNullOrBlank() }
            .joinToString(" ")
    }

    override suspend fun allIds(): List<Long> = vehicles.allServiceIdsForIndex()
}
```

Bind it in `RepositoryModule` beside the other three:

```kotlin
@Binds
@IntoSet
abstract fun bindVehicleServiceIndexSource(impl: VehicleServiceIndexSource): IndexSource
```

- [ ] **Step 3: Add the fourth group to the search screen**

`SearchUiState` gains `val services: List<ServiceSummary> = emptyList()`, and both `isEmpty`
and `total` include it. `SearchViewModel` resolves `ENTITY_VEHICLE_SERVICE` ids through
`vehicleDao.summariesByIds(ids)` exactly as it resolves restaurants. `SearchScreen` renders a
`Car service` group in the same shape as the existing ones.

- [ ] **Step 4: Run, then commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*Search*' --tests '*VehicleServiceIndexSource*'`
Expected: PASS.

```bash
git add app/src/main app/src/test
git commit -m "feat(vehicle): brake pads and workshops are findable"
```

---

### Task 4: The service editor

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/vehicle/ServiceEditorScreen.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/vehicle/ServiceEditorUiState.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/vehicle/ServiceEditorViewModel.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/vehicle/ServiceEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `VehicleRepository`, `PlaceRepository`, `PlaceDao`, `MediaStore`, `AmountKeypad`,
  `FieldScaffold`, `KhataGlass`, `MoneyText`.
- Produces: `ServiceEditorUiState(servicedAt, odometerKm, workshopQuery, workshopSuggestions,
  placeId, costMinor, items, note, photos, saving)`, `ServiceEditorActions`.

Model this file on `feature/restaurants/VisitEditorScreen.kt` and its view model — same field
order, same `AmountKeypad` usage, same photo picker, same save-and-pop.

- [ ] **Step 1: Write the failing view-model tests**

```kotlin
@Test
fun `saving with a typed workshop that matches nothing creates the place`() = runTest {
    viewModel.onWorkshopChange("Navana Workshop")
    viewModel.onCostChange(500_00L)
    viewModel.onSave()
    advanceUntilIdle()

    val service = vehicleDao.findService(1L)!!
    assertNotNull(service.placeId)
    assertEquals("Navana Workshop", placeDao.findById(service.placeId!!)?.name)
}

@Test
fun `an empty odometer saves as null rather than zero`() = runTest {
    viewModel.onOdometerChange("")
    viewModel.onSave()
    advanceUntilIdle()
    assertNull(vehicleDao.findService(1L)?.odometerKm)
}

@Test
fun `blank item rows are dropped rather than saved as empty names`() = runTest {
    viewModel.onItemAdd()
    viewModel.onItemNameChange(0, "   ")
    viewModel.onSave()
    advanceUntilIdle()
    assertEquals(emptyList<String>(), vehicleDao.itemsFor(1L).map { it.name })
}

@Test
fun `editing an existing service loads its items`() = runTest {
    val vehicleId = repository.findOrCreateVehicle()
    val id = repository.saveService(
        ServiceDraft(vehicleId = vehicleId, servicedAt = 1L),
        listOf(ItemDraft("Oil filter", 120_00L)),
    )
    val editing = viewModelFor(serviceId = id)
    advanceUntilIdle()
    assertEquals(listOf("Oil filter"), editing.state.value.items.map { it.name })
}
```

- [ ] **Step 2: Run, fail, implement, pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*ServiceEditorViewModelTest*'`
Expected: FAIL first, PASS after the view model exists.

The workshop field resolves like this, and the comment explains why it is not FTS:

```kotlin
// A LIKE prefix over a short list of places, ordered by most recent use -- not the
// FTS index, which returns fuzzier results in a field where the user is trying to hit
// one specific row. The same call the restaurant name field makes.
private suspend fun workshopSuggestions(prefix: String) =
    if (prefix.isBlank()) emptyList() else placeDao.namesLike(prefix.trim())
```

- [ ] **Step 3: Build the screen**

`ServiceEditorScreen` in field order: date, odometer, workshop, cost, items, photos, note.
**Save** sits in the bottom third per the one-handed rule. Photos use the same
`PickMultipleVisualMedia` contract and `MediaStore.import(uri, ENTITY_VEHICLE_SERVICE, id)`
call the visit editor makes.

- [ ] **Step 4: Run, then commit**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

```bash
git add app/src/main app/src/test
git commit -m "feat(vehicle): the screen that records a job"
```

---

### Task 5: The lists, the costs, and the hub tile

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/vehicle/VehicleScreen.kt` (hub destination)
- Create: `app/src/main/java/com/wasif/khata/feature/vehicle/VehicleUiState.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/vehicle/VehicleViewModel.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/vehicle/ServiceScreen.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/vehicle/ServiceViewModel.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/vehicle/CostsScreen.kt`
- Modify: `app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/hub/ModulesScreen.kt` (wake the tile)
- Test: `app/src/test/java/com/wasif/khata/feature/vehicle/VehicleViewModelTest.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/vehicle/VehicleScreenTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 1–4.
- Produces: routes `KhataRoutes.Vehicle = "vehicle"`, `VehicleService = "vehicle/service/{serviceId}"`,
  `VehicleServiceNew = "vehicle/service/new"`, `VehicleCosts = "vehicle/costs"`, and
  `fun vehicleService(id: Long)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `the header shows cost per km only when two readings exist`() = runTest {
    val vehicleId = repository.findOrCreateVehicle()
    repository.saveService(ServiceDraft(vehicleId = vehicleId, servicedAt = 1L, odometerKm = 42_000, costMinor = 500_00L), emptyList())
    advanceUntilIdle()
    assertNull(viewModel.state.value.costPerKm)

    repository.saveService(ServiceDraft(vehicleId = vehicleId, servicedAt = 2L, odometerKm = 42_050, costMinor = 0L), emptyList())
    advanceUntilIdle()
    assertNotNull(viewModel.state.value.costPerKm)
}

@Test
fun `an empty module shows no services and no invented totals`() = runTest {
    advanceUntilIdle()
    assertEquals(emptyList<ServiceSummary>(), viewModel.state.value.services)
    assertNull(viewModel.state.value.totalMinor)
}
```

And a Compose test in the shape of `LedgerScreenTest`:

```kotlin
@Test
fun `a service row shows its items and its workshop`() {
    composeRule.setContent {
        KhataTheme { VehicleContent(state = stateWithOneService(), actions = NoopActions) }
    }
    composeRule.onNodeWithText("Oil filter, Brake pads").assertIsDisplayed()
    composeRule.onNodeWithText("Navana Workshop").assertIsDisplayed()
}
```

- [ ] **Step 2: Build `VehicleScreen`**

Header: vehicle name and odometer, editable inline (C8) — a tap turns the two into text
fields and saves on blur, no separate screen. Beneath it the running-cost row: total,
this year, cost per km, each absent rather than zero when undefined. Then services
newest-first: date, odometer, workshop, total, and `itemNames` on one quiet line.
**Log a service** sits in the bottom third.

- [ ] **Step 3: Build `ServiceScreen`**

One job in full, photos in a grid over glass, and the *other* line rendered from
`otherMinor(summary)` — absent when that returns null.

- [ ] **Step 4: Build `CostsScreen`**

`spendByItem` descending with a total above it and `spendByYear` beneath. No chart library.

- [ ] **Step 5: Wire navigation and wake the hub tile**

Add the four routes to `KhataRoutes`, the four `composable` blocks to `KhataNavHost`, and
replace the `Car service` dormant tile in `ModulesScreen` with a live one that navigates to
`KhataRoutes.Vehicle`. `DormantRow` keeps `Watchlist` and `Notes` until their modules exist.

- [ ] **Step 6: Run everything, then commit**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

```bash
git add app/src/main app/src/test
git commit -m "feat(vehicle): the list, the costs, and a hub tile that opens"
```

---

## Self-Review

**Spec coverage:** §3 tables and derived pieces → Tasks 1–2. §4 entry → Task 4. §5 screens →
Tasks 4–5. §6 search → Task 3. §7 design system → Tasks 4–5 (reuse only). §8 testing → every
task's test step. §9 out of scope → nothing here builds intervals, documents, fuel, a second
vehicle in the UI, or a transaction link.

**Types:** `ServiceSummary`, `CostTotals`, `ItemSpend`, `YearSpend`, `ServiceDraft`, `ItemDraft`,
`ENTITY_VEHICLE_SERVICE`, `costPerKm`, `otherMinor` are defined in Tasks 1–2 and used under those
exact names in Tasks 3–5.
