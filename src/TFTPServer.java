import javax.xml.crypto.Data;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TFTPServer
{
	private static final int TFTPPORT = 4970;
	private static final int BUFSIZE = 516;
	private static final String READDIR = "TFTP\\read\\";
	private static final String WRITEDIR = "TFTP\\read\\"; //custom address at your PC

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
/*
						System.out.printf("%s request for %s from %s using port %d\n",
								(reqtype == OP_RRQ)?"Read":"Write",
								clientAddress.getHostName(), clientAddress.getPort());
*/
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

		int port = dp.getPort();

		return new InetSocketAddress(dp.getAddress(), port);
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

		ByteBuffer wrap= ByteBuffer.wrap(buf);
		short opcode = wrap.getShort();

		// We can now parse the request message for opcode and requested file as:

		int readBytes = 2;// where readBytes is the number of bytes read into the byte array buf.

		//filename is followed by 1 byte of 0s
		while (buf[readBytes] != 0)
			readBytes ++;

		String fileName = new String(buf, 2, readBytes-2);  //converts readBytes to the length of the filename

		requestedFile.append(fileName); //Store the filename in the StringBuffer

		//parse transfer mode, we are supposed to do that but I am not sure what to use it for atm

		int offset = readBytes; //save the offset for mode
		readBytes ++; //"step over" 0 that signified the end of the filename

		//mode is followed by 1 byte of 0s
		while (buf[readBytes] != 0)
			readBytes ++;

		//readBytes - offset give length of the mode; saving the mode in lower case for convenience
		String mode = new String(buf, offset, readBytes - offset).toLowerCase();
		System.out.println("OPCODE: " + opcode);
		System.out.println("FILENAME: " + fileName);
		System.out.println("MODE: " + mode);
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
			boolean result = send_DATA_receive_ACK(sendSocket, requestedFile);
			System.out.println("SENT SUCCESSFULLY: " + result);
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
	//TODO: right now works for files of size 508 or less bytes
	private boolean send_DATA_receive_ACK(DatagramSocket socket, String requestedFile)
	{
		try {
			//form the packet to send

			byte[] packet = new byte[512]; //right now has max length, we might want to change that
			//set opcode
			packet[0] = 0;
			packet[1] = 3;

			//set block number
			packet[2] = 0;
			packet[3] = 1;

			byte[] file = Files.readAllBytes(Paths.get(requestedFile).normalize()); //read the file into byte array

			//set data
			System.arraycopy(file, 0, packet, 4, file.length);

			DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, socket.getInetAddress(), socket.getPort());

			socket.send(sendPacket);//send

			//receving acknowledgement for packet
			byte[] ACKbuf = new byte[4]; //ACK packet is 4 bytes long (RFC1350)
			DatagramPacket receivePacket = new DatagramPacket(ACKbuf, ACKbuf.length);
			socket.receive(receivePacket);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	private boolean receive_DATA_send_ACK(params)
	{return true;}

	private void send_ERR(params)
	{}
}



