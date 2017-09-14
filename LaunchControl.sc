/*
 * This class provides an interface to the Novation LaunchControl MIDI controller
 */

LaunchControl {
	var midiOut;
	var pages;
	var currentPage;
	// assume the user has the controller in the first factory preset
	const <padNotes = [9, 10, 11, 12, 25, 26, 27, 28];
	const <knobNums = [21, 22, 23, 24, 25, 26, 27, 28, 41, 42, 43, 44, 45, 46, 47, 48];
	const <upNum = 114;
	const <downNum = 115;
	const <leftNum = 116;
	const <rightNum = 117;
	const channel = 8;

	*new {
		if(MIDIClient.initialized.not, { MIDIClient.init; });
		// TODO: this part needs work
		var uid = MIDIIn.findPort("Launch Control", "Launch Control").uid;
		MIDIIn.connectByUID(0, uid);
		var midiOut = MIDIOut.newByName("Launch Control", "Launch Control");
		~launchcontrol_in.latency = 0;
	}

	// sets a pad color on the current page
	setPadColor {
		| padIdx, redBrightness, greenBrightness |
		/* 0x10 * green + red + flags
		 * flags are:
		 *    0x0C usually
		 *    0x08 to flash (if flashing is configured)
		 *    0x00 if using double-buffering
		 */
		var value = 0x10 * greenBrightness + redBrightness + 0x0C;
		currentPage.ledStates[padIdx] = value;
		midiOut.noteOn(padNotes[padIdx], value);
	}
}

LaunchControlPage {
	var <>knobValues;
	var <>ledStates;
	var >onPad;
	var >onKnob;

	*new {
		^super.newCopyArgs(
			16.collect {nil},
			8.collect (nil))
	}
}