import java.io.IOException;
import java.net.*;

/**
 * Class that can be used to diagnose replies/error messages from a TFTP-server and test it's overall stability
 * See instructions down in the main-method
 * @author Peter Danielsson, pd222dj@student.lnu.se
 */
public class RogueClient
{

    private static final int CLIENT_PORT = 0;
    private static final String REMOTE_IP = "localhost";
    private static final int REMOTE_PORT = 4970;
    private static final int CONNECTION_TIMEOUT = 10 * 1000;

    private static final String TEST_FILE = "kappa.png";
    private static final String TEST_MODE = "octet";

    // OP codes
    private static final int OP_RRQ = 1;
    private static final int OP_WRQ = 2;
    private static final int OP_DAT = 3;
    private static final int OP_ACK = 4;
    private static final int OP_ERR = 5;

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
    public static final String[] ERROR_MESSAGES = {"", "File not found.", "Access violation.", "Disk full or allocation exceeded.",
            "Illegal TFTP operation.", "Unknown transfer ID.", "File already exists.", "No such user."};

    // Illegal values
    static final int ILLEGAL_OP_CODE = 7;

    public static void main(String[] args) throws IOException {

        DatagramSocket socket = new DatagramSocket(null);
        SocketAddress localBindPoint = new InetSocketAddress(CLIENT_PORT);
        socket.bind(localBindPoint);

        socket.setSoTimeout(CONNECTION_TIMEOUT);

        /*
            _INSTRUCTIONS_

            Easy as one-two-three.
            1. Send a packet, using one of the methods where the name begins with "send"
            2. Grab the server reply with the receiveServerResponse (String)
            3. (Print out the server reply if you want)

         */

        //sendIllegalOpCodeRequest(socket, ILLEGAL_OP_CODE);

        //sendDataPacket(socket);

        //sendACKPacket(socket, 1);

        //sendErrorPacket(socket, ERR_ACCESS_VIOLATION, ERROR_MESSAGES[ERR_ACCESS_VIOLATION]);

        //sendReadRequest(socket, "kappa2.png");

        sendWriteRequest(socket, "kappa2.png");

        try
        {
            System.out.println("Message received from server: ");

            // Using loop here for testing the retransmissions from
            // the server and that we get an error-message at the end.
            for (int i=0; i < 12; i++)
            {
                System.out.println(receiveServerResponse(socket));
            }

        }
        catch (SocketTimeoutException e)
        {
            System.out.println("No reply from server within reasonable time, closing connection");
        }

    }

    /**
     * Grabs the next packet available and returns the packet contents as a string
     * @param socket
     * @return
     * @throws IOException
     */
    private static String receiveServerResponse(DatagramSocket socket) throws IOException
    {
        byte[] buf = new byte[516];
        DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
        socket.receive(receivePacket);

        // Grab Op-code and block/error code
        String message = "" + buf[0] + buf[1] + buf[2] + buf[3];

        // Grab rest of contents, if any
        message += new String(buf, 4, receivePacket.getLength() - 4, "US-ASCII");

        return message;
    }

    /**
     * Send an error-package
     * @param socket
     * @param errorCode
     * @param message
     * @throws IOException
     */
    private static void sendErrorPacket(DatagramSocket socket, int errorCode, String message) throws IOException
    {
        byte[] messageData = message.getBytes();

        byte[] buf = new byte[5 + messageData.length];

        // Set opcode
        buf[0] = 0;
        buf[1] = OP_ERR;

        // Set error code
        buf[2] = 0;
        buf[3] = (byte) errorCode;

        int indexPointer = 4;

        // copy Filename to buffer
        for (int i=0; i < messageData.length; i++)
        {
            buf[indexPointer++] = messageData[i];
        }

        buf[indexPointer] = 0;

        SocketAddress remoteBindPoint = new InetSocketAddress(REMOTE_IP, REMOTE_PORT);
        DatagramPacket sendPacket = new DatagramPacket(buf, buf.length, remoteBindPoint);

        // debug
        //System.out.println("Packet contents: " + buf[0] + buf[1] + buf[2] + buf[3] + new String(buf, 4, buf.length - 4, "US-ASCII"));

        socket.send(sendPacket);
    }

