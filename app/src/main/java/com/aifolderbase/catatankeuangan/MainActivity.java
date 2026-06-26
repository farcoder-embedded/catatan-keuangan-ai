package com.aifolderbase.catatankeuangan;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_SIGN_IN = 4001;
    private static final int REQUEST_DRIVE_AUTH = 4002;

    private FinanceDbHelper db;
    private ExecutorService executor;
    private Handler mainHandler;
    private SharedPreferences preferences;

    private TextView balanceText;
    private TextView incomeText;
    private TextView expenseText;
    private TextView syncStatusText;
    private EditText amountInput;
    private EditText categoryInput;
    private EditText noteInput;
    private RadioButton expenseRadio;
    private RadioButton incomeRadio;
    private Button syncButton;
    private LinearLayout recordList;

    private final NumberFormat moneyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", new Locale("id", "ID"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new FinanceDbHelper(this);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        preferences = getSharedPreferences("settings", MODE_PRIVATE);
        buildUi();
        refreshData();
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        db.close();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(247, 247, 242));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Catatan Keuangan");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(25, 35, 31));
        title.setGravity(Gravity.START);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        syncStatusText = new TextView(this);
        syncStatusText.setText("Belum sinkron");
        syncStatusText.setTextColor(Color.rgb(92, 101, 96));
        syncStatusText.setPadding(0, dp(4), 0, dp(12));
        root.addView(syncStatusText);

        LinearLayout summary = panel();
        balanceText = metricText("Saldo", summary);
        incomeText = metricText("Pemasukan", summary);
        expenseText = metricText("Pengeluaran", summary);
        root.addView(summary);

        LinearLayout form = panel();
        TextView formTitle = sectionTitle("Tambah Catatan");
        form.addView(formTitle);

        RadioGroup typeGroup = new RadioGroup(this);
        typeGroup.setOrientation(RadioGroup.HORIZONTAL);
        expenseRadio = new RadioButton(this);
        expenseRadio.setId(View.generateViewId());
        expenseRadio.setText("Pengeluaran");
        expenseRadio.setChecked(true);
        incomeRadio = new RadioButton(this);
        incomeRadio.setId(View.generateViewId());
        incomeRadio.setText("Pemasukan");
        typeGroup.addView(expenseRadio);
        typeGroup.addView(incomeRadio);
        form.addView(typeGroup);

        amountInput = input("Nominal");
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        categoryInput = input("Kategori, contoh: Makan, Stok, Gaji");
        noteInput = input("Catatan singkat");
        form.addView(amountInput);
        form.addView(categoryInput);
        form.addView(noteInput);

        Button saveButton = primaryButton("Simpan");
        saveButton.setOnClickListener(v -> saveRecord());
        form.addView(saveButton);
        root.addView(form);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, dp(10));
        syncButton = primaryButton("Sinkron ke Google Drive");
        syncButton.setOnClickListener(v -> startSyncFlow());
        actions.addView(syncButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(actions);

        TextView listTitle = sectionTitle("Riwayat");
        root.addView(listTitle);
        recordList = new LinearLayout(this);
        recordList.setOrientation(LinearLayout.VERTICAL);
        root.addView(recordList);

        setContentView(scrollView);
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        panel.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(12), 0, 0);
        panel.setLayoutParams(params);
        return panel;
    }

    private TextView metricText(String label, LinearLayout parent) {
        TextView view = new TextView(this);
        view.setText(label + ": Rp0");
        view.setTextSize(18);
        view.setTextColor(Color.rgb(25, 35, 31));
        view.setPadding(0, dp(4), 0, dp(4));
        parent.addView(view);
        return view;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(19);
        view.setTextColor(Color.rgb(25, 35, 31));
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(4), 0, dp(8));
        return view;
    }

    private EditText input(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setSingleLine(false);
        editText.setMinLines(1);
        editText.setTextSize(16);
        editText.setPadding(dp(10), dp(6), dp(10), dp(6));
        return editText;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(31, 122, 90));
        button.setAllCaps(false);
        return button;
    }

    private void saveRecord() {
        String rawAmount = amountInput.getText().toString().trim().replace(",", ".");
        String category = categoryInput.getText().toString().trim();
        String note = noteInput.getText().toString().trim();
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
        record.category = category.isEmpty() ? "Umum" : category;
        record.note = note;
        record.type = incomeRadio.isChecked() ? MoneyRecord.TYPE_INCOME : MoneyRecord.TYPE_EXPENSE;
        db.saveRecord(record);

        amountInput.setText("");
        categoryInput.setText("");
        noteInput.setText("");
        expenseRadio.setChecked(true);
        syncStatusText.setText("Ada perubahan lokal. Tekan sinkron untuk menyimpan ke Drive.");
        refreshData();
    }

    private void refreshData() {
        FinanceDbHelper.Summary summary = db.getSummary();
        balanceText.setText("Saldo: " + money(summary.balance()));
        incomeText.setText("Pemasukan: " + money(summary.income));
        expenseText.setText("Pengeluaran: " + money(summary.expense));

        recordList.removeAllViews();
        List<MoneyRecord> records = db.getVisibleRecords();
        if (records.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Belum ada catatan.");
            empty.setTextColor(Color.rgb(92, 101, 96));
            empty.setPadding(0, dp(10), 0, dp(10));
            recordList.addView(empty);
            return;
        }

        for (MoneyRecord record : records) {
            recordList.addView(recordRow(record));
        }
    }

    private View recordRow(MoneyRecord record) {
        LinearLayout row = panel();
        TextView main = new TextView(this);
        String prefix = MoneyRecord.TYPE_INCOME.equals(record.type) ? "+ " : "- ";
        main.setText(prefix + money(record.amount) + "  |  " + record.category);
        main.setTextSize(17);
        main.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        main.setTextColor(MoneyRecord.TYPE_INCOME.equals(record.type) ? Color.rgb(31, 122, 90) : Color.rgb(164, 62, 48));
        row.addView(main);

        TextView detail = new TextView(this);
        String note = record.note == null || record.note.trim().isEmpty() ? "" : "\n" + record.note;
        detail.setText(dateFormat.format(new Date(record.occurredAt)) + note);
        detail.setTextColor(Color.rgb(92, 101, 96));
        detail.setPadding(0, dp(4), 0, dp(8));
        row.addView(detail);

        Button delete = new Button(this);
        delete.setText("Hapus");
        delete.setAllCaps(false);
        delete.setOnClickListener(v -> {
            db.deleteRecord(record.id);
            syncStatusText.setText("Catatan dihapus lokal. Tekan sinkron untuk memperbarui Drive.");
            refreshData();
        });
        row.addView(delete);
        return row;
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
                    syncStatusText.setText("Sinkron selesai. Folder: " + DriveSyncManager.ROOT_FOLDER_NAME + "/" + DriveSyncManager.APP_FOLDER_NAME);
                    refreshData();
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
