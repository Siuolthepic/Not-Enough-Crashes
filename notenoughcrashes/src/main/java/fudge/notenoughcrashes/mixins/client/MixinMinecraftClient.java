package fudge.notenoughcrashes.mixins.client;

import java.io.File;
import java.util.Queue;

import fudge.notenoughcrashes.NotEnoughCrashes;
import fudge.notenoughcrashes.mixinhandlers.EntryPointCatcher;
import fudge.notenoughcrashes.mixinhandlers.InGameCatcher;
import fudge.notenoughcrashes.stacktrace.CrashUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.thread.ReentrantThreadExecutor;

import net.fabricmc.loader.entrypoint.minecraft.hooks.EntrypointClient;

@Mixin(MinecraftClient.class)
@SuppressWarnings("StaticVariableMayNotBeInitialized")
public abstract class MixinMinecraftClient extends ReentrantThreadExecutor<Runnable> {
    @Shadow
    volatile boolean running;
    @Shadow
    private CrashReport crashReport;

    @Shadow
    @Final
    private Queue<Runnable> renderTaskQueue;

    public MixinMinecraftClient(String string_1) {
        super(string_1);
    }

    @Shadow
    protected abstract void render(boolean boolean_1);
//
//    // This is loader's entry point, it is manually generated bytecode specified in EntrypointPatchHook#finishEntrypoint
//    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/fabricmc/loader/entrypoint/minecraft/hooks/EntrypointClient;start(Ljava/io/File;Ljava/lang/Object;)V"))
//    private void redirectModEntryPoints(File runDir, Object gameInstance) {
//        // Displaying a screen is just a nuisance in dev
//        if (!NotEnoughCrashes.ENABLE_ENTRYPOINT_CATCHING) {
//            EntrypointClient.start(runDir, gameInstance);
//            return;
//        }
//        try {
//            EntrypointClient.start(runDir, gameInstance);
//        } catch (Throwable e) {
//            // Only note the crash, open the screen later when opening screens is possible
//            EntryPointCatcher.handleEntryPointError(e);
//        }
//    }

    /**
     * @author runemoro
     * @reason Allows the player to choose to return to the title screen after a crash, or get
     * a pasteable link to the crash report on paste.dimdev.org.
     */
    @Overwrite
    public void run() {
        if (EntryPointCatcher.crashedDuringStartup()) EntryPointCatcher.displayInitErrorScreen();
        while (running) {
            if (crashReport == null) {
                try {
                    render(true);
                } catch (CrashException e) {
                    InGameCatcher.handleClientCrash(e, e.getReport(), true, renderTaskQueue);
                } catch (Throwable e) {
                    InGameCatcher.handleClientCrash(e, new CrashReport("Unexpected error", e), false, renderTaskQueue);
                }
            } else {
                InGameCatcher.handleServerCrash(crashReport);
                crashReport = null;
            }
        }
    }


    /**
     * Prevent the integrated server from exiting in the case it crashed
     */
    @Redirect(method = "startIntegratedServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;printCrashReport(Lnet/minecraft/util/crash/CrashReport;)V"))
    private void redirectPrintCrashReport(CrashReport report) {
        CrashUtils.outputReport(report);
    }


    /**
     * @author runemoro
     * @reason Disconnect from the current world and free memory, using a memory reserve
     * to make sure that an OutOfMemory doesn't happen while doing this.
     * <p>
     * Bugs Fixed:
     * - https://bugs.mojang.com/browse/MC-128953
     * - Memory reserve not recreated after out-of memory
     */
    @Overwrite
    //TODO: can be replaced by 2-4 injection/redirections
    public void cleanUpAfterCrash() {
        InGameCatcher.resetGameState(renderTaskQueue);
    }
}
