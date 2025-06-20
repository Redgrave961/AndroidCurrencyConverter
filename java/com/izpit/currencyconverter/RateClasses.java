package com.izpit.currencyconverter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

class CurrencyRate {
    private String currency;
    private double rate;

    public CurrencyRate(String currency, double rate) {
        this.currency = currency;
        this.rate = rate;
    }

    public String getCurrency() { return currency; }
    public double getRate() { return rate; }
}


class RatesAdapter extends RecyclerView.Adapter<RatesAdapter.RateViewHolder> {
    private List<CurrencyRate> rates = new ArrayList<>();

    @Override
    public RateViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rate, parent, false);
        return new RateViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RateViewHolder holder, int position) {
        CurrencyRate rate = rates.get(position);
        holder.currencyText.setText(rate.getCurrency());
        holder.rateText.setText(String.format("%.4f", rate.getRate()));
    }

    @Override
    public int getItemCount() {
        return rates.size();
    }

    public void setRates(List<CurrencyRate> rates) {
        this.rates = rates;
        notifyDataSetChanged();
    }

    static class RateViewHolder extends RecyclerView.ViewHolder {
        TextView currencyText;
        TextView rateText;

        RateViewHolder(View view) {
            super(view);
            currencyText = view.findViewById(R.id.currencyText);
            rateText = view.findViewById(R.id.rateText);
        }
    }
}