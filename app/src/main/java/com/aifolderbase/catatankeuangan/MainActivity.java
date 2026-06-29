package com.aifolderbase.catatankeuangan;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.services.drive.DriveScopes;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_SIGN_IN = 4001;
    private static final int REQUEST_DRIVE_AUTH = 4002;

    private static final String TAB_DASHBOARD = "dashboard";
    private static final String TAB_ADD = "add";
    private static final String TAB_RECORDS = "records";
    private static final String TAB_REPORTS = "reports";
    private static final String TAB_BUDGETS = "budgets";

    private final String[] defaultCategories = {
            "Makan", "Transport", "Belanja", "Tagihan", "Stok Usaha", "Penjualan", "Gaji", "Kesehatan", "Pendidikan", "Lainnya"
    };
    private final String[] defaultAccounts = {"Kas", "Bank", "E-Wallet", "Kartu"};
    private final String[] defaultMethods = {"Tunai", "Transfer", "Debit", "QRIS", "E-Wallet"};

    private FinanceDbHelper db;
    private ExecutorService executor;
    private Handler mainHandler;
    private SharedPreferences preferences;

    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout nav;
    private TextView syncStatusText;
    private Button syncButton;
    private String activeTab = TAB_DASHBOARD;
    private String recordFilter = "";

    private final NumberFormat moneyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", new Locale("id", "ID"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new FinanceDbHelper(this);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        preferences = getSharedPreferences("settings", MODE_PRIVATE);
        buildShell();
        showTab(TAB_DASHBOARD);
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        db.close();
        super.onDestroy();
    }

    private void buildShell() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(244, 247, 244));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(24));
        scrollView.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header);

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView title = text("Money Ledger", 27, Color.rgb(22, 37, 32), true);
        titleBlock.addView(title);
        syncStatusText = text("Data lokal siap digunakan", 13, Color.rgb(88, 101, 95), false);
        titleBlock.addView(syncStatusText);

        syncButton = smallButton("Sync");
        syncButton.setOnClickListener(v -> startSyncFlow());
        header.addView(syncButton, new LinearLayout.LayoutParams(dp(84), dp(42)));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(0, dp(14), 0, dp(8));
        root.addView(nav);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);

        setContentView(scrollView);
    }

    private void renderNav() {
        nav.removeAllViews();
        addTabButton("Ringkas", TAB_DASHBOARD);
        addTabButton("Catat", TAB_ADD);
        addTabButton("Riwayat", TAB_RECORDS);
        addTabButton("Laporan", TAB_REPORTS);
        addTabButton("Budget", TAB_BUDGETS);
    }

    private void addTabButton(String label, String tab) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(activeTab.equals(tab) ? Color.WHITE : Color.rgb(38, 77, 63));
        button.setBackground(bg(activeTab.equals(tab) ? Color.rgb(31, 122, 90) : Color.WHITE, dp(18)));
        button.setOnClickListener(v -> showTab(tab));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
        params.setMargins(dp(2), 0, dp(2), 0);
        nav.addView(button, params);
    }

    private void showTab(String tab) {
        activeTab = tab;
        renderNav();
        content.removeAllViews();
        if (TAB_DASHBOARD.equals(tab)) {
            renderDashboard();
        } else if (TAB_ADD.equals(tab)) {
            renderAddForm();
        } else if (TAB_RECORDS.equals(tab)) {
            renderRecords();
        } else if (TAB_REPORTS.equals(tab)) {
            renderReports();
        } else {
            renderBudgets();
        }
    }

    private void renderDashboard() {
        FinanceDbHelper.Summary all = db.getSummary();
        FinanceDbHelper.Summary month = db.getSummaryBetween(monthStart(), monthEnd());

        LinearLayout hero = panel(Color.rgb(22, 93, 74));
        hero.setBackground(gradient(Color.rgb(22, 93, 74), Color.rgb(43, 141, 111), dp(12)));
        hero.addView(text("Saldo saat ini", 14, Color.rgb(216, 240, 230), false));
        hero.addView(text(money(all.balance()), 31, Color.WHITE, true));
        hero.addView(text("Bulan ini: " + money(month.income) + " masuk - " + money(month.expense) + " keluar", 13, Color.rgb(216, 240, 230), false));
        content.addView(hero);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        addMetric(metrics, "Pemasukan", money(all.income), Color.rgb(31, 122, 90));
        addMetric(metrics, "Pengeluaran", money(all.expense), Color.rgb(181, 70, 54));
        content.addView(metrics);

        LinearLayout monthPanel = panel(Color.WHITE);
        monthPanel.addView(sectionTitle("Bulan Ini"));
        monthPanel.addView(rowText("Pemasukan", money(month.income), Color.rgb(31, 122, 90)));
        monthPanel.addView(rowText("Pengeluaran", money(month.expense), Color.rgb(181, 70, 54)));
        monthPanel.addView(rowText("Arus kas", money(month.balance()), month.balance() >= 0 ? Color.rgb(31, 122, 90) : Color.rgb(181, 70, 54)));
        content.addView(monthPanel);

        LinearLayout recent = panel(Color.WHITE);
        recent.addView(sectionTitle("Transaksi Terbaru"));
        List<MoneyRecord> records = db.getRecentRecords(5);
        if (records.isEmpty()) {
            recent.addView(emptyText("Belum ada transaksi. Mulai dari tab Catat."));
        } else {
            for (MoneyRecord record : records) {
                recent.addView(compactRecord(record));
            }
        }
        content.addView(recent);
    }

    private void addMetric(LinearLayout parent, String label, String value, int color) {
        LinearLayout item = panel(Color.WHITE);
        item.addView(text(label, 12, Color.rgb(88, 101, 95), false));
        item.addView(text(value, 17, color, true));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(2), dp(10), dp(2), 0);
        parent.addView(item, params);
    }

    private void renderAddForm() {
        LinearLayout form = panel(Color.WHITE);
        form.addView(sectionTitle("Catat Transaksi"));

        RadioGroup typeGroup = new RadioGroup(this);
        typeGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton expenseRadio = radio("Pengeluaran", true);
        RadioButton incomeRadio = radio("Pemasukan", false);
        typeGroup.addView(expenseRadio);
        typeGroup.addView(incomeRadio);
        form.addView(typeGroup);

        EditText amountInput = input("Nominal");
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Spinner categorySpinner = spinner(mergedOptions(defaultCategories, db.getDistinctValues("category")));
        Spinner accountSpinner = spinner(mergedOptions(defaultAccounts, db.getDistinctValues("account")));
        Spinner methodSpinner = spinner(mergedOptions(defaultMethods, db.getDistinctValues("payment_method")));
        EditText noteInput = input("Catatan singkat");

        form.addView(label("Kategori"));
        form.addView(categorySpinner);
        form.addView(label("Akun / Dompet"));
        form.addView(accountSpinner);
        form.addView(label("Metode"));
        form.addView(methodSpinner);
        form.addView(amountInput);
        form.addView(noteInput);

        Button saveButton = primaryButton("Simpan Transaksi");
        saveButton.setOnClickListener(v -> {
            String rawAmount = amountInput.getText().toString().trim().replace(",", ".");
            if (rawAmount.isEmpty()) {
                amountInput.setError("Nominal wajib diisi");
                return;
            }
            double amount;
            try {
                amount = Double.parseDouble(rawAmount);
            } catch (NumberFormatException error) {
                amountInput.setError("Nominal tidak valid");
                return;
            }
            if (amount <= 0) {
                amountInput.setError("Nominal harus lebih dari 0");
                return;
            }

            MoneyRecord record = new MoneyRecord();
            record.amount = amount;
            record.type = incomeRadio.isChecked() ? MoneyRecord.TYPE_INCOME : MoneyRecord.TYPE_EXPENSE;
            record.category = selected(categorySpinner);
            record.account = selected(accountSpinner);
            record.paymentMethod = selected(methodSpinner);
            record.note = noteInput.getText().toString().trim();
            db.saveRecord(record);
            syncStatusText.setText("Ada perubahan lokal. Tekan Sync untuk menyimpan ke Drive.");
            showMessage("Transaksi tersimpan");
            showTab(TAB_DASHBOARD);
        });
        form.addView(saveButton);
        content.addView(form);

        LinearLayout hint = panel(Color.rgb(237, 246, 241));
        hint.addView(text("Tips usaha kecil", 15, Color.rgb(38, 77, 63), true));
        hint.addView(text("Pisahkan akun Kas, Bank, dan E-Wallet agar laporan arus uang lebih mudah dibaca.", 14, Color.rgb(88, 101, 95), false));
        content.addView(hint);
    }

    private void renderRecords() {
        LinearLayout searchPanel = panel(Color.WHITE);
        searchPanel.addView(sectionTitle("Cari & Filter"));
        EditText search = input("Cari kategori, akun, metode, atau catatan");
        searchPanel.addView(search);
        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        Button all = filterButton("Semua", "");
        Button income = filterButton("Masuk", MoneyRecord.TYPE_INCOME);
        Button expense = filterButton("Keluar", MoneyRecord.TYPE_EXPENSE);
        filters.addView(all, weightParams());
        filters.addView(income, weightParams());
        filters.addView(expense, weightParams());
        searchPanel.addView(filters);
        Button apply = primaryButton("Terapkan Filter");
        apply.setOnClickListener(v -> renderRecordResults(search.getText().toString()));
        searchPanel.addView(apply);
        content.addView(searchPanel);

        LinearLayout listPanel = panel(Color.WHITE);
        listPanel.setTag("results");
        content.addView(listPanel);
        renderRecordResults("");
    }

    private void renderRecordResults(String keyword) {
        LinearLayout listPanel = (LinearLayout) content.findViewWithTag("results");
        if (listPanel == null) {
            return;
        }
        listPanel.removeAllViews();
        listPanel.addView(sectionTitle("Riwayat Transaksi"));
        List<MoneyRecord> records = db.searchRecords(keyword, recordFilter);
        if (records.isEmpty()) {
            listPanel.addView(emptyText("Tidak ada transaksi sesuai filter."));
            return;
        }
        for (MoneyRecord record : records) {
            listPanel.addView(recordCard(record));
        }
    }

    private Button filterButton(String label, String type) {
        Button button = smallButton(label);
        button.setOnClickListener(v -> {
            recordFilter = type;
            showTab(TAB_RECORDS);
        });
        return button;
    }

    private void renderReports() {
        long start = monthStart();
        long end = monthEnd();
        FinanceDbHelper.Summary month = db.getSummaryBetween(start, end);
        Map<String, Double> categories = db.getExpenseByCategory(start, end);

        LinearLayout panel = panel(Color.WHITE);
        panel.addView(sectionTitle("Laporan Bulan Ini"));
        panel.addView(rowText("Total masuk", money(month.income), Color.rgb(31, 122, 90)));
        panel.addView(rowText("Total keluar", money(month.expense), Color.rgb(181, 70, 54)));
        panel.addView(rowText("Sisa bulan ini", money(month.balance()), month.balance() >= 0 ? Color.rgb(31, 122, 90) : Color.rgb(181, 70, 54)));
        content.addView(panel);

        LinearLayout chart = panel(Color.WHITE);
        chart.addView(sectionTitle("Pengeluaran per Kategori"));
        if (categories.isEmpty()) {
            chart.addView(emptyText("Belum ada pengeluaran bulan ini."));
        } else {
            double max = 1;
            for (double value : categories.values()) {
                if (value > max) {
                    max = value;
                }
            }
            for (Map.Entry<String, Double> entry : categories.entrySet()) {
                chart.addView(categoryBar(entry.getKey(), entry.getValue(), max));
            }
        }
        content.addView(chart);
    }

    private void renderBudgets() {
        LinearLayout form = panel(Color.WHITE);
        form.addView(sectionTitle("Budget Bulanan"));
        Spinner categorySpinner = spinner(mergedOptions(defaultCategories, db.getDistinctValues("category")));
        EditText limitInput = input("Limit bulanan");
        limitInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(label("Kategori"));
        form.addView(categorySpinner);
        form.addView(limitInput);
        Button save = primaryButton("Simpan Budget");
        save.setOnClickListener(v -> {
            String rawLimit = limitInput.getText().toString().trim().replace(",", ".");
            if (rawLimit.isEmpty()) {
                limitInput.setError("Limit wajib diisi");
                return;
            }
            double limit = Double.parseDouble(rawLimit);
            db.saveBudget(selected(categorySpinner), limit);
            syncStatusText.setText("Budget diperbarui. Tekan Sync untuk menyimpan ke Drive.");
            showTab(TAB_BUDGETS);
        });
        form.addView(save);
        content.addView(form);

        LinearLayout list = panel(Color.WHITE);
        list.addView(sectionTitle("Pemakaian Budget"));
        List<FinanceDbHelper.Budget> budgets = db.getBudgets();
        if (budgets.isEmpty()) {
            list.addView(emptyText("Belum ada budget. Tambahkan limit kategori dulu."));
        } else {
            Map<String, Double> spent = db.getExpenseByCategory(monthStart(), monthEnd());
            for (FinanceDbHelper.Budget budget : budgets) {
                double used = spent.containsKey(budget.category) ? spent.get(budget.category) : 0;
                list.addView(budgetRow(budget, used));
            }
        }
        content.addView(list);
    }

    private View recordCard(MoneyRecord record) {
        LinearLayout row = panel(Color.WHITE);
        row.addView(compactRecord(record));
        TextView details = text(record.account + " - " + record.paymentMethod + " - " + dateFormat.format(new Date(record.occurredAt)), 13, Color.rgb(88, 101, 95), false);
        details.setPadding(0, dp(3), 0, dp(3));
        row.addView(details);
        if (record.note != null && record.note.trim().length() > 0) {
            row.addView(text(record.note, 14, Color.rgb(64, 75, 70), false));
        }
        Button delete = smallButton("Hapus");
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Hapus transaksi?")
                .setMessage("Transaksi akan disembunyikan dan perubahan dapat disinkronkan ke Drive.")
                .setPositiveButton("Hapus", (dialog, which) -> {
                    db.deleteRecord(record.id);
                    syncStatusText.setText("Transaksi dihapus lokal. Tekan Sync untuk menyimpan ke Drive.");
                    showTab(TAB_RECORDS);
                })
                .setNegativeButton("Batal", null)
                .show());
        row.addView(delete);
        return row;
    }

    private View compactRecord(MoneyRecord record) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        String prefix = MoneyRecord.TYPE_INCOME.equals(record.type) ? "+ " : "- ";
        int color = MoneyRecord.TYPE_INCOME.equals(record.type) ? Color.rgb(31, 122, 90) : Color.rgb(181, 70, 54);
        row.addView(text(prefix + money(record.amount), 18, color, true));
        row.addView(text(record.category, 14, Color.rgb(22, 37, 32), true));
        return row;
    }

    private View categoryBar(String category, double value, double max) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(8), 0, dp(6));
        block.addView(rowText(category, money(value), Color.rgb(22, 37, 32)));
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress((int) Math.round((value / max) * 100));
        block.addView(progress, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)));
        return block;
    }

    private View budgetRow(FinanceDbHelper.Budget budget, double used) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(10), 0, dp(8));
        int percent = budget.monthlyLimit <= 0 ? 0 : (int) Math.min(100, Math.round((used / budget.monthlyLimit) * 100));
        int color = used > budget.monthlyLimit ? Color.rgb(181, 70, 54) : Color.rgb(31, 122, 90);
        block.addView(rowText(budget.category, money(used) + " / " + money(budget.monthlyLimit), color));
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(percent);
        block.addView(progress, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)));
        Button delete = smallButton("Hapus Budget");
        delete.setOnClickListener(v -> {
            db.deleteBudget(budget.category);
            showTab(TAB_BUDGETS);
        });
        block.addView(delete);
        return block;
    }

    private LinearLayout rowText(String left, String right, int rightColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(7), 0, dp(7));
        row.addView(text(left, 15, Color.rgb(64, 75, 70), false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView value = text(right, 15, rightColor, true);
        value.setGravity(Gravity.END);
        row.addView(value);
        return row;
    }

    private LinearLayout panel(int color) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        panel.setBackground(bg(color, dp(12)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, 0);
        panel.setLayoutParams(params);
        return panel;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 18, Color.rgb(22, 37, 32), true);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 13, Color.rgb(88, 101, 95), true);
        view.setPadding(0, dp(10), 0, 0);
        return view;
    }

    private TextView emptyText(String value) {
        TextView view = text(value, 14, Color.rgb(88, 101, 95), false);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private EditText input(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setSingleLine(false);
        editText.setMinLines(1);
        editText.setTextSize(16);
        editText.setPadding(dp(12), dp(8), dp(12), dp(8));
        editText.setBackground(bg(Color.rgb(250, 251, 248), dp(8)));
        return editText;
    }

    private Spinner spinner(List<String> values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
        spinner.setAdapter(adapter);
        spinner.setPadding(0, dp(4), 0, dp(4));
        return spinner;
    }

    private RadioButton radio(String label, boolean checked) {
        RadioButton radio = new RadioButton(this);
        radio.setId(View.generateViewId());
        radio.setText(label);
        radio.setChecked(checked);
        return radio;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackground(bg(Color.rgb(31, 122, 90), dp(10)));
        return button;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(31, 122, 90));
        button.setBackground(bg(Color.WHITE, dp(10)));
        return button;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
        params.setMargins(dp(2), dp(8), dp(2), dp(4));
        return params;
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private List<String> mergedOptions(String[] defaults, List<String> stored) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String item : defaults) {
            values.put(item, item);
        }
        for (String item : stored) {
            values.put(item, item);
        }
        return new ArrayList<>(values.values());
    }

    private String selected(Spinner spinner) {
        Object selected = spinner.getSelectedItem();
        return selected == null ? "" : selected.toString();
    }

    private long monthStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long monthEnd() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    private void startSyncFlow() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        Scope driveScope = new Scope(DriveScopes.DRIVE);
        if (account != null && account.getEmail() != null && GoogleSignIn.hasPermissions(account, driveScope)) {
            syncWithAccount(account.getEmail());
            return;
        }

        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(driveScope)
                .build();
        GoogleSignInClient client = GoogleSignIn.getClient(this, options);
        startActivityForResult(client.getSignInIntent(), REQUEST_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DRIVE_AUTH && resultCode == RESULT_OK) {
            String accountName = preferences.getString("accountName", null);
            if (accountName != null) {
                syncWithAccount(accountName);
            }
            return;
        }

        if (requestCode == REQUEST_SIGN_IN && resultCode == RESULT_OK) {
            GoogleSignIn.getSignedInAccountFromIntent(data)
                    .addOnSuccessListener(account -> {
                        if (account.getEmail() != null) {
                            syncWithAccount(account.getEmail());
                        }
                    })
                    .addOnFailureListener(error -> showMessage("Login Google gagal: " + error.getMessage()));
        }
    }

    private void syncWithAccount(String accountName) {
        preferences.edit().putString("accountName", accountName).apply();
        syncButton.setEnabled(false);
        syncStatusText.setText("Menyinkronkan dengan Google Drive...");

        executor.execute(() -> {
            try {
                DriveSyncManager manager = new DriveSyncManager(this, accountName, db);
                DriveSyncManager.SyncResult result = manager.sync();
                mainHandler.post(() -> {
                    syncButton.setEnabled(true);
                    syncStatusText.setText("Sinkron selesai ke " + DriveSyncManager.ROOT_FOLDER_NAME + "/" + DriveSyncManager.APP_FOLDER_NAME);
                    showTab(activeTab);
                    showMessage("Sinkron selesai. Data baru: " + result.importedRecords);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    syncButton.setEnabled(true);
                    if (error instanceof UserRecoverableAuthIOException) {
                        syncStatusText.setText("Google Drive memerlukan izin tambahan.");
                        startActivityForResult(((UserRecoverableAuthIOException) error).getIntent(), REQUEST_DRIVE_AUTH);
                        return;
                    }
                    syncStatusText.setText("Sinkron gagal. Coba lagi saat internet stabil.");
                    showMessage("Sinkron gagal: " + error.getMessage());
                });
            }
        });
    }

    private String money(double amount) {
        return moneyFormat.format(amount);
    }

    private void showMessage(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
