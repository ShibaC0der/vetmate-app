# VetMate: Foundation & Pet Management — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Working KMP app on Android + iOS with full pet CRUD, SQLDelight persistence, Voyager navigation, and Koin DI. PetDetailScreen scaffolded with tab stubs for Plans 2–3.

**Architecture:** MVVM + Clean layers (data / domain / ui) in single `shared` module. Repositories return `Flow<T>`; use cases map Command objects to domain models before calling repos.

**Tech Stack:** Kotlin 2.4.10, Compose Multiplatform 1.11.1, SQLDelight 2.0.2, Voyager 1.1.0-beta03, Koin 4.0.0, kotlinx-datetime 0.6.2

> **Scope:** This is Plan 1 of 3.
> - **Plan 2** (follow-up): Vaccination, VetVisit, Medication screens
> - **Plan 3** (follow-up): Documents, Health Summary, Notifications, FilePicker, Localization

## Global Constraints

- minSdk 29, compileSdk 36, JVM target 11
- All shared logic and UI in `shared/src/commonMain`; platform code only in `androidMain` / `iosMain`
- Dates stored as ISO 8601 TEXT in SQLite; domain layer uses `kotlinx.datetime.LocalDate`
- No network, no auth — local-only storage
- `ON DELETE CASCADE` on all tables referencing Pet
- String resources: English default in `values/strings.xml`, Russian in `values-ru/strings.xml`

---

## File Map

```
gradle/libs.versions.toml                                      MODIFY
shared/build.gradle.kts                                        MODIFY
androidApp/build.gradle.kts                                    MODIFY
shared/src/commonMain/kotlin/app/vetmate/App.kt                MODIFY
androidApp/src/main/kotlin/app/vetmate/MainActivity.kt         MODIFY
iosApp/iosApp/iOSApp.swift                                     MODIFY

shared/src/commonMain/sqldelight/app/vetmate/db/
  Pet.sq, Vaccination.sq, VetVisit.sq, Medication.sq,
  Document.sq, Allergy.sq, ChronicCondition.sq                 CREATE

shared/src/commonMain/kotlin/app/vetmate/
  platform/DatabaseDriverFactory.kt                            CREATE (expect)
  data/db/DatabaseFactory.kt                                   CREATE
  data/repository/PetRepositoryImpl.kt                         CREATE
  domain/model/Pet.kt                                          CREATE
  domain/model/Vaccination.kt                                  CREATE (stub)
  domain/model/VetVisit.kt                                     CREATE (stub)
  domain/model/Medication.kt                                   CREATE (stub)
  domain/model/Document.kt                                     CREATE (stub)
  domain/model/Allergy.kt                                      CREATE (stub)
  domain/model/ChronicCondition.kt                             CREATE (stub)
  domain/repository/PetRepository.kt                           CREATE
  domain/usecase/pet/AddPetCommand.kt                          CREATE
  domain/usecase/pet/AddPetUseCase.kt                          CREATE
  domain/usecase/pet/UpdatePetCommand.kt                       CREATE
  domain/usecase/pet/UpdatePetUseCase.kt                       CREATE
  domain/usecase/pet/DeletePetUseCase.kt                       CREATE
  domain/usecase/pet/GetAllPetsUseCase.kt                      CREATE
  domain/usecase/pet/GetPetByIdUseCase.kt                      CREATE
  di/Modules.kt                                                CREATE (platformModule expect + shared modules)
  ui/common/Theme.kt                                           CREATE
  ui/pets/PetListScreen.kt                                     CREATE
  ui/pets/PetListViewModel.kt                                  CREATE
  ui/pets/AddPetScreen.kt                                      CREATE
  ui/pets/AddPetViewModel.kt                                   CREATE
  ui/pets/EditPetScreen.kt                                     CREATE
  ui/pets/EditPetViewModel.kt                                  CREATE
  ui/pets/PetDetailScreen.kt                                   CREATE
  ui/pets/PetFormScreen.kt                                     CREATE (shared form composable)
  ui/pets/PetTabs.kt                                           CREATE (stub tabs)

shared/src/androidMain/kotlin/app/vetmate/
  platform/DatabaseDriverFactory.android.kt                    CREATE (actual)
  di/Modules.android.kt                                        CREATE (actual platformModule)

shared/src/iosMain/kotlin/app/vetmate/
  platform/DatabaseDriverFactory.ios.kt                        CREATE (actual)
  di/Modules.ios.kt                                            CREATE (actual platformModule)
  di/KoinHelper.kt                                             CREATE (iOS entry point)

shared/src/androidHostTest/kotlin/app/vetmate/
  data/repository/PetRepositoryTest.kt                         CREATE
  domain/usecase/FakePetRepository.kt                          CREATE

shared/src/commonMain/composeResources/
  values/strings.xml                                           CREATE
  values-ru/strings.xml                                        CREATE
```

---

### Task 1: Add Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`
- Modify: `androidApp/build.gradle.kts`

**Interfaces:**
- Produces: SQLDelight, Voyager, Koin, kotlinx-datetime available in all source sets

- [ ] **Step 1: Add versions and libraries to libs.versions.toml**

Add after existing `[versions]` entries:
```toml
kotlinx-datetime = "0.6.2"
koin = "4.0.0"
sqldelight = "2.0.2"
voyager = "1.1.0-beta03"
sqlite-jdbc = "3.46.1.3"
```

Add after existing `[libraries]` entries:
```toml
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }

koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }

voyager-navigator = { module = "cafe.adriel.voyager:voyager-navigator", version.ref = "voyager" }
voyager-tab-navigator = { module = "cafe.adriel.voyager:voyager-tab-navigator", version.ref = "voyager" }
voyager-screenmodel = { module = "cafe.adriel.voyager:voyager-screenmodel", version.ref = "voyager" }
voyager-koin = { module = "cafe.adriel.voyager:voyager-koin", version.ref = "voyager" }

sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-jdbc-driver = { module = "app.cash.sqldelight:jdbc-driver", version.ref = "sqldelight" }
sqlite-jdbc = { module = "org.xerial:sqlite-jdbc", version.ref = "sqlite-jdbc" }
```

Add to `[plugins]`:
```toml
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

- [ ] **Step 2: Update shared/build.gradle.kts**

Replace the full file:
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    android {
        namespace = "app.vetmate.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.tab.navigator)
            implementation(libs.voyager.screenmodel)
            implementation(libs.voyager.koin)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.koin.android)
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        androidHostTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.sqldelight.jdbc.driver)
            implementation(libs.sqlite.jdbc)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

sqldelight {
    databases {
        create("VetMateDatabase") {
            packageName.set("app.vetmate.db")
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
```

- [ ] **Step 3: Add koin-android to androidApp/build.gradle.kts**

Add inside `dependencies { }`:
```kotlin
implementation(libs.koin.android)
```

- [ ] **Step 4: Verify sync compiles**

```bash
./gradlew :shared:compileCommonMainKotlinMetadata
```
Expected: BUILD SUCCESSFUL (no errors)

---

### Task 2: SQLDelight Schema

**Files:**
- Create: `shared/src/commonMain/sqldelight/app/vetmate/db/Pet.sq`
- Create: `shared/src/commonMain/sqldelight/app/vetmate/db/Vaccination.sq`
- Create: `shared/src/commonMain/sqldelight/app/vetmate/db/VetVisit.sq`
- Create: `shared/src/commonMain/sqldelight/app/vetmate/db/Medication.sq`
- Create: `shared/src/commonMain/sqldelight/app/vetmate/db/Document.sq`
- Create: `shared/src/commonMain/sqldelight/app/vetmate/db/Allergy.sq`
- Create: `shared/src/commonMain/sqldelight/app/vetmate/db/ChronicCondition.sq`

**Interfaces:**
- Produces: `app.vetmate.db.VetMateDatabase` with typed query objects per table

