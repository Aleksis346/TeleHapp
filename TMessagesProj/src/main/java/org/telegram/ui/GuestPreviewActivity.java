package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class GuestPreviewActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private int rowCount, headerRow, infoRow, featuresHeaderRow, f1, f2, f3, f4, happRow, loginRow, footerRow;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Гостевой режим");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout fl = (FrameLayout) fragmentView;
        fl.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        fl.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        adapter = new ListAdapter(context);
        listView.setAdapter(adapter);
        updateRows();
        listView.setOnItemClickListener((view, position) -> {
            if (position == happRow) {
                presentFragment(new HappServersActivity());
            } else if (position == loginRow) {
                presentFragment(new LoginActivity());
            }
        });
        return fragmentView;
    }

    private void updateRows() {
        rowCount = 0;
        headerRow = rowCount++;
        infoRow = rowCount++;
        featuresHeaderRow = rowCount++;
        f1 = rowCount++;
        f2 = rowCount++;
        f3 = rowCount++;
        f4 = rowCount++;
        happRow = rowCount++;
        loginRow = rowCount++;
        footerRow = rowCount++;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;
        ListAdapter(Context context) { this.context = context; }

        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int p = holder.getAdapterPosition();
            return p == happRow || p == loginRow;
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
                HeaderCell hc = (HeaderCell) holder.itemView;
                if (position == headerRow) hc.setText("TeleHapp — гостевой просмотр");
                else if (position == featuresHeaderRow) hc.setText("Возможности приложения");
            } else if (vt == 1) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                if (position == f1) cell.setText("• Мессенджер на базе TeleHapp: чаты, каналы, медиа", true);
                else if (position == f2) cell.setText("• Встроенный Happ: VLESS/SS (Xray), подписки, импорт", true);
                else if (position == f3) cell.setText("• Управление серверами, логи Happ/VPN, HWID для подписок", true);
                else if (position == f4) cell.setText("• Быстрый доступ к Happ из нижней панели и экрана входа", true);
                else if (position == happRow) {
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    cell.setText("Открыть Happ (серверы/VPN) — просмотр", true);
                } else if (position == loginRow) {
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    cell.setText("Войти / зарегистрироваться", false);
                }
            } else {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                if (position == infoRow) {
                    cell.setText("Вы находитесь в гостевом режиме: можно ознакомиться с возможностями TeleHapp без регистрации. Изменение данных аккаунта недоступно без входа.");
                } else {
                    cell.setText("Для полноценной работы (чаты, синхронизация, отправка сообщений) выполните вход в аккаунт TeleHapp.");
                }
                cell.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow || position == featuresHeaderRow) return 0;
            if (position == infoRow || position == footerRow) return 2;
            return 1;
        }
    }
}
