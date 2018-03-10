/*
 * This class provides an interface to the Novation LaunchControl MIDI controller
 */
LaunchControl {
	var uid;
	var midiOut;
	var defaultPage; // this page is used if the user hasn't created any pages
	var pages;
	var currentPageIdx;
	var >debug;

	// assume the user has the controller in the first factory preset
	const <padNotes = #[9, 10, 11, 12, 25, 26, 27, 28];
	const <knobNums = #[21, 22, 23, 24, 25, 26, 27, 28,
					   41, 42, 43, 44, 45, 46, 47, 48];
	const <dirNotes = #[114, 115, 116, 117];
	const <channel = 8;
	// enums used to indicate direction buttons.
	const <up = 0;
	const <down = 1;
	const <left = 2;
	const <right = 3;

	*new {
		var uid, midiOut;
		if(MIDIClient.initialized.not, {
			MIDIClient.init;
		});
		uid = this.deviceUid();
		MIDIIn.connectByUID(0, uid);
		MIDIClient.destinations.do { | client |
			if(client.device == "Launch Control" &&
				((client.name == "Launch Control MIDI 1") ||
				 (client.name == "Launch Control")),
			{ midiOut = MIDIOut.newByName(client.device, client.name); } );
		};
		if(midiOut.isNil, {
			Error("Couldn't find Launch Control MIDI Device").throw;
			});
		midiOut.latency = 0;
		// this is needed on linux, hope it doesn't mess things up on other
		// platforms. I'm also not sure why it should be 1, but 0 didn't work.
		midiOut.connect(1);

		^super.newCopyArgs(uid, midiOut, LaunchControlPage(), [], nil, false).init;
	}

	init {
		padNotes.do { | noteNum, idx |
			MIDIdef.noteOn(
				"launchcontrol-noteon-%".format(noteNum).asSymbol,
				{ | val, num, chan, src |
					this.handlePad(idx, val);
				},
				noteNum, nil, uid
			);
			MIDIdef.noteOff(
				"launchcontrol-noteoff-%".format(noteNum).asSymbol,
				{ | val, num, chan, src |
					this.handlePad(idx, val);
				},
				noteNum, nil, uid
			);
		};
		dirNotes.do { | noteNum, idx |
			MIDIdef.cc(
				"launchcontrol-cc-%".format(noteNum).asSymbol,
				{ | val, num, chan, src |
					this.handleDirection(idx, val);
				},
				noteNum, nil, uid
			);
			MIDIdef.cc(
				"launchcontrol-cc-%".format(noteNum).asSymbol,
				{ | val, num, chan, src |
					this.handleDirection(idx, val);
				},
				noteNum, nil, uid
			);
		};

		knobNums.do { | ccNum, idx |
			MIDIdef.cc(
				"launchcontrol-cc-%".format(ccNum).asSymbol,
				{ | val, num, chan, src |
					this.handleKnob(idx, val);
				},
				ccNum, nil, uid
			)
		};

		8.do { |idx|
			this.setPadColor(idx, 0, 0);
		};
		4.do { |idx|
			this.setDirectionColor(idx, 0);
		};
	}

	// let the user assign the callbacks to the current page
	onPad_ { | callback |
		this.currentPage.onPad = callback;
	}

	onKnob_ { | callback |
		this.currentPage.onKnob = callback;
	}

	onDirection_ { | callback |
		this.currentPage.onDirection = callback;
	}

	knobValues { ^this.currentPage.knobValues }

	handlePad { | idx, val |
		val = val/127;
		if(this.currentPage.onPad.notNil, {
			this.currentPage.onPad.value(idx, val);
		});
		if(debug, {
			"LaunchControl - Pad %, val: %\n".postf(idx, val);
		})
	}

	handleKnob { | idx, val |
		val = val/127;
		this.currentPage.knobValues[idx] = val;
		if(this.currentPage.onKnob.notNil, {
			this.currentPage.onKnob.value(idx, val);
		});
		if(debug, {
			"LaunchControl - Knob %, val: %\n".postf(idx, val);
		})
	}

	handleDirection { | dir, val |
		val = val/127;
		if(this.currentPage.onDirection.notNil, {
			this.currentPage.onDirection.value(dir, val);
		});
		if(debug, {
			var dirstr = dir.switch
				{up} {"up"}
				{down} {"down"}
				{left} {"left"}
				{right} {"right"};
			"LaunchControl - %: %\n".postf(dirstr, val);
		})
	}

	*deviceUid {
		var inPort = MIDIIn.findPort("Launch Control", "Launch Control MIDI") ?
		    MIDIIn.findPort("Launch Control", "Launch Control MIDI 1") ?
			MIDIIn.findPort("Launch Control", "Launch Control"); // windows
		if(inPort.isNil, {
			Error("Couldn't find Launch Control MIDI Device").throw;
			});
		^inPort.uid;
	}

	// private method to calculate the 8-bit value from red and green brightness
	// TODO: private methods could just be functions assigned to class/instance
	// variables
	colorValue { | redBrightness, greenBrightness |
		// bits are 00GGLPRR
		// GG - green 0-3
		// L - cLear the others buffers copy
		// P - coPy this data to other buffer (overrides clear bit if set)
		// RR - red 0-3
		// 0x10 * green + red + flags
		// flags are:
		//    0x0C usually
		//    0x08 to flash (if flashing is configured)
		//    0x00 if using double-buffering
		//
		var greenInt = (greenBrightness * 4).floor.clip(0,3).asInt;
		var redInt = (redBrightness * 4).floor.clip(0,3).asInt;
		^(0x10 * greenInt + redInt + 0x0C);
	}

	// sets a pad color on the current page
	setPadColor {
		| padIdx, redBrightness, greenBrightness |
		var value = this.colorValue(redBrightness, greenBrightness);
		this.currentPage.ledStates[padIdx] = value;
		midiOut.noteOn(channel, padNotes[padIdx], value);
	}

	// direction pads only support Red
	setDirectionColor {
		| dir, redBrightness |
		var value = this.colorValue(redBrightness, 0);
		this.currentPage.dirStates[dir] = value;
		midiOut.control(channel, dirNotes[dir], value);
	}

	// TODO: add red/green set methods for direction buttons
	setPadGreen {
		| padIdx, brightness |
		var intVal = (brightness * 4).floor.clip(0,3).asInt;
		var prevVal = this.currentPage.ledStates[padIdx];
		var value = prevVal.bitAnd(0xCF) + (intVal * 0x10);
		this.currentPage.ledStates[padIdx] = value;
		midiOut.noteOn(channel, padNotes[padIdx], value);
	}

	setPadRed {
		| padIdx, brightness |
		var intVal = (brightness * 4).floor.clip(0,3).asInt;
		var prevVal = this.currentPage.ledStates[padIdx];
		var value = prevVal.bitAnd(0xFC) + intVal;
		this.currentPage.ledStates[padIdx] = value;
		midiOut.noteOn(channel, padNotes[padIdx], value);
	}

	currentPage {
		if(currentPageIdx.isNil, {
			^defaultPage;
		}, {
			^pages[currentPageIdx];
		})
	}
}

LaunchControlPage {
	var <>knobValues;
	var <>ledStates;
	var <>dirStates;
	var <>onPad;
	var <>onKnob;
	var <>onDirection;

	*new {
		^super.newCopyArgs(
			16.collect {nil},
			8.collect {nil},
			4.collect {nil})
	}
}