- [ ] **Step 1: Create Pet.sq**

```sql
CREATE TABLE Pet (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    species     TEXT    NOT NULL,
    breed       TEXT,
    birthDate   TEXT,
    gender      TEXT    NOT NULL DEFAULT 'unknown',
    color       TEXT,
    chipNumber  TEXT,
    photoPath   TEXT,
    createdAt   TEXT    NOT NULL
);

selectAll:
SELECT * FROM Pet ORDER BY name ASC;

selectById:
SELECT * FROM Pet WHERE id = :id;

insert:
INSERT INTO Pet(name, species, breed, birthDate, gender, color, chipNumber, photoPath, createdAt)
VALUES (:name, :species, :breed, :birthDate, :gender, :color, :chipNumber, :photoPath, :createdAt);

lastInsertRowId:
SELECT last_insert_rowid();

updateById:
UPDATE Pet
SET name = :name, species = :species, breed = :breed, birthDate = :birthDate,
    gender = :gender, color = :color, chipNumber = :chipNumber, photoPath = :photoPath
WHERE id = :id;

deleteById:
DELETE FROM Pet WHERE id = :id;
```

- [ ] **Step 2: Create Vaccination.sq**

```sql
CREATE TABLE Vaccination (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    petId       INTEGER NOT NULL REFERENCES Pet(id) ON DELETE CASCADE,
    name        TEXT    NOT NULL,
    date        TEXT    NOT NULL,
    nextDate    TEXT,
    clinic      TEXT,
    doctor      TEXT,
    batchNumber TEXT,
    notes       TEXT
);

selectByPetId:
SELECT * FROM Vaccination WHERE petId = :petId ORDER BY date DESC;

insert:
INSERT INTO Vaccination(petId, name, date, nextDate, clinic, doctor, batchNumber, notes)
VALUES (:petId, :name, :date, :nextDate, :clinic, :doctor, :batchNumber, :notes);

lastInsertRowId:
SELECT last_insert_rowid();

deleteById:
DELETE FROM Vaccination WHERE id = :id;
```

- [ ] **Step 3: Create VetVisit.sq**

```sql
CREATE TABLE VetVisit (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    petId       INTEGER NOT NULL REFERENCES Pet(id) ON DELETE CASCADE,
    date        TEXT    NOT NULL,
    clinic      TEXT,
    doctor      TEXT,
    reason      TEXT    NOT NULL,
    diagnosis   TEXT,
    treatment   TEXT,
    nextVisit   TEXT,
    notes       TEXT
);

selectByPetId:
SELECT * FROM VetVisit WHERE petId = :petId ORDER BY date DESC;

selectById:
SELECT * FROM VetVisit WHERE id = :id;

insert:
INSERT INTO VetVisit(petId, date, clinic, doctor, reason, diagnosis, treatment, nextVisit, notes)
VALUES (:petId, :date, :clinic, :doctor, :reason, :diagnosis, :treatment, :nextVisit, :notes);

lastInsertRowId:
SELECT last_insert_rowid();

deleteById:
DELETE FROM VetVisit WHERE id = :id;
```

- [ ] **Step 4: Create Medication.sq**

```sql
CREATE TABLE Medication (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    petId       INTEGER NOT NULL REFERENCES Pet(id) ON DELETE CASCADE,
    name        TEXT    NOT NULL,
    startDate   TEXT    NOT NULL,
    endDate     TEXT,
    dosage      TEXT,
    frequency   TEXT,
    notes       TEXT
);

selectByPetId:
SELECT * FROM Medication WHERE petId = :petId ORDER BY startDate DESC;

insert:
INSERT INTO Medication(petId, name, startDate, endDate, dosage, frequency, notes)
VALUES (:petId, :name, :startDate, :endDate, :dosage, :frequency, :notes);

lastInsertRowId:
SELECT last_insert_rowid();

deleteById:
DELETE FROM Medication WHERE id = :id;
```

- [ ] **Step 5: Create Document.sq**

```sql
CREATE TABLE Document (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    petId       INTEGER NOT NULL REFERENCES Pet(id) ON DELETE CASCADE,
    visitId     INTEGER REFERENCES VetVisit(id) ON DELETE SET NULL,
    type        TEXT    NOT NULL,
    title       TEXT    NOT NULL,
    filePath    TEXT    NOT NULL,
    mimeType    TEXT    NOT NULL,
    createdAt   TEXT    NOT NULL
);

selectByPetId:
SELECT * FROM Document WHERE petId = :petId ORDER BY createdAt DESC;

selectByVisitId:
SELECT * FROM Document WHERE visitId = :visitId ORDER BY createdAt DESC;

insert:
INSERT INTO Document(petId, visitId, type, title, filePath, mimeType, createdAt)
VALUES (:petId, :visitId, :type, :title, :filePath, :mimeType, :createdAt);

lastInsertRowId:
SELECT last_insert_rowid();

deleteById:
DELETE FROM Document WHERE id = :id;
```

- [ ] **Step 6: Create Allergy.sq**

```sql
CREATE TABLE Allergy (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    petId       INTEGER NOT NULL REFERENCES Pet(id) ON DELETE CASCADE,
    name        TEXT    NOT NULL,
    severity    TEXT,
    notes       TEXT
);

selectByPetId:
SELECT * FROM Allergy WHERE petId = :petId ORDER BY name ASC;

insert:
INSERT INTO Allergy(petId, name, severity, notes)
VALUES (:petId, :name, :severity, :notes);

lastInsertRowId:
SELECT last_insert_rowid();

deleteById:
DELETE FROM Allergy WHERE id = :id;
```

- [ ] **Step 7: Create ChronicCondition.sq**

```sql
CREATE TABLE ChronicCondition (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    petId       INTEGER NOT NULL REFERENCES Pet(id) ON DELETE CASCADE,
    name        TEXT    NOT NULL,
    diagnosedAt TEXT,
    notes       TEXT
);

selectByPetId:
SELECT * FROM ChronicCondition WHERE petId = :petId ORDER BY name ASC;

insert:
INSERT INTO ChronicCondition(petId, name, diagnosedAt, notes)
VALUES (:petId, :name, :diagnosedAt, :notes);

lastInsertRowId:
SELECT last_insert_rowid();

deleteById:
DELETE FROM ChronicCondition WHERE id = :id;
```

- [ ] **Step 8: Generate SQLDelight code**

```bash
./gradlew :shared:generateCommonMainSqldelightInterface
```
Expected: BUILD SUCCESSFUL. Check `shared/build/generated/sqldelight/` contains `VetMateDatabase.kt`.

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts androidApp/build.gradle.kts shared/src/commonMain/sqldelight/
git commit -m "feat: add dependencies and SQLDelight schema"
```

---

### Task 3: Domain Models

**Files:**
- Create: `shared/src/commonMain/kotlin/app/vetmate/domain/model/Pet.kt`
- Create: stubs for Vaccination, VetVisit, Medication, Document, Allergy, ChronicCondition, HealthSummary

**Interfaces:**
- Produces: `Pet`, `Gender`, `DocumentType`, `AllergySeverity` used by all other tasks

- [ ] **Step 1: Create domain/model/Pet.kt**

```kotlin
package app.vetmate.domain.model

import kotlinx.datetime.LocalDate

data class Pet(
    val id: Long,
    val name: String,
    val species: String,
    val breed: String?,
    val birthDate: LocalDate?,
    val gender: Gender,
    val color: String?,
    val chipNumber: String?,
    val photoPath: String?
)

enum class Gender { MALE, FEMALE, UNKNOWN }
```

- [ ] **Step 2: Create stub models for Plan 2/3**

`domain/model/Vaccination.kt`:
```kotlin
package app.vetmate.domain.model

import kotlinx.datetime.LocalDate

