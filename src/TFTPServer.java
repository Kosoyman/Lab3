import com.sun.media.sound.InvalidDataException;
import javax.naming.SizeLimitExceededException;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.zip.DataFormatException;

/**
 *  A simple multi-threaded TFTPServer that supports file-transfers in octet mode
 *  @author Maxim Kravchenko, mk223hm@student.lnu.se
 *  @author Peter Danielsson, pd222dj@student.lnu.se
 */

public class TFTPServer
{
    private static final int TFTPPORT = 4970;
    private static final int BUFSIZE = 516;
    private static final String READDIR = "TFTP/read/";
    private static final String WRITEDIR = "TFTP/write/";

    // OP codes
    private static final int OP_RRQ = 1;
    private static final int OP_WRQ = 2;
    private static final int OP_DAT = 3;
    private static final int OP_ACK = 4;
    private static final int OP_ERR = 5;

    // Constants related to retransmissions
    private static final int WAITING_LIMIT = 200; // Specifies how long we should wait for a ACK before re-transmitting
    private static final int MAXIMUM_RETRIES = 10; // Maximum re-transmitting tries

    // Constants related to error packets
    private static final int ERR_NOT_DEFINED = 0;
    private static final int ERR_FILE_NOT_FOUND = 1;
    private static final int ERR_ACCESS_VIOLATION = 2;
    private static final int ERR_DISK_FULL = 3;
    private static final int ERR_ILLEGAL_OPERATION = 4;
    private static final int ERR_UNKNOWN_TRANSFER_ID = 5;
    private static final int ERR_FILE_ALREADY_EXISTS = 6;
    private static final int ERR_NO_SUCH_USER = 7;

    // Corresponding error messages to the error codes above
    private static final String[] ERROR_MESSAGES = {"", "File not found.", "Access violation.", "Disk full or allocation exceeded.",
            "Illegal TFTP operation.", "Unknown transfer ID.", "File already exists.", "No such user."};

    // Constants related to size limit for write folder
    private static int BYTES_PER_KB = 1024;
    private static int KB_PER_MB = 1024;
    private static int WRITE_FOLDER_SIZE_LIMIT = 10 * KB_PER_MB * BYTES_PER_KB;

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

            System.out.printf("\n***** Request from %s using port %d *****\n", clientAddress.getHostName(), clientAddress.getPort());

            final StringBuffer requestedFile= new StringBuffer();
            StringBuffer mode = new StringBuffer(); //used for storing mode of the current request
            final int reqtype = ParseRQ(buf, requestedFile, mode);

