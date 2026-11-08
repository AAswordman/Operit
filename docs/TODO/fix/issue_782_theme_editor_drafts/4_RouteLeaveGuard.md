# Route Leave Guard

## Previous Behavior

The route guard registry was invoked only for back navigation. Drawer selection, shortcuts, external route requests, and router gateway calls changed routes without consulting a screen's unsaved-state handler.

## Change

Use one suspended route-transition gate for all `AppRouterState.navigate`, `resetTo`, and `pop` entry points in `OperitApp`. The gate captures the current route instance, waits for its registered handler, and applies the transition only while that same instance remains active.

Incoming intents keep the existing Compose tree alive so registered route guards survive the request.

The gate executes on the Compose coroutine scope, which serializes UI, gateway, and external route requests. The latest external target request is retained while a guard is pending.

## Expected Result

The theme editor's save, discard, or cancel dialog is shown consistently when leaving through back navigation, a drawer item, a shortcut, or an external route request.

[DONE]