data class Vaccination(
    val id: Long,
    val petId: Long,
    val name: String,
    val date: LocalDate,
    val nextDate: LocalDate?,
    val clinic: String?,
    val doctor: String?,
    val batchNumber: String?,
    val notes: String?
)
```

`domain/model/VetVisit.kt`:
```kotlin
package app.vetmate.domain.model

import kotlinx.datetime.LocalDate

data class VetVisit(
    val id: Long,
    val petId: Long,
    val date: LocalDate,
    val clinic: String?,
    val doctor: String?,
    val reason: String,
    val diagnosis: String?,
    val treatment: String?,
    val nextVisit: LocalDate?,
    val notes: String?
)
```

`domain/model/Medication.kt`:
```kotlin
package app.vetmate.domain.model

import kotlinx.datetime.LocalDate

data class Medication(
    val id: Long,
    val petId: Long,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val dosage: String?,
    val frequency: String?,
    val notes: String?
)
```

`domain/model/Document.kt`:
```kotlin
package app.vetmate.domain.model

import kotlinx.datetime.LocalDate

data class Document(
    val id: Long,
    val petId: Long,
    val visitId: Long?,
    val type: DocumentType,
    val title: String,
    val filePath: String,
    val mimeType: String,
    val createdAt: LocalDate
)

enum class DocumentType { PASSPORT, ANALYSIS, XRAY, OTHER }
```

`domain/model/Allergy.kt`:
```kotlin
package app.vetmate.domain.model

data class Allergy(
    val id: Long,
    val petId: Long,
    val name: String,
    val severity: AllergySeverity?,
    val notes: String?
)

enum class AllergySeverity { MILD, MODERATE, SEVERE }
```

`domain/model/ChronicCondition.kt`:
```kotlin
package app.vetmate.domain.model

import kotlinx.datetime.LocalDate

data class ChronicCondition(
    val id: Long,
    val petId: Long,
    val name: String,
    val diagnosedAt: LocalDate?,
    val notes: String?
)
```

`domain/model/HealthSummary.kt`:
```kotlin
package app.vetmate.domain.model

data class HealthSummary(
    val allergies: List<Allergy>,
    val conditions: List<ChronicCondition>
)
```

- [ ] **Step 3: Create PetRepository interface**

`domain/repository/PetRepository.kt`:
```kotlin
package app.vetmate.domain.repository

import app.vetmate.domain.model.Pet
import kotlinx.coroutines.flow.Flow

interface PetRepository {
    fun getAll(): Flow<List<Pet>>
    suspend fun getById(id: Long): Pet?
    suspend fun add(pet: Pet): Long
    suspend fun update(pet: Pet)
    suspend fun delete(id: Long)
}
```

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/app/vetmate/domain/
git commit -m "feat: add domain models and PetRepository interface"
```

---

### Task 4: DatabaseDriverFactory (expect/actual)

**Files:**
- Create: `shared/src/commonMain/kotlin/app/vetmate/platform/DatabaseDriverFactory.kt`
- Create: `shared/src/androidMain/kotlin/app/vetmate/platform/DatabaseDriverFactory.android.kt`
- Create: `shared/src/iosMain/kotlin/app/vetmate/platform/DatabaseDriverFactory.ios.kt`
- Create: `shared/src/commonMain/kotlin/app/vetmate/data/db/DatabaseFactory.kt`

**Interfaces:**
- Consumes: `VetMateDatabase` (generated by SQLDelight in Task 2)
- Produces: `DatabaseDriverFactory`, `DatabaseFactory.create(factory)` → `VetMateDatabase`

- [ ] **Step 1: Create expect class**

`platform/DatabaseDriverFactory.kt`:
```kotlin
package app.vetmate.platform

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
```

- [ ] **Step 2: Create Android actual**

`androidMain/platform/DatabaseDriverFactory.android.kt`:
```kotlin
package app.vetmate.platform

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.vetmate.db.VetMateDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(VetMateDatabase.Schema, context, "vetmate.db")
}
```

- [ ] **Step 3: Create iOS actual**

`iosMain/platform/DatabaseDriverFactory.ios.kt`:
```kotlin
package app.vetmate.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.vetmate.db.VetMateDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(VetMateDatabase.Schema, "vetmate.db")
}
```

- [ ] **Step 4: Create DatabaseFactory**

`data/db/DatabaseFactory.kt`:
```kotlin
package app.vetmate.data.db

import app.vetmate.db.VetMateDatabase
import app.vetmate.platform.DatabaseDriverFactory

object DatabaseFactory {
    fun create(driverFactory: DatabaseDriverFactory): VetMateDatabase =
        VetMateDatabase(driverFactory.createDriver())
}
```

- [ ] **Step 5: Verify compilation**

```bash
./gradlew :shared:compileCommonMainKotlinMetadata
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/app/vetmate/platform/ shared/src/androidMain/kotlin/app/vetmate/platform/ shared/src/iosMain/kotlin/app/vetmate/platform/ shared/src/commonMain/kotlin/app/vetmate/data/
git commit -m "feat: add DatabaseDriverFactory expect/actual and DatabaseFactory"
```

---

### Task 5: PetRepositoryImpl + Tests

**Files:**
- Create: `shared/src/commonMain/kotlin/app/vetmate/data/repository/PetRepositoryImpl.kt`
- Create: `shared/src/androidHostTest/kotlin/app/vetmate/data/repository/PetRepositoryTest.kt`
- Create: `shared/src/commonTest/kotlin/app/vetmate/domain/FakePetRepository.kt`

**Interfaces:**
- Consumes: `VetMateDatabase` (from Task 2), `PetRepository` interface (Task 3), `Pet` domain model (Task 3)
- Produces: `PetRepositoryImpl(db: VetMateDatabase): PetRepository`

- [ ] **Step 1: Write the failing test**

`androidHostTest/data/repository/PetRepositoryTest.kt`:
```kotlin
package app.vetmate.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.vetmate.db.VetMateDatabase
import app.vetmate.domain.model.Gender
import app.vetmate.domain.model.Pet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetRepositoryTest {

    private lateinit var db: VetMateDatabase
    private lateinit var repo: PetRepositoryImpl

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        VetMateDatabase.Schema.create(driver)
        db = VetMateDatabase(driver)
        repo = PetRepositoryImpl(db)
    }

    @Test
    fun `add returns generated id and pet appears in getAll`() = runTest {
        val id = repo.add(Pet(0L, "Buddy", "dog", null, null, Gender.MALE, null, null, null))
        val pets = repo.getAll().first()
        assertEquals(1, pets.size)
        assertEquals("Buddy", pets[0].name)
        assertEquals(Gender.MALE, pets[0].gender)
        assertEquals(id, pets[0].id)
    }

    @Test
    fun `getById returns pet when exists`() = runTest {
        val id = repo.add(Pet(0L, "Max", "cat", "Siamese", null, Gender.FEMALE, "white", null, null))
        val pet = repo.getById(id)
        assertEquals("Max", pet?.name)
        assertEquals("Siamese", pet?.breed)
    }

    @Test
    fun `getById returns null when not exists`() = runTest {
        assertNull(repo.getById(999L))
    }

    @Test
    fun `update changes pet fields`() = runTest {
        val id = repo.add(Pet(0L, "Rex", "dog", null, null, Gender.MALE, null, null, null))
        val pet = repo.getById(id)!!
        repo.update(pet.copy(name = "Rexy", breed = "Labrador"))
        val updated = repo.getById(id)!!
        assertEquals("Rexy", updated.name)
        assertEquals("Labrador", updated.breed)
    }

    @Test
    fun `delete removes pet from getAll`() = runTest {
        val id = repo.add(Pet(0L, "Luna", "rabbit", null, null, Gender.FEMALE, null, null, null))
        repo.delete(id)
        assertTrue(repo.getAll().first().isEmpty())
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

```bash
./gradlew :shared:testAndroidHostTest --tests "app.vetmate.data.repository.PetRepositoryTest"
```
Expected: FAIL — `PetRepositoryImpl` does not exist yet

- [ ] **Step 3: Implement PetRepositoryImpl**

`data/repository/PetRepositoryImpl.kt`:
```kotlin
package app.vetmate.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.vetmate.db.Pet as PetRow
import app.vetmate.db.VetMateDatabase
import app.vetmate.domain.model.Gender
import app.vetmate.domain.model.Pet
import app.vetmate.domain.repository.PetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class PetRepositoryImpl(private val db: VetMateDatabase) : PetRepository {

    override fun getAll(): Flow<List<Pet>> =
        db.petQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: Long): Pet? = withContext(Dispatchers.IO) {
        db.petQueries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun add(pet: Pet): Long = withContext(Dispatchers.IO) {
        db.petQueries.insert(
            name = pet.name,
            species = pet.species,
            breed = pet.breed,
            birthDate = pet.birthDate?.toString(),
            gender = pet.gender.name.lowercase(),
            color = pet.color,
            chipNumber = pet.chipNumber,
            photoPath = pet.photoPath,
            createdAt = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        )
        db.petQueries.lastInsertRowId().executeAsOne()
    }

    override suspend fun update(pet: Pet): Unit = withContext(Dispatchers.IO) {
        db.petQueries.updateById(
            name = pet.name,
            species = pet.species,
            breed = pet.breed,
            birthDate = pet.birthDate?.toString(),
            gender = pet.gender.name.lowercase(),
            color = pet.color,
            chipNumber = pet.chipNumber,
            photoPath = pet.photoPath,
            id = pet.id
        )
    }

    override suspend fun delete(id: Long): Unit = withContext(Dispatchers.IO) {
        db.petQueries.deleteById(id)
    }
}

