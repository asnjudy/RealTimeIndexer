package rti.example;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SearchReq {

	public static void main(String[] args) {

		try {
			Socket socket = new Socket("localhost", 9091);

			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
			PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

			DateFormat fullFormat = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);        
	        String startS = "Wed Apr 11 16:18:40 +0800 2012";
	        String endS = "Wed Apr 11 16:18:42 +0800 2012";
	        Date date  = fullFormat.parse(startS);
	        long start = date.getTime();
			date = fullFormat.parse(endS);
			long end = date.getTime();
			
			// requests format: keywords&start&end&back_num
			// start, end and back_num may be -1, if they are not used.
			// encode: utf-8
			//String req = "name&-1&-1&200";
			String req = "ø’œ–&" + start + "&" + end + "&-1";
			out.write(req);
			out.write("\r\n");
			out.flush();
			
			// file to store search results
			File file = new File("result.json." + new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss").format(new Date()));
			if (!file.exists()) {
				file.createNewFile();
			}
			BufferedWriter bw = new BufferedWriter(new FileWriter(file));

			System.out.println("search results: ");
			
			String reply = null;
			while (!((reply = in.readLine()) == null)) {
				System.out.println(reply);
				bw.write(reply);
			}

			// cleanup
			out.close();
			in.close();
			bw.close();

			socket.close();

		} catch (UnknownHostException e) {
			System.out.println("create socket error");
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
