/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.di

import android.content.Context
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.repository.TodoRepository
import me.rerere.rikkahub.data.repository.BulletinRepository
import me.rerere.rikkahub.data.repository.DiaryRepository
import me.rerere.rikkahub.data.repository.AlbumRepository
import me.rerere.rikkahub.data.repository.AlbumFolderRepository
import me.rerere.rikkahub.data.repository.LoveDateRepository
import me.rerere.rikkahub.data.security.SecurityAuditRepository
import me.rerere.workspace.ProotShellRunner
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceBindMount
import me.rerere.workspace.WorkspaceManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

val repositoryModule = module {
    single {
        ConversationRepository(get(), get(), get(), get(), get(), get())
    }

    single {
        FolderRepository(get(), get())
    }

    single {
        MemoryRepository(get())
    }

    single {
        GenMediaRepository(get())
    }

    single {
        FilesRepository(get())
    }

    single {
        FavoriteRepository(get())
    }

    single {
        val context: Context = get()
        WorkspaceManager(
            baseDir = File(context.filesDir, "workspaces"),
            shellRunner = ProotShellRunner(
                nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir),
                extraBindMounts = listOf(
                    WorkspaceBindMount(
                        source = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() },
                        target = "/skills",
                    ),
                    WorkspaceBindMount(
                        source = File(context.filesDir, FileFolders.UPLOAD).apply { mkdirs() },
                        target = "/upload",
                    ),
                ),
            )
        )
    }

    single {
        RootfsInstaller(get())
    }

    single {
        WorkspaceRepository(get(), get(), get(), get())
    }

    single {
        FilesManager(get(), get(), get())
    }

    single {
        SkillManager(get(), get())
    }

    single {
        SecurityAuditRepository(
            get<me.rerere.rikkahub.data.db.AppDatabase>().securityAuditDao()
        )
    }

    single {
        TodoRepository(
            get<me.rerere.rikkahub.data.db.AppDatabase>().todoDao()
        )
    }

    single {
        BulletinRepository(
            get<me.rerere.rikkahub.data.db.AppDatabase>().bulletinDao()
        )
    }

    single {
        DiaryRepository(
            get<me.rerere.rikkahub.data.db.AppDatabase>().diaryDao()
        )
    }

    single {
        AlbumRepository(
            get<me.rerere.rikkahub.data.db.AppDatabase>().albumDao(),
            androidContext()
        )
    }

    single {
        AlbumFolderRepository(
            get<me.rerere.rikkahub.data.db.AppDatabase>().albumFolderDao()
        )
    }
    single {
        LoveDateRepository(
            get<me.rerere.rikkahub.data.db.AppDatabase>().loveDateDao()
        )
    }
    single {
        me.rerere.rikkahub.data.repository.StickerRepository(
            get<me.rerere.rikkahub.data.db.AppDatabase>().stickerDao(),
            androidContext()
        )
    }

    single {
        me.rerere.rikkahub.data.repository.BubbleSkinRepository(
            get<me.rerere.rikkahub.data.db.AppDatabase>().bubbleKThemeDao(),
            androidContext()
        )
    }
}
