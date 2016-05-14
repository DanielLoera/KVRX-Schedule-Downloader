import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.SocketException;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/*
 * Created by: Daniel Loera
 * Date: 5/13/16
 * 
 * ScheduleDownloader is a tool created to easily
 * download the entire KVRX Radio schedule and display them
 * at http://dannyloera.com/kvrx/shows.txt for easy viewing and
 * manipulation.
 * 
 * This schedule is crucial for the KVRX Radio android app which uses
 * all show information to display them on the interface.
 * 
 * This program is run weekly on a ubuntu server to ensure the app's
 * schedule is always up to date.
 * */

public class ScheduleDownloader {

	private static final int MAX_RETRIES = 20;
	private static int retryCount;

	private static final String KVRX_URL = "http://www.kvrx.org";

	private static FTPClient ftpClient;

	private static final String FTP_SERVER = "ftp.dannyloera.com";
	private static final int FTP_PORT = 21;
	private static final String FTP_LOGIN = "SECRET";
	private static final String FTP_PASSWORD = "SECRET";

	private static final String SHOW_FILE = "/home/daniel/Projects/KVRX/shows.txt";
	private static final String LOG_FILE = "/home/daniel/Projects/KVRX/downloader_log.txt";
	private static Logger log;

	// Main method to kickstart the entire process
	public static void main(String[] args) {
		// Initialize the Logger
		log = new Logger(LOG_FILE);
		log.println("KVRX Schedule Downloader v0.02\n");

		// Connect to FTP Server
		initializeFTPClient();
		// download all the data!
		downloadSchedule();
		// Finished. :)
		disconnectFTP();
	}

	// Initializes a fully functional FTP
	// Client defined by the class constants
	private static void initializeFTPClient() {
		log.println("Connecting to FTP server");
		ftpClient = new FTPClient();
		try {
			ftpClient.connect(FTP_SERVER, FTP_PORT);
			logFTPStatus("CONNECTION", ftpClient);
			ftpClient.login(FTP_LOGIN, FTP_PASSWORD);
			logFTPStatus("LOGIN", ftpClient);
			ftpClient.enterLocalPassiveMode();
		} catch (IOException e) {
			handleException(e);
		}

	}

	// Disconnect the FTP Client
	// and logout!
	private static void disconnectFTP() {
		try {
			ftpClient.logout();
			ftpClient.disconnect();
		} catch (IOException e) {
			handleException(e);
		}

	}

	// The heart of this program.
	// Parses through the HTML and stores each
	// KVRX show to save to a file later.
	private static void downloadSchedule() {

		log.println("Connecting to schedule page.");
		Document schedulePage = getDocumentFromString(KVRX_URL + "/schedule");

		// Gather up show information to initialize parsing

		// Select each individual show div for parsing
		// in a loop
		log.println("Gathering show information.");
		Elements daysContainer = schedulePage.select("#single-day-container");
		Elements showDivs = daysContainer.select(".field-content");
		ShowInfo[] shows = new ShowInfo[showDivs.size()];
		log.println("Found " + shows.length + " shows.");

		// Iterate through each show and create a ShowInfo object
		// for each.
		for (int i = 0; i < showDivs.size(); i++) {
			Element currentDiv = showDivs.get(i);
			ShowInfo temp = new ShowInfo();

			String link = currentDiv.select("a").attr("href");
			Document page = getDocumentFromString(KVRX_URL + link);

			String name = currentDiv.text();
			String time = page.select(".program-times").text();

			temp.setName(name);
			temp.setTime(time);
			temp.setId(link);
			log.println("Got Show #" + (i + 1) + " \"" + temp + "\"");

			shows[i] = temp;
		}

		log.println("Schedule Download Complete. :)");

		// Show download completed, send over all the data!
		File showFile = createShowFile(shows);
		sendFileToServer(showFile);
		sendFileToServer(log.getLogFile());

	}

	// Send a given file to FTP Server ftpClient
	// is connected with.
	private static void sendFileToServer(File fileToSend) {
		try {
			FileInputStream fis = new FileInputStream(fileToSend);
			String fileName = fileToSend.getName();
			ftpClient.storeFile(fileName, fis);
			fis.close();
			logFTPStatus("STORE FILE " + fileName, ftpClient);

		} catch (SocketException e) {
			handleException(e);
		} catch (IOException e) {
			handleException(e);
		}
	}

	// Used to easily debug and Log ftpClient codes and errors
	private static void logFTPStatus(String name, FTPClient ftp) {
		int code = ftp.getReplyCode();
		log.println(name + " Status: " + code + " Is Positive: " + FTPReply.isPositiveCompletion(code));
	}

	// Converts a list of Shows to an easily readable file.
	private static File createShowFile(ShowInfo[] shows) {
		File savedData = new File(SHOW_FILE);

		try {

			if (!savedData.exists()) {
				savedData.createNewFile();
			}

			PrintWriter pw = new PrintWriter(new FileWriter(savedData));

			for (ShowInfo show : shows) {
				pw.println(show.getId());
				pw.println(show.getName());
				pw.println(show.getTime());
			}
			pw.close();
		} catch (IOException e) {
			handleException(e);
		}
		return savedData;
	}

	// returns a JSoup Document with the given url
	private static Document getDocumentFromString(String url) {

		Document d = null;
		try {
			d = Jsoup.connect(url).get();
		} catch (java.net.SocketTimeoutException time) {
			// Retries a maximum of MAX_RETRIES amount of times
			// until an exception is thrown. :(
			if (retryCount < MAX_RETRIES) {
				retryCount++;
				log.println("Connection timed out on \"" + url + "\"!! :-( Try #" + retryCount + ".");
			} else {
				log.println("Max tries " + MAX_RETRIES + " exceeded.");
				handleException(time);
			}

			return getDocumentFromString(url);

		} catch (IOException e) {
			handleException(e);
		}

		// Gives the socket room to "Breathe"
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			handleException(e);
		}
		retryCount = 0;

		return d;
	}

	// Used to handle exceptions in case of disaster.
	// Logs the final exception (If the Log is available),
	// sends the file to the server and throws a final exception.
	private static void handleException(Exception e) {
		if (!log.finished()) {
			log.println("Download aborting due to:\n" + e.toString());
			sendFileToServer(log.getLogFile());
		}
		throw new IllegalStateException("Kvrx done goofed.");
	}

}
