package com.izpit.currencyconverter;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Converter extends AppCompatActivity {
    private static final float MIN_DISTANCE = 150;
    private static final int VIBRATION_DURATION = 50;

    private float x1, x2;

    private AutoCompleteTextView fromCurrencySpinner, toCurrencySpinner;
    private TextInputEditText amountEditText;
    private TextView resultTextView;
    private ImageButton swapButton;
    private ConstraintLayout mainLayout;
    private Vibrator vibrator;

    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_converter);

        initializeViews();
        setupToolbar();
        setupListeners();
        fetchCurrencies();
    }

    private void initializeViews() {
        mainLayout = findViewById(R.id.main);
        fromCurrencySpinner = findViewById(R.id.fromCurrencySpinner);
        toCurrencySpinner = findViewById(R.id.toCurrencySpinner);
        amountEditText = findViewById(R.id.amountEditText);
        resultTextView = findViewById(R.id.resultTextView);
        swapButton = findViewById(R.id.swapButton);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    private void setupListeners() {
        mainLayout.setOnTouchListener((v, event) -> handleSwipe(event));
        swapButton.setOnClickListener(v -> swapCurrencies());
        findViewById(R.id.convertButton).setOnClickListener(v -> performConversion());
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
                        navigateToActivity(MainActivity.class, true);
                    } else {
                        navigateToActivity(History.class, false);
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

    private void swapCurrencies() {
        String fromCurrency = fromCurrencySpinner.getText().toString();
        String toCurrency = toCurrencySpinner.getText().toString();

        fromCurrencySpinner.setText(toCurrency, false);
        toCurrencySpinner.setText(fromCurrency, false);

        if (!amountEditText.getText().toString().isEmpty()) {
            performConversion();
        }
    }

    private void performConversion() {
        String amountStr = amountEditText.getText().toString();
        if (amountStr.isEmpty()) {
            amountEditText.setError("Please enter an amount");
            return;
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_DURATION,
                        VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(VIBRATION_DURATION);
            }
        }

        String fromCurrency = extractCurrencyCode(fromCurrencySpinner.getText().toString());
        String toCurrency = extractCurrencyCode(toCurrencySpinner.getText().toString());

        try {
            double amount = Double.parseDouble(amountStr);
            convertCurrency(fromCurrency, toCurrency, amount);
        } catch (NumberFormatException e) {
            amountEditText.setError("Invalid number format");
        }
    }

    private String extractCurrencyCode(String fullCurrencyString) {
        return fullCurrencyString.split(" - ")[0];
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
                    List<String> currencies = new ArrayList<>();
                    Iterator<String> keys = json.keys();

                    while (keys.hasNext()) {
                        String code = keys.next();
                        currencies.add(code + " - " + json.getString(code));
                    }

                    runOnUiThread(() -> setupSpinners(currencies));
                } catch (Exception e) {
                    showToast("Error parsing currencies");
                }
            }
        });
    }

    private void setupSpinners(List<String> currencies) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, currencies);
        fromCurrencySpinner.setAdapter(adapter);
        toCurrencySpinner.setAdapter(adapter);
    }

    private void convertCurrency(String from, String to, double amount) {
        String url = String.format("https://api.frankfurter.app/latest?amount=%s&from=%s&to=%s",
                amount, from, to);

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                showToast("Conversion failed");
            }

            @Override
            public void onResponse(Call call, Response response)  {
                if (!response.isSuccessful()) {
                    showToast("Error during conversion");
                    return;
                }

                try {
                    JSONObject json = new JSONObject(response.body().string());
                    double result = json.getJSONObject("rates").getDouble(to);

                    // Save conversion to database using the nested ConversionHistory class
                    History.ConversionHistory conversion = new History.ConversionHistory(from, to, amount, result);
                    new Thread(() -> AppDatabase.getInstance(Converter.this)
                            .conversionHistoryDao()
                            .insert(conversion)).start();

                    runOnUiThread(() -> resultTextView.setText(String.format("%.2f %s", result, to)));
                } catch (Exception e) {
                    showToast("Error processing conversion");
                }
            }
        });
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
}