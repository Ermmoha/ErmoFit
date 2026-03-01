package com.ermofit.app.di

import android.content.Context
import androidx.room.Room
import com.ermofit.app.data.local.AppDatabase
import com.ermofit.app.data.local.dao.FitnessDao
import com.ermofit.app.newplan.data.local.dao.NewPlanDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ermofit.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideFitnessDao(database: AppDatabase): FitnessDao = database.fitnessDao()

    @Provides
    fun provideNewPlanDao(database: AppDatabase): NewPlanDao = database.newPlanDao()
}
