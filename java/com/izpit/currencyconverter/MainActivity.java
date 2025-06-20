package com.izpit.currencyconverter;

import android.app.ActivityOptions;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int MENU_THEME_LIGHT = 1;
    private static final int MENU_THEME_DARK = 2;
    private static final String PREFERENCES_NAME = "AppPreferences";
    private static final String BASE_CURRENCY_KEY = "BaseCurrency";
    private static final String THEME_KEY = "AppTheme";
    private static final String CHANNEL_ID = "CURRENCY_UPDATES";

    private static final List<String> ALLOWED_BASE_CURRENCIES = Arrays.asList(
            "BGN - Bulgarian Lev",
            "USD - US Dollar",
            "EUR - Euro"
    );

    private TextView baseCurrencyText;
    private RecyclerView ratesRecyclerView;
    private RatesAdapter ratesAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private final OkHttpClient client = new OkHttpClient();
    private SharedPreferences preferences;
    private NotificationManager notificationManager;
    private float x1, x2;
    private static final float MIN_DISTANCE = 150;
    private List<String> currencies = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        applySavedTheme();

        createNotificationChannel();
        setupViews();
        fetchCurrencies();
        registerForContextMenu(findViewById(R.id.appBar));
    }

    private void setupViews() {
        baseCurrencyText = findViewById(R.id.titleText);
        ratesRecyclerView = findViewById(R.id.ratesRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);

        ratesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        ratesAdapter = new RatesAdapter();
        ratesRecyclerView.setAdapter(ratesAdapter);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchRates();
            showRefreshNotification();
        });

        findViewById(R.id.popupMenuButton).setOnClickListener(this::showPopupMenu);
        updateBaseCurrencyText();

        ratesRecyclerView.setOnTouchListener((v, event) -> handleSwipe(event));

        notificationManager = getSystemService(NotificationManager.class);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Currency Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications for currency rate updates");
            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void fetchCurrencies() {
        Request request = new Request.Builder()
                .url("https://api.frankfurter.app/currencies")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                showToast("Failed to fetch currencies");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    showToast("Error fetching currencies");
                    return;
                }

                try {
                    JSONObject json = new JSONObject(response.body().string());
                    currencies.clear();
                    Iterator<String> keys = json.keys();

                    while (keys.hasNext()) {
                        String code = keys.next();
                        currencies.add(code + " - " + json.getString(code));
                    }

                    runOnUiThread(() -> {
                        updateBaseCurrencyText();
                        fetchRates();
                    });
                } catch (Exception e) {
                    showToast("Error parsing currencies");
                }
            }
        });
    }

    private void showRefreshNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_sync_24)
                .setContentTitle("Currency Rates Updated")
                .setContentText("Latest exchange rates have been fetched")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(1, builder.build());
    }

    private void fetchRates() {
        String baseCurrency = extractCurrencyCode(preferences.getString(BASE_CURRENCY_KEY, "USD"));
        String url = "https://api.frankfurter.app/latest?base=" + baseCurrency;

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(MainActivity.this, "Failed to fetch rates", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                swipeRefreshLayout.setRefreshing(false);
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONObject rates = json.getJSONObject("rates");
                        List<CurrencyRate> ratesList = new ArrayList<>();

                        for (String currencyFullName : currencies) {
                            String currencyCode = extractCurrencyCode(currencyFullName);
                            if (rates.has(currencyCode)) {
                                double rate = rates.getDouble(currencyCode);
                                ratesList.add(new CurrencyRate(currencyFullName, rate));
                                if (ratesList.size() >= 10) break;
                            }
                        }

                        runOnUiThread(() -> ratesAdapter.setRates(ratesList));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void showPopupMenu(View view) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        for (int i = 0; i < ALLOWED_BASE_CURRENCIES.size(); i++) {
            popupMenu.getMenu().add(0, i, Menu.NONE, ALLOWED_BASE_CURRENCIES.get(i));
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            String selectedCurrency = extractCurrencyCode(ALLOWED_BASE_CURRENCIES.get(item.getItemId()));
            preferences.edit().putString(BASE_CURRENCY_KEY, selectedCurrency).apply();
            updateBaseCurrencyText();
            fetchRates();
            return true;
        });
        popupMenu.show();
    }

    private String extractCurrencyCode(String fullCurrencyString) {
        return fullCurrencyString.split(" - ")[0];
    }

    private void updateBaseCurrencyText() {
        String baseCurrency = preferences.getString(BASE_CURRENCY_KEY, "USD");
        String fullName = "";

        for (String currency : ALLOWED_BASE_CURRENCIES) {
            if (currency.startsWith(baseCurrency + " - ")) {
                fullName = currency;
                break;
            }
        }

        if (fullName.isEmpty()) {
            for (String currency : currencies) {
                if (currency.startsWith(baseCurrency + " - ")) {
                    fullName = currency;
                    break;
                }
            }
        }

        baseCurrencyText.setText("Base Currency: " + (fullName.isEmpty() ? baseCurrency : fullName));
    }



    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, MENU_THEME_LIGHT, Menu.NONE, "Light Theme");
        menu.add(0, MENU_THEME_DARK, Menu.NONE, "Dark Theme");
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        SharedPreferences.Editor editor = preferences.edit();

        switch (item.getItemId()) {
            case MENU_THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                editor.putInt(THEME_KEY, AppCompatDelegate.MODE_NIGHT_NO);
                editor.apply();
                Toast.makeText(this, "Light theme applied", Toast.LENGTH_SHORT).show();
                return true;

            case MENU_THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                editor.putInt(THEME_KEY, AppCompatDelegate.MODE_NIGHT_YES);
                editor.apply();
                Toast.makeText(this, "Dark theme applied", Toast.LENGTH_SHORT).show();
                return true;

            default:
                return super.onContextItemSelected(item);
        }
    }

    private void applySavedTheme() {
        int savedTheme = preferences.getInt(THEME_KEY, AppCompatDelegate.MODE_NIGHT_NO);
        AppCompatDelegate.setDefaultNightMode(savedTheme);
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
                        navigateToActivity(History.class, true);
                    } else {
                        navigateToActivity(Converter.class, false);
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

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
}