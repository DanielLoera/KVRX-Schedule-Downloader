import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Logger is a specialized PrintWriter that logs everything into a file while
 * also displaying it onto the console.
 */

public class Logger {

	private PrintWriter logWriter;
	private File log;
	private boolean finished;

	// default and only constructor
	// used to create the initial
	// PrintWritter
	public Logger(String logFile) {
		log = new File(logFile);

		try {
			if (!log.exists())
				log.createNewFile();
			logWriter = new PrintWriter(new FileWriter(log));

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// print data onto the file
	// and console
	public void print(String data) {
		logWriter.print(data);
		System.out.print(data);
	}

	// print data onto the file
	// and the console with a \n at
	// the end
	public void println(String data) {
		logWriter.println(data);
		System.out.println(data);
	}

	// Close the Log
	// and return the File
	// it was attached to.
	public File getLogFile() {
		finished = true;
		logWriter.close();
		return log;
	}
	
	//returns whether or not
	//the log has been closed.
	public boolean finished(){
		return finished;
	}


}
