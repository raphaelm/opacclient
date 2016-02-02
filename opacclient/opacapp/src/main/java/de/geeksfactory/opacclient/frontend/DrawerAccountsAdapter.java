package de.geeksfactory.opacclient.frontend;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.geeksfactory.opacclient.R;
import de.geeksfactory.opacclient.objects.Account;
import de.geeksfactory.opacclient.storage.AccountDataSource;
import de.geeksfactory.opacclient.utils.Utils;

public class DrawerAccountsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<Account> accounts;
    private List<Account> accountsWithoutCurrent;
    private Context context;
    private Map<Account, Integer> expiring;
    private Account currentAccount;
    private Listener listener;

    private static final int TYPE_ACCOUNT = 0;
    private static final int TYPE_SEPARATOR = 1;
    private static final int TYPE_FOOTER = 2;

    private static final int FOOTER_COUNT = 2;

    public DrawerAccountsAdapter(Context context, List<Account> accounts, Account currentAccount) {
        this.accounts = accounts;
        this.context = context;
        this.expiring = new HashMap<>();
        this.currentAccount = currentAccount;
        this.accountsWithoutCurrent = new ArrayList<>();

        SharedPreferences sp =
                PreferenceManager.getDefaultSharedPreferences(context);
        long tolerance = Long.decode(sp.getString("notification_warning", "367200000"));

        AccountDataSource adata = new AccountDataSource(context);
        adata.open();
        for (Account account : accounts) {
            expiring.put(account, adata.getExpiring(account, tolerance));
            if (account.getId() != currentAccount.getId()) {
                accountsWithoutCurrent.add(account);
            }
        }
        adata.close();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case TYPE_ACCOUNT:
                view = LayoutInflater.from(context).inflate(
                        R.layout.navigation_drawer_item_account, parent, false);
                return new AccountViewHolder(view);
            case TYPE_SEPARATOR:
                view = LayoutInflater.from(context).inflate(
                        R.layout.navigation_drawer_item_separator, parent, false);
                return new SeparatorViewHolder(view);
            case TYPE_FOOTER:
                view = LayoutInflater.from(context).inflate(
                        R.layout.navigation_drawer_item_footer, parent, false);
                return new FooterViewHolder(view);
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AccountViewHolder) {
            Account account = accountsWithoutCurrent.get(position);
            ((AccountViewHolder) holder).setData(account, expiring.get(account));
        } else if (holder instanceof FooterViewHolder) {
            FooterViewHolder footer = (FooterViewHolder) holder;
            if (position == accountsWithoutCurrent.size() + 1) {
                footer.setTitle(R.string.account_add);
                footer.setIcon(R.drawable.ic_add_24dp);
                footer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (listener != null) listener.onAddAccountClicked();
                    }
                });
            } else if (position == accountsWithoutCurrent.size() + 2) {
                footer.setTitle(R.string.accounts);
                footer.setIcon(R.drawable.ic_settings_24dp);
                footer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (listener != null) listener.onManageAccountsClicked();
                    }
                });
            }
        }
    }

    @Override
    public int getItemCount() {
        return accountsWithoutCurrent.size() + 1 + FOOTER_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        if (position < accountsWithoutCurrent.size()) {
            return TYPE_ACCOUNT;
        } else if (position == accountsWithoutCurrent.size()) {
            return TYPE_SEPARATOR;
        } else {
            return TYPE_FOOTER;
        }
    }


    public void setCurrentAccount(Account account) {
        if (currentAccount == null || account == currentAccount) return;

        this.accountsWithoutCurrent.add(accounts.indexOf(currentAccount), currentAccount);
        notifyItemInserted(accounts.indexOf(currentAccount));

        this.currentAccount = account;
        this.accountsWithoutCurrent.remove(currentAccount);
        notifyItemRemoved(accounts.indexOf(currentAccount));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public class AccountViewHolder extends RecyclerView.ViewHolder {
        private TextView title;
        private TextView subtitle;
        private TextView warning;
        private View view;
        private Account account;

        public AccountViewHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.account_title);
            subtitle = (TextView) itemView.findViewById(R.id.account_subtitle);
            warning = (TextView) itemView.findViewById(R.id.account_warning);
            view = itemView;
        }

        public void setData(Account account, int expiring) {
            this.account = account;
            title.setText(Utils.getAccountTitle(account, context));
            subtitle.setText(Utils.getAccountSubtitle(account, context));
            if (expiring > 0) {
                warning.setText(String.valueOf(expiring));
                warning.setVisibility(View.VISIBLE);
            } else {
                warning.setVisibility(View.INVISIBLE);
            }
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (listener != null) listener.onAccountClicked(AccountViewHolder.this.account);
                }
            });
        }
    }

    public interface Listener {
        void onAccountClicked(Account account);

        void onAddAccountClicked();

        void onManageAccountsClicked();
    }

    private class SeparatorViewHolder extends RecyclerView.ViewHolder {
        public SeparatorViewHolder(View view) {
            super(view);
        }
    }

    private class FooterViewHolder extends RecyclerView.ViewHolder {
        private TextView title;
        private ImageView icon;
        private View view;

        public FooterViewHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.footer_title);
            icon = (ImageView) itemView.findViewById(R.id.footer_icon);
            view = itemView;
        }

        public void setTitle(int string) {
            title.setText(string);
        }

        public void setIcon(int id) {
            Drawable drawable = DrawableCompat.wrap(ContextCompat.getDrawable(context, id));
            DrawableCompat.setTint(drawable, Color.argb(138, 0, 0, 0));
            icon.setImageDrawable(drawable);
        }

        public void setOnClickListener(View.OnClickListener listener) {
            view.setOnClickListener(listener);
        }
    }
}
