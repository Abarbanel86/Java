//package exercise14;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.*;
import java.nio.channels.*;
import java.util.Iterator;

public class ChatServer implements AutoCloseable{
	private static final String EXIT = "EXIT";
	private static final int BUFFER_SIZE = 1024;
	private final ServerSocketChannel serverChannel = ServerSocketChannel.open(); 
	private final Selector selector = Selector.open();
	private final SelectionKey serverKey;
	private boolean ShouldRun = true;

	ChatServer(String listenAddress, int listenPortNum) throws NumberFormatException, IOException
	{
		serverChannel.configureBlocking(false);
		serverChannel.bind(new InetSocketAddress(listenAddress, listenPortNum));		
		serverKey = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
	}
	
	private void startChatServer() throws IOException {
		while (ShouldRun) {
			selector.select();
			Iterator<SelectionKey> iterKey = selector.selectedKeys().iterator();
			
			while(iterKey.hasNext()) {
				SelectionKey key = iterKey.next();
				if (!key.isValid()) {
					key.cancel();
				} else if(key.isAcceptable()) {
					addNewChannel();
				} else if(key.isReadable()) {
					sendMessage(key);
				}
				
				iterKey.remove();
			}}}
	
	private void sendMessage(SelectionKey senderKey) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		while(0 < ((SocketChannel)(senderKey.channel())).read(buffer)) {
			String msg = new String(buffer.array());
			int indexExit = msg.indexOf(":") + 2;
			String exit = msg.substring(indexExit , indexExit + EXIT.length());
			
			if(exit.equals(EXIT)) {
				senderKey.cancel();
			}else {
				buffer.flip();
				for(SelectionKey key : selector.keys()) {
					if((!key.equals(senderKey)) && (!key.equals(serverKey))){
						((SocketChannel)(key.channel())).write(buffer);
					}
				
					buffer.rewind();
				}
			}
			
			buffer.clear();
		}}

	private void addNewChannel() throws IOException {
		SocketChannel userChannel = serverChannel.accept();
		if(null == userChannel)
		{
			throw new ClosedChannelException();
		}
		
		userChannel.configureBlocking(false);
		userChannel.register(selector, SelectionKey.OP_READ);
		System.out.println("new user logged in");
	}

	@Override
	public void close() throws Exception {
		ShouldRun = false;		
	}
	
	public static void main(String[] args) throws Exception {
		try(ChatServer chatServer = new ChatServer(args[0], Integer.parseInt(args[1]));)
		{
			 chatServer.startChatServer();
		} 
		catch (NumberFormatException e) 
		{
			throw new IllegalArgumentException(e);
		} 
	}}

