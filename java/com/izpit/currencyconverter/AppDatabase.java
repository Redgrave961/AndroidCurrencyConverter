package com.izpit.currencyconverter;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.izpit.currencyconverter.History.ConversionHistory;
import com.izpit.currencyconverter.History.ConversionHistoryDao;

@Database(entities = {ConversionHistory.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance;
    public abstract ConversionHistoryDao conversionHistoryDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    "currency_converter_db"
            ).build();
        }
        return instance;
    }
}