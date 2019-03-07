package exercise14;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient implements AutoCloseable {
	private final Socket clientSocket;
	private final PrintWriter chatOut;
	private final BufferedReader chatIn ;
	private final BufferedReader stdIn;
	private final Thread stdInThread;
	private final String exit = "exit";

	ChatClient(String clientName, String hostName, int portNum) throws IOException {
		clientSocket = new Socket(hostName, portNum);
		chatOut = new PrintWriter(clientSocket.getOutputStream(), true);
		chatIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		stdIn = new BufferedReader(new InputStreamReader(System.in));
		
		stdInThread =  new Thread( ()-> {	
	        while (true) {
	        	try {
					String msg = stdIn.readLine();
		        	chatOut.println("<" + clientName + ": " + msg + ">");
		          	if (exit.equals(msg)){
		          		chatOut.close();
		          		chatIn.close();
		        		break;
		          	}
		        	chatOut.flush();
	        	} catch (IOException e) {
					e.printStackTrace();
				} catch (Exception e) {}
	        }
		});
	}
    	
	public void startChat() throws InterruptedException{
		stdInThread.start();
		
		String msg;
		try {
			while(null != (msg = chatIn.readLine())){
				System.out.println(msg);
			}
		} catch (IOException e) {}
	}	

	@Override
	public void close() throws Exception {
		System.out.println("The chat ended");
		clientSocket.close();
		stdIn.close();
		stdInThread.join();
	}
	
	public static void main(String[] args) throws NumberFormatException, Exception {
		try (ChatClient chatClient = new ChatClient(args[0], args[1], Integer.parseInt(args[2]));) {
			chatClient.startChat();
		}}}

