package com.jianzhang.core.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideSecretStore(): SecretStore = SecretStore()

    @Provides
    @Singleton
    fun provideKeyManager(
        @ApplicationContext context: Context,
        secretStore: SecretStore,
    ): DatabaseKeyManager = DatabaseKeyManager(context, secretStore)

    @Provides
    @Singleton
    fun provideSettingsStore(
        @ApplicationContext context: Context,
        secretStore: SecretStore,
    ): SettingsStore = SettingsStore(context, secretStore)

}
