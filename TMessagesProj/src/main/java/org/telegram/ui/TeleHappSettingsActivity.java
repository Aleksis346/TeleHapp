package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.TeleHappConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class TeleHappSettingsActivity extends BaseFragment {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_CHECK = 1;
    private static final int VIEW_TYPE_INFO = 2;
    private static final int VIEW_TYPE_TEXT = 3;

    private RecyclerListView listView;
    private ListAdapter adapter;

    private int rowCount;
    private int headerRow;
    private int customFeaturesRow;
    private int customFeaturesInfoRow;

    private int iconRow;
    private int titleStyleRow;
    private int customFeaturesExtendedInfoRow;

    private int premiumHeaderRow;
    private int premiumDesignRow;
    private int premiumDesignInfoRow;

    private static final String[] ICON_IDS = new String[]{
            TeleHappConfig.ICON_DEFAULT,
            TeleHappConfig.ICON_MEME_1,
            TeleHappConfig.ICON_MEME_2,
            TeleHappConfig.ICON_MEME_3,
            TeleHappConfig.ICON_MEME_4
    };

    private static final String[] ICON_NAMES = new String[]{
            "Стандартная TeleHapp",
            "Мем-Кот",
            "Мем-Лягушка",
            "Мем-Доге",
            "Кибер-мем"
    };

    private static final String[] ICON_ALIASES = new String[]{
            "org.telegram.messenger.DefaultIcon",
            "org.telegram.messenger.MemeIcon1",
            "org.telegram.messenger.MemeIcon2",
            "org.telegram.messenger.MemeIcon3",
            "org.telegram.messenger.MemeIcon4"
    };

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Настройки TeleHapp");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        updateRows();

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new ListAdapter(context));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> {
            if (position == customFeaturesRow) {
                boolean enabled = !TeleHappConfig.isCustomFeaturesEnabled();
                TeleHappConfig.setCustomFeaturesEnabled(enabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
                updateRows();
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            } else if (position == iconRow) {
                showIconPicker();
            } else if (position == titleStyleRow) {
                showTitleStylePicker();
            } else if (position == premiumDesignRow) {
                if (!UserConfig.getInstance(currentAccount).isPremium()) {
                    return;
                }
                boolean enabled = !TeleHappConfig.isUsePremiumDesign();
                TeleHappConfig.setUsePremiumDesign(enabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
            }
        });

        return fragmentView;
    }

    private void updateRows() {
        rowCount = 0;
        headerRow = rowCount++;
        customFeaturesRow = rowCount++;
        customFeaturesInfoRow = rowCount++;

        iconRow = -1;
        titleStyleRow = -1;
        customFeaturesExtendedInfoRow = -1;
        if (TeleHappConfig.isCustomFeaturesEnabled()) {
            iconRow = rowCount++;
            titleStyleRow = rowCount++;
            customFeaturesExtendedInfoRow = rowCount++;
        }

        premiumHeaderRow = rowCount++;
        premiumDesignRow = rowCount++;
        premiumDesignInfoRow = rowCount++;
    }

    private String getIconName(String iconId) {
        for (int i = 0; i < ICON_IDS.length; i++) {
            if (ICON_IDS[i].equals(iconId)) {
                return ICON_NAMES[i];
            }
        }
        return ICON_NAMES[0];
    }

    private void showIconPicker() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Иконка приложения (Мемы)\nТекущая: " + getIconName(TeleHappConfig.getSelectedAppIcon()));
        builder.setItems(ICON_NAMES, (dialog, which) -> {
            TeleHappConfig.setSelectedAppIcon(ICON_IDS[which]);
            applyLauncherIcon(ICON_IDS[which]);
            if (adapter != null) {
                adapter.notifyItemChanged(iconRow);
            }
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void showTitleStylePicker() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Стиль названия TeleHapp");
        builder.setItems(TeleHappConfig.TITLE_STYLES, (dialog, which) -> {
            TeleHappConfig.setTitleStyleIndex(which);
            if (adapter != null) {
                adapter.notifyItemChanged(titleStyleRow);
            }
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void applyLauncherIcon(String iconId) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) {
                return;
            }
            PackageManager pm = ctx.getPackageManager();
            String packageName = ctx.getPackageName();
            String enableAlias = ICON_ALIASES[0];
            for (int i = 0; i < ICON_IDS.length; i++) {
                if (ICON_IDS[i].equals(iconId)) {
                    enableAlias = ICON_ALIASES[i];
                    break;
                }
            }
            // Disable other known launcher aliases first, then enable selected one.
            String[] allAliases = new String[]{
                    "org.telegram.messenger.DefaultIcon",
                    "org.telegram.messenger.VintageIcon",
                    "org.telegram.messenger.AquaIcon",
                    "org.telegram.messenger.PremiumIcon",
                    "org.telegram.messenger.TurboIcon",
                    "org.telegram.messenger.NoxIcon",
                    "org.telegram.messenger.MemeIcon1",
                    "org.telegram.messenger.MemeIcon2",
                    "org.telegram.messenger.MemeIcon3",
                    "org.telegram.messenger.MemeIcon4"
            };
            for (String alias : allAliases) {
                ComponentName cn = new ComponentName(packageName, alias);
                int state = alias.equals(enableAlias) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                try {
                    pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP);
                } catch (Throwable ignore) {
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position == customFeaturesRow || position == iconRow || position == titleStyleRow) {
                return true;
            }
            if (position == premiumDesignRow) {
                return UserConfig.getInstance(currentAccount).isPremium();
            }
            return false;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_TYPE_HEADER) {
                view = new HeaderCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == VIEW_TYPE_CHECK) {
                view = new TextCheckCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == VIEW_TYPE_TEXT) {
                view = new TextSettingsCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else {
                view = new TextInfoPrivacyCell(mContext);
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int viewType = holder.getItemViewType();
            if (viewType == VIEW_TYPE_HEADER) {
                HeaderCell cell = (HeaderCell) holder.itemView;
                if (position == headerRow) {
                    cell.setText("TeleHapp");
                } else if (position == premiumHeaderRow) {
                    cell.setText("Дизайн для Telegram Premium");
                }
            } else if (viewType == VIEW_TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                if (position == customFeaturesRow) {
                    cell.setTextAndCheck("Расширенные функции TeleHapp", TeleHappConfig.isCustomFeaturesEnabled(), iconRow != -1 || premiumHeaderRow != -1);
                } else if (position == premiumDesignRow) {
                    boolean isPremium = UserConfig.getInstance(currentAccount).isPremium();
                    boolean enabled = isPremium && TeleHappConfig.isUsePremiumDesign();
                    cell.setTextAndCheck("Особый дизайн TeleHapp Premium", enabled, false);
                    cell.setEnabled(isPremium);
                }
            } else if (viewType == VIEW_TYPE_TEXT) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (position == iconRow) {
                    cell.setTextAndValue("Иконка приложения (Мемы)", getIconName(TeleHappConfig.getSelectedAppIcon()), true);
                } else if (position == titleStyleRow) {
                    cell.setTextAndValue("Стиль названия TeleHapp", TeleHappConfig.TITLE_STYLES[TeleHappConfig.getTitleStyleIndex()], false);
                }
            } else if (viewType == VIEW_TYPE_INFO) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                if (position == customFeaturesInfoRow) {
                    cell.setText("Включает безопасные локальные функции клиента: выбор иконки приложения, стиль названия и локальные настройки интерфейса. Не влияет на логику серверов Telegram.");
                    cell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                } else if (position == customFeaturesExtendedInfoRow) {
                    cell.setText("При включённых расширенных функциях можно выбрать одну из 5 иконок и стиль заголовка TeleHapp на главном экране.");
                    cell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                } else if (position == premiumDesignInfoRow) {
                    boolean isPremium = UserConfig.getInstance(currentAccount).isPremium();
                    if (isPremium) {
                        cell.setText("Если у вас активна официальная подписка Telegram Premium, вы можете включить или отключить особый визуальный стиль TeleHapp.");
                    } else {
                        cell.setText("Доступно пользователям с активной подпиской Telegram Premium.");
                    }
                    cell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow || position == premiumHeaderRow) {
                return VIEW_TYPE_HEADER;
            } else if (position == customFeaturesRow || position == premiumDesignRow) {
                return VIEW_TYPE_CHECK;
            } else if (position == iconRow || position == titleStyleRow) {
                return VIEW_TYPE_TEXT;
            } else {
                return VIEW_TYPE_INFO;
            }
        }
    }
}
