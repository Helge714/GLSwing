package com.vingame.glswing.component;

/** Recent non-blocking timing breakdown for one GL presentation surface. */
public record GLFrameProfile(
		double endToEndMillis,
		double callbackCpuMillis,
		double housekeepingCpuMillis,
		double sceneCpuMillis,
		double presentCpuMillis,
		double sceneGpuMillis,
		double presentGpuMillis) {

	public static final GLFrameProfile EMPTY =
			new GLFrameProfile(0, 0, 0, 0, 0, 0, 0);

	/** Time outside the measured GLEventListener callback. */
	public double backendRemainderMillis() {
		return Math.max(0, endToEndMillis - callbackCpuMillis);
	}
}
