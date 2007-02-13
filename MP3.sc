// A class for making it easier to read MP3 files / streams in SC
// Written by Dan Stowell, Jan 2007.
// Free to use under the GPL.

MP3 {
	
	classvar	<>lamepath 
				= "/opt/local/bin/lame",
			//	= "/sw/bin/lame",
			<>curlpath = "/usr/bin/curl"
			;
	
	var // These are filled by newCopyArgs:
		<path, <mode,
		// These are other vars:
		<fifo, <lameproc, /* <proctrackfile, */ <pid;
	
	*initClass {
		// Check that at least *something* exists at the desired executable paths
		this.checkForExecutable(lamepath, "lame", "lamepath");
		this.checkForExecutable(curlpath, "curl", "curlpath");
	}
	
	*checkForExecutable { |path, execname, varname|
		if(("ls" + path + "| grep" + path + "> /dev/null 2>&1").systemCmd != 0, {
			("'"++execname++"' executable not found. Please modify the MP3:"++varname++" class variable.").warn;
		});
	}
	
	*new { |path, mode=\readfile|
		^super.newCopyArgs(path, mode).init;
	}
	
	init {
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
		
		"".postln;
		//"MP3.start: command to execute is:".postln;
		//cmd.postln;
				
		// Need to start the process and store a reference to it
		Task({
			// List processes before we launch
			pipe = Pipe.new("ps -xc -o \"pid command\" | grep" + cmdname + "| sed 's/" ++ cmdname ++ "//; s/ //g'", "r");
			line = pipe.getLine;
			while({line.notNil}, {lines = lines ++ line ++ "\n"; line = pipe.getLine; });
			pipe.close;
			prepids = if(lines.isNil, [], {lines.split($\n).collect(_.asInteger)});
			//("PIDS pre:  " + prepids).postln;
			
			// Run the cmd! NB use .unixCmd because we don't want to wait for a result (as would .systemCmd).
			cmd.unixCmd;

			0.2.wait;
			
			// List processes after we launch
			lines = "";
			pipe = Pipe.new("ps -xc -o \"pid command\" | grep" + cmdname + "| sed 's/" ++ cmdname ++ "//; s/ //g'", "r");
			line = pipe.getLine;
			while({line.notNil}, {lines = lines ++ line ++ "\n"; line = pipe.getLine; });
			pipe.close;
			postpids = if(lines.isNil, [], {lines.split($\n).collect(_.asInteger)});
			//("PIDS post: " + postpids).postln;
			
			
			// Can we spot a single addition?
			diff = difference(postpids, prepids).select(_ > 0);
			if(diff.size != 1, {
				("MP3.start - unable to be sure of the " ++ cmdname ++ " PID - you will need to terminate it yourself").warn;
				pid = nil;
			}, {
				pid = diff[0];
				("MP3.start - PID is" + pid).postln;
			});
			
			"MP3.start completed".postln;
		}).play(AppClock);
	}
	
	stop {
		if(pid.isNil, {
			"MP3.stop - unable to stop automatically, PID not known".warn;
		}, {
			("kill" + pid).systemCmd;
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
	
	// Method based on suggestion by Till Bovermann
	*readToBuffer { |server,path,startFrame = 0,numFrames, action, bufnum, lameopts="" |
		var tmpPath = "/tmp/sc3mp3read-" ++ this.hash ++ ".wav" ;
		(MP3.lamepath + "--decode" + lameopts + "\"" ++ path ++ "\"" + tmpPath).systemCmd;
		^Buffer.read(server,tmpPath,startFrame,numFrames, {("rm" + tmpPath).unixCmd} <> action, bufnum);
	}

}