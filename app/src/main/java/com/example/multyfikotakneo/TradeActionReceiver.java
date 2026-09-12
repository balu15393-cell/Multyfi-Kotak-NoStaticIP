package com.example.multyfikotakneo;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TradeActionReceiver extends BroadcastReceiver {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        String ticketId = intent.getStringExtra(ApprovalNotifier.EXTRA_TICKET_ID);
        if (ticketId == null) return;
        TradeStore store = new TradeStore(context);

        if (ApprovalNotifier.ACTION_REJECT.equals(intent.getAction())) {
            try {
                TradeTicket t = store.loadTicket(ticketId);
                if (t == null) return;
                boolean exit = "EXIT".equals(t.ticketKind);
                ApprovalNotifier.cancelTicket(context, t);
                store.appendLog(exit ? "EXIT_IGNORED" : "ENTRY_REJECTED", t,
                        exit ? "User ignored the prepared exit ticket." : "User rejected the prepared entry ticket.");
                store.deleteTicket(ticketId);
            } catch (Exception e) {
                ApprovalNotifier.showStatus(context, "Trade assistant", "Could not reject ticket: " + safe(e));
            }
            return;
        }

        if (!ApprovalNotifier.ACTION_APPROVE.equals(intent.getAction())) return;
        if (!store.claimExecution(ticketId)) {
            ApprovalNotifier.showStatus(context, "Kotak Neo ticket", "This ticket is already being processed.");
            return;
        }

        final PendingResult pending = goAsync();
        EXECUTOR.execute(() -> {
            try {
                TradeTicket original = store.loadTicket(ticketId);
                if (original == null) return;
                boolean exit = "EXIT".equals(original.ticketKind);

                ApprovalNotifier.showStatus(context,
                        exit ? original.symbol + " — refreshing exit" : original.symbol + " — refreshing entry",
                        "Checking latest Kotak Neo LTP/position/funds before opening Kotak Neo…");

                KotakNeoApprovalPreflight.Result refreshed = KotakNeoApprovalPreflight.refresh(context, original);
                if (refreshed.noAction) {
                    store.appendLog(exit ? "EXIT_NO_ACTION" : "ENTRY_NO_ACTION", original, refreshed.message);
                    ApprovalNotifier.cancelTicket(context, original);
                    ApprovalNotifier.showStatus(context, original.symbol + " — no action needed", refreshed.message);
                    store.deleteTicket(ticketId);
                    return;
                }

                TradeTicket t = refreshed.ticket;
                ClipboardManager cb = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText(exit ? "Prepared MIS exit" : "Prepared MIS order", t.displayText()));
                store.appendLog(exit ? "EXIT_APPROVED_OPENED_KOTAK" : "ENTRY_APPROVED_OPENED_KOTAK", t,
                        refreshed.message + " Ticket copied and Kotak Neo opened. No broker order was transmitted by this app.");
                ApprovalNotifier.cancelTicket(context, t);
                ApprovalNotifier.showStatus(context,
                        exit ? t.symbol + " — EXIT READY" : t.symbol + " — ORDER READY",
                        "Latest ticket copied. Review the details in Kotak Neo and confirm the MIS order manually.");
                openKotakNeo(context);
                store.deleteTicket(ticketId);
            } catch (Exception e) {
                store.releaseExecution(ticketId);
                ApprovalNotifier.showStatus(context, "Kotak Neo ticket blocked", safe(e));
            } finally {
                pending.finish();
            }
        });
    }

    private static void openKotakNeo(Context context) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.kotak.neo");
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
            return;
        }
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://ntrade.kotakneo.com/"));
        web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(web);
    }

    private static String safe(Exception e) {
        String m = e == null ? null : e.getMessage();
        return m == null || m.trim().isEmpty() ? "Unknown error" : m;
    }
}
