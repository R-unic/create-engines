package com.createengines.compat.sable;

import dev.ryanhcode.sable.sound.SoundInstanceDelegated;

/**
 * Names Sable's sound wrapper, which is not in its companion library. Apart from {@link SableBridge} so that a change to
 * Sable's own classes can only take this with it. Reached only through {@link SableCompat#playedAs}, on a client.
 */
final class SableSoundBridge {

	private SableSoundBridge() {
	}

	static Object playedAs(Object sound) {
		if (sound instanceof SoundInstanceDelegated delegated) {
			Object wrapper = delegated.getDelegate();
			if (wrapper != null)
				return wrapper;
		}
		return sound;
	}
}
