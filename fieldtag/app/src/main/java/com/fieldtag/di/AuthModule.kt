package com.fieldtag.di

import com.fieldtag.domain.auth.AuthRepository
import com.fieldtag.domain.auth.StubAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: StubAuthRepository): AuthRepository
}
