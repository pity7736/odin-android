# Work Order: User Registration — Redirect to Home After Success

**Feature design:** `specs/auth/registration/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/auth/registration/spec.md`

> Work order for: **redirect to the home screen after successful registration**.
> Disposable — overwritten by the next change (git keeps the history). The living
> design is in design.md; hydrate it before this change merges, then freeze this
> file.

## Change

After a successful registration the user currently sees a static success message
and stays on the registration form. This change makes the app navigate the user
to the home screen instead. This requires introducing Jetpack Navigation Compose
for screen routing, adding a one-shot navigation event from the ViewModel via a
`Channel`, and wiring the `NavHost` in `MainActivity`.

The dummy `HomeScreen` composable already exists at
`home/presentation/home/HomeScreen.kt`.

**Spec scenario satisfied:** Successful registration (updated: "redirected to
the home area" instead of "see a confirmation message").

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounts/
└── presentation/
    └── registration/
        ├── RegistrationViewModel.kt             # MODIFY — add Channel for navigation event
        └── RegistrationScreen.kt                # MODIFY — add onRegistrationSuccess callback, consume Channel

app/src/main/java/dev/raiseexception/odin/
└── MainActivity.kt                              # MODIFY — NavHost with registration + home routes

gradle/libs.versions.toml                        # MODIFY — add navigation-compose version + library
app/build.gradle.kts                             # MODIFY — add navigation-compose dependency

app/src/test/java/dev/raiseexception/odin/accounts/
└── presentation/
    └── registration/
        └── RegistrationViewModelTest.kt         # MODIFY — add navigation event test

app/src/androidTest/java/dev/raiseexception/odin/accounts/
└── presentation/
    └── registration/
        └── RegistrationScreenTest.kt            # MODIFY — update success test, add navigation callback test
```

## Key Types & Signatures

### RegistrationViewModel (modified)

```kotlin
class RegistrationViewModel(
    private val userRegistrar: UserRegistrar
) : ViewModel() {

    val uiState: StateFlow<RegistrationUiState>

    private val navigationChannel: Channel<NavigationTarget>
    val navigationEvent: Flow<NavigationTarget>

    fun register(rawPassword: String, rawPasswordConfirmation: String)
}

enum class NavigationTarget {
    Home
}
```

On `Outcome.Success`, the ViewModel sends `NavigationTarget.Home` into the
channel. The `UiState` still transitions to `Success` (the screen may briefly
render it before navigation fires — this is fine).

### RegistrationScreen (modified)

```kotlin
@Composable
fun RegistrationScreen(
    uiState: RegistrationUiState,
    onRegister: (String, String) -> Unit,
    navigationEvent: Flow<NavigationTarget>,
    onRegistrationSuccess: () -> Unit,
    modifier: Modifier = Modifier
)
```

The screen collects `navigationEvent` in a `LaunchedEffect` and calls
`onRegistrationSuccess` when `NavigationTarget.Home` arrives.

### MainActivity (modified)

```kotlin
NavHost(navController, startDestination = "registration") {
    composable("registration") { /* RegistrationScreen wired with navigation */ }
    composable("home") { /* HomeScreen */ }
}
```

Navigation to `"home"` uses `popUpTo("registration") { inclusive = true }` so
pressing back from home does not return to registration.

## Implementation Phases (TDD)

### Phase 1: Presentation — ViewModel navigation event

**Red:** Modify `RegistrationViewModelTest`:
- `given valid password, when registering, then emits navigation event to Home`
  (receive from the channel and assert `NavigationTarget.Home`)
- Existing `given valid password, when registering, then emits Loading then Success`
  stays — `UiState` behavior is unchanged.

**Green:** Add `NavigationTarget` enum, `Channel<NavigationTarget>` to
`RegistrationViewModel`, expose as `Flow` via `channel.receiveAsFlow()`. In
`register()`, after setting `UiState.Success`, send `NavigationTarget.Home`
into the channel.

### Phase 2: Presentation — RegistrationScreen callback

**Red:** Modify `RegistrationScreenTest`:
- `given success state, when displayed, then calls onRegistrationSuccess` (pass
  a `Channel` that emits `NavigationTarget.Home`, capture the callback
  invocation).
- Existing success message test is removed — the success message is no longer
  shown since navigation fires immediately.

**Green:** Add `navigationEvent: Flow<NavigationTarget>` and
`onRegistrationSuccess: () -> Unit` parameters to `RegistrationScreen`. Collect
`navigationEvent` in a `LaunchedEffect` and call `onRegistrationSuccess` when
received. Remove the success message from `GeneralMessage`.

### Phase 3: Wiring — Navigation + MainActivity

**Green:** (no new tests — wiring is exercised by manual testing)
1. Add `navigation-compose` to `gradle/libs.versions.toml` and
   `app/build.gradle.kts`.
2. Update `MainActivity`: create `NavHost` with `rememberNavController()`, two
   `composable` routes (`"registration"` and `"home"`). Wire `RegistrationScreen`
   with `onRegistrationSuccess` that navigates to `"home"` with
   `popUpTo("registration") { inclusive = true }`. Wire `HomeScreen` for the
   `"home"` route.

Run `./gradlew check` — all tests green, detekt clean, coverage passing.

## Design decisions to hydrate into design.md

- [ ] Navigation event via `Channel` (not `UiState`) for one-shot actions — `UiState` is persistent screen state, `Channel` is for one-time side effects
- [ ] `NavigationTarget` enum as the event type
- [ ] `MainActivity` now uses `NavHost` with two routes instead of rendering a single screen directly
- [ ] `popUpTo("registration") { inclusive = true }` prevents back-navigating to registration after success
- [ ] Success message removed from registration screen — replaced by immediate navigation
- [ ] Remove "No navigation after success" from Known Limitations
