# Multyfi → Kotak Neo Approval Assistant v2.6 (FINAL — No Static IP)

This Android app listens only to Multyfi (`com.multyfi.invest`) notifications and prepares Kotak Neo NSE cash MIS/intraday entry or urgent-exit tickets.

## Why this build exists

This build intentionally contains **no Kotak Neo place/modify/cancel order call**. It is designed for users who do not have a whitelisted static public IP for live Trade API order submission.

## Entry flow

Multyfi BUY/SELL alert → parse symbol/entry/target/SL → resolve NSE instrument → fetch Kotak Neo LTP → read Kotak available funds → use the lower of saved capital cap or live available funds → calculate MIS quantity → MARKET/LIMIT/no-chase decision → show approval notification.

When you tap **APPROVE & OPEN KOTAK**, the app performs a final preflight before opening Kotak Neo:

- re-fetches latest LTP,
- re-checks live available funds,
- blocks a duplicate open MIS position,
- recalculates quantity,
- keeps MARKET only when price remains inside the advisory range/tolerance,
- changes a chased BUY to LIMIT at the advisory maximum,
- changes a chased SELL to LIMIT at the advisory minimum,
- blocks materially unfavorable movement for manual review.

The refreshed ticket is copied to the clipboard and Kotak Neo opens. You manually review and confirm the order in Kotak Neo.

## Exit flow

Multyfi EXIT / BOOK PROFIT / COST-TO-COST / REDUCE LOSS AND EXIT → verify the current Kotak Neo MIS position → check for an already-active opposite-side order → show urgent exit ticket.

When you tap **EXIT & OPEN KOTAK**, the app checks the position again:

- if already closed manually: no action,
- if partially closed: only the remaining quantity is prepared,
- if an exit order is already active: duplicate exit is blocked,
- otherwise the refreshed MARKET exit ticket is copied and Kotak Neo opens for your manual confirmation.

## Security

- API access token/consumer key, mobile number, UCC and API session are encrypted with Android Keystore-backed storage.
- TOTP and MPIN are used for login only and are not saved.
- Test notifications are always dry-run.
- No securities order is transmitted by this app.

## Build

The included GitHub Actions workflow builds `app-debug.apk` and uploads artifact:

`MultyfiKotakNeoApprovalOnly-debug-apk`

Java 17 / Android compileSdk 35 / minSdk 26.


## v2.4 — Fixed 3-point target
- Advisory target text is ignored for entry-ticket targeting.
- BUY target = prepared entry + 3.00 points.
- SELL target = prepared entry - 3.00 points.
- The target is recalculated at final approval using the latest prepared entry price (latest LTP for MARKET, advisory limit price for LIMIT).
- Because the app is approval-only and the actual MARKET fill can differ slightly, after the order fills use Kotak Neo's actual average entry ± 3 points as the final target.
- Entry remains MIS/INTRADAY. Kotak Neo GTT is not used because its GTT product flow is not MIS. For an open intraday position, use Kotak Neo Target/Stoploss/OCO in the app/website if desired.

## v2.5 stop-loss rule
- Target only is overridden: BUY = prepared entry + 3 points; SELL = prepared entry - 3 points.
- Stop-loss is always copied from the Multyfi advisory notification exactly as parsed.
- The approval-time recheck may change MARKET/LIMIT choice, LTP, capital, and quantity, but it must not recalculate, tighten, widen, or replace the advisory stop-loss.
- If the advisory stop-loss is missing or invalid, the entry is blocked for manual review.


## v2.6 — Final no-static-IP build
- Finalized specifically for connections that do not have a static public IPv4.
- No Kotak Neo place/modify/cancel endpoint exists in this app.
- APPROVE only refreshes LTP/funds/position, prepares the MIS ticket, copies it, and opens Kotak Neo for manual confirmation.
- Entry target rule remains fixed at 3 points from the prepared/actual entry (BUY +3, SELL -3).
- Stop-loss remains exactly the Multyfi advisory stop-loss and is never recalculated by the app.
- Price-chasing protection remains enabled: unfavorable moved entries become advisory-bound LIMIT tickets or are blocked for review.
- Exit alerts re-check the remaining MIS position and pending exit status before opening Kotak Neo.
