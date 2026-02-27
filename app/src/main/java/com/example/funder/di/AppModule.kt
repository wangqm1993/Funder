package com.example.funder.di

import android.content.Context
import androidx.room.Room
import com.example.funder.data.local.FundDao
import com.example.funder.data.local.FundDatabase
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FundDatabase {
        return Room.databaseBuilder(
            context,
            FundDatabase::class.java,
            "funder_database"
        )
            .addMigrations(FundDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideFundDao(database: FundDatabase): FundDao {
        return database.fundDao()
    }
}
