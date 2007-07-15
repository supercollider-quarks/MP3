// A class for making it easier to read MP3 files / streams in SC
// Written by Dan Stowell, Jan 2007.
// Free to use under the GPL.

MP3 {
	
	classvar	<>lamepath 
			//	= "/usr/local/bin/lame",
				= "/sw/bin/lame",
			<>curlpath = "/usr/bin/curl"
			;
	
	var // These are filled by newCopyArgs:
		<path, <mode,
		// These are other vars:
		<fifo, <lameproc, <pid, playing=false;
	
	*initClass {
		// Check that at least *something* exists at the desired executable paths
		this.checkForExecutable(lamepath, "lame", "lamepath");
		this.checkForExecutable(curlpath, "curl", "curlpath");
	}
	
	*checkForExecutable { |path, execname, varname|
		var srch;
		if(File.exists(path).not, {
			srch = ("which" + execname).unixCmdGetStdOut.split($\n).join("");
			//"Result of search for executable:".postln;
			//srch.postln;
			if((srch.beginsWith("no ") || srch.isNil || (srch=="")).not, {
				("MP3."++varname + "=" + $" ++ srch ++ $").interpret;
				("MP3."++varname + "automatically set to" + srch).postln;
			}, {
				("'"++execname++"' executable not found. Please modify the MP3:"++varname++" class variable.").warn;
			});
		});
	}
	
	*new { |path, mode=\readfile|
		^super.newCopyArgs(path, mode).init;
	}
	
	init {
		
		// If we're reading a local file, let's check it exists so as to prevent later problems
		if((mode!=\readurl) && (mode !=\writefile) && File.exists(path).not, {
			"Local MP3 file not found".warn;
			^nil;
		});
		
		// Establish our FIFO
		fifo = "/tmp/sc3mp3-" ++ this.hash ++ ".fifo";
		("mkfifo "++fifo).systemCmd;
		
		// Ensure things will be tidied up if the user recompiles
		UI.registerForShutdown({this.finish});
	}
	
	// Start the LAME command - involving some elastic trickery to work out the PID of the created process.
	start { |lameopts=""|
		var cmd, prepids, postpids, diff, cmdname, pipe, line, lines;
		
		// cmd is the command to execute, cmdname is used to search for it in the list of PIDs
		mode.switch(
		\readurl, {
			cmd = curlpath + "--silent \"" ++ path ++ "\" |" + lamepath + "--mp3input --decode --silent" + lameopts + " - " + fifo + "> /dev/null";
			cmdname = "curl";
		},
		\writefile, {
			cmd = lamepath + "--silent -r -s 44.1 --bitwidth 16" + lameopts + "\"" ++ fifo ++ "\"" + path + "> /dev/null";
			cmdname = "lame";
		}, { // Default is to read a local file
			cmd = lamepath + "--decode --silent " + lameopts + "\"" ++ path ++ "\"" + fifo + "> /dev/null";
			cmdname = "lame";
		}
		);
		
		//"".postln;
		//"MP3.start: command to execute is:".postln;
		//cmd.postln;
		
		cmd.unixCmdInferPID({|thepid|
			pid = thepid;
			("MP3.start completed (PID"+(pid?"unknown")++")").postln;
			playing = true;
		});
	}
	
	stop {
		if(pid.isNil, {
			"MP3.stop - unable to stop automatically, PID not known".warn;
		}, {
			("kill" + pid).systemCmd;
			pid = nil;
			playing = false;
		});
	}
	
	restart {
		this.stop;
		this.start;
	}
		
	finish {
		this.stop;
		("rm " ++ fifo).systemCmd;
	}
	
	// Return a boolean to say whether we're playing or not
	playing {
		if(playing, {
			if(pid.isNil, {
				^true; // We can only assume it's still playing - we have no better info!
			}, {
				if(pid.isPIDRunning, {
					^true;
				}, {
					playing = false;
					^false;
				});
				
			});
		}, {
			^false;
		})
	}
	
	// Method based on suggestion by Till Bovermann
	*readToBuffer { |server,path,startFrame = 0,numFrames, action, bufnum, lameopts="" |
		var tmpPath = "/tmp/sc3mp3read-" ++ this.hash ++ ".wav" ;
		if((MP3.lamepath + "--decode" + lameopts + "\"" ++ path ++ "\"" + tmpPath).systemCmd == 0, {
			^Buffer.read(server,tmpPath,startFrame,numFrames, {("rm" + tmpPath).unixCmd} <> action, bufnum);
		}, {
			("Unable to read MP3 file:" + path).warn;
			("rm" + tmpPath).unixCmd;
		});
	}

}