package com.mckimquyen.reader.infrastructure.di

import com.mckimquyen.reader.domain.p2psync.LanGate
import com.mckimquyen.reader.infrastructure.mirror.LanAddressProvider
import com.mckimquyen.reader.infrastructure.p2psync.AndroidLanGate
import com.mckimquyen.reader.infrastructure.p2psync.AndroidNsdPlatform
import com.mckimquyen.reader.infrastructure.p2psync.KeystoreSecretWrapper
import com.mckimquyen.reader.infrastructure.p2psync.NsdPlatform
import com.mckimquyen.reader.infrastructure.p2psync.PeerDiscovery
import com.mckimquyen.reader.infrastructure.p2psync.SecretWrapper
import com.mckimquyen.reader.infrastructure.mirror.LanAddressSource
import com.mckimquyen.reader.infrastructure.mirror.MirrorEngine
import com.mckimquyen.reader.domain.p2psync.SyncSessionTransport
import com.mckimquyen.reader.infrastructure.mirror.MirrorServer
import com.mckimquyen.reader.infrastructure.mirror.SyncSessionEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MirrorAddressModule {
    @Binds
    abstract fun bindLanAddressSource(provider: LanAddressProvider): LanAddressSource

    @Binds
    abstract fun bindLanGate(gate: AndroidLanGate): LanGate

    @Binds
    abstract fun bindNsdPlatform(platform: AndroidNsdPlatform): NsdPlatform

    @Binds
    abstract fun bindSecretWrapper(wrapper: KeystoreSecretWrapper): SecretWrapper
}

/** One server per ViewModel: leaving the reading page clears the ViewModel, which stops that server. */
@Module
@InstallIn(ViewModelComponent::class)
object MirrorEngineModule {
    @Provides
    @ViewModelScoped
    fun provideMirrorEngine(): MirrorEngine = MirrorServer()
}

/** One sync server per sync ViewModel: leaving the sync screen clears it, which stops that server. */
@Module
@InstallIn(ViewModelComponent::class)
object SyncSessionModule {
    @Provides
    @ViewModelScoped
    fun provideSyncSessionEngine(): SyncSessionEngine = MirrorServer()

    @Provides
    fun provideSyncSessionTransport(): SyncSessionTransport = SyncSessionTransport()

    /** Per sync screen, so clearing the ViewModel also withdraws the advertisement and stops discovery. */
    @Provides
    @ViewModelScoped
    fun providePeerDiscovery(platform: NsdPlatform): PeerDiscovery = PeerDiscovery(platform)
}
