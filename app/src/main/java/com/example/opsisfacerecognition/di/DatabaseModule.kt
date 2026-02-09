package com.example.opsisfacerecognition.di

import android.content.Context
import androidx.room.Room
import com.example.opsisfacerecognition.data.local.AppDatabase
import com.example.opsisfacerecognition.data.local.DatabasePassphraseProvider
import com.example.opsisfacerecognition.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.sqlcipher.database.SupportFactory

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "app.db"

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider
    ): AppDatabase {
        // Get existing passphrase or create a new one (first encrypted setup).
        val passphraseResult = passphraseProvider.getOrCreate()

        // No migration path by request:
        // when encryption is initialized for the first time, recreate DB from scratch.
        if (passphraseResult.isNewlyCreated) {
            context.deleteDatabase(DATABASE_NAME)
        }

        // Plug SQLCipher factory into Room so app.db is encrypted at rest.
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME
        )
            .openHelperFactory(SupportFactory(passphraseResult.passphrase))
            .build()
    }

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
}
