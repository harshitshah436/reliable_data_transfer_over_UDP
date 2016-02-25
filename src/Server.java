/* 
 * Server.java
 * 
 * CSCI 651: Foundations of Computer Networks, Project 2. To implement RDT
 * protocol using UDP datagrams and TCP tahoe like congestion control.
 */

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Server class receives file in form of packets and send acknowledgement for
 * each packets.
 * 
 * @author Harshit
 */
public class Server {

    // Initialize all instance and class variables

    float p = 0f; // packet loss probability (To drop a packet manually for
    // testing) between 0 and 1

    DatagramSocket server_socket;
    InetAddress client_ip;
    int client_port;
    int port;

    String filename, initial_client_file_hash;
    File output_file = null;

    boolean resend_packet = false;
    boolean startflag = true;
    boolean quiet = false;

    static long old_seq_num = 0;
    static long ack_num = 0;

    /**
     * Constructor to assign port and quiet flag.
     * 
     * @param port
     * @param quiet
     */
    public Server(int port, boolean quiet) {
        this.port = port;
        this.quiet = quiet;
    }

    /**
     * Set up server and start receiving file from client.
     */
    public void start() {
        try {
            server_socket = new DatagramSocket(port);
        } catch (SocketException e) {
            System.err
                    .println("Could not start server socket on port: " + port);
            System.exit(410);
        }

        filename = "csci651_proj2_2M_new.bin";

        System.out
                .println("New file will be received in the current working directory with Name: "
                        + filename);

        output_file = new File(filename);

        // Start receive packet thread.
        Thread receive_pckt = new Thread(new ReceivePacket());
        receive_pckt.start();
    }

    /**
     * Class to send acknowledgements after correctly receiving packets.
     * 
     * @author Harshit
     */
    class SendAckPacket implements Runnable {
        byte send_buff[] = new byte[700];

        // Initial Ack and checksum fields
        String ack_indicator = "1010101010101010";
        String chksum_field = "0000000000000000";

        public synchronized void run() {
            try {
                if (resend_packet == false)
                    ack_num += 1;

                String getAckHeader = makeHeader(ack_num, chksum_field,
                        ack_indicator);
                byte[] header = getAckHeader.getBytes();
                DatagramPacket packet_sent = new DatagramPacket(header,
                        header.length, client_ip, client_port);
                server_socket.send(packet_sent);
            }

            catch (Exception e) {
                System.out.println(e);
            }
        }

        /**
         * Make acknowledgement header adding ack no, checksum and ack
         * indicator.
         * 
         * @param ack
         * @param checksum
         * @param ack_field
         * @return final header
         */
        public String makeHeader(long ack, String checksum, String ack_field) {
            String binaryAck = Long.toBinaryString(ack);
            binaryAck = "00000000000000000000000000000000".substring(0,
                    (32 - (binaryAck.length()))) + binaryAck;

            String AckPacket = binaryAck + checksum + ack_field;

            return AckPacket;
        }
    }

    /**
     * This class receive packets and call send ack class. Even this will take
     * care of out of order packets.
     * 
     * @author Harshit
     */
    class ReceivePacket implements Runnable {
        // create temporary byte array to receive packets.
        byte recv_buff[] = new byte[70000];

