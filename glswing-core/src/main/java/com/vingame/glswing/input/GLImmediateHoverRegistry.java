package com.vingame.glswing.input;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Internal opt-in CPU hover path for simple retained overlays such as menus. */
public final class GLImmediateHoverRegistry {

	@FunctionalInterface
	public interface Resolver {
		/** Returns {@code null} when normal GPU picking should be used. */
		GLPickResult resolve(float screenX, float screenY);
	}

	@FunctionalInterface
	public interface Registration {
		void close();
	}

	private static final Map<Object, WeakReference<Resolver>> RESOLVERS =
			new WeakHashMap<>();

	private GLImmediateHoverRegistry() {
	}

	public static Registration register(Object host, Resolver resolver) {
		if (host == null || resolver == null)
			throw new IllegalArgumentException("Hover host and resolver must not be null");
		synchronized (RESOLVERS) {
			RESOLVERS.put(host, new WeakReference<>(resolver));
		}
		return () -> {
			synchronized (RESOLVERS) {
				WeakReference<Resolver> current = RESOLVERS.get(host);
				if (current != null && current.get() == resolver)
					RESOLVERS.remove(host);
			}
		};
	}

	public static GLPickResult resolve(Object host, float screenX, float screenY) {
		Resolver resolver;
		synchronized (RESOLVERS) {
			WeakReference<Resolver> reference = RESOLVERS.get(host);
			resolver = reference == null ? null : reference.get();
			if (reference != null && resolver == null)
				RESOLVERS.remove(host);
		}
		return resolver == null ? null : resolver.resolve(screenX, screenY);
	}
}
