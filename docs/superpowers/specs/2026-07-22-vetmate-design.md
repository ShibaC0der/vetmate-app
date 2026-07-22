# VetMate — Design Spec

**Date:** 2026-07-22  
**Stack:** Kotlin Multiplatform + Compose Multiplatform  
**Targets:** Android (minSdk 29), iOS (iosArm64, iosSimulatorArm64)

---

## Overview

VetMate — мобильное приложение-ветпаспорт для любых животных (собаки, кошки, экзотика, скот и т.д.). Хранит медицинскую историю питомца: прививки, визиты к ветеринару, лекарства, документы, аллергии и хронические болезни. Данные хранятся локально на устройстве — без аккаунта и интернета. Поддерживает несколько питомцев. Напоминает о предстоящих прививках и визитах через локальные push-уведомления. Интерфейс на русском и английском (по локали устройства).

---

## Architecture

Единый Gradle-модуль `shared` с тремя слоями. Зависимости текут только вниз: `ui → domain → data`.

```
shared/src/commonMain/kotlin/app/vetmate/
├── data/
│   ├── db/           # SQLDelight .sq файлы + DatabaseFactory
│   └── repository/   # реализации репозиториев
├── domain/
│   ├── model/        # доменные модели
│   └── usecase/      # use cases + command-объекты
├── ui/
│   ├── navigation/   # Voyager экраны
│   ├── pets/
│   ├── health/
│   ├── documents/
│   └── common/       # Theme, Typography, shared компоненты
└── platform/
    # expect-объявления: NotificationManager, FilePicker, ImageStorage

shared/src/androidMain/   # actual реализации для Android
shared/src/iosMain/       # actual реализации для iOS
```

**DI:** Koin. ViewModel регистрируется как `screenModel` (Voyager), живёт пока экран в стеке навигации.

---

## Data Layer — SQLDelight Schema

Даты хранятся как `TEXT` (ISO 8601). `ColumnAdapter` конвертирует `String ↔ LocalDate` один раз в `DatabaseFactory`. Удаление питомца каскадно удаляет все связанные записи (`ON DELETE CASCADE`).

```sql
CREATE TABLE Pet (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    species     TEXT    NOT NULL,
    breed       TEXT,
    birthDate   TEXT,
    gender      TEXT,        -- "male" / "female" / "unknown"
    color       TEXT,
    chipNumber  TEXT,
    photoPath   TEXT,
    createdAt   TEXT    NOT NULL
);

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

CREATE TABLE Document (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    petId       INTEGER NOT NULL REFERENCES Pet(id) ON DELETE CASCADE,
    visitId     INTEGER REFERENCES VetVisit(id) ON DELETE SET NULL,  -- опционально
    type        TEXT    NOT NULL,   -- "passport" / "analysis" / "xray" / "other"
    title       TEXT    NOT NULL,
    filePath    TEXT    NOT NULL,
    mimeType    TEXT    NOT NULL,
    createdAt   TEXT    NOT NULL
);

CREATE TABLE Allergy (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    petId       INTEGER NOT NULL REFERENCES Pet(id) ON DELETE CASCADE,
    name        TEXT    NOT NULL,
    severity    TEXT,              -- "mild" / "moderate" / "severe"
    notes       TEXT
);

CREATE TABLE ChronicCondition (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    petId       INTEGER NOT NULL REFERENCES Pet(id) ON DELETE CASCADE,
    name        TEXT    NOT NULL,
    diagnosedAt TEXT,
    notes       TEXT
);
```

`Document.visitId` — nullable. Документ всегда принадлежит питомцу, но опционально привязан к визиту. `ON DELETE SET NULL` — при удалении визита документ сохраняется без привязки.

---

## Domain Layer

### Models