private fun PetRow.toDomain() = Pet(
    id = id,
    name = name,
    species = species,
    breed = breed,
    birthDate = birthDate?.let { LocalDate.parse(it) },
    gender = when (gender.lowercase()) {
        "male" -> Gender.MALE
        "female" -> Gender.FEMALE
        else -> Gender.UNKNOWN
    },
    color = color,
    chipNumber = chipNumber,
    photoPath = photoPath
)
```

- [ ] **Step 4: Run test — verify it passes**

```bash
./gradlew :shared:testAndroidHostTest --tests "app.vetmate.data.repository.PetRepositoryTest"
```
Expected: 5 tests PASSED

- [ ] **Step 5: Create FakePetRepository for use case tests**

`commonTest/domain/FakePetRepository.kt`:
```kotlin
package app.vetmate.domain

import app.vetmate.domain.model.Pet
import app.vetmate.domain.repository.PetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePetRepository : PetRepository {
    private val _pets = MutableStateFlow<List<Pet>>(emptyList())
    private var nextId = 1L

    override fun getAll(): Flow<List<Pet>> = _pets

    override suspend fun getById(id: Long): Pet? = _pets.value.find { it.id == id }

    override suspend fun add(pet: Pet): Long {
        val id = nextId++
        _pets.value = _pets.value + pet.copy(id = id)
        return id
    }

    override suspend fun update(pet: Pet) {
        _pets.value = _pets.value.map { if (it.id == pet.id) pet else it }
    }

    override suspend fun delete(id: Long) {
        _pets.value = _pets.value.filter { it.id != id }
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add shared/src/
git commit -m "feat: implement PetRepositoryImpl with tests"
```

---

### Task 6: Pet Use Cases + Tests

**Files:**
- Create: `shared/src/commonMain/kotlin/app/vetmate/domain/usecase/pet/AddPetCommand.kt`
- Create: `shared/src/commonMain/kotlin/app/vetmate/domain/usecase/pet/AddPetUseCase.kt`
- Create: `shared/src/commonMain/kotlin/app/vetmate/domain/usecase/pet/UpdatePetCommand.kt`
- Create: `shared/src/commonMain/kotlin/app/vetmate/domain/usecase/pet/UpdatePetUseCase.kt`
- Create: `shared/src/commonMain/kotlin/app/vetmate/domain/usecase/pet/DeletePetUseCase.kt`
- Create: `shared/src/commonMain/kotlin/app/vetmate/domain/usecase/pet/GetAllPetsUseCase.kt`
- Create: `shared/src/commonMain/kotlin/app/vetmate/domain/usecase/pet/GetPetByIdUseCase.kt`
- Test: `shared/src/commonTest/kotlin/app/vetmate/domain/usecase/PetUseCaseTest.kt`

**Interfaces:**
- Consumes: `PetRepository` (Task 3), `FakePetRepository` (Task 5), `Pet`, `Gender` (Task 3)
- Produces: use cases invoked as `useCase(command)` or `useCase(id)`

- [ ] **Step 1: Write failing tests**

`commonTest/domain/usecase/PetUseCaseTest.kt`:
```kotlin
package app.vetmate.domain.usecase

import app.vetmate.domain.FakePetRepository
import app.vetmate.domain.model.Gender
import app.vetmate.domain.usecase.pet.AddPetCommand
import app.vetmate.domain.usecase.pet.AddPetUseCase
import app.vetmate.domain.usecase.pet.DeletePetUseCase
import app.vetmate.domain.usecase.pet.GetAllPetsUseCase
import app.vetmate.domain.usecase.pet.GetPetByIdUseCase
import app.vetmate.domain.usecase.pet.UpdatePetCommand
import app.vetmate.domain.usecase.pet.UpdatePetUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetUseCaseTest {

    private val repo = FakePetRepository()
    private val addPet = AddPetUseCase(repo)
    private val getAllPets = GetAllPetsUseCase(repo)
    private val getPetById = GetPetByIdUseCase(repo)
    private val updatePet = UpdatePetUseCase(repo)
    private val deletePet = DeletePetUseCase(repo)

    @Test
    fun `add pet stores it with generated id`() = runTest {
        val id = addPet(AddPetCommand(name = "Buddy", species = "dog"))
        val pets = getAllPets().first()
        assertEquals(1, pets.size)
        assertEquals("Buddy", pets[0].name)
        assertEquals(id, pets[0].id)
    }

    @Test
    fun `get pet by id returns correct pet`() = runTest {
        val id = addPet(AddPetCommand(name = "Luna", species = "cat"))
        val pet = getPetById(id)
        assertEquals("Luna", pet?.name)
    }

    @Test
    fun `get pet by unknown id returns null`() = runTest {
        assertNull(getPetById(999L))
    }

    @Test
    fun `update changes name and species`() = runTest {
        val id = addPet(AddPetCommand(name = "Rex", species = "dog"))
        updatePet(UpdatePetCommand(id = id, name = "Rexy", species = "dog",
            breed = "Labrador", birthDate = null, gender = Gender.MALE,
            color = null, chipNumber = null, photoPath = null))
        val pet = getPetById(id)
        assertEquals("Rexy", pet?.name)
        assertEquals("Labrador", pet?.breed)
    }

    @Test
    fun `delete removes pet`() = runTest {
        val id = addPet(AddPetCommand(name = "Max", species = "hamster"))
        deletePet(id)
        assertTrue(getAllPets().first().isEmpty())
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
./gradlew :shared:testAndroidHostTest --tests "app.vetmate.domain.usecase.PetUseCaseTest"
```
Expected: FAIL — use case classes don't exist

- [ ] **Step 3: Implement commands and use cases**

`domain/usecase/pet/AddPetCommand.kt`:
```kotlin
package app.vetmate.domain.usecase.pet

import app.vetmate.domain.model.Gender
import kotlinx.datetime.LocalDate

data class AddPetCommand(
    val name: String,
    val species: String,
    val breed: String? = null,
    val birthDate: LocalDate? = null,
    val gender: Gender = Gender.UNKNOWN,
    val color: String? = null,
    val chipNumber: String? = null,
    val photoPath: String? = null
)
```

`domain/usecase/pet/AddPetUseCase.kt`:
```kotlin
package app.vetmate.domain.usecase.pet

import app.vetmate.domain.model.Pet
import app.vetmate.domain.repository.PetRepository

class AddPetUseCase(private val repo: PetRepository) {
    suspend operator fun invoke(cmd: AddPetCommand): Long = repo.add(
        Pet(id = 0L, name = cmd.name, species = cmd.species, breed = cmd.breed,
            birthDate = cmd.birthDate, gender = cmd.gender, color = cmd.color,
            chipNumber = cmd.chipNumber, photoPath = cmd.photoPath)
    )
}
```

`domain/usecase/pet/UpdatePetCommand.kt`:
```kotlin
package app.vetmate.domain.usecase.pet

import app.vetmate.domain.model.Gender
import kotlinx.datetime.LocalDate

data class UpdatePetCommand(
    val id: Long,
    val name: String,
    val species: String,
    val breed: String?,
    val birthDate: LocalDate?,
    val gender: Gender,
    val color: String?,
    val chipNumber: String?,
    val photoPath: String?
)
```

`domain/usecase/pet/UpdatePetUseCase.kt`:
```kotlin
package app.vetmate.domain.usecase.pet

import app.vetmate.domain.model.Pet
import app.vetmate.domain.repository.PetRepository

class UpdatePetUseCase(private val repo: PetRepository) {
    suspend operator fun invoke(cmd: UpdatePetCommand) = repo.update(
        Pet(id = cmd.id, name = cmd.name, species = cmd.species, breed = cmd.breed,
            birthDate = cmd.birthDate, gender = cmd.gender, color = cmd.color,
            chipNumber = cmd.chipNumber, photoPath = cmd.photoPath)
    )
}
```

`domain/usecase/pet/DeletePetUseCase.kt`:
```kotlin
package app.vetmate.domain.usecase.pet

import app.vetmate.domain.repository.PetRepository

class DeletePetUseCase(private val repo: PetRepository) {
    suspend operator fun invoke(petId: Long) = repo.delete(petId)
}
```

`domain/usecase/pet/GetAllPetsUseCase.kt`:
```kotlin
package app.vetmate.domain.usecase.pet

import app.vetmate.domain.model.Pet
import app.vetmate.domain.repository.PetRepository
import kotlinx.coroutines.flow.Flow

class GetAllPetsUseCase(private val repo: PetRepository) {
    operator fun invoke(): Flow<List<Pet>> = repo.getAll()
}
```

`domain/usecase/pet/GetPetByIdUseCase.kt`:
```kotlin
package app.vetmate.domain.usecase.pet

import app.vetmate.domain.model.Pet
import app.vetmate.domain.repository.PetRepository

class GetPetByIdUseCase(private val repo: PetRepository) {
    suspend operator fun invoke(id: Long): Pet? = repo.getById(id)
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
./gradlew :shared:testAndroidHostTest --tests "app.vetmate.domain.usecase.PetUseCaseTest"
```
Expected: 5 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add shared/src/
git commit -m "feat: add pet use cases with tests"
```

---

### Task 7: Koin Modules + App Entry Points

**Files:**
- Create: `shared/src/commonMain/kotlin/app/vetmate/di/Modules.kt`
- Create: `shared/src/androidMain/kotlin/app/vetmate/di/Modules.android.kt`
- Create: `shared/src/iosMain/kotlin/app/vetmate/di/Modules.ios.kt`
- Create: `shared/src/iosMain/kotlin/app/vetmate/di/KoinHelper.kt`
- Modify: `androidApp/src/main/kotlin/app/vetmate/MainActivity.kt`
- Modify: `iosApp/iosApp/iOSApp.swift`

**Interfaces:**
- Consumes: `DatabaseDriverFactory` (Task 4), `DatabaseFactory` (Task 4), `PetRepositoryImpl` (Task 5), all use cases (Task 6)
- Produces: Koin initialized before first composable runs on both platforms

- [ ] **Step 1: Create commonMain Modules.kt**

`di/Modules.kt`:
```kotlin
package app.vetmate.di

import app.vetmate.data.db.DatabaseFactory
import app.vetmate.data.repository.PetRepositoryImpl
import app.vetmate.domain.repository.PetRepository
import app.vetmate.domain.usecase.pet.AddPetUseCase
import app.vetmate.domain.usecase.pet.DeletePetUseCase
import app.vetmate.domain.usecase.pet.GetAllPetsUseCase
import app.vetmate.domain.usecase.pet.GetPetByIdUseCase
import app.vetmate.domain.usecase.pet.UpdatePetUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformModule: Module

val dataModule = module {
    single { DatabaseFactory.create(get()) }
    single<PetRepository> { PetRepositoryImpl(get()) }
}

val domainModule = module {
    factory { AddPetUseCase(get()) }
    factory { GetAllPetsUseCase(get()) }
    factory { GetPetByIdUseCase(get()) }
    factory { UpdatePetUseCase(get()) }
    factory { DeletePetUseCase(get()) }
}
```

- [ ] **Step 2: Create Android platformModule**

`androidMain/di/Modules.android.kt`:
```kotlin
package app.vetmate.di

import app.vetmate.platform.DatabaseDriverFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { DatabaseDriverFactory(androidContext()) }
}
```

- [ ] **Step 3: Create iOS platformModule**

`iosMain/di/Modules.ios.kt`:
```kotlin
package app.vetmate.di

import app.vetmate.platform.DatabaseDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { DatabaseDriverFactory() }
}
```

- [ ] **Step 4: Create iOS KoinHelper**

`iosMain/di/KoinHelper.kt`:
```kotlin
package app.vetmate.di

import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(platformModule, dataModule, domainModule)
    }
}
```

- [ ] **Step 5: Update MainActivity**

`androidApp/src/main/kotlin/app/vetmate/MainActivity.kt`:
```kotlin
package app.vetmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.vetmate.di.dataModule
import app.vetmate.di.domainModule
import app.vetmate.di.platformModule
import org.koin.android.ext.android.inject
import org.koin.android.scope.AndroidScopeComponent
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        startKoin {
            androidContext(this@MainActivity.applicationContext)
            modules(platformModule, dataModule, domainModule)
        }

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
```

- [ ] **Step 6: Update iOSApp.swift to call initKoin**

`iosApp/iosApp/iOSApp.swift`:
```swift
import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        KoinHelperKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

- [ ] **Step 7: Verify Android build**

```bash
./gradlew :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add shared/src/ androidApp/src/ iosApp/
git commit -m "feat: add Koin DI modules and initialize on both platforms"
```

---

### Task 8: Theme + App Scaffold + String Resources

**Files:**
- Create: `shared/src/commonMain/kotlin/app/vetmate/ui/common/Theme.kt`
- Create: `shared/src/commonMain/composeResources/values/strings.xml`
- Create: `shared/src/commonMain/composeResources/values-ru/strings.xml`
- Modify: `shared/src/commonMain/kotlin/app/vetmate/App.kt`

**Interfaces:**
- Consumes: Voyager Navigator, VetMateTheme
- Produces: `App()` composable with Navigator root + theme; `Res.string.*` accessible in all screens

- [ ] **Step 1: Create Theme.kt**

`ui/common/Theme.kt`:
```kotlin
package app.vetmate.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VetMateColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF1565C0),
    onSecondary = Color.White,
    surface = Color(0xFFF9F9F9),
    background = Color(0xFFF9F9F9)
)

@Composable
fun VetMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VetMateColors,
        content = content
    )
}
```

- [ ] **Step 2: Create English strings**

`composeResources/values/strings.xml`:
```xml
<resources>
    <string name="app_name">VetMate</string>

    <!-- Pet List -->
    <string name="pet_list_title">My Pets</string>
    <string name="pet_list_empty">No pets yet. Tap + to add one.</string>

    <!-- Pet Form -->
    <string name="add_pet">Add Pet</string>
    <string name="edit_pet">Edit Pet</string>
    <string name="action_save">Save</string>
    <string name="pet_field_name">Name *</string>
    <string name="pet_field_species">Species *</string>
    <string name="pet_field_breed">Breed</string>
    <string name="pet_field_birth_date">Birth Date (YYYY-MM-DD)</string>
    <string name="pet_field_gender">Gender</string>
    <string name="pet_field_color">Color</string>
    <string name="pet_field_chip">Chip Number</string>

    <!-- Pet Detail Tabs -->
    <string name="pet_detail_title">Pet Profile</string>
    <string name="tab_vaccinations">Vaccines</string>
    <string name="tab_vet_visits">Visits</string>
    <string name="tab_medications">Meds</string>
    <string name="tab_documents">Docs</string>
    <string name="tab_health">Health</string>

    <!-- Common -->
    <string name="coming_soon">Coming soon</string>
    <string name="action_delete">Delete</string>
    <string name="action_back">Back</string>
    <string name="action_edit">Edit</string>
</resources>
```

- [ ] **Step 3: Create Russian strings**

`composeResources/values-ru/strings.xml`:
```xml
<resources>
    <string name="app_name">VetMate</string>

    <!-- Pet List -->
    <string name="pet_list_title">Мои питомцы</string>
    <string name="pet_list_empty">Питомцев пока нет. Нажмите + чтобы добавить.</string>

    <!-- Pet Form -->
    <string name="add_pet">Добавить питомца</string>
    <string name="edit_pet">Редактировать</string>
    <string name="action_save">Сохранить</string>
    <string name="pet_field_name">Имя *</string>
    <string name="pet_field_species">Вид *</string>
    <string name="pet_field_breed">Порода</string>
    <string name="pet_field_birth_date">Дата рождения (ГГГГ-ММ-ДД)</string>
    <string name="pet_field_gender">Пол</string>
    <string name="pet_field_color">Окрас</string>
    <string name="pet_field_chip">Номер чипа</string>

    <!-- Pet Detail Tabs -->
    <string name="pet_detail_title">Профиль питомца</string>
    <string name="tab_vaccinations">Прививки</string>
    <string name="tab_vet_visits">Визиты</string>
    <string name="tab_medications">Лекарства</string>
    <string name="tab_documents">Документы</string>
    <string name="tab_health">Здоровье</string>

    <!-- Common -->
    <string name="coming_soon">Скоро будет</string>
    <string name="action_delete">Удалить</string>
    <string name="action_back">Назад</string>
    <string name="action_edit">Редактировать</string>
</resources>
```

- [ ] **Step 4: Replace App.kt**

`commonMain/kotlin/app/vetmate/App.kt`:
```kotlin
package app.vetmate

import androidx.compose.runtime.Composable
import app.vetmate.ui.common.VetMateTheme
import app.vetmate.ui.pets.PetListScreen
import cafe.adriel.voyager.navigator.Navigator

@Composable
fun App() {
    VetMateTheme {
        Navigator(PetListScreen())
    }
}
```

- [ ] **Step 5: Verify build**

```bash
./gradlew :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL (PetListScreen doesn't exist yet — this will fail; create stub in next task)

- [ ] **Step 6: Commit after Task 9 passes build**

(Combine with Task 9 commit)

---

### Task 9: PetListScreen + ViewModel

**Files:**
- Create: `shared/src/commonMain/kotlin/app/vetmate/ui/pets/PetListViewModel.kt`
- Create: `shared/src/commonMain/kotlin/app/vetmate/ui/pets/PetListScreen.kt`
- Modify: `shared/src/commonMain/kotlin/app/vetmate/di/Modules.kt` (add uiModule)

**Interfaces:**
- Consumes: `GetAllPetsUseCase`, `DeletePetUseCase`, `Pet`, Voyager `Navigator`, Koin `getScreenModel`
- Produces: `PetListScreen()` navigates to `AddPetScreen()` and `PetDetailScreen(petId)`

- [ ] **Step 1: Create PetListViewModel.kt**

`ui/pets/PetListViewModel.kt`:
```kotlin
package app.vetmate.ui.pets

import app.vetmate.domain.model.Pet
import app.vetmate.domain.usecase.pet.DeletePetUseCase
import app.vetmate.domain.usecase.pet.GetAllPetsUseCase
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PetListViewModel(
    getAllPets: GetAllPetsUseCase,
    private val deletePet: DeletePetUseCase
) : ScreenModel {

    val pets: StateFlow<List<Pet>> = getAllPets()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(petId: Long) {
        screenModelScope.launch { deletePet(petId) }
    }
}
```

- [ ] **Step 2: Create PetListScreen.kt**

`ui/pets/PetListScreen.kt`:
```kotlin
package app.vetmate.ui.pets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vetmate.domain.model.Pet
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.jetbrains.compose.resources.stringResource
import vetmate.shared.generated.resources.Res
import vetmate.shared.generated.resources.action_delete
import vetmate.shared.generated.resources.add_pet
import vetmate.shared.generated.resources.pet_list_empty
import vetmate.shared.generated.resources.pet_list_title

class PetListScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm = getScreenModel<PetListViewModel>()
        val pets by vm.pets.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(Res.string.pet_list_title)) })
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { navigator.push(AddPetScreen()) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_pet))
                }
            }
        ) { padding ->
            if (pets.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(Res.string.pet_list_empty))
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    items(pets, key = { it.id }) { pet ->
                        PetListItem(
                            pet = pet,
                            onClick = { navigator.push(PetDetailScreen(pet.id)) },
                            onDelete = { vm.delete(pet.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PetListItem(pet: Pet, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(pet.name, style = MaterialTheme.typography.titleMedium)
                Text(pet.species, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete))
            }
        }
    }
}
```

- [ ] **Step 3: Add uiModule to Modules.kt**

Add to `di/Modules.kt`:
```kotlin
import app.vetmate.ui.pets.AddPetViewModel
import app.vetmate.ui.pets.EditPetViewModel
import app.vetmate.ui.pets.PetListViewModel
import cafe.adriel.voyager.core.model.screenModel
import org.koin.core.parameter.parametersOf

val uiModule = module {
    screenModel { PetListViewModel(get(), get()) }
    screenModel { AddPetViewModel(get()) }
    screenModel { (petId: Long) -> EditPetViewModel(get(), get(), petId) }
}
```

Update both platform `initKoin` / `startKoin` calls to include `uiModule`:
- `androidMain/di/Modules.android.kt` — not changed (uiModule is in commonMain)
- `MainActivity.kt` — add `uiModule` to modules list
- `KoinHelper.kt` — add `uiModule` to modules list

In `MainActivity.kt`, change:
```kotlin
modules(platformModule, dataModule, domainModule)
```
to:
```kotlin
modules(platformModule, dataModule, domainModule, uiModule)
```

In `KoinHelper.kt`, change:
```kotlin
modules(platformModule, dataModule, domainModule)
```
to:
```kotlin
modules(platformModule, dataModule, domainModule, uiModule)
```

- [ ] **Step 4: Build and verify**

```bash
./gradlew :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL (AddPetScreen and PetDetailScreen stubs needed — create empty class stubs temporarily):

Create `ui/pets/AddPetScreen.kt` stub:
```kotlin
package app.vetmate.ui.pets

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen

class AddPetScreen : Screen {
    @Composable override fun Content() {}
}
```

Create `ui/pets/PetDetailScreen.kt` stub:
```kotlin
package app.vetmate.ui.pets

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen

data class PetDetailScreen(val petId: Long) : Screen {
    @Composable override fun Content() {}
}
```

Run build again:
```bash
./gradlew :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add shared/src/ androidApp/src/
git commit -m "feat: add PetListScreen and PetListViewModel"
```

---

### Task 10: Add & Edit Pet Screens

**Files:**
- Create: `shared/src/commonMain/kotlin/app/vetmate/ui/pets/PetFormScreen.kt`
- Create: `shared/src/commonMain/kotlin/app/vetmate/ui/pets/AddPetViewModel.kt`
- Replace: `shared/src/commonMain/kotlin/app/vetmate/ui/pets/AddPetScreen.kt`
- Create: `shared/src/commonMain/kotlin/app/vetmate/ui/pets/EditPetViewModel.kt`
- Replace: `shared/src/commonMain/kotlin/app/vetmate/ui/pets/EditPetScreen.kt`

**Interfaces:**
- Consumes: `AddPetUseCase`, `GetPetByIdUseCase`, `UpdatePetUseCase`, `AddPetCommand`, `UpdatePetCommand`, Koin `getScreenModel`
- Produces: `AddPetScreen()` and `EditPetScreen(petId)` push onto Navigator, pop on save

- [ ] **Step 1: Create PetFormScreen.kt (shared form)**

`ui/pets/PetFormScreen.kt`:
```kotlin
package app.vetmate.ui.pets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vetmate.domain.model.Gender
import org.jetbrains.compose.resources.stringResource
import vetmate.shared.generated.resources.Res
import vetmate.shared.generated.resources.action_back
import vetmate.shared.generated.resources.action_save
import vetmate.shared.generated.resources.pet_field_birth_date
import vetmate.shared.generated.resources.pet_field_breed
import vetmate.shared.generated.resources.pet_field_chip
import vetmate.shared.generated.resources.pet_field_color
import vetmate.shared.generated.resources.pet_field_gender
import vetmate.shared.generated.resources.pet_field_name
import vetmate.shared.generated.resources.pet_field_species

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetFormScreen(
    title: String,
    name: String, onNameChange: (String) -> Unit,
    species: String, onSpeciesChange: (String) -> Unit,
    breed: String, onBreedChange: (String) -> Unit,
    birthDateText: String, onBirthDateChange: (String) -> Unit,
    gender: Gender, onGenderChange: (Gender) -> Unit,
    color: String, onColorChange: (String) -> Unit,
    chipNumber: String, onChipNumberChange: (String) -> Unit,
    isValid: Boolean,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = isValid) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(value = name, onValueChange = onNameChange,
                label = { Text(stringResource(Res.string.pet_field_name)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            OutlinedTextField(value = species, onValueChange = onSpeciesChange,
                label = { Text(stringResource(Res.string.pet_field_species)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            OutlinedTextField(value = breed, onValueChange = onBreedChange,
                label = { Text(stringResource(Res.string.pet_field_breed)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            OutlinedTextField(value = birthDateText, onValueChange = onBirthDateChange,
                label = { Text(stringResource(Res.string.pet_field_birth_date)) },
                placeholder = { Text("2022-03-15") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            Text(stringResource(Res.string.pet_field_gender),
                style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Gender.entries.forEach { g ->
                    FilterChip(
                        selected = gender == g,
                        onClick = { onGenderChange(g) },
                        label = { Text(g.name.lowercase().replaceFirstChar { it.uppercaseChar() }) }
                    )
                }
            }

            OutlinedTextField(value = color, onValueChange = onColorChange,
                label = { Text(stringResource(Res.string.pet_field_color)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            OutlinedTextField(value = chipNumber, onValueChange = onChipNumberChange,
                label = { Text(stringResource(Res.string.pet_field_chip)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
    }
}
```

- [ ] **Step 2: Create AddPetViewModel.kt**

`ui/pets/AddPetViewModel.kt`:
```kotlin
package app.vetmate.ui.pets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.vetmate.domain.model.Gender
import app.vetmate.domain.usecase.pet.AddPetCommand
import app.vetmate.domain.usecase.pet.AddPetUseCase
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class AddPetViewModel(private val addPet: AddPetUseCase) : ScreenModel {

    var name by mutableStateOf("")
    var species by mutableStateOf("")
    var breed by mutableStateOf("")
    var birthDateText by mutableStateOf("")
    var gender by mutableStateOf(Gender.UNKNOWN)
    var color by mutableStateOf("")
    var chipNumber by mutableStateOf("")

    val isValid: Boolean get() = name.isNotBlank() && species.isNotBlank()

    fun save(onSuccess: () -> Unit) {
        if (!isValid) return
        screenModelScope.launch {
            addPet(AddPetCommand(
                name = name.trim(),
                species = species.trim(),
                breed = breed.trim().ifEmpty { null },
                birthDate = birthDateText.trim().ifEmpty { null }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                gender = gender,
                color = color.trim().ifEmpty { null },
                chipNumber = chipNumber.trim().ifEmpty { null }
            ))
            onSuccess()
        }
    }
}
```

- [ ] **Step 3: Replace AddPetScreen.kt**

`ui/pets/AddPetScreen.kt`:
```kotlin
package app.vetmate.ui.pets

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.jetbrains.compose.resources.stringResource
import vetmate.shared.generated.resources.Res
import vetmate.shared.generated.resources.add_pet

class AddPetScreen : Screen {
    @Composable
    override fun Content() {
        val vm = getScreenModel<AddPetViewModel>()
        val navigator = LocalNavigator.currentOrThrow

        PetFormScreen(
            title = stringResource(Res.string.add_pet),
            name = vm.name, onNameChange = { vm.name = it },
            species = vm.species, onSpeciesChange = { vm.species = it },
            breed = vm.breed, onBreedChange = { vm.breed = it },
            birthDateText = vm.birthDateText, onBirthDateChange = { vm.birthDateText = it },
            gender = vm.gender, onGenderChange = { vm.gender = it },
            color = vm.color, onColorChange = { vm.color = it },
            chipNumber = vm.chipNumber, onChipNumberChange = { vm.chipNumber = it },
            isValid = vm.isValid,
            onSave = { vm.save { navigator.pop() } },
            onBack = { navigator.pop() }
        )
    }
}
```

- [ ] **Step 4: Create EditPetViewModel.kt**

`ui/pets/EditPetViewModel.kt`:
```kotlin
package app.vetmate.ui.pets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.vetmate.domain.model.Gender
import app.vetmate.domain.usecase.pet.GetPetByIdUseCase
import app.vetmate.domain.usecase.pet.UpdatePetCommand
import app.vetmate.domain.usecase.pet.UpdatePetUseCase
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class EditPetViewModel(
    private val getPetById: GetPetByIdUseCase,
    private val updatePet: UpdatePetUseCase,
    private val petId: Long
) : ScreenModel {

    var name by mutableStateOf("")
    var species by mutableStateOf("")
    var breed by mutableStateOf("")
    var birthDateText by mutableStateOf("")
    var gender by mutableStateOf(Gender.UNKNOWN)
    var color by mutableStateOf("")
    var chipNumber by mutableStateOf("")

    val isValid: Boolean get() = name.isNotBlank() && species.isNotBlank()

    init {
        screenModelScope.launch {
            getPetById(petId)?.let { pet ->
                name = pet.name
                species = pet.species
                breed = pet.breed ?: ""
                birthDateText = pet.birthDate?.toString() ?: ""
                gender = pet.gender
                color = pet.color ?: ""
                chipNumber = pet.chipNumber ?: ""
            }
        }
    }

    fun save(onSuccess: () -> Unit) {
        if (!isValid) return
        screenModelScope.launch {
            updatePet(UpdatePetCommand(
                id = petId,
                name = name.trim(),
                species = species.trim(),
                breed = breed.trim().ifEmpty { null },
                birthDate = birthDateText.trim().ifEmpty { null }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                gender = gender,
                color = color.trim().ifEmpty { null },
                chipNumber = chipNumber.trim().ifEmpty { null },
                photoPath = null
            ))
            onSuccess()
        }
    }
}
```

- [ ] **Step 5: Replace EditPetScreen.kt**

`ui/pets/EditPetScreen.kt`:
```kotlin
package app.vetmate.ui.pets

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.core.parameter.parametersOf
import org.jetbrains.compose.resources.stringResource
import vetmate.shared.generated.resources.Res
import vetmate.shared.generated.resources.edit_pet

data class EditPetScreen(val petId: Long) : Screen {
    @Composable
    override fun Content() {
        val vm = getScreenModel<EditPetViewModel> { parametersOf(petId) }
        val navigator = LocalNavigator.currentOrThrow

        PetFormScreen(
            title = stringResource(Res.string.edit_pet),
            name = vm.name, onNameChange = { vm.name = it },
            species = vm.species, onSpeciesChange = { vm.species = it },
            breed = vm.breed, onBreedChange = { vm.breed = it },
            birthDateText = vm.birthDateText, onBirthDateChange = { vm.birthDateText = it },
            gender = vm.gender, onGenderChange = { vm.gender = it },
            color = vm.color, onColorChange = { vm.color = it },
            chipNumber = vm.chipNumber, onChipNumberChange = { vm.chipNumber = it },
            isValid = vm.isValid,
            onSave = { vm.save { navigator.pop() } },
            onBack = { navigator.pop() }
        )
    }
}
```

- [ ] **Step 6: Build**

```bash
./gradlew :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add shared/src/
git commit -m "feat: add AddPet and EditPet screens"
```

---

### Task 11: PetDetailScreen with Tab Stubs

**Files:**
- Create: `shared/src/commonMain/kotlin/app/vetmate/ui/pets/PetTabs.kt`
- Replace: `shared/src/commonMain/kotlin/app/vetmate/ui/pets/PetDetailScreen.kt`

**Interfaces:**
- Consumes: Voyager `TabNavigator`, `Tab`, `GetPetByIdUseCase`
- Produces: `PetDetailScreen(petId)` with 5 tabs; Plans 2–3 replace stub tab bodies

- [ ] **Step 1: Create PetTabs.kt (stub tabs)**

`ui/pets/PetTabs.kt`:
```kotlin
package app.vetmate.ui.pets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import org.jetbrains.compose.resources.stringResource
import vetmate.shared.generated.resources.Res
import vetmate.shared.generated.resources.coming_soon
import vetmate.shared.generated.resources.tab_documents
import vetmate.shared.generated.resources.tab_health
import vetmate.shared.generated.resources.tab_medications
import vetmate.shared.generated.resources.tab_vaccinations
import vetmate.shared.generated.resources.tab_vet_visits

data class VaccinationsTab(val petId: Long) : Tab {
    override val key = "vaccinations-$petId"
    override val options: TabOptions
        @Composable get() = TabOptions(0u, stringResource(Res.string.tab_vaccinations),
            rememberVectorPainter(Icons.Default.Vaccines))
    @Composable override fun Content() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.coming_soon))
        }
    }
}

data class VetVisitsTab(val petId: Long) : Tab {
    override val key = "vetvisits-$petId"
    override val options: TabOptions
        @Composable get() = TabOptions(1u, stringResource(Res.string.tab_vet_visits),
            rememberVectorPainter(Icons.Default.LocalHospital))
    @Composable override fun Content() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.coming_soon))
        }
    }
}

data class MedicationsTab(val petId: Long) : Tab {
    override val key = "medications-$petId"
    override val options: TabOptions
        @Composable get() = TabOptions(2u, stringResource(Res.string.tab_medications),
            rememberVectorPainter(Icons.Default.Medication))
    @Composable override fun Content() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.coming_soon))
        }
    }
}

data class DocumentsTab(val petId: Long) : Tab {
    override val key = "documents-$petId"
    override val options: TabOptions
        @Composable get() = TabOptions(3u, stringResource(Res.string.tab_documents),
            rememberVectorPainter(Icons.Default.Description))
    @Composable override fun Content() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.coming_soon))
        }
    }
}

data class HealthTab(val petId: Long) : Tab {
    override val key = "health-$petId"
    override val options: TabOptions
        @Composable get() = TabOptions(4u, stringResource(Res.string.tab_health),
            rememberVectorPainter(Icons.Default.Favorite))
    @Composable override fun Content() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.coming_soon))
        }
    }
}
```

- [ ] **Step 2: Replace PetDetailScreen.kt**

`ui/pets/PetDetailScreen.kt`:
```kotlin
package app.vetmate.ui.pets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import org.jetbrains.compose.resources.stringResource
import vetmate.shared.generated.resources.Res
import vetmate.shared.generated.resources.action_back
import vetmate.shared.generated.resources.action_edit
import vetmate.shared.generated.resources.pet_detail_title

