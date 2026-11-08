---
fork: https://github.com/AAswordman/Operit
---

# GitHub OAuth Completion Delivery

## Context

The unshipped broker client repeatedly called the Worker while the user was authorizing GitHub. That amplified idle authorization time into request and D1 read volume.

## Goal

Deliver one opaque completion URL after the Worker has stored the encrypted authorization result. Each application owns its browser presentation and completion detection. The client Core keeps the delivery credential private, validates the returned transaction ID and callback destination, and claims the result once.

## Scope

- Worker transaction schema, callback route, and completion redirect validation
- App-owned browser completion handling in Operit and Operit2
- Flutter market-dialog completion delivery
- Rust CLI loopback callback delivery and protocol tests

## Deployment

1. Apply the Worker D1 migration.
2. Deploy the Worker.
3. Ship the clients after their app-owned completion flows are available.