```kotlin
data class Pet(val id: Long, val name: String, val species: String,
               val breed: String?, val birthDate: LocalDate?, val gender: Gender,
               val color: String?, val chipNumber: String?, val photoPath: String?)

data class Vaccination(val id: Long, val petId: Long, val name: String,
                       val date: LocalDate, val nextDate: LocalDate?,
                       val clinic: String?, val doctor: String?,
                       val batchNumber: String?, val notes: String?)

data class VetVisit(val id: Long, val petId: Long, val date: LocalDate,
                    val clinic: String?, val doctor: String?, val reason: String,
                    val diagnosis: String?, val treatment: String?,
                    val nextVisit: LocalDate?, val notes: String?)

data class Medication(val id: Long, val petId: Long, val name: String,
                      val startDate: LocalDate, val endDate: LocalDate?,
                      val dosage: String?, val frequency: String?, val notes: String?)

data class Document(val id: Long, val petId: Long, val visitId: Long?,
                    val type: DocumentType, val title: String,
                    val filePath: String, val mimeType: String,
                    val createdAt: LocalDate)

data class Allergy(val id: Long, val petId: Long, val name: String,
                   val severity: AllergySeverity?, val notes: String?)

data class ChronicCondition(val id: Long, val petId: Long, val name: String,
                             val diagnosedAt: LocalDate?, val notes: String?)

data class HealthSummary(val allergies: List<Allergy>,
                         val conditions: List<ChronicCondition>)

enum class Gender { MALE, FEMALE, UNKNOWN }
enum class DocumentType { PASSPORT, ANALYSIS, XRAY, OTHER }
enum class AllergySeverity { MILD, MODERATE, SEVERE }
```

### Repository Interfaces

Репозитории всегда принимают domain-модели (не Command). Маппинг Command → domain происходит внутри use case перед вызовом репозитория. При insert `id` игнорируется (AUTOINCREMENT).

```kotlin
interface PetRepository {
    fun getAll(): Flow<List<Pet>>
    suspend fun add(pet: Pet): Long
    suspend fun update(pet: Pet)
    suspend fun delete(id: Long)
}

interface VaccinationRepository {
    fun getByPet(petId: Long): Flow<List<Vaccination>>
    suspend fun add(vaccination: Vaccination): Long
    suspend fun delete(id: Long)
}

// Аналогично: VetVisitRepository, MedicationRepository,
// DocumentRepository, AllergyRepository, ChronicConditionRepository
```

### Use Cases

| Фича | Use Case | Вход | Выход |
|---|---|---|---|
| Питомцы | `AddPetUseCase` | `AddPetCommand` | `Long` (id) |
| | `UpdatePetUseCase` | `UpdatePetCommand` | `Unit` |
| | `DeletePetUseCase` | `petId: Long` | `Unit` |
| | `GetAllPetsUseCase` | — | `Flow<List<Pet>>` |
| Прививки | `AddVaccinationUseCase` | `AddVaccinationCommand` | `Long` + планирует уведомление за 3 дня до nextDate |
| | `DeleteVaccinationUseCase` | `id: Long` | `Unit` + отменяет уведомление |
| | `GetVaccinationsUseCase` | `petId: Long` | `Flow<List<Vaccination>>` |
| Визиты | `AddVetVisitUseCase` | `AddVetVisitCommand` | `Long` + уведомление за 3 дня до nextVisit если задан |
| | `DeleteVetVisitUseCase` | `id: Long` | `Unit` + отменяет уведомление |
| | `GetVetVisitsUseCase` | `petId: Long` | `Flow<List<VetVisit>>` |
| Лекарства | `AddMedicationUseCase` | `AddMedicationCommand` | `Long` |
| | `DeleteMedicationUseCase` | `id: Long` | `Unit` |
| | `GetMedicationsUseCase` | `petId: Long` | `Flow<List<Medication>>` |
| Документы | `AddDocumentUseCase` | `AddDocumentCommand` | `Long` |
| | `DeleteDocumentUseCase` | `id: Long` | `Unit` |
| | `GetDocumentsUseCase` | `petId: Long, visitId: Long?` | `Flow<List<Document>>` |
| Здоровье | `AddAllergyUseCase` | `AddAllergyCommand` | `Long` |
| | `DeleteAllergyUseCase` | `id: Long` | `Unit` |
| | `AddChronicConditionUseCase` | `AddChronicConditionCommand` | `Long` |
| | `DeleteChronicConditionUseCase` | `id: Long` | `Unit` |
| | `GetHealthSummaryUseCase` | `petId: Long` | `Flow<HealthSummary>` |

