package rti.example;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class IndexReq {

	public static void main(String[] args) {
		
		try {
			Socket socket = new Socket("localhost", 9081);
			
			OutputStream os = socket.getOutputStream();
            PrintWriter pw = new PrintWriter(os);

            long start = System.currentTimeMillis();
            long end = start;
            int interval = 5 * 60;
            // reqs format: 1&path&start&end&interval(s)
            String req = "1&E:\\test\\weibo\\1334132368160_statuses.json&" + start + "&" + end + "&" + interval + "\n";
            
            pw.write(req);
            pw.flush();
            
            // cleanup
            pw.close();
            os.close();
            socket.close();
            
		} catch (UnknownHostException e) {
			System.out.println("create socket error");
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
