package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RunningWindowTest {

	@Test
	void anEngineNeverSeenRunningIsNotRunning() {
		RunningWindow window = new RunningWindow();
		assertFalse(window.ranWithin(0, RunningWindow.DEFAULT_WINDOW));
		assertFalse(window.ranWithin(Long.MAX_VALUE, RunningWindow.DEFAULT_WINDOW));
		window.observe(5, false);
		assertFalse(window.ranWithin(5, RunningWindow.DEFAULT_WINDOW));
	}

	@Test
	void aStutterOfTwentyTicksStillCountsAsRunning() {
		RunningWindow window = new RunningWindow();
		for (int t = 700; t < 713; t++)
			window.observe(t, true);
		for (int t = 713; t < 733; t++) {
			window.observe(t, false);
			assertTrue(window.ranWithin(t, RunningWindow.DEFAULT_WINDOW), "tick " + t);
		}
	}

	@Test
	void anEngineThatStaysDownDoesNotCountAsRunning() {
		RunningWindow window = new RunningWindow();
		window.observe(100, true);
		for (int t = 101; t <= 400; t++)
			window.observe(t, false);
		assertTrue(window.ranWithin(200, RunningWindow.DEFAULT_WINDOW));
		assertFalse(window.ranWithin(201, RunningWindow.DEFAULT_WINDOW));
		assertFalse(window.ranWithin(400, RunningWindow.DEFAULT_WINDOW));
	}

	@Test
	void resetForgetsAnEngineAndASightingInTheFutureDoesNotCount() {
		RunningWindow window = new RunningWindow();
		window.observe(300, true);
		window.reset();
		assertFalse(window.ranWithin(310, RunningWindow.DEFAULT_WINDOW));
		window.observe(500, true);
		assertFalse(window.ranWithin(499, RunningWindow.DEFAULT_WINDOW));
		assertTrue(window.ranWithin(500, 0));
	}

	@Test
	void hostileWindowsAndTicksDoNotThrowOrPass() {
		RunningWindow window = new RunningWindow();
		window.observe(Long.MAX_VALUE, true);
		assertTrue(window.ranWithin(Long.MAX_VALUE, 0));
		assertFalse(window.ranWithin(0, -1));
		assertFalse(window.ranWithin(Long.MIN_VALUE, Integer.MAX_VALUE));
	}
}
