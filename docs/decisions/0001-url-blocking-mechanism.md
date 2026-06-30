# ADR 0001 — URL blocking mechanism (M2.0)

**Status:** Accepted · **Date:** 2026-06-30 · **Milestone:** M2.0

## Decision

Build toward a **hybrid** blocker:

1. **Domain-level via `VpnService`** (primary, M2.1) — a local, no-root, no-cloud VPN that
   inspects outbound DNS / connection hostnames and allow/denies by domain. Works across **all**
   apps, no browser dependency.
2. **Path-level via the Accessibility service** (later) — read the full URL from a browser's
   address bar (reusing the Phase 1 `ForegroundService`) to block specific paths where the network
   layer can't see them.

Both consult the same local **registry** (M2.2) first, so known-bad entries block instantly.

## Why

The honest HTTPS constraint (from the roadmap): at the network layer you can see the **hostname**
via the TLS SNI field, but **not the path/query** — those are encrypted. So:

- A VPN/DNS filter can block `example.com` but **cannot** see `example.com/specific-page`.
- Per-path blocking is only possible by (a) reading the browser address bar via Accessibility
  (browser-only, only while visible, brittle across browser UIs), or (b) a browser extension.

Domain-level gives robust coverage with no browser dependency; path-level adds precision where it
matters most (browsers). Neither alone is sufficient, hence hybrid.

## Known limits

- **No path blocking at the network layer** — domain granularity only via the VPN.
- **Path reading is browser-only and brittle** — depends on each browser's address-bar UI.
- **Emerging ECH / Encrypted SNI** will eventually hide the hostname from network-level filtering
  too; revisit when that becomes common.
- **One VPN slot** — Android allows a single active VPN; this app occupies it (no other VPN at the
  same time).

## Build order

M2.2 registry (mechanism-agnostic, built first) → M2.1 VpnService skeleton (domain filter consulting
the registry) → M2.3 classifier (decides what gets added) → path-level address-bar reading (Phase 3
text reading feeds this).
