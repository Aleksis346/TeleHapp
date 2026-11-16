package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HappLogsActivity extends BaseFragment {

    public static final String PREFS = "happ_servers";
    public static final String KEY_DEBUG_LOG = "debug_log_v1";
    public static final String DEBUG_LOG_FILE = "happ_debug.log";
    public static final String ERROR_LOG_FILE = "happ_last_error.txt";
    public static final String CONFIG_FILE = "happ_last_config.json";

    private RecyclerListView listView;
    private ListAdapter adapter;

    private int rowCount;
    private int headerRow;
    private int toggleRow;
    private int refreshRow;
    private int clearRow;
    private int copyRow;
    private int infoRow;

    private String logText = "";

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        reloadLogs();
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadLogs();
        updateRows();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Логи Happ");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout fl = (FrameLayout) fragmentView;
        fl.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        fl.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        adapter = new ListAdapter(context);
        listView.setAdapter(adapter);
        updateRows();
        listView.setOnItemClickListener((view, position) -> {
            if (position == toggleRow) {
                setDebugEnabled(!isDebugEnabled());
                logInternal("Debug logging " + (isDebugEnabled() ? "enabled" : "disabled"));
                reloadLogs();
                updateRows();
                adapter.notifyDataSetChanged();
            } else if (position == refreshRow) {
                reloadLogs();
                updateRows();
                adapter.notifyDataSetChanged();
            } else if (position == clearRow) {
                clearLogs();
                reloadLogs();
                updateRows();
                adapter.notifyDataSetChanged();
            } else if (position == copyRow) {
                if (!TextUtils.isEmpty(logText)) {
                    AndroidUtilities.addToClipboard(logText);
                    // simple feedback via title flash not necessary
                }
            }
        });

        return fragmentView;
    }

    private void updateRows() {
        rowCount = 0;
        headerRow = rowCount++;
        toggleRow = rowCount++;
        refreshRow = rowCount++;
        clearRow = rowCount++;
        copyRow = rowCount++;
        infoRow = rowCount++;
    }

    private Context ctx() {
        Context c = getParentActivity();
        if (c == null) c = ApplicationLoader.applicationContext;
        return c;
    }

    private SharedPreferences prefs() {
        Context c = ctx();
        if (c == null) return null;
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isDebugEnabled(Context c) {
        if (c == null) c = ApplicationLoader.applicationContext;
        if (c == null) return false;
        try {
            return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DEBUG_LOG, false);
        } catch (Throwable ignore) { return false; }
    }

    private boolean isDebugEnabled() {
        return isDebugEnabled(ctx());
    }

    private void setDebugEnabled(boolean enabled) {
        SharedPreferences p = prefs();
        if (p == null) return;
        p.edit().putBoolean(KEY_DEBUG_LOG, enabled).commit();
    }

    public static void appendDebugLog(Context c, String msg) {
        if (c == null) c = ApplicationLoader.applicationContext;
        if (c == null || TextUtils.isEmpty(msg)) return;
        if (!isDebugEnabled(c)) return;
        try {
            File dir = c.getExternalFilesDir(null);
            if (dir == null) dir = c.getFilesDir();
            if (dir == null) return;
            File f = new File(dir, DEBUG_LOG_FILE);
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            String line = ts + " " + msg + "\n";
            FileOutputStream fos = new FileOutputStream(f, true);
            try {
                fos.write(line.getBytes(StandardCharsets.UTF_8));
            } finally {
                fos.close();
            }
            // keep file from growing unbounded (rough trim)
            if (f.length() > 512 * 1024) {
                trimFile(f, 256 * 1024);
            }
        } catch (Throwable ignore) {}
    }

    private static void trimFile(File f, int keepBytes) {
        try {
            byte[] all = readAllBytes(f);
            if (all == null || all.length <= keepBytes) return;
            int start = all.length - keepBytes;
            // move to next newline
            while (start < all.length && all[start] != '\n') start++;
            if (start < all.length) start++;
            FileOutputStream fos = new FileOutputStream(f, false);
            try {
                fos.write(all, start, all.length - start);
            } finally { fos.close(); }
        } catch (Throwable ignore) {}
    }

    private static byte[] readAllBytes(File f) {
        try {
            FileInputStream fis = new FileInputStream(f);
            try {
                byte[] buf = new byte[(int) Math.max(0, f.length())];
                int off = 0, r;
                while (off < buf.length && (r = fis.read(buf, off, buf.length - off)) >= 0) off += r;
                return buf;
            } finally { fis.close(); }
        } catch (Throwable e) { return null; }
    }

    private void logInternal(String msg) {
        appendDebugLog(ctx(), msg);
    }

    private void reloadLogs() {
        StringBuilder sb = new StringBuilder();
        sb.append("Debug logging: ").append(isDebugEnabled() ? "ON" : "OFF").append("\n\n");
        appendFile(sb, "happ_last_error.txt", ERROR_LOG_FILE);
        appendFile(sb, "happ_last_config.json", CONFIG_FILE);
        appendFile(sb, "happ_debug.log", DEBUG_LOG_FILE);
        String text = sb.toString();
        if (text.length() > 12000) {
            text = text.substring(0, 12000) + "\n...(truncated)";
        }
        logText = text;
    }

    private void appendFile(StringBuilder sb, String title, String name) {
        sb.append("=== ").append(title).append(" ===\n");
        try {
            Context c = ctx();
            File dir = c != null ? c.getExternalFilesDir(null) : null;
            if (dir == null && c != null) dir = c.getFilesDir();
            File f = dir != null ? new File(dir, name) : null;
            if (f == null || !f.exists()) {
                sb.append("(not found)\n\n");
                return;
            }
            String content = readFileTail(f, 6000);
            if (TextUtils.isEmpty(content)) sb.append("(empty)\n\n");
            else sb.append(content).append("\n\n");
        } catch (Throwable e) {
            sb.append("(error: ").append(e.getMessage()).append(")\n\n");
        }
    }

    private String readFileTail(File f, int maxChars) {
        try {
            byte[] all = readAllBytes(f);
            if (all == null) return "";
            String s = new String(all, StandardCharsets.UTF_8);
            if (s.length() > maxChars) s = "...(tail)\n" + s.substring(s.length() - maxChars);
            return s;
        } catch (Throwable e) { return ""; }
    }

    private void clearLogs() {
        Context c = ctx();
        if (c == null) return;
        File dir = c.getExternalFilesDir(null);
        if (dir == null) dir = c.getFilesDir();
        if (dir == null) return;
        deleteFile(new File(dir, ERROR_LOG_FILE));
        deleteFile(new File(dir, CONFIG_FILE));
        deleteFile(new File(dir, DEBUG_LOG_FILE));
        logInternal("Logs cleared");
    }

    private void deleteFile(File f) {
        try { if (f.exists()) f.delete(); } catch (Throwable ignore) {}
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;
        ListAdapter(Context context) { this.context = context; }

        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int p = holder.getAdapterPosition();
            return p == toggleRow || p == refreshRow || p == clearRow || p == copyRow;
        }
        @Override public int getItemCount() { return rowCount; }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) {
                view = new HeaderCell(context);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == 1) {
                view = new TextSettingsCell(context);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else {
                view = new TextInfoPrivacyCell(context);
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int vt = holder.getItemViewType();
            if (vt == 0) {
                ((HeaderCell) holder.itemView).setText("Логи Happ / VPN");
            } else if (vt == 1) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (position == toggleRow) {
                    boolean on = isDebugEnabled();
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    cell.setText((on ? "Выключить" : "Включить") + " запись debug-логов", true);
                } else if (position == refreshRow) {
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    cell.setText("Обновить", true);
                } else if (position == clearRow) {
                    cell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                    cell.setText("Очистить логи", true);
                } else if (position == copyRow) {
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    cell.setText("Копировать логи", false);
                }
            } else {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setText(logText != null ? logText : "");
                cell.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) return 0;
            if (position == toggleRow || position == refreshRow || position == clearRow || position == copyRow) return 1;
            return 2;
        }
    }
}