        public synchronized void run() {
            FileOutputStream fos = null;
            try {

                // Generate output file stream.
                fos = new FileOutputStream(output_file);
                boolean file_received = false;
                while (!file_received) {
                    DatagramPacket packet = new DatagramPacket(recv_buff,
                            recv_buff.length);
                    server_socket.receive(packet);
                    client_ip = packet.getAddress();
                    client_port = packet.getPort();

                    // Generate a random float number.
                    Random r = new Random();
                    float random = r.nextFloat();

                    byte[] readData = packet.getData();
                    String readDataString = new String(readData);

                    String actualDataString = readDataString.substring(0,
                            packet.getLength());

                    // Divide into 16 bits (2 bytes) of data.
                    String[] binaryData = Utility
                            .divideInto16bits(actualDataString);

                    String seq_num = "";
                    long seq_int;
                    try {
                        seq_num = binaryData[0] + binaryData[1];
                        seq_int = Long.parseLong(seq_num, 2);
                    } catch (ArrayIndexOutOfBoundsException
                            | NumberFormatException e) {
                        // Get the client's file hash as the last packet.
                        initial_client_file_hash = actualDataString;
                        file_received = true;
                        continue;
                    }

                    String checksum = "";

                    // Manually timeout for testing
                    if (random > p) {

                        // Checksume should be '0' for correctly received
                        // packet.
                        checksum = Utility.calculateChecksum(binaryData);
                        long result = Integer.valueOf(checksum, 2);

                        String stringData = getStringDataFromBinaryData(actualDataString);
                        boolean error = false;

                        // Print into table format.
                        if (seq_int == 1 && startflag && !quiet) {
                            System.out.println("Receiving Packets:\n");
                            System.out
                                    .println("Packet No."
                                            + "  |  "
                                            + String.format("%32s",
                                                    "Sequence No.") + "  |  "
                                            + "Priviously Acknoledged Packet");
                            System.out
                                    .println("-----------------------------------------"
                                            + "----------------------------------------");
                            startflag = false;
                        }

                        if (result == 0) {
                            error = false;
                        } else {
                            System.out.println("Packet received WITH ERRORS!");
                            error = true;
                        }

                        // Check if packets are received in order.
                        if (error == false) {
                            if (seq_int == 0)
                                System.out.println("Sequence no 1!");
                            else if (seq_int == (old_seq_num + 1)) {
                                // Decoding to write contents of a binary data
                                // file (.bin)
                                byte[] byteString = Base64.getDecoder().decode(
                                        stringData);
                                fos.write(byteString);
                                resend_packet = false;

                                if (!quiet)
                                    if (ack_num == 0) {
                                        System.out.println(String.format(
                                                "%10s", seq_int)
                                                + "  |  "
                                                + seq_num + "  |  " + "None");
                                    } else {
                                        System.out.println(String.format(
                                                "%10s", seq_int)
                                                + "  |  "
                                                + seq_num + "  |  " + ack_num);
                                    }

                                Thread send = new Thread(new SendAckPacket());
                                send.start();
                                send.join();
                                old_seq_num = seq_int;
                            } else {
                                System.out
                                        .println("Resending ack. Packet out of order!");
                                resend_packet = true;
                                Thread send = new Thread(new SendAckPacket());
                                send.start();
                                send.join();
                                old_seq_num = seq_int;
                            }

                        }

                    } else {
                        if (!quiet)
                            System.out
                                    .println("Packet Loss Occurred for Packet No = "
                                            + seq_int);
                    }
                }

                fos.close();

                // Calculate MD5 hash of received file.
                String received_file_hash = Utility
                        .calculateMD5hashOfFile(output_file);

                // Compare MD5 hash with client's file and print the result.
                System.out
                        .println("\nChecking final MD5 hash of received file is same as the sender file hash:");
                if (received_file_hash.equals(initial_client_file_hash)) {
                    System.out
                            .println("The file is correctly verified with MD5 hash: "
                                    + received_file_hash + ".");
                } else {
                    System.err
                            .println("\tFile is not correctly received at receiver side. Both the hashes are different.");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        /**
         * Convert binary data from received packet into proper string format to
         * compare checksum and sequence numbers.
         * 
         * @param binaryData
         * @return
         */
        public String getStringDataFromBinaryData(String binaryData) {
            int len = binaryData.length();
            int len2 = len / 8;

            String[] intoBytes = new String[(len2)];
            char[] character = new char[len2];
            String result;

            for (int i = 0; i < len2; i++) {
                intoBytes[i] = binaryData.substring(i * 8, (i + 1) * 8);

            }
            int count = 0;

            for (int i = 9; i < len2; i++) {
                character[count] = (char) (Integer.parseInt(intoBytes[i], 2));
                count += 1;

            }
            result = Character.toString(character[0]);

            for (int i = 1; i < count; i++) {
                result += Character.toString(character[i]);

            }
            return result;
        }
    }
}