            new Thread()
            {
                public void run()
                {
                    try
                    {
                        DatagramSocket sendSocket= new DatagramSocket(0);

                        // Connect to client
                        sendSocket.connect(clientAddress);


                        // Read request
                        if (reqtype == OP_RRQ)
                        {
                            //check if mode is ok
                            if (!mode.toString().equals("octet"))
                            {
                                System.out.println("Invalid mode of operation. Sending an error packet.");
                                send_ERR(sendSocket, ERR_NOT_DEFINED, "Invalid mode of operation. The only supported mode is octet.");
                            }
                            else
                            {
                                requestedFile.insert(0, READDIR);
                                HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
                                //send_ERR(sendSocket, ERR_DISK_FULL);
                            }
                        }
                        // Write request
                        else if (reqtype == OP_WRQ)
                        {
                            //check if mode is ok
                            if (!mode.toString().equals("octet"))
                            {
                                System.out.println("Invalid mode of operation. Sending an error packet.");
                                send_ERR(sendSocket, ERR_NOT_DEFINED, "Invalid mode of operation. The only supported mode is octet.");
                            }
                            else
                            {
                                requestedFile.insert(0, WRITEDIR);
                                HandleRQ(sendSocket, requestedFile.toString(), OP_WRQ);
                            }
                        }
                        // In case of Data or ACK received on a non-established connection
                        else if (reqtype == OP_ACK || reqtype == OP_DAT)
                        {
                            System.out.println("Unknown transfer ID. Sending an error packet.");
                            send_ERR(sendSocket, ERR_UNKNOWN_TRANSFER_ID);
                        }
                        // In case of error message, we don't send anything back.
                        else if (reqtype == OP_ERR)
                        {
                            // Debug
                            System.out.println("Error-package received from client with non-established connection. " +
                                    "Discarding packet and connection.");
                        }
                        // For all other requests, consider them as illegal
                        else
                        {
                            System.out.println("Invalid request from client. Sending an error packet.");
                            send_ERR(sendSocket, ERR_ILLEGAL_OPERATION);
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

        return new InetSocketAddress(dp.getAddress(), dp.getPort());
    }

    /**
     * Parses the request in buf to retrieve the type of request and requestedFile
     *
     * @param buf (received request)
     * @param requestedFile (name of file to read/write)
     * @param mode (mode of the request)
     * @return opcode (request type: RRQ or WRQ)
     */
    private int ParseRQ(byte[] buf, StringBuffer requestedFile, StringBuffer mode)
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

        readBytes ++; //"step over" 0 that signified the end of the filename
        int offset = readBytes; //save the offset for mode

        //mode is followed by 1 byte of 0s
        while (buf[readBytes] != 0)
            readBytes ++;

        //readBytes - offset give length of the mode; saving the mode in lower case for convenience
        mode.append(new String(buf, offset, readBytes - offset).toLowerCase());
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
            boolean result = receive_DATA_send_ACK(sendSocket, requestedFile);
            System.out.println("RECEIVED SUCCESSFULLY: " + result);
        }
    }

    /**
     * Received data from client and sends ACK-packets back.
     * @param socket socket used for client communication
     * @param requestedFile filename for specific file
     * @return true if file is received succesfully, false otherwise
     */
    private boolean send_DATA_receive_ACK(DatagramSocket socket, String requestedFile)
    {
        try {
            //form the packet to send
            int  filePointer = 0,
                    packetPointer,
                    port = socket.getPort(),
                    bytesLeft; //amount of bytes left to read from file
            InetAddress ip = socket.getInetAddress();

            //
            byte[] file = Files.readAllBytes(Paths.get(requestedFile).normalize()),
                    packet,
                    block = new byte[2];
            DatagramPacket sendPacket;
            ByteBuffer wrap= ByteBuffer.wrap(block);
            short blockNumber = wrap.getShort();

            //if the last portion of data is exactly 512 bytes, we will need to send an empty packet to ensure  proper connection termination
            boolean needEmpty = false;
            while (filePointer < file.length || needEmpty) {

                bytesLeft = file.length - filePointer;

                //check if the package is the final one
                if(bytesLeft < 512)
                    packet = new byte[bytesLeft + 4]; //+4 for the header

                    //check if the packet is the final one AND it's exactly 512 bytes ling
                else if (bytesLeft == 512){
                    packet = new byte[516];
                    needEmpty = true;
                }

                else
                    packet = new byte [516]; //otherwise set size to max

                //set opcode
                packet[0] = 0;
                packet[1] = OP_DAT;

                //set block number
                blockNumber++;
                packet[2] = (byte)((blockNumber >> 8) & 0xff);
                packet[3] = (byte)(blockNumber & 0xff);

                //copy as much as possible from file into the packet
                if (filePointer < file.length) {
                    for (packetPointer = 4; packetPointer < packet.length; packetPointer++) {
                        packet[packetPointer] = file[filePointer];
                        filePointer++;
                    }
                }
                //if this packet must be empty, disable the flag so we don't get stuck in infinite loop
                else
                    needEmpty = false;

                sendPacket = new DatagramPacket(packet, packet.length, ip, port);
                socket.send(sendPacket); //send

                // Counter keeping track of retransmission tries
                int reTransmitCounter = 0;

                boolean correctBn = false;
                boolean maxRetries = false;

                // Do retransmissions as needed.
                while (!correctBn && !maxRetries)
                {
                    try
                    {
                        short bn = receive_ACK(socket);

                        if (bn == blockNumber)
                        {
                            correctBn = true;
                        }
                        else
                        {
                            System.out.println("INCORRECT ACK NUMBER RECEIVED.");
                        }
                    }
                    // In case of error-packet from client, we stop transmitting immediately. In all other cases, we try
                    // Retransmission
                    catch (InterruptedException e)
                    {
                        throw new InterruptedException(e.getMessage());
                    }
                    catch (Exception e)
                    {
                        // In case of any problems with receiving ACK, print exception message for debugging purposes
                        System.out.println(e.getMessage());
                    }

                    if (!correctBn)
                    {
                        // Check if max retries has been reached
                        if (reTransmitCounter == MAXIMUM_RETRIES)
                        {
                            maxRetries = true;
                        }
                        else
                        {
                            // Re-transmit
                            reTransmitCounter++;
                            System.out.println("RETRANSMITTING BLOCK: " + blockNumber);
                            socket.send(sendPacket); //send
                        }
                    }
                }

                // Check if we have failed all transmissions
                if (!correctBn)
                {
                    // Send Error-packet before terminating
                    send_ERR(socket, ERR_NOT_DEFINED, "Maximum number of retransmissions reached.");

                    // For debugging purposes
                    System.out.println("Maximum number of retransmissions reached. Giving up, closing connection.");

                    return false;
                }

            }
        }
        catch (InterruptedException e)
        {
            // In case of error-packet received from client in the middle of the transfer
            System.out.println(e.getMessage());
            return false;
        }
        catch (NoSuchFileException e)
        {
            System.out.println("File not found on server!");
            send_ERR(socket, ERR_FILE_NOT_FOUND);
            return false;
        }
        catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Help-method to extract a block-number from a ACK-packet. Use this method when sending packets and an ACK is expected.
     * @param socket socket used for client communication
     * @return blocknumber
     * @throws IOException in case of incorrect package type, package timeout or IO-error
     * @throws InterruptedException if the packet is an error-message
     */
    private short receive_ACK(DatagramSocket socket) throws IOException, InterruptedException {

        byte[] ACKbuf = new byte[4]; //ACK packet is 4 bytes long (RFC1350)
        DatagramPacket receivePacket = new DatagramPacket(ACKbuf, ACKbuf.length);
        try {

            // Set timeout limit so we don't wait until forever.
            socket.setSoTimeout(WAITING_LIMIT);
            socket.receive(receivePacket);

            byte[] ACK = receivePacket.getData();
            ByteBuffer wrap= ByteBuffer.wrap(ACK);

            short opcode = wrap.getShort(), //parse opcode
                    blockNumber = wrap.getShort(); //parse block number

            if(opcode == OP_ACK) {
                return blockNumber;
            }
            else if (opcode == OP_ERR)
            {
                throw new InterruptedException("ERROR-MESSAGE RECEIVED FROM CLIENT, CLOSING CONNECTION.");
            }
            else
            {
                throw new InvalidDataException("RECEIVED PACKET NOT OF TYPE ACK");
            }

        }
        catch (SocketTimeoutException e) {
            throw new SocketTimeoutException("NO ACK RECEIVED WITHIN REASONABLE TIME");
        }
        catch (IOException e) {
            throw new IOException("CONNECTION PROBLEM");
        }

    }

    /**
     * Sends ACK to establish "connection", receives packets and sends ACKs
     * @param socket - Datagram socket
     * @param requestedFile - name of the file that will be saved
     * @return - returns false if IOException is thrown, otherwise returns true
     */
    private boolean receive_DATA_send_ACK(DatagramSocket socket, String requestedFile){

        byte[] fileBuf = new byte[512], //temporary storage for the file bytes
                file, //full file bytes
                packet, //packet array
                temp, //temporary array, used for increasing the size of fileBuf
                ACK = new byte[4]; //ACKnowledgement array
        short currentBN = 0, //block number of the last received packet
                incomingBN; //block number of the incoming packet
        int totalBytes = 0; //total amount of bytes read

        //send an acknowledgement to establish connection

        //set opcode
        ACK[0] = 0;
        ACK[1] = OP_ACK;

        //set block number
        ACK[2] = (byte)((currentBN >> 8) & 0xff);
        ACK[3] = (byte)(currentBN & 0xff);

        DatagramPacket receivePacket = null,
                ackPacket = new DatagramPacket(ACK, ACK.length, socket.getInetAddress(), socket.getPort());

        try {

            Path testFilePath = Paths.get(requestedFile).normalize();

            // First make user user has provided only a filename without directory structure
            if (Files.exists(testFilePath))
            {
                throw new FileAlreadyExistsException("File already exists!");
            }
            else
            {
                // Try to create a file to see that it's possible to write to path.
                // Will throw Exception if failing, which we catch later
                Files.createFile(testFilePath);

                // If we could create the file we should delete it before moving on
                Files.delete(testFilePath);

            }

            boolean transmissionComplete = false;
            boolean done = false;

            // Keep receiving and sending ACKs until we are done or until connection problems
            while (!done && !transmissionComplete)
            {
                try {

                    boolean correctDataBlock = false;
                    int reTransmitCounter = 0;

                    // Keep going until correct datablock has been received or we run out of retransmission retries
                    while (!correctDataBlock && reTransmitCounter <= MAXIMUM_RETRIES)
                    {
                        try
                        {
                            socket.send(ackPacket); //send ACK packet

                            //receive packet
                            packet = new byte[516]; //reset the packet array
                            receivePacket = new DatagramPacket(packet, packet.length);
                            socket.setSoTimeout(WAITING_LIMIT); //set timeout
                            socket.receive(receivePacket);

                            //process received packet
                            packet = receivePacket.getData();
                            ByteBuffer wrap= ByteBuffer.wrap(packet);
                            short opCode = wrap.getShort();
                            incomingBN = wrap.getShort();

                            if (opCode == OP_DAT && incomingBN == currentBN + 1) { //check if the bn is ok and that the packet is not empty
                                currentBN = incomingBN;

                                //copy the contents of the packet into fileBuf
                                for (int i = 4; i < receivePacket.getLength(); i++) {
                                    fileBuf[totalBytes] = packet[i];
                                    totalBytes++;
                                }

                                //increase the size of filBuf by 512, so next packet data will fit
                                temp = new byte[fileBuf.length];
                                System.arraycopy(fileBuf, 0, temp, 0, fileBuf.length);
                                fileBuf = new byte[totalBytes + 512];
                                System.arraycopy(temp, 0, fileBuf, 0, totalBytes);

                                //set opcode
                                ACK[0] = 0;
                                ACK[1] = OP_ACK;

                                //set block number
                                ACK[2] = (byte) ((currentBN >> 8) & 0xff);
                                ACK[3] = (byte) (currentBN & 0xff);

                                // Create ACK-packet
                                ackPacket = new DatagramPacket(ACK, ACK.length, socket.getInetAddress(), socket.getPort());

                                correctDataBlock = true;
                            }
                            else
                            {
                                // If the packet is an error-message, we stop executing. Other packet-types are simply discarded
                                if (opCode == OP_ERR)
                                {
                                    //System.err.println("ERROR IN THE MIDDLE OF THE TRANSFER");
                                    throw new InterruptedException("Client sent an error-packet in the middle of transmission, closing connection.");
                                }
                                reTransmitCounter++;
                                System.out.println("Incorrect Data-block (or wrong packet type) received, resending ACK.");
                            }
                        }
                        catch (SocketTimeoutException e)
                        {
                            reTransmitCounter++;
                            if(reTransmitCounter <= MAXIMUM_RETRIES)
                            {
                                System.out.println("No new DATA-packet received, resending ACK.");
                            }
                        }

                    }

                    // If we never got the correct data block, it means we ran out of retries.
                    if (!correctDataBlock)
                    {
                        done = true;
                    }
                    // Check if received packet was the last one.
                    else if (receivePacket.getLength() < 516)
                    {
                        // No dallying atm
                        // Make sure we have enough space left in write-folder before sending final ACK.
                        if (!hasEnoughSpace(totalBytes))
                        {
                            throw new SizeLimitExceededException("Not enough disk space for storing file!");
                        }

                        socket.send(ackPacket); //send ACK packet
                        transmissionComplete = true;
                    }

                }
                catch (IOException e) {
                    System.out.println("Connection problems, aborting.");
                    return false;
                }
            }

            // If we never received all the data from the client, we stop executing.
            if (!transmissionComplete)
            {
                throw new DataFormatException("Maximum number of retransmission reached. Aborting.");
            }

            //save file
            FileOutputStream fos;

            //get bytes from fileBuf into file array
            file = new byte[totalBytes];
            System.arraycopy(fileBuf,0, file, 0, totalBytes);

            fos = new FileOutputStream(requestedFile);
            fos.write(file);
            fos.close();

        }
        catch (InterruptedException e)
        {
            // Debug
            System.out.println(e.getMessage());
            return false;
        }
        catch (DataFormatException e)
        {
            // Debug
            System.out.println(e.getMessage());
            send_ERR(socket, ERR_NOT_DEFINED, "Retransmission limit exceeded, closing connection.");
            return false;
        }
        catch (SizeLimitExceededException e)
        {
            // Debug
            System.out.println(e.getMessage());

            send_ERR(socket, ERR_DISK_FULL);
            return false;
        }
        catch (FileAlreadyExistsException e)
        {
            // Debug
            System.out.println(e.getMessage());

            send_ERR(socket, ERR_FILE_ALREADY_EXISTS);
            return false;
        }
        catch (NoSuchFileException e)
        {
            // Debug
            System.out.println("User specified an invalid path along with the filename. sending error message");

            send_ERR(socket, ERR_ACCESS_VIOLATION);
            return false;
        }
        catch (IOException e) {

            e.printStackTrace();

            // Sending "No such user"-error, as described in Assignment questions.
            // https://mymoodle.lnu.se/mod/forum/discuss.php?d=917218
            send_ERR(socket, ERR_NO_SUCH_USER);
            return false;
        }

        return true;
    }

    /**
     * Sends an error-message to receiver
     * @param socket client connection socket
     * @param errorCode Error code (0-7 supported)
     * @return true if error message is sent, false otherwise
     */
    private boolean send_ERR(DatagramSocket socket, int errorCode)
    {
        return send_ERR(socket, errorCode, ERROR_MESSAGES[errorCode]);
    }

    /**
     * Sends an error-message to receiver
     * @param socket client connection socket
     * @param errorCode Error code (0-7 supported)
     * @param message Error message
     * @return true if error message is sent, false otherwise
     */
    private boolean send_ERR(DatagramSocket socket, int errorCode, String message) {

        byte[] mess = message.getBytes();

        // + 5 so we got space for Opcode, error-code and terminating byte
        byte[] buf = new byte[mess.length + 5];

        // Set opcode
        buf[0] = 0;
        buf[1] = OP_ERR;

        // Set error-code
        buf[2] = 0;
        buf[3] = (byte) errorCode;

        // Copy message contents to buffer
        System.arraycopy(mess, 0, buf, 4, mess.length);

        // Set terminating byte in the end
        buf[buf.length -1] = 0;

        // Create datapacket and send message
        DatagramPacket errorPacket = new DatagramPacket(buf, buf.length, socket.getInetAddress(), socket.getPort());

        try
        {
            socket.send(errorPacket);
        }
        catch (IOException e)
        {
            return false;
        }

        return true;
    }

    /**
     * Checks if there's enough storage in write-folder for storing a file
     * @param fileSize Size of file
     * @return true if enough space, false otherwise
     */
    private boolean hasEnoughSpace(long fileSize) throws IOException
    {
        return (getFolderSize(WRITEDIR) + fileSize) <= WRITE_FOLDER_SIZE_LIMIT;
    }

    /**
     * Calculates the size of a directory by traversing the directory-structure and checking the size of each file
     * @param filePath Directory path
     * @return total size of directory in bytes
     */
    private long getFolderSize(String filePath)
    {
        // Source/Inspiration for codeblock below: http://www.baeldung.com/java-folder-size
        File dir = new File(filePath);

        File[] filesinDir = dir.listFiles();

        long directorySize = 0;

        // traverse through dir structure
        if (filesinDir != null) {
            for (File currentFile : filesinDir) {
                if (currentFile.isFile()) {
                    directorySize += currentFile.length();
                } else {
                    // Recursive call for subdirectories
                    directorySize += getFolderSize(currentFile.getPath());
                }
            }
        }
        return directorySize;
    }
}