`GetDocumentsUseCase(petId, visitId=null)` — все документы питомца. `visitId != null` — только документы конкретного визита.

Use cases принимают Command-объекты (без `id`, без readonly-полей). Маппинг `Command → Domain model` происходит внутри use case перед передачей в репозиторий.

---

## UI Layer — Screens & Navigation

**Voyager** для навигации. Каждый экран — `data class` с параметрами, реализующий `Screen`.

### Screen Map

```
PetListScreen
├── AddPetScreen
├── EditPetScreen(petId)
└── PetDetailScreen(petId)
    ├── [tab] VaccinationListScreen(petId)
    │         └── AddVaccinationScreen(petId)
    ├── [tab] VetVisitListScreen(petId)
    │         ├── AddVetVisitScreen(petId)
    │         └── VetVisitDetailScreen(visitId)
    │                   └── DocumentsScreen(petId, visitId)
    ├── [tab] MedicationListScreen(petId)
    │         └── AddMedicationScreen(petId)
    ├── [tab] DocumentsScreen(petId, visitId=null)
    │         └── DocumentViewerScreen(documentId)
    └── [tab] HealthSummaryScreen(petId)
```

`PetDetailScreen` использует `TabNavigator` от Voyager — переключение между вкладками не сбрасывает состояние.

### ViewModel Pattern

```kotlin
data class VetVisitDetailScreen(val visitId: Long) : Screen {
    @Composable
    override fun Content() {
        val vm = getScreenModel<VetVisitDetailViewModel>(
            parameters = { parametersOf(visitId) }
        )
        // UI...
    }
}
```

### UI State

Каждый экран имеет собственный sealed `UiState`:

```kotlin
sealed class VaccinationListState {
    object Loading : VaccinationListState()
    data class Success(val items: List<VaccinationUiItem>) : VaccinationListState()
    data class Error(val message: String) : VaccinationListState()
}

data class VaccinationUiItem(
    val id: Long,
    val name: String,
    val dateFormatted: String,
    val isOverdue: Boolean
)
```

Маппинг `Vaccination → VaccinationUiItem` (форматирование дат, вычисление флагов) — extension-функции в пакете `ui/`, не в domain.

---

## Platform-Specific (expect/actual)

### NotificationManager

```kotlin
// commonMain
expect class NotificationManager {
    fun schedule(id: Long, title: String, body: String, date: LocalDate)
    fun cancel(id: Long)
}

// androidMain → AlarmManager + BroadcastReceiver
// iosMain    → UNUserNotificationCenter
```

Уведомления планируются за 3 дня до `nextDate` прививки и `nextVisit` визита — внутри соответствующих use cases.

### FilePicker

```kotlin
expect class FilePicker {
    suspend fun pickImage(): String?     // возвращает path или null если отменено
    suspend fun pickDocument(): String?
}
// androidMain → ActivityResultContracts
// iosMain    → UIImagePickerController / UIDocumentPickerViewController
```

### ImageStorage

```kotlin
expect class ImageStorage {
    suspend fun save(petId: Long, imageBytes: ByteArray): String  // возвращает path
    fun delete(path: String)
}
// androidMain → context.filesDir
// iosMain    → NSDocumentDirectory
```

### Koin DI инициализация

```kotlin
// androidMain
fun initKoin(context: Context) = startKoin {
    androidContext(context)
    modules(platformModule, dataModule, domainModule, uiModule)
}

// iosMain
fun initKoin() = startKoin {
    modules(platformModule, dataModule, domainModule, uiModule)
}
```

---

## Localization

Compose Multiplatform `composeResources`. Язык определяется локалью устройства — ручного переключения нет.

```
shared/src/commonMain/composeResources/
├── values/        → strings.xml  (EN, дефолт)
└── values-ru/     → strings.xml  (RU)
```

Использование: `stringResource(Res.string.key)` — одинаково на Android и iOS.

---

## Dependencies to Add

| Библиотека | Назначение | Версия |
|---|---|---|
| SQLDelight | Локальная БД | 2.x |
| Voyager | Навигация | 1.x |
| Koin | DI | 4.x |

Текущий стек (`compose-multiplatform`, `material3`, `lifecycle-viewmodel-compose`) сохраняется без изменений.
