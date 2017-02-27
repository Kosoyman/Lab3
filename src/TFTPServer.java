import java.io.IOException;
import java.net.*;

public class TFTPServer
{
	private static final int TFTPPORT = 4970;
	private static final int BUFSIZE = 516;
	private static final String READDIR = "/TFTP//read/"; //custom address at your PC
	private static final String WRITEDIR = "/TFTP//write/"; //custom address at your PC
	// OP codes
	private static final int OP_RRQ = 1;
	private static final int OP_WRQ = 2;
	private static final int OP_DAT = 3;
	private static final int OP_ACK = 4;
	private static final int OP_ERR = 5;

	public static void main(String[] args) {
		if (args.length > 0)
		{
			System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}
		//Starting the server
		try
		{
			TFTPServer server= new TFTPServer();
			server.start();
		}
		catch (SocketException e)
		{e.printStackTrace();}
	}

	private void start() throws SocketException
	{
		byte[] buf= new byte[BUFSIZE];

		// Create socket
		DatagramSocket socket= new DatagramSocket(null);

		// Create local bind point 
		SocketAddress localBindPoint= new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

		// Loop to handle client requests 
		while (true)
		{

			final InetSocketAddress clientAddress = receiveFrom(socket, buf);

			// If clientAddress is null, an error occurred in receiveFrom()
			if (clientAddress == null)
				continue;

			final StringBuffer requestedFile= new StringBuffer();
			final int reqtype = ParseRQ(buf, requestedFile);

			new Thread()
			{
				public void run()
				{
					try
					{
						DatagramSocket sendSocket= new DatagramSocket(0);

						// Connect to client
						sendSocket.connect(clientAddress);

						System.out.printf("%s request for %s from %s using port %d\n",
								(reqtype == OP_RRQ)?"Read":"Write",
								clientAddress.getHostName(), clientAddress.getPort());

						// Read request
						if (reqtype == OP_RRQ)
						{
							requestedFile.insert(0, READDIR);
							HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
						}
						// Write request
						else
						{
							requestedFile.insert(0, WRITEDIR);
							HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ);
						}
						sendSocket.close();
					}
					catch (SocketException e)
					{e.printStackTrace();}
				}
			}.start();
		}
	}

	/**
	 * Reads the first block of data, i.e., the request for an action (read or write).
	 * @param socket (socket to read from)
	 * @param buf (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf)
	{
		// Create datagram packet
		DatagramPacket dp = new DatagramPacket(buf, buf.length);

		// Receive packet
		try {
			socket.receive(dp);
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Get client address and port from the packet

		String ip = dp.getAddress().toString();
		int port = dp.getPort();

		return new InetSocketAddress(ip, port);
	}

	/**
	 * Parses the request in buf to retrieve the type of request and requestedFile
	 *
	 * @param buf (received request)
	 * @param requestedFile (name of file to read/write)
	 * @return opcode (request type: RRQ or WRQ)
	 */
	private int ParseRQ(byte[] buf, StringBuffer requestedFile)
	{
		// See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents

		int opcode = buf[1], //opcode is 0th and 1st bytes, but 0th byte is always 0 (I THINK)
				position = 2; //filename starts right after opcode

		while (buf[position] != 0) //filename is followed by 1 byte of 0s
			requestedFile.append(buf[position]);

		return opcode;
	}

	/**
	 * Handles RRQ and WRQ requests 
	 *
	 * @param sendSocket (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @param opcode (RRQ or WRQ)
	 */
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode)
	{
		if(opcode == OP_RRQ)
		{
			// See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
			boolean result = send_DATA_receive_ACK(params);
		}
		else if (opcode == OP_WRQ)
		{
			boolean result = receive_DATA_send_ACK(params);
		}
		else
		{
			System.err.println("Invalid request. Sending an error packet.");
			// See "TFTP Formats" in TFTP specification for the ERROR packet contents
			send_ERR(params);
			return;
		}
	}

	/**
	 To be implemented
	 */
	private boolean send_DATA_receive_ACK(params)
	{return true;}

	private boolean receive_DATA_send_ACK(params)
	{return true;}

	private void send_ERR(params)
	{}

}