data class PetDetailScreen(val petId: Long) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        TabNavigator(VaccinationsTab(petId)) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(Res.string.pet_detail_title)) },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.action_back))
                            }
                        },
                        actions = {
                            IconButton(onClick = { navigator.push(EditPetScreen(petId)) }) {
                                Icon(Icons.Default.Edit,
                                    contentDescription = stringResource(Res.string.action_edit))
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        TabItem(VaccinationsTab(petId))
                        TabItem(VetVisitsTab(petId))
                        TabItem(MedicationsTab(petId))
                        TabItem(DocumentsTab(petId))
                        TabItem(HealthTab(petId))
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    CurrentTab()
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current
    NavigationBarItem(
        selected = tabNavigator.current.key == tab.key,
        onClick = { tabNavigator.current = tab },
        icon = { Icon(tab.options.icon!!, contentDescription = tab.options.title) },
        label = { Text(tab.options.title) }
    )
}
```

- [ ] **Step 3: Final build**

```bash
./gradlew :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all tests**

```bash
./gradlew :shared:testAndroidHostTest
```
Expected: All tests PASSED

- [ ] **Step 5: Commit**

```bash
git add shared/src/
git commit -m "feat: add PetDetailScreen with tab navigation scaffold"
```

---

## Self-Review

**Spec coverage check:**
- [x] Local-only storage — SQLDelight on-device, no network
- [x] Multiple pets — PetListScreen with list, AddPet, EditPet, Delete
- [x] All domain models defined (Vaccination, VetVisit, etc.) — stubs ready for Plan 2
- [x] Clean architecture (data / domain / ui) — enforced by package structure
- [x] expect/actual — DatabaseDriverFactory for Android/iOS
- [x] Koin DI — platformModule, dataModule, domainModule, uiModule
- [x] Voyager navigation — Navigator + TabNavigator in PetDetailScreen
- [x] Localization — RU + EN string resources wired
- [ ] Notifications — Plan 3
- [ ] FilePicker / ImageStorage — Plan 3
- [ ] Vaccination/VetVisit/Medication screens — Plan 2
- [ ] Documents / Health Summary screens — Plan 3

**Placeholder scan:** No TBD or TODO in task steps.

**Type consistency:**
- `PetListViewModel(getAllPets: GetAllPetsUseCase, deletePet: DeletePetUseCase)` matches Koin registration `screenModel { PetListViewModel(get(), get()) }`
- `EditPetViewModel(getPetById, updatePet, petId: Long)` matches registration `screenModel { (petId: Long) -> EditPetViewModel(get(), get(), petId) }`
- `PetRepositoryImpl(db: VetMateDatabase)` matches `single<PetRepository> { PetRepositoryImpl(get()) }`
- All use case `operator fun invoke` signatures consistent throughout
