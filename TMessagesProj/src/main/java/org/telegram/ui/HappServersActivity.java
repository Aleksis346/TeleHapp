package org.telegram.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.happ.HappLinkParser;
import org.telegram.messenger.happ.HappVpnManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class HappServersActivity extends BaseFragment {
    private static final String PREFS = "happ_servers";
    private static final String KEY_SERVERS = "servers_v1";
    private static final String KEY_SERVER_META = "servers_meta_v1";
    private static final String KEY_HIDE_AUTO = "hide_auto_v1";
    private static final String KEY_GROUP_MODE = "group_mode_v1";
    private static final String KEY_EXPANDED_GROUPS = "expanded_groups_v1";
    private static final int REQ_VPN_PERMISSION = 5011;
    private static final int MENU_VPN = 1;

    private RecyclerListView listView;
    private ListAdapter adapter;
    private ActionBarMenuItem vpnMenuItem;
    private final ArrayList<String> servers = new ArrayList<>();
    private final ArrayList<String> serverSources = new ArrayList<>();
    private boolean hideAuto;
    private boolean groupMode = true;
    private final HashSet<String> expandedGroups = new HashSet<>();
    private int headerRow, addRow, connectRow, refreshRow, cleanupRow, toggleAutoRow, toggleGroupRow, logsRow, clearAllRow, infoRow, serversStartRow, serversEndRow, rowCount;
    private final BroadcastReceiver vpnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && HappVpnManager.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                AndroidUtilities.runOnUIThread(() -> updateUiState());
            }
        }
    };
    private boolean vpnReceiverRegistered;

    private static class DisplayItem {
        int rawIndex = -1;
        String groupKey;
        String title;
        boolean group;
        boolean expanded;
        int count;
        ArrayList<Integer> rawIndices;
    }
    private final ArrayList<DisplayItem> displayItems = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        loadState();
        rebuildDisplay();
        updateRows();
        registerVpnReceiver();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        unregisterVpnReceiver();
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        registerVpnReceiver();
        updateUiState();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterVpnReceiver();
    }

    private void registerVpnReceiver() {
        if (vpnReceiverRegistered) return;
        Context ctx = getParentActivity();
        if (ctx == null) {
            try { ctx = org.telegram.messenger.ApplicationLoader.applicationContext; } catch (Throwable ignore) {}
        }
        if (ctx == null) return;
        try {
            IntentFilter filter = new IntentFilter(HappVpnManager.ACTION_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(vpnStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                ctx.registerReceiver(vpnStateReceiver, filter);
            }
            vpnReceiverRegistered = true;
        } catch (Throwable ignore) {}
    }

    private void unregisterVpnReceiver() {
        if (!vpnReceiverRegistered) return;
        Context ctx = getParentActivity();
        if (ctx == null) {
            try { ctx = org.telegram.messenger.ApplicationLoader.applicationContext; } catch (Throwable ignore) {}
        }
        if (ctx == null) return;
        try {
            ctx.unregisterReceiver(vpnStateReceiver);
        } catch (Throwable ignore) {}
        vpnReceiverRegistered = false;
    }

    private void updateUiState() {
        boolean running = HappVpnManager.isRunning();
        if (actionBar != null) {
            actionBar.setTitle(running ? "TeleHapp Servers (Connected)" : "TeleHapp Servers");
        }
        if (vpnMenuItem != null) {
            vpnMenuItem.setText(running ? "VPN ON" : "VPN OFF");
        }
        rebuildDisplay();
        if (adapter != null) {
            updateRows();
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(HappVpnManager.isRunning() ? "TeleHapp Servers (Connected)" : "TeleHapp Servers");
        ActionBarMenu menu = actionBar.createMenu();
        vpnMenuItem = menu.addItem(MENU_VPN, HappVpnManager.isRunning() ? "VPN ON" : "VPN OFF");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_VPN) {
                    onConnectClicked();
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
        listView.setOnItemClickListener((view, position) -> onItemClicked(position));
        listView.setOnItemLongClickListener((view, position) -> {
            if (position >= serversStartRow && position < serversEndRow) {
                DisplayItem di = getDisplayItem(position);
                if (di != null && !di.group && di.rawIndex >= 0) {
                    promptServerActions(di.rawIndex);
                    return true;
                }
                if (di != null && di.group) {
                    promptGroupActions(di);
                    return true;
                }
            }
            return false;
        });
        return fragmentView;
    }

    private void onItemClicked(int position) {
        if (position == addRow) {
            promptAddServer();
        } else if (position == connectRow) {
            onConnectClicked();
        } else if (position == refreshRow) {
            refreshSubscriptions();
        } else if (position == cleanupRow) {
            cleanupServers();
        } else if (position == toggleAutoRow) {
            hideAuto = !hideAuto;
            saveUiPrefs();
            updateUiState();
        } else if (position == toggleGroupRow) {
            groupMode = !groupMode;
            saveUiPrefs();
            updateUiState();
        } else if (position == logsRow) {
            presentFragment(new HappLogsActivity());
        } else if (position == clearAllRow) {
            confirmClearAll();
        } else if (position >= serversStartRow && position < serversEndRow) {
            DisplayItem di = getDisplayItem(position);
            if (di == null) return;
            if (di.group) {
                if (di.expanded) expandedGroups.remove(di.groupKey); else expandedGroups.add(di.groupKey);
                saveUiPrefs();
                rebuildDisplay();
                updateRows();
                if (adapter != null) adapter.notifyDataSetChanged();
            } else if (di.rawIndex >= 0 && di.rawIndex < servers.size()) {
                promptServerActions(di.rawIndex);
            }
        }
    }

    private DisplayItem getDisplayItem(int position) {
        int i = position - serversStartRow;
        if (i < 0 || i >= displayItems.size()) return null;
        return displayItems.get(i);
    }

    private void updateRows() {
        rowCount = 0;
        headerRow = rowCount++;
        addRow = rowCount++;
        connectRow = rowCount++;
        refreshRow = rowCount++;
        cleanupRow = rowCount++;
        toggleAutoRow = rowCount++;
        toggleGroupRow = rowCount++;
        logsRow = rowCount++;
        clearAllRow = rowCount++;
        serversStartRow = rowCount;
        rowCount += displayItems.size();
        serversEndRow = rowCount;
        infoRow = rowCount++;
    }

    private void loadState() {
        servers.clear();
        serverSources.clear();
        SharedPreferences prefs = getSharedPreferences();
        if (prefs == null) return;
        hideAuto = prefs.getBoolean(KEY_HIDE_AUTO, false);
        groupMode = prefs.getBoolean(KEY_GROUP_MODE, true);
        expandedGroups.clear();
        String exp = prefs.getString(KEY_EXPANDED_GROUPS, "");
        if (!TextUtils.isEmpty(exp)) {
            try {
                JSONArray arr = new JSONArray(exp);
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, null);
                    if (!TextUtils.isEmpty(s)) expandedGroups.add(s);
                }
            } catch (Exception ignore) {}
        }
        String data = prefs.getString(KEY_SERVERS, "");
        if (!TextUtils.isEmpty(data)) {
            String trimmed = data.trim();
            if (trimmed.startsWith("[")) {
                try {
                    JSONArray arr = new JSONArray(trimmed);
                    for (int i = 0; i < arr.length(); i++) {
                        String s = arr.optString(i, null);
                        if (!TextUtils.isEmpty(s)) {
                            String t = s.trim();
                            if (!t.isEmpty()) servers.add(t);
                        }
                    }
                } catch (Exception ignore) {}
            } else {
                for (String p : data.split("\n")) {
                    String t = p.trim();
                    if (!t.isEmpty()) servers.add(t);
                }
            }
        }
        ArrayList<String> loadedSources = new ArrayList<>();
        String meta = prefs.getString(KEY_SERVER_META, "");
        if (!TextUtils.isEmpty(meta)) {
            try {
                JSONArray arr = new JSONArray(meta);
                for (int i = 0; i < arr.length(); i++) loadedSources.add(arr.optString(i, ""));
            } catch (Exception ignore) {}
        }
        for (int i = 0; i < servers.size(); i++) {
            serverSources.add(i < loadedSources.size() ? (loadedSources.get(i) == null ? "" : loadedSources.get(i)) : "");
        }
    }

    private void saveServers() {
        SharedPreferences prefs = getSharedPreferences();
        if (prefs == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < servers.size(); i++) arr.put(servers.get(i));
            JSONArray meta = new JSONArray();
            for (int i = 0; i < servers.size(); i++) meta.put(i < serverSources.size() ? serverSources.get(i) : "");
            prefs.edit().putString(KEY_SERVERS, arr.toString()).putString(KEY_SERVER_META, meta.toString()).commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void saveUiPrefs() {
        SharedPreferences prefs = getSharedPreferences();
        if (prefs == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (String s : expandedGroups) arr.put(s);
            prefs.edit().putBoolean(KEY_HIDE_AUTO, hideAuto)
                    .putBoolean(KEY_GROUP_MODE, groupMode)
                    .putString(KEY_EXPANDED_GROUPS, arr.toString())
                    .commit();
        } catch (Exception ignore) {}
    }

    private SharedPreferences getSharedPreferences() {
        Context ctx = getParentActivity();
        if (ctx == null) {
            try { ctx = org.telegram.messenger.ApplicationLoader.applicationContext; } catch (Throwable ignore) {}
        }
        if (ctx == null) return null;
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void rebuildDisplay() {
        displayItems.clear();
        ArrayList<Integer> visible = new ArrayList<>();
        for (int i = 0; i < servers.size(); i++) {
            if (hideAuto && isAutoServer(servers.get(i))) continue;
            visible.add(i);
        }
        if (!groupMode) {
            for (int idx : visible) {
                DisplayItem di = new DisplayItem();
                di.rawIndex = idx;
                di.title = serverTitle(servers.get(idx), idx, false);
                di.group = false;
                displayItems.add(di);
            }
            return;
        }
        LinkedHashMap<String, ArrayList<Integer>> groups = new LinkedHashMap<>();
        for (int idx : visible) {
            String key = groupKey(servers.get(idx));
            ArrayList<Integer> list = groups.get(key);
            if (list == null) { list = new ArrayList<>(); groups.put(key, list); }
            list.add(idx);
        }
        for (Map.Entry<String, ArrayList<Integer>> e : groups.entrySet()) {
            ArrayList<Integer> list = e.getValue();
            if (list.size() == 1) {
                DisplayItem di = new DisplayItem();
                di.rawIndex = list.get(0);
                di.title = serverTitle(servers.get(di.rawIndex), di.rawIndex, false);
                di.group = false;
                displayItems.add(di);
            } else {
                String key = e.getKey();
                boolean expanded = expandedGroups.contains(key);
                DisplayItem g = new DisplayItem();
                g.group = true;
                g.expanded = expanded;
                g.groupKey = key;
                g.rawIndices = list;
                g.count = list.size();
                g.title = groupTitle(list, expanded);
                displayItems.add(g);
                if (expanded) {
                    for (int idx : list) {
                        DisplayItem di = new DisplayItem();
                        di.rawIndex = idx;
                        di.title = "    " + serverTitle(servers.get(idx), idx, false);
                        di.group = false;
                        displayItems.add(di);
                    }
                }
            }
        }
    }

    private boolean isAutoServer(String link) {
        String n = displayName(link).toLowerCase(Locale.ROOT);
        return n.contains("авто") || n.contains("auto") || n.contains("turbo") || n.contains("турбо");
    }

    private String displayName(String link) {
        HappLinkParser.Profile p = HappLinkParser.parse(link);
        if (p != null && p.name != null && !p.name.trim().isEmpty()) return p.name.trim();
        if (p != null && p.server != null) return p.type + "://" + p.server + ":" + p.port;
        return link != null ? link : "";
    }

    private String groupKey(String link) {
        HappLinkParser.Profile p = HappLinkParser.parse(link);
        String name = displayName(link);
        String endpoint = "";
        if (p != null && p.server != null) endpoint = p.server + ":" + p.port + ":" + (p.type != null ? p.type : "");
        return name + "|" + endpoint;
    }

    private String groupTitle(ArrayList<Integer> indices, boolean expanded) {
        String base = displayName(servers.get(indices.get(0)));
        if (base.length() > 60) base = base.substring(0, 57) + "...";
        String arrow = expanded ? "▾ " : "▸ ";
        boolean hasSel = indices.contains(HappVpnManager.getSelectedIndex());
        return (hasSel ? "● " : "○ ") + arrow + base + "  (" + indices.size() + ")";
    }

    private String serverTitle(String value, int index, boolean indent) {
        HappLinkParser.Profile p = HappLinkParser.parse(value);
        String title = displayName(value);
        if (title.length() > 70) title = title.substring(0, 67) + "...";
        boolean supported = p != null && p.isSupported();
        String prefix = (HappVpnManager.getSelectedIndex() == index ? "● " : "○ ");
        if (!supported) prefix = "× ";
        String t = prefix + title + (supported ? "" : " (не поддерживается для VPN)");
        return indent ? "    " + t : t;
    }

    private void onConnectClicked() {
        Activity a = getParentActivity();
        if (a == null) return;
        if (HappVpnManager.isRunning()) {
            HappVpnManager.stop(a);
            HappVpnManager.setRunning(false);
            updateUiState();
            AndroidUtilities.runOnUIThread(this::updateUiState, 300);
            AndroidUtilities.runOnUIThread(this::updateUiState, 1000);
            return;
        }
        int idx = HappVpnManager.getSelectedIndex();
        if (idx < 0 || idx >= servers.size()) {
            showSimpleAlert("VPN", "Сначала выберите сервер.");
            return;
        }
        String link = servers.get(idx);
        HappLinkParser.Profile p = HappLinkParser.parse(link);
        if (p == null || !p.isSupported()) {
            showSimpleAlert("VPN", "Выбранный профиль не поддерживается. Нужен VLESS или Shadowsocks (SS).");
            return;
        }
        Intent prep = HappVpnManager.prepare(a);
        if (prep != null) {
            startActivityForResult(prep, REQ_VPN_PERMISSION);
            return;
        }
        HappVpnManager.start(a, link);
        HappVpnManager.setRunning(true);
        updateUiState();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_VPN_PERMISSION) {
            if (resultCode == Activity.RESULT_OK) {
                int idx = HappVpnManager.getSelectedIndex();
                if (idx >= 0 && idx < servers.size() && getParentActivity() != null) {
                    HappVpnManager.start(getParentActivity(), servers.get(idx));
                    HappVpnManager.setRunning(true);
                }
            } else {
                HappVpnManager.setRunning(false);
                showSimpleAlert("VPN", "Разрешение VPN не было предоставлено.");
            }
            updateUiState();
        }
    }

    private void promptAddServer() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Add / Import servers");
        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(16);
        editText.setHint("Ссылка (vless://, ss://, vmess://, trojan://), JSON или URL подписки (https://...)");
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI);
        editText.setMinLines(3);
        editText.setMaxLines(8);
        editText.setGravity(Gravity.TOP | Gravity.LEFT);
        editText.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton("Import/Add", (dialog, which) -> {
            String value = editText.getText() != null ? editText.getText().toString().trim() : "";
            if (value.isEmpty()) return;
            if (isHttpUrl(value) && !value.contains("\n") && !value.contains(" ")) {
                promptSubscriptionImportMode(value);
            } else {
                ArrayList<String> parsed = parseServersInput(value);
                if (parsed.isEmpty()) {
                    showSimpleAlert("Import", "Не удалось распознать серверы. Вставьте ссылки (vless/ss/vmess/trojan), JSON, base64-текст или URL подписки.");
                } else {
                    int[] r = addServers(parsed, "", false, false);
                    showSimpleAlert("Import", "Добавлено: " + r[0] + ", пропущено (дубли/некорр.): " + r[1]);
                }
            }
        });
        showDialog(builder.create());
    }

    private void promptSubscriptionImportMode(final String url) {
        if (getParentActivity() == null) return;
        CharSequence[] items = new CharSequence[]{
                "Добавить новые (не удалять текущие)",
                "Заменить серверы из этой подписки",
                "Заменить все серверы этой подпиской"
        };
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Импорт подписки");
        b.setItems(items, (d, which) -> {
            if (which == 0) importFromUrl(url, false, false);
            else if (which == 1) importFromUrl(url, true, false);
            else importFromUrl(url, false, true);
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void importFromUrl(String url, boolean replaceSameSource, boolean replaceAll) {
        if (getParentActivity() == null) return;
        final AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        showDialog(progress);
        new Thread(() -> {
            String error = null;
            ArrayList<String> parsed = new ArrayList<>();
            try {
                String body = fetchUrl(url);
                parsed = parseServersInput(body);
            } catch (Exception e) {
                FileLog.e(e);
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            final String finalError = error;
            final ArrayList<String> finalParsed = parsed;
            AndroidUtilities.runOnUIThread(() -> {
                try { progress.dismiss(); } catch (Exception ignore) {}
                if (getParentActivity() == null) return;
                if (finalError != null) {
                    showSimpleAlert("Import error", finalError);
                    return;
                }
                if (finalParsed.isEmpty()) {
                    showSimpleAlert("Import", "По ссылке не найдено серверов. Проверьте, что это подписка/JSON/base64 со ссылками.");
                } else {
                    int[] r = addServers(finalParsed, url, replaceSameSource, replaceAll);
                    showSimpleAlert("Import", "Найдено: " + finalParsed.size() + "\nДобавлено: " + r[0] + "\nУдалено (замена): " + r[2] + "\nПропущено (дубли/некорр.): " + r[1]);
                }
            });
        }, "happ-import-url").start();
    }

    private int[] addServers(ArrayList<String> parsed, String sourceUrl, boolean replaceSameSource, boolean replaceAll) {
        if (parsed == null) parsed = new ArrayList<>();
        String src = sourceUrl == null ? "" : sourceUrl.trim();
        int removed = 0;
        String selectedLink = getSelectedLink();
        if (replaceAll) {
            removed = servers.size();
            servers.clear();
            serverSources.clear();
        } else if (replaceSameSource && !TextUtils.isEmpty(src)) {
            for (int i = servers.size() - 1; i >= 0; i--) {
                String ssrc = i < serverSources.size() ? serverSources.get(i) : "";
                if (src.equals(ssrc)) {
                    servers.remove(i);
                    if (i < serverSources.size()) serverSources.remove(i);
                    removed++;
                }
            }
        }
        ArrayList<String> uniqueParsed = dedupePreserve(parsed);
        LinkedHashMap<String, String> byKey = new LinkedHashMap<>();
        for (String s : uniqueParsed) {
            String key = normalizedServerKey(s);
            if (!byKey.containsKey(key)) byKey.put(key, s.trim());
        }
        HashSet<String> existingExact = new HashSet<>(servers);
        HashSet<String> existingKeys = new HashSet<>();
        for (String s : servers) existingKeys.add(normalizedServerKey(s));
        int added = 0, skipped = 0;
        for (Map.Entry<String, String> e : byKey.entrySet()) {
            String t = e.getValue();
            if (TextUtils.isEmpty(t)) { skipped++; continue; }
            if (existingExact.contains(t) || existingKeys.contains(e.getKey())) { skipped++; continue; }
            servers.add(t);
            serverSources.add(src);
            existingExact.add(t);
            existingKeys.add(e.getKey());
            added++;
        }
        if (added > 0 || removed > 0) {
            restoreSelection(selectedLink);
            saveServers();
            rebuildDisplay();
            updateRows();
        }
        return new int[]{added, skipped, removed};
    }

    private String getSelectedLink() {
        int idx = HappVpnManager.getSelectedIndex();
        if (idx >= 0 && idx < servers.size()) return servers.get(idx);
        return null;
    }

    private void restoreSelection(String selectedLink) {
        if (selectedLink == null) {
            if (servers.isEmpty()) HappVpnManager.setSelectedIndex(-1);
            else if (HappVpnManager.getSelectedIndex() < 0 || HappVpnManager.getSelectedIndex() >= servers.size()) HappVpnManager.setSelectedIndex(0);
            return;
        }
        int idx = servers.indexOf(selectedLink);
        if (idx >= 0) {
            HappVpnManager.setSelectedIndex(idx);
        } else {
            String key = normalizedServerKey(selectedLink);
            int found = -1;
            for (int i = 0; i < servers.size(); i++) {
                if (key.equals(normalizedServerKey(servers.get(i)))) { found = i; break; }
            }
            if (found >= 0) HappVpnManager.setSelectedIndex(found);
            else if (!servers.isEmpty()) HappVpnManager.setSelectedIndex(Math.min(Math.max(HappVpnManager.getSelectedIndex(), 0), servers.size() - 1));
            else HappVpnManager.setSelectedIndex(-1);
        }
    }

    private String normalizedServerKey(String link) {
        if (link == null) return "";
        HappLinkParser.Profile p = HappLinkParser.parse(link);
        String name = displayName(link).trim().toLowerCase(Locale.ROOT);
        if (p != null && p.server != null) {
            String type = p.type != null ? p.type.toLowerCase(Locale.ROOT) : "";
            String sec = p.security != null ? p.security.toLowerCase(Locale.ROOT) : "";
            String net = p.network != null ? p.network.toLowerCase(Locale.ROOT) : "";
            String id = p.uuid != null ? p.uuid.toLowerCase(Locale.ROOT) : (p.password != null ? p.password : "");
            return name + "|" + type + "|" + p.server.toLowerCase(Locale.ROOT) + "|" + p.port + "|" + sec + "|" + net + "|" + id;
        }
        return link.trim();
    }

    private void refreshSubscriptions() {
        ArrayList<String> urls = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < serverSources.size(); i++) {
            String s = serverSources.get(i);
            if (!TextUtils.isEmpty(s) && isHttpUrl(s) && seen.add(s)) urls.add(s);
        }
        if (urls.isEmpty()) {
            showSimpleAlert("Подписки", "Нет сохранённых URL подписок. Импортируйте серверы по URL подписки, чтобы обновлять их.");
            return;
        }
        refreshSubscriptionsSequential(urls, 0, 0, 0, 0);
    }

    private void refreshSubscriptionsSequential(final ArrayList<String> urls, final int idx, final int totalAdded, final int totalRemoved, final int totalFound) {
        if (getParentActivity() == null) return;
        if (idx >= urls.size()) {
            showSimpleAlert("Подписки", "Обновлено подписок: " + urls.size() + "\nНайдено: " + totalFound + "\nДобавлено: " + totalAdded + "\nУдалено (замена): " + totalRemoved);
            return;
        }
        final String url = urls.get(idx);
        final AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.setMessage("Обновление " + (idx + 1) + "/" + urls.size() + "...");
        showDialog(progress);
        new Thread(() -> {
            String error = null;
            ArrayList<String> parsed = new ArrayList<>();
            try {
                String body = fetchUrl(url);
                parsed = parseServersInput(body);
            } catch (Exception e) {
                FileLog.e(e);
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            final String fErr = error;
            final ArrayList<String> fParsed = parsed;
            AndroidUtilities.runOnUIThread(() -> {
                try { progress.dismiss(); } catch (Exception ignore) {}
                if (getParentActivity() == null) return;
                int add = totalAdded, rem = totalRemoved, found = totalFound;
                if (fErr == null && !fParsed.isEmpty()) {
                    int[] r = addServers(fParsed, url, true, false);
                    add += r[0]; rem += r[2]; found += fParsed.size();
                }
                final int na = add, nr = rem, nf = found;
                if (fErr != null) {
                    AlertDialog.Builder eb = new AlertDialog.Builder(getParentActivity());
                    eb.setTitle("Ошибка обновления");
                    String u = url.length() > 60 ? url.substring(0, 57) + "..." : url;
                    eb.setMessage(u + "\n" + fErr);
                    eb.setPositiveButton("Далее", (d, w) -> refreshSubscriptionsSequential(urls, idx + 1, na, nr, nf));
                    eb.setNegativeButton("Стоп", null);
                    showDialog(eb.create());
                } else {
                    refreshSubscriptionsSequential(urls, idx + 1, na, nr, nf);
                }
            });
        }, "happ-refresh-sub").start();
    }

    private void cleanupServers() {
        if (getParentActivity() == null) return;
        String selectedLink = getSelectedLink();
        int before = servers.size();
        LinkedHashMap<String, Integer> keep = new LinkedHashMap<>();
        int sel = HappVpnManager.getSelectedIndex();
        for (int i = 0; i < servers.size(); i++) {
            String key = normalizedServerKey(servers.get(i));
            if (!keep.containsKey(key)) keep.put(key, i);
        }
        if (sel >= 0 && sel < servers.size()) keep.put(normalizedServerKey(servers.get(sel)), sel);
        ArrayList<Integer> keptIdx = new ArrayList<>(new HashSet<>(keep.values()));
        Collections.sort(keptIdx);
        ArrayList<String> ns = new ArrayList<>(), nsrc = new ArrayList<>();
        for (int i : keptIdx) {
            ns.add(servers.get(i));
            nsrc.add(i < serverSources.size() ? serverSources.get(i) : "");
        }
        servers.clear();
        servers.addAll(ns);
        serverSources.clear();
        serverSources.addAll(nsrc);
        restoreSelection(selectedLink);
        saveServers();
        rebuildDisplay();
        updateRows();
        if (adapter != null) adapter.notifyDataSetChanged();
        showSimpleAlert("Очистка", "Удалено повторяющихся/лишних: " + (before - servers.size()) + "\nОсталось: " + servers.size());
    }

    private void confirmClearAll() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Очистить список");
        b.setMessage("Удалить все серверы (" + servers.size() + ")?");
        b.setPositiveButton("Удалить", (d, w) -> {
            servers.clear();
            serverSources.clear();
            HappVpnManager.setSelectedIndex(-1);
            saveServers();
            rebuildDisplay();
            updateRows();
            if (adapter != null) adapter.notifyDataSetChanged();
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void promptGroupActions(DisplayItem group) {
        if (getParentActivity() == null || group == null || group.rawIndices == null) return;
        CharSequence[] items = new CharSequence[]{"Свернуть/развернуть", "Удалить группу", "Копировать первый"};
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Группа (" + group.count + ")");
        b.setItems(items, (d, which) -> {
            if (which == 0) {
                if (group.expanded) expandedGroups.remove(group.groupKey); else expandedGroups.add(group.groupKey);
                saveUiPrefs();
                updateUiState();
            } else if (which == 1) {
                String sel = getSelectedLink();
                ArrayList<Integer> idxs = new ArrayList<>(group.rawIndices);
                Collections.sort(idxs);
                Collections.reverse(idxs);
                for (int i : idxs) {
                    if (i >= 0 && i < servers.size()) {
                        servers.remove(i);
                        if (i < serverSources.size()) serverSources.remove(i);
                    }
                }
                restoreSelection(sel);
                saveServers();
                rebuildDisplay();
                updateRows();
                if (adapter != null) adapter.notifyDataSetChanged();
            } else if (which == 2) {
                if (!group.rawIndices.isEmpty()) {
                    int i = group.rawIndices.get(0);
                    if (i >= 0 && i < servers.size()) AndroidUtilities.addToClipboard(servers.get(i));
                }
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private String fetchUrl(String urlString) throws Exception {
        HttpURLConnection connection = null;
        InputStream is = null;
        try {
            String finalUrl = appendHwidParam(urlString);
            URL url = new URL(finalUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod("GET");
            String hwid = org.telegram.messenger.happ.HappHwid.getHwid();
            connection.setRequestProperty("User-Agent", "Happ/1.0 (TeleHapp)");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("x-hwid", hwid);
            connection.setRequestProperty("X-HWID", hwid);
            connection.setRequestProperty("x-device-os", "android");
            connection.setRequestProperty("X-Device-OS", "android");
            int code = connection.getResponseCode();
            is = (code >= 200 && code < 300) ? connection.getInputStream() : connection.getErrorStream();
            if (is == null) throw new Exception("HTTP " + code);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            if (code < 200 || code >= 300) {
                String body = sb.toString();
                if (body.length() > 200) body = body.substring(0, 200);
                throw new Exception("HTTP " + code + (body.isEmpty() ? "" : ": " + body));
            }
            return sb.toString();
        } finally {
            try { if (is != null) is.close(); } catch (Exception ignore) {}
            if (connection != null) connection.disconnect();
        }
    }

    private String appendHwidParam(String urlString) {
        try {
            if (urlString == null) return urlString;
            if (urlString.toLowerCase().contains("hwid=")) return urlString;
            String hwid = org.telegram.messenger.happ.HappHwid.getHwid();
            return urlString + (urlString.contains("?") ? "&" : "?") + "hwid=" + URLEncoder.encode(hwid, "UTF-8");
        } catch (Exception e) {
            return urlString;
        }
    }

    private boolean isHttpUrl(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase();
        return t.startsWith("http://") || t.startsWith("https://");
    }

    private ArrayList<String> parseServersInput(String input) {
        ArrayList<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(input)) return out;
        String trimmed = input.trim();
        try {
            if (trimmed.startsWith("[")) {
                JSONArray arr = new JSONArray(trimmed);
                extractFromJsonArray(arr, out);
                if (!out.isEmpty()) return dedupePreserve(out);
            } else if (trimmed.startsWith("{")) {
                JSONObject obj = new JSONObject(trimmed);
                extractFromJsonObject(obj, out);
                if (!out.isEmpty()) return dedupePreserve(out);
            }
        } catch (Exception ignore) {}
        collectFromText(trimmed, out);
        if (!out.isEmpty()) return dedupePreserve(out);
        String decoded = tryDecodeBase64ToString(trimmed);
        if (decoded != null && !decoded.equals(trimmed)) {
            ArrayList<String> out2 = parseServersInput(decoded);
            if (!out2.isEmpty()) return out2;
        }
        return dedupePreserve(out);
    }

    private void extractFromJsonArray(JSONArray arr, ArrayList<String> out) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            Object v = arr.opt(i);
            if (v instanceof String) {
                String s = ((String) v).trim();
                if (looksLikeServerLink(s) || isHttpUrl(s)) out.add(s); else collectFromText(s, out);
            } else if (v instanceof JSONObject) {
                JSONObject jo = (JSONObject) v;
                ArrayList<String> converted = convertHappConfigToLinks(jo);
                if (!converted.isEmpty()) out.addAll(converted); else extractFromJsonObject(jo, out);
            } else if (v instanceof JSONArray) {
                extractFromJsonArray((JSONArray) v, out);
            }
        }
    }

    private void extractFromJsonObject(JSONObject obj, ArrayList<String> out) {
        if (obj == null) return;
        ArrayList<String> converted = convertHappConfigToLinks(obj);
        if (!converted.isEmpty()) { out.addAll(converted); return; }
        String[] keys = new String[]{"link", "url", "share", "share_link", "subscription", "sub", "server", "servers", "proxies", "configs", "outbounds", "data", "items", "result"};
        for (String k : keys) {
            if (!obj.has(k)) continue;
            Object v = obj.opt(k);
            if (v instanceof String) {
                String s = ((String) v).trim();
                if (looksLikeServerLink(s)) out.add(s);
                else { collectFromText(s, out); String dec = tryDecodeBase64ToString(s); if (dec != null) collectFromText(dec, out); }
            } else if (v instanceof JSONArray) {
                extractFromJsonArray((JSONArray) v, out);
            } else if (v instanceof JSONObject) {
                extractFromJsonObject((JSONObject) v, out);
            }
        }
        JSONArray names = obj.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String k = names.optString(i);
                Object v = obj.opt(k);
                if (v instanceof String) {
                    String s = ((String) v).trim();
                    if (looksLikeServerLink(s)) out.add(s);
                }
            }
        }
    }

    private void collectFromText(String text, ArrayList<String> out) {
        if (TextUtils.isEmpty(text)) return;
        String normalized = text.replace('\r', '\n');
        for (String line : normalized.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.contains(" ") && !looksLikeServerLink(t)) {
                for (String p : t.split("\\s+")) {
                    String pt = p.trim();
                    if (looksLikeServerLink(pt)) out.add(pt);
                }
            } else if (looksLikeServerLink(t)) {
                out.add(t);
            }
        }
    }

    private ArrayList<String> convertHappConfigToLinks(JSONObject obj) {
        ArrayList<String> res = new ArrayList<>();
        if (obj == null) return res;
        JSONArray outbounds = obj.optJSONArray("outbounds");
        if (outbounds == null) return res;
        String remark = obj.optString("remarks", obj.optString("remark", obj.optString("ps", obj.optString("name", ""))));
        for (int i = 0; i < outbounds.length(); i++) {
            JSONObject ob = outbounds.optJSONObject(i);
            if (ob == null) continue;
            String protocol = ob.optString("protocol", "").toLowerCase();
            if ("vless".equals(protocol)) {
                String link = happVlessOutboundToLink(ob, remark);
                if (!TextUtils.isEmpty(link)) res.add(link);
            } else if ("shadowsocks".equals(protocol)) {
                String link = happSsOutboundToLink(ob, remark);
                if (!TextUtils.isEmpty(link)) res.add(link);
            }
        }
        return res;
    }

    private String happVlessOutboundToLink(JSONObject ob, String remark) {
        try {
            JSONObject settings = ob.optJSONObject("settings");
            if (settings == null) return null;
            JSONArray vnext = settings.optJSONArray("vnext");
            if (vnext == null || vnext.length() == 0) return null;
            JSONObject vn = vnext.optJSONObject(0);
            if (vn == null) return null;
            String address = vn.optString("address", "");
            int port = vn.optInt("port", 0);
            JSONArray users = vn.optJSONArray("users");
            if (TextUtils.isEmpty(address) || port <= 0 || users == null || users.length() == 0) return null;
            JSONObject user = users.optJSONObject(0);
            if (user == null) return null;
            String id = user.optString("id", "");
            if (TextUtils.isEmpty(id)) return null;
            String flow = user.optString("flow", "");
            JSONObject stream = ob.optJSONObject("streamSettings");
            String network = "tcp", security = "none", sni = "", fp = "", pbk = "", sid = "", path = "", host = "", serviceName = "", alpn = "";
            if (stream != null) {
                network = stream.optString("network", "tcp");
                security = stream.optString("security", "none");
                if ("reality".equalsIgnoreCase(security)) {
                    JSONObject rs = stream.optJSONObject("realitySettings");
                    if (rs != null) {
                        sni = rs.optString("serverName", rs.optString("sni", ""));
                        fp = rs.optString("fingerprint", "");
                        pbk = rs.optString("publicKey", "");
                        sid = rs.optString("shortId", "");
                    }
                } else if ("tls".equalsIgnoreCase(security)) {
                    JSONObject ts = stream.optJSONObject("tlsSettings");
                    if (ts != null) {
                        sni = ts.optString("serverName", ts.optString("sni", ""));
                        fp = ts.optString("fingerprint", "");
                        JSONArray alpnArr = ts.optJSONArray("alpn");
                        if (alpnArr != null && alpnArr.length() > 0) {
                            StringBuilder ab = new StringBuilder();
                            for (int j = 0; j < alpnArr.length(); j++) {
                                String a = alpnArr.optString(j, "").trim();
                                if (a.isEmpty()) continue;
                                if (ab.length() > 0) ab.append(',');
                                ab.append(a);
                            }
                            alpn = ab.toString();
                        }
                    }
                }
                if ("ws".equalsIgnoreCase(network)) {
                    JSONObject ws = stream.optJSONObject("wsSettings");
                    if (ws != null) {
                        path = ws.optString("path", "");
                        JSONObject headers = ws.optJSONObject("headers");
                        if (headers != null) host = headers.optString("Host", headers.optString("host", ""));
                    }
                } else if ("grpc".equalsIgnoreCase(network)) {
                    JSONObject gr = stream.optJSONObject("grpcSettings");
                    if (gr != null) serviceName = gr.optString("serviceName", gr.optString("path", ""));
                } else if ("xhttp".equalsIgnoreCase(network) || "splithttp".equalsIgnoreCase(network)) {
                    JSONObject xh = stream.optJSONObject("xhttpSettings");
                    if (xh == null) xh = stream.optJSONObject("splithttpSettings");
                    if (xh != null) {
                        path = xh.optString("path", "");
                        host = xh.optString("host", "");
                    }
                }
            }
            StringBuilder q = new StringBuilder();
            appendQuery(q, "encryption", user.optString("encryption", "none"));
            appendQuery(q, "security", security);
            if (!TextUtils.isEmpty(network)) appendQuery(q, "type", network);
            if (!TextUtils.isEmpty(flow)) appendQuery(q, "flow", flow);
            if (!TextUtils.isEmpty(sni)) appendQuery(q, "sni", sni);
            if (!TextUtils.isEmpty(fp)) appendQuery(q, "fp", fp);
            if (!TextUtils.isEmpty(pbk)) appendQuery(q, "pbk", pbk);
            if (!TextUtils.isEmpty(sid)) appendQuery(q, "sid", sid);
            if (!TextUtils.isEmpty(path)) appendQuery(q, "path", path);
            if (!TextUtils.isEmpty(host)) appendQuery(q, "host", host);
            if (!TextUtils.isEmpty(serviceName)) appendQuery(q, "serviceName", serviceName);
            if (!TextUtils.isEmpty(alpn)) appendQuery(q, "alpn", alpn);
            String u = "vless://" + id + "@" + address + ":" + port;
            if (q.length() > 0) u += "?" + q.toString();
            if (!TextUtils.isEmpty(remark)) u += "#" + android.net.Uri.encode(remark);
            return u;
        } catch (Exception ignore) {
            return null;
        }
    }

    private String happSsOutboundToLink(JSONObject ob, String remark) {
        try {
            JSONObject settings = ob.optJSONObject("settings");
            if (settings == null) return null;
            JSONArray srvs = settings.optJSONArray("servers");
            if (srvs == null || srvs.length() == 0) return null;
            JSONObject srv = srvs.optJSONObject(0);
            if (srv == null) return null;
            String address = srv.optString("address", "");
            int port = srv.optInt("port", 0);
            String method = srv.optString("method", "");
            String password = srv.optString("password", "");
            if (TextUtils.isEmpty(address) || port <= 0 || TextUtils.isEmpty(method)) return null;
            String userInfo = method + ":" + password;
            String b64 = Base64.encodeToString(userInfo.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            String u = "ss://" + b64 + "@" + address + ":" + port;
            if (!TextUtils.isEmpty(remark)) u += "#" + android.net.Uri.encode(remark);
            return u;
        } catch (Exception ignore) {
            return null;
        }
    }

    private void appendQuery(StringBuilder sb, String key, String value) {
        if (TextUtils.isEmpty(value)) return;
        if (sb.length() > 0) sb.append('&');
        sb.append(key).append('=').append(android.net.Uri.encode(value));
    }

    private boolean looksLikeServerLink(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase();
        return t.startsWith("vless://") || t.startsWith("vmess://") || t.startsWith("trojan://") || t.startsWith("ss://")
                || t.startsWith("ssr://") || t.startsWith("hysteria://") || t.startsWith("hysteria2://") || t.startsWith("hy2://")
                || t.startsWith("tuic://") || t.startsWith("wg://") || t.startsWith("wireguard://") || t.startsWith("socks://")
                || t.startsWith("socks5://") || t.startsWith("http://proxy") || t.startsWith("https://t.me/proxy");
    }

    private String tryDecodeBase64ToString(String s) {
        if (s == null) return null;
        String t = s.trim().replace("\n", "").replace("\r", "").replace(" ", "");
        if (t.length() < 8) return null;
        try {
            byte[] decoded = Base64.decode(t, Base64.DEFAULT);
            if (decoded != null && decoded.length > 0) {
                String str = new String(decoded, StandardCharsets.UTF_8);
                if (looksLikeServerLink(str) || str.contains("\n") || str.contains("vmess") || str.contains("vless") || str.contains("ss://") || str.startsWith("[") || str.startsWith("{")) return str.trim();
            }
        } catch (Exception ignore) {}
        try {
            byte[] decoded = Base64.decode(t, Base64.URL_SAFE | Base64.NO_WRAP);
            if (decoded != null && decoded.length > 0) {
                String str = new String(decoded, StandardCharsets.UTF_8);
                if (looksLikeServerLink(str) || str.contains("\n") || str.contains("vmess") || str.contains("vless") || str.contains("ss://") || str.startsWith("[") || str.startsWith("{")) return str.trim();
            }
        } catch (Exception ignore) {}
        return null;
    }

    private ArrayList<String> dedupePreserve(ArrayList<String> in) {
        ArrayList<String> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        if (in == null) return out;
        for (String s : in) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            if (seen.add(t)) out.add(t);
        }
        return out;
    }

    private void promptServerActions(int index) {
        if (getParentActivity() == null || index < 0 || index >= servers.size()) return;
        String server = servers.get(index);
        String src = index < serverSources.size() ? serverSources.get(index) : "";
        ArrayList<CharSequence> items = new ArrayList<>();
        items.add("Выбрать");
        items.add("Удалить");
        items.add("Копировать");
        if (!TextUtils.isEmpty(src) && isHttpUrl(src)) items.add("Обновить эту подписку");
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Server");
        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            String choice = items.get(which).toString();
            if ("Выбрать".equals(choice)) {
                HappVpnManager.setSelectedIndex(index);
                rebuildDisplay();
                if (adapter != null) adapter.notifyDataSetChanged();
            } else if ("Удалить".equals(choice)) {
                String sel = getSelectedLink();
                servers.remove(index);
                if (index < serverSources.size()) serverSources.remove(index);
                if (servers.isEmpty()) HappVpnManager.setSelectedIndex(-1);
                else if (TextUtils.equals(sel, server)) HappVpnManager.setSelectedIndex(Math.min(index, servers.size() - 1));
                else restoreSelection(sel);
                saveServers();
                rebuildDisplay();
                updateRows();
                if (adapter != null) adapter.notifyDataSetChanged();
            } else if ("Копировать".equals(choice)) {
                AndroidUtilities.addToClipboard(server);
            } else if ("Обновить эту подписку".equals(choice)) {
                importFromUrl(src, true, false);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showSimpleAlert(String title, String message) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(title);
        b.setMessage(message);
        b.setPositiveButton("OK", null);
        showDialog(b.create());
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;
        ListAdapter(Context context) { this.context = context; }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int p = holder.getAdapterPosition();
            if (p == addRow || p == connectRow || p == refreshRow || p == cleanupRow || p == toggleAutoRow || p == toggleGroupRow || p == logsRow || p == clearAllRow) return true;
            return p >= serversStartRow && p < serversEndRow;
        }

        @Override
        public int getItemCount() { return rowCount; }

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
                ((HeaderCell) holder.itemView).setText("Servers (" + servers.size() + ")");
            } else if (vt == 1) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (position == addRow) {
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    cell.setText("Add / Import (link, URL, JSON)", true);
                } else if (position == connectRow) {
                    boolean running = HappVpnManager.isRunning();
                    cell.setTextColor(running ? Theme.getColor(Theme.key_text_RedRegular) : Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    String label = running ? "Disable VPN" : "Enable VPN (VLESS/SS)";
                    int sel = HappVpnManager.getSelectedIndex();
                    if (!running && sel >= 0 && sel < servers.size()) {
                        HappLinkParser.Profile p = HappLinkParser.parse(servers.get(sel));
                        if (p != null && p.server != null) label = "Enable VPN: " + p.type + " " + p.server;
                    }
                    cell.setText(label, true);
                } else if (position == refreshRow) {
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    int subs = countSubscriptionSources();
                    cell.setText("Обновить подписки" + (subs > 0 ? " (" + subs + ")" : ""), true);
                } else if (position == cleanupRow) {
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    cell.setText("Очистить/дедуплицировать серверы", true);
                } else if (position == toggleAutoRow) {
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    cell.setText((hideAuto ? "Показать" : "Скрыть") + " авто-серверы (Авто/Auto/Turbo)", true);
                } else if (position == toggleGroupRow) {
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    cell.setText((groupMode ? "Выключить группы" : "Включить группы (имя+endpoint)"), true);
                } else if (position == logsRow) {
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    cell.setText("Логи Happ / VPN (вкл, очистка)", true);
                } else if (position == clearAllRow) {
                    cell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                    cell.setText("Очистить все серверы", serversStartRow < serversEndRow);
                } else if (position >= serversStartRow && position < serversEndRow) {
                    DisplayItem di = getDisplayItem(position);
                    if (di == null) return;
                    boolean selected = !di.group && di.rawIndex == HappVpnManager.getSelectedIndex();
                    boolean gsel = di.group && di.rawIndices != null && di.rawIndices.contains(HappVpnManager.getSelectedIndex());
                    cell.setTextColor((selected || gsel) ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4) : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    cell.setText(di.title, position < serversEndRow - 1);
                }
            } else if (vt == 2) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setText("Импорт: vless/ss ссылки, URL подписки или Happ JSON. Режимы: добавить / заменить эту подписку / заменить все. Обновление, очистка/дедупликация, группы, скрытие авто. Логи Happ/VPN: отдельный экран. VPN: VLESS и Shadowsocks (Xray).");
                cell.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            }
        }

        private int countSubscriptionSources() {
            HashSet<String> seen = new HashSet<>();
            for (String s : serverSources) if (!TextUtils.isEmpty(s) && isHttpUrl(s)) seen.add(s);
            return seen.size();
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) return 0;
            if (position == addRow || position == connectRow || position == refreshRow || position == cleanupRow || position == toggleAutoRow || position == toggleGroupRow || position == logsRow || position == clearAllRow || (position >= serversStartRow && position < serversEndRow)) return 1;
            return 2;
        }
    }
}
