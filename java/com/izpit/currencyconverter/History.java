package com.izpit.currencyconverter;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Dao;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class History extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Entity(tableName = "conversion_history")
    public static class ConversionHistory {
        @PrimaryKey(autoGenerate = true)
        private int id;
        private String fromCurrency;
        private String toCurrency;
        private double amount;
        private double result;
        private long timestamp;

        public ConversionHistory(String fromCurrency, String toCurrency, double amount, double result) {
            this.fromCurrency = fromCurrency;
            this.toCurrency = toCurrency;
            this.amount = amount;
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public int getId() { return id; }
        public String getFromCurrency() { return fromCurrency; }
        public String getToCurrency() { return toCurrency; }
        public double getAmount() { return amount; }
        public double getResult() { return result; }
        public long getTimestamp() { return timestamp; }

        // Setters
        public void setId(int id) { this.id = id; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    @Dao
    public interface ConversionHistoryDao {
        @Insert
        void insert(ConversionHistory conversion);

        @Query("SELECT * FROM conversion_history ORDER BY timestamp DESC")
        LiveData<List<ConversionHistory>> getAllConversions();

        @Query("DELETE FROM conversion_history")
        void deleteAll();
    }

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
        private List<ConversionHistory> conversions = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        @NonNull
        @Override
        public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(HistoryViewHolder holder, int position) {
            ConversionHistory conversion = conversions.get(position);
            holder.conversionText.setText(String.format(Locale.getDefault(),
                    "%.2f %s → %.2f %s",
                    conversion.getAmount(),
                    conversion.getFromCurrency(),
                    conversion.getResult(),
                    conversion.getToCurrency()));
            holder.dateText.setText(dateFormat.format(new Date(conversion.getTimestamp())));
        }

        @Override
        public int getItemCount() {
            return conversions.size();
        }

        public void setConversions(List<ConversionHistory> conversions) {
            this.conversions = conversions;
            notifyDataSetChanged();
        }

        static class HistoryViewHolder extends RecyclerView.ViewHolder {
            TextView conversionText;
            TextView dateText;

            HistoryViewHolder(View view) {
                super(view);
                conversionText = view.findViewById(R.id.conversionText);
                dateText = view.findViewById(R.id.dateText);
            }
        }
    }

    private RecyclerView historyRecyclerView;
    private HistoryAdapter historyAdapter;
    private TextView emptyStateText;
    private float x1, x2;
    private static final float MIN_DISTANCE = 150;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);

        setupViews();
        viewHistory();
    }

    private void setupViews() {
        findViewById(R.id.main).setOnTouchListener((v, event) -> handleSwipe(event));

        historyRecyclerView = findViewById(R.id.historyRecyclerView);
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new HistoryAdapter();
        historyRecyclerView.setAdapter(historyAdapter);
        historyRecyclerView.setOnTouchListener((v, event) -> handleSwipe(event));

        emptyStateText = findViewById(R.id.emptyStateText);

        MaterialButton clearButton = findViewById(R.id.clearButton);
        clearButton.setOnClickListener(v -> showClearConfirmationDialog());
    }

    private void showClearConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to clear all conversion history?")
                .setPositiveButton("Clear", (dialog, which) -> clearHistory())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearHistory() {
        executor.execute(() -> {
            AppDatabase.getInstance(this).conversionHistoryDao().deleteAll();
        });
    }

    private void viewHistory() {
        AppDatabase.getInstance(this).conversionHistoryDao().getAllConversions()
                .observe(this, conversions -> {
                    historyAdapter.setConversions(conversions);
                    emptyStateText.setVisibility(conversions.isEmpty() ? View.VISIBLE : View.GONE);
                    historyRecyclerView.setVisibility(conversions.isEmpty() ? View.GONE : View.VISIBLE);
                });
    }

    private boolean handleSwipe(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                x1 = event.getX();
                return true;
            case MotionEvent.ACTION_UP:
                x2 = event.getX();
                float deltaX = x2 - x1;
                if (Math.abs(deltaX) > MIN_DISTANCE) {
                    if (deltaX > 0) {
                        navigateToActivity(Converter.class, true);
                    } else {
                        navigateToActivity(MainActivity.class, false);
                    }
                }
                return true;
        }
        return false;
    }

    private void navigateToActivity(Class<?> targetClass, boolean swipingRight) {
        Intent intent = new Intent(this, targetClass);
        ActivityOptions options = ActivityOptions.makeCustomAnimation(this,
                swipingRight ? R.anim.slide_in_right : R.anim.slide_in_left,
                swipingRight ? R.anim.slide_out_left : R.anim.slide_out_right);
        startActivity(intent, options.toBundle());
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
