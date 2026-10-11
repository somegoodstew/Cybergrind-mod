package dev.cybergrind.client.mixin;

import dev.cybergrind.client.MusicLibrary;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Songs from the music folder: their sound ids are streamed from the file on disk instead of a resource pack. */
@Mixin(SoundBufferLibrary.class)
abstract class SoundBufferLibraryMixin {
	@Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
	private void cybergrind$musicFile(final Identifier location, final boolean looping,
		final CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
		if (!MusicLibrary.FILE_NAMESPACE.equals(location.getNamespace())) {
			return;
		}
		Path file = MusicLibrary.fileFor(location);
		cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
			try {
				if (file == null) {
					throw new IOException("no music file for " + location);
				}
				InputStream in = Files.newInputStream(file);
				return looping ? new LoopingAudioStream(JOrbisAudioStream::new, in) : new JOrbisAudioStream(in);
			} catch (IOException e) {
				throw new CompletionException(e);
			}
		}, Util.nonCriticalIoPool()));
	}
}