    /**
     * Send an ACK-packet
     * @param socket
     * @param blockNumber block number
     * @throws IOException
     */
    private static void sendACKPacket(DatagramSocket socket, int blockNumber) throws IOException {

        byte[] buf = new byte[4];

        // Set opcode
        buf[0] = 0;
        buf[1] = OP_ACK;

        // Set block number
        buf[2] = 0;
        buf[3] = (byte) blockNumber;

        SocketAddress remoteBindPoint = new InetSocketAddress(REMOTE_IP, REMOTE_PORT);
        DatagramPacket sendPacket = new DatagramPacket(buf, buf.length, remoteBindPoint);

        // debug
        //System.out.println("Packet contents: " + buf[0] + buf[1] + buf[2] + buf[3]);

        socket.send(sendPacket);
    }

    /**
     * Sends an datap acket to the server
     *
     * @param socket
     * @throws IOException
     */
    private static void sendDataPacket(DatagramSocket socket) throws IOException
    {
        byte[] testData = "Keke".getBytes();

        byte[] buf = new byte[4 + testData.length];

        // Set opcode
        buf[0] = 0;
        buf[1] = OP_DAT;

        // Set block number
        buf[2] = 0;
        buf[3] = 1;

        int indexPointer = 4;

        // copy Filename to buffer
        for (int i=0; i < testData.length; i++)
        {
            buf[indexPointer++] = testData[i];
        }

        SocketAddress remoteBindPoint = new InetSocketAddress(REMOTE_IP, REMOTE_PORT);
        DatagramPacket sendPacket = new DatagramPacket(buf, buf.length, remoteBindPoint);

        // debug
        //System.out.println("Packet contents: " + buf[0] + buf[1] + buf[2] + buf[3] + new String(buf, 4, testData.length, "US-ASCII"));

        socket.send(sendPacket);
    }

    /**
     * Send a Write-request to the server without any successive data
     * @param socket
     * @param fileName
     */
    private static void sendWriteRequest(DatagramSocket socket, String fileName) throws IOException
    {
        sendRequestPacket(socket, OP_WRQ, fileName, TEST_MODE);
    }

    /**
     * Send a read-request to the server
     * @param socket
     * @param fileName
     * @throws IOException
     */
    private static void sendReadRequest(DatagramSocket socket, String fileName) throws IOException
    {
        sendRequestPacket(socket, OP_RRQ, fileName, TEST_MODE);
    }

    /**
     * Use this method to send an illegal Op-code request
     * @param socket
     * @throws IOException
     */
    private static void sendIllegalOpCodeRequest(DatagramSocket socket, int opCode) throws IOException
    {
        sendRequestPacket(socket, opCode, TEST_FILE, TEST_MODE);
    }

    /**
     * General help-method to construct a Request-package
     * @param socket
     * @param opcode
     * @param filename
     * @param mode
     * @throws IOException
     */
    private static void sendRequestPacket(DatagramSocket socket, int opcode, String filename, String mode) throws IOException {

        byte[] buf = new byte[4 + filename.getBytes().length + mode.getBytes().length];

        // Set an unsupported Opcode
        buf[0] = 0;
        buf[1] = (byte) opcode;

        int indexPointer = 2;

        byte[] fileNameToBytes = filename.getBytes();

        // copy Filename to buffer
        for (int i=0; i < fileNameToBytes.length; i++)
        {
            buf[indexPointer++] = fileNameToBytes[i];
        }

        buf[indexPointer++] = 0;

        byte[] modeToBytes = mode.getBytes();

        // copy Filename to buffer
        for (int i=0; i < modeToBytes.length; i++)
        {
            buf[indexPointer++] = modeToBytes[i];
        }

        buf[indexPointer] = 0;

        // Debug (will include the terminating zeroes in the string, but they are not visible)
        //System.out.println("Packet contents: " + buf[0] + buf[1] + new String(buf, 2, buf.length - 2, "US-ASCII"));

        SocketAddress remoteBindPoint = new InetSocketAddress(REMOTE_IP, REMOTE_PORT);
        DatagramPacket sendPacket = new DatagramPacket(buf, buf.length, remoteBindPoint);

        socket.send(sendPacket);
    }
}
