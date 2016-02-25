/* 
 * Client.java
 * 
 * CSCI 651: Foundations of Computer Networks, Project 2. To implement RDT
 * protocol using UDP datagrams and TCP tahoe like congestion control.
 */
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Client class sends file in form of packets and receive acknowledgments for
 * each packets.
 * 
 * @author Harshit
 */
public class Client {

    // Initialize all class and instance variables

    // Change for different file sizes accordingly.
    int MSS = 1460;

    String host = null;
    boolean acked_flag = true;
    long old_seq_no_acked = 0, last_seq_no = -1;
    int port = 0;

    int timeout;
    String filename, initial_file_hash;

    Thread thread;

    static boolean ack;
    static boolean packet_sent;
    DatagramSocket send_sock;
    DatagramPacket data_packet;

    List<String> packetsToSend;

    // Variables used to implement TCP Tahoe like congestion control.
    public long CongWindow;
    public boolean congAvoidance;
    public long EffectiveWindow;
    public boolean windowChangeFlag = false;
    boolean lossOrTimeout = false;
    public long rtt;
    long startTime;
    long endTime;
    public long threshold = 1000;
    public int dupAcks = 0;
    boolean quiet = false;

    // For TCP Vegas implementation
    public long base_rtt = 100;

    // For TCP BIC implementation
    public long maxCongWindow = 1000; // Just keeping less for this example.
                                      // Otherwise it will be sufficiently
                                      // large.

    String algorithm;

    /**
     * Constructor to initialize destination host, port, filename, timeout and
     * quiet flag.
     * 
     * @param host
     * @param port
     * @param filename
     * @param timeout
     * @param quiet
     * @param algorithm
     */
    public Client(String host, int port, String filename, int timeout,
            boolean quiet, String algorithm) {
        this.host = host;
        this.port = port;
        this.filename = filename;
        this.timeout = timeout;
        this.quiet = quiet;
        this.algorithm = algorithm;
    }

    /**
     * Set up client and start sending file packets.
     */
    public void start() {

        ack = false;
        packet_sent = false;
        try {
            send_sock = new DatagramSocket();
            // Set timeout.
            send_sock.setSoTimeout(timeout);
        } catch (SocketException e) {
            System.err.println("Could not start client socket.");
            System.exit(420);
        }

        // Call sending thread.
        Thread thread = new Thread(new ClientSender());
        thread.start();
    }

    public class ClientSender implements Runnable {

        // Packet to store header(sequence number, checksum) and body.
        String packet;

        // Padding is added to ensure that the TCP header is end and data begins
        // on a 32 bit boundary.
        String padding = "0101010101010101";
        String byte_padding = "00000000";

        String payload = new String();
        int packet_num = 1;
        String seq_num;
        String checksum;

        boolean proceed_sending = true;
        boolean startflag = true;

        public void run() {
            try {

                System.out
                        .println("Starting client with congestion avoidance algorithm: "
                                + algorithm);

                // Read file contents and make packets with appropriate header
                // including checksum, sequence number.
                readFileAndCreatePacketsToSend();

                packet_num = 1;
                boolean file_sending_done = false;

                while (proceed_sending == true && !file_sending_done) {

                    // Set Congestion Avoidance flag true.
                    if (CongWindow >= threshold) {
                        congAvoidance = true;
                    }

                    if (CongWindow == 0) {
                        // Starting with Slow start
                        CongWindow = 1;
                        windowChangeFlag = true;

                    } else {
                        // Slow start state
                        if (!congAvoidance) {
                            CongWindow *= 2;
                            windowChangeFlag = true;
                        }
                        // Congestion Avaoidance state
                        else {
                            CongWindow += 1;
                            windowChangeFlag = true;
                        }
                    }

                    EffectiveWindow = CongWindow;

                    while (EffectiveWindow > 0) {

                        startTime = System.currentTimeMillis();

                        if (packet_num == packetsToSend.size()) {
                            last_seq_no = packet_num;
                        }

                        ack = false;

                        Thread th_send = new Thread(new SendPackets(
                                packetsToSend.get(packet_num - 1), host, port));
                        th_send.start();
                        th_send.join();

                        windowChangeFlag = false;
                        lossOrTimeout = false;

                        ClientReceiver client_receiver = new ClientReceiver(
                                packet_num);
                        thread = new Thread(client_receiver);
                        thread.start();
                        thread.join();

                        // Resend when packet loss or timeout occurs.
                        try {
                            proceed_sending = ack;
                            while (proceed_sending == false) {
                                SendPackets resend = new SendPackets(
                                        packetsToSend.get(packet_num - 1),
                                        host, port);
                                Thread resendth = new Thread(resend);
                                resendth.start();
                                resendth.join();

                                client_receiver = new ClientReceiver(packet_num);
                                thread = new Thread(client_receiver);
                                thread.start();
                                thread.join();

                                proceed_sending = ack;
                            }

                            old_seq_no_acked = packet_num;
                            packet_num++;
                            dupAcks = 0;
                            if (old_seq_no_acked == packetsToSend.size()) {
                                file_sending_done = true;
                                break;
                            }

                        }

                        catch (Exception e) {
                            e.printStackTrace();
                        }
                        EffectiveWindow--;
                        endTime = System.currentTimeMillis();

                        // Calculate RTT for each packet.
                        rtt = endTime - startTime;
                    }
                }

                // Last send the input file MD5 hash.
                Thread th_send = new Thread(new SendPackets(packet, host, port));
                th_send.start();
                th_send.join();

                System.out.println("The file is sent successfully.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Read input file and create packets and store them into array list.
         * 
         * @throws IOException
         */
        public void readFileAndCreatePacketsToSend() throws IOException {
            File input_file = new File(filename);
            FileInputStream fis = new FileInputStream(input_file);
            initial_file_hash = Utility.calculateMD5hashOfFile(input_file);
            System.out.println("Initial input file MD5 hash: "
                    + initial_file_hash);
            if (!quiet)
                System.out
                        .println("Read input file and creating packets of MSS: "
                                + (MSS + 40)
                                + " bytes. ("
                                + MSS
                                + " bytes of data and 40 bytes of header)");

            packetsToSend = new ArrayList<String>();

            boolean read_done = false;
            while (!read_done && fis.available() > 0) {

                byte[] read_bytes = new byte[MSS];
                int chars_read = fis.read(read_bytes, 0, MSS);

                // Copy payload to smaller sized payload to avoid sending
                // unnessessary data in the last packet.
                if (chars_read < MSS) {
                    read_done = true;
                    byte[] temp = new byte[chars_read];
                    System.arraycopy(read_bytes, 0, temp, 0, chars_read);
                    read_bytes = temp;
                }

                // Encoding to read contents from binary data file (.bin)
                String file_contents = Base64.getEncoder().encodeToString(
                        read_bytes);

                // null character
                payload = "/0";

                // Storing file data into payload variable
                for (int i = 0; i < file_contents.length(); i++) {
                    String binary_data = Integer
                            .toBinaryString((int) file_contents.charAt(i));
                    if (binary_data.length() < 8) {
                        binary_data = byte_padding.substring(0,
                                8 - binary_data.length()).concat(binary_data);
                    }

                    if (payload.equals("/0")) {
                        payload = binary_data;
                    }
                    payload = payload + binary_data;
                }

                packet = null;
                packet = createPacketToSend(packet_num, padding, payload);
                packet_num++;

                packetsToSend.add(packet);
            }
            fis.close();
        }

        /**
         * Create a packet from header and payload. Prior to that make a header
         * adding sequence number, checksum and respective padding bits.
         * 
         * @param seq_no
         * @param padding
         * @param payload
         * @return a packet
         */
        public String createPacketToSend(int seq_no, String padding,
                String payload) {

            // 16 bit initial checksum
            String initial_checksum = "0000000000000000";

            String seq_num_binary = Integer.toBinaryString(seq_no);

            // Converting sequence number into 32 bit binary representation.
            seq_num_binary = "00000000000000000000000000000000".substring(0,
                    32 - seq_num_binary.length()) + seq_num_binary;

            String data_packet = seq_num_binary + initial_checksum + padding
                    + payload;
            // return data_packet;

            // Divide into 16 bits (2 bytes) of data.
            String array_16_bits[] = Utility.divideInto16bits(data_packet);

            // Calculate checksum for the packet.
            String checksum_result = Utility.calculateChecksum(array_16_bits);

            // System.out.println("CHECKSUM VALUE :" + checksum_result);
            char[] replace_checksum = data_packet.toCharArray();

            for (int i = 32; i < 48; i++) {
                replace_checksum[i] = checksum_result.charAt(i - 32);
            }
            data_packet = String.valueOf(replace_checksum);

            return data_packet;

        }

        /**
         * Class for reliable transmission of packets from client to server.
         * 
         * @author Harshit
         */
        public class SendPackets implements Runnable {
            String packet;
            String server_ip;
            int port;

            public SendPackets(String packet, String server_ip, int port) {
                this.packet = packet;
                this.server_ip = server_ip;
                this.port = port;
            }

            public void run() {
                try {
                    // Print into table format.
                    if (packet_num == 1 && startflag && !quiet) {
                        System.out.println("Sending Packets:\n");
                        System.out.println("Packet No." + "  |  "
                                + String.format("%32s", "Sequence No.")
                                + "  |  " + String.format("%16s", "Checksum")
                                + "  |  " + "Window Size" + "  |  "
                                + String.format("%21s", "Congestion Phase")
                                + "  |  " + "Ack Status");
                        startflag = false;
                    }

                    String[] binaryData = Utility.divideInto16bits(packet);

                    seq_num = binaryData[0] + binaryData[1];
                    checksum = binaryData[2];

                    // When ack is not received send the packet.
                    if (ack == false) {
                        byte[] send_data = packet.getBytes();
                        InetAddress inetAddr = InetAddress.getByName(server_ip);

                        data_packet = new DatagramPacket(send_data,
                                send_data.length, inetAddr, port);

                        send_sock.send(data_packet);
                        packet_sent = true;

                        // Print into table format.
                        if (windowChangeFlag && !quiet)
                            System.out
                                    .println("--------------------------------------------"
                                            + "----------------"
                                            + "----------------"
                                            + "-----------"
                                            + "------------"
                                            + "--------------" + "------------");

                        if (!quiet)
                            if (!lossOrTimeout) {
                                System.out
                                        .print(String
                                                .format("%10s", packet_num)
                                                + "  |  "
                                                + seq_num
                                                + "  |  "
                                                + checksum
                                                + "  |  "
                                                + (windowChangeFlag ? String
                                                        .format("%11s",
                                                                EffectiveWindow)
                                                        : String.format("%11s",
                                                                ""))
                                                + "  |  "
                                                + (windowChangeFlag ? (congAvoidance ? String
                                                        .format("%21s",
                                                                "Congestion Avoidance")
                                                        : String.format("%21s",
                                                                "Slow Start"))
                                                        : String.format("%21s",
                                                                "")));
                            } else {
                                System.out
                                        .print(String
                                                .format("%10s", packet_num)
                                                + "  |  "
                                                + seq_num
                                                + "  |  "
                                                + checksum
                                                + "  |  "
                                                + (windowChangeFlag ? String
                                                        .format("%11s",
                                                                CongWindow)
                                                        : String.format("%11s",
                                                                ""))
                                                + "  |  "
                                                + (windowChangeFlag ? (congAvoidance ? String
                                                        .format("%21s",
                                                                "Congestion Avoidance")
                                                        : String.format("%21s",
                                                                "Slow Start"))
                                                        : String.format("%21s",
                                                                "")));
                            }
                    }

                    // Send last packet as MD5 hash of input file.
                    if (last_seq_no > 0 && ack == true) {

                        byte[] send_data = initial_file_hash.getBytes();
                        InetAddress inetAddr = InetAddress.getByName(server_ip);

                        data_packet = new DatagramPacket(send_data,
                                send_data.length, inetAddr, port);

                        send_sock.send(data_packet);
                        packet_sent = true;
                    }
                    Thread.sleep(1);
                } catch (Exception e) {
                    // System.out.println(e);
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Receiver class to accept acknowledgements.
     * 
     * @author Harshit
     */
    public class ClientReceiver implements Runnable {

        // Instance variables
        DatagramSocket datagram_sock;
        DatagramPacket recv_pkt;
        boolean received_response;
        boolean timed_out = false;
        byte[] ack_recv = new byte[256];
        int seq_no = 0;
        boolean vegas_flag = false;

        public ClientReceiver(int seq_no) {
            this.seq_no = seq_no;
        }

        public void run() {

            received_response = false;

            try {
                if (packet_sent == true) {
                    recv_pkt = new DatagramPacket(ack_recv, 100);
                    do {
                        try {
                            if (received_response == false) {
                                send_sock.receive(recv_pkt);
                                received_response = true;
                                ack = true;
                                packet_sent = false;
                                dupAcks++;
                            }
                            // Check if 3 duplicate acknoledgements received.
                            if (dupAcks >= 3) {
                                throw new Exception();
                            }
                            // Check if RTT timeout occurs.
                            if (rtt > timeout) {
                                throw new Exception();
                            }
                            if (algorithm.equalsIgnoreCase("vagas"))
                                if (rtt > base_rtt) {
                                    vegas_flag = true;
                                    throw new Exception();
                                }
                        } catch (Exception e) {

                            // When packet loss occured due to RTT timeout or 3
                            // duplicate acks.
                            timed_out = true;
                            received_response = false;
                            EffectiveWindow = 0;
                            threshold = CongWindow / 2;

                            // For TCP Vegas when RTT is increased than base_rtt
                            // than decrease window size to accomodate
                            // bandwidth.
                            if (vegas_flag) {
                                if (!quiet)
                                    System.out
                                            .println("TCP Vegas Congestion Avoidance State:");
                                if (CongWindow > 1)
                                    CongWindow = CongWindow - 1;
                            }

                            if (dupAcks >= 3) {
                                if (algorithm.equalsIgnoreCase("reno")) {
                                    if (!quiet)
                                        System.out
                                                .println("TCP Reno Congestion Avoidance: Fast Retransmit State");
                                    // Fast Retransmit in case of TCP Reno
                                    CongWindow = CongWindow / 2;
                                } else if (algorithm.equalsIgnoreCase("bic")) {
                                    if (!quiet)
                                        System.out
                                                .println("TCP BIC Congestion Avoidance.");
                                    // Increment congestion window by binary
                                    // search algorithm
                                    if (CongWindow < maxCongWindow)
                                        CongWindow += (maxCongWindow - CongWindow) / 2;
                                    else
                                        CongWindow += CongWindow
                                                - maxCongWindow;
                                } else
                                    // Default Case with TCP Tahoe
                                    CongWindow = 1;
                            } else {
                                if (algorithm.equalsIgnoreCase("bic")) {
                                    if (!quiet)
                                        System.out
                                                .println("TCP BIC Congestion Avoidance.");
                                    // Increment congestion window by binary
                                    // search algorithm
                                    if (CongWindow < maxCongWindow)
                                        CongWindow += (maxCongWindow - CongWindow) / 2;
                                    else
                                        CongWindow += CongWindow
                                                - maxCongWindow;
                                } else
                                    CongWindow = 1;
                            }
                            windowChangeFlag = true;
                            lossOrTimeout = true;
                            congAvoidance = false;
                            startTime = System.currentTimeMillis();

                            if (!quiet)
                                if (dupAcks >= 3) {
                                    System.out
                                            .println("\n3 duplicate acks received for packet number = "
                                                    + seq_no
                                                    + ". \nResending the packet and resetting window size to 1 with slow start."
                                                    + " New threshold = "
                                                    + threshold);
                                } else if (rtt > timeout) {
                                    System.out
                                            .println("\nRTT Timeout occured with RTT > "
                                                    + timeout
                                                    + " msec. for packet number = "
                                                    + seq_no
                                                    + ". \nResending the packet and resetting window size to 1 with slow start."
                                                    + " New threshold = "
                                                    + threshold);
                                } else {

                                    System.out
                                            .println("\nPacket loss occured. Timeout for packet number = "
                                                    + seq_no
                                                    + ". \nResending the packet and resetting window size to 1 with slow start."
                                                    + " New threshold = "
                                                    + threshold);
                                }
                        }

                    } while ((dupAcks == 0 && timed_out == false));

                    if (received_response == true) {
                        // If correct ack received then validate ack packet and
                        // check correct sequence number.
                        byte[] received_ack_bytes = recv_pkt.getData();
                        String received_ack_string = new String(
                                received_ack_bytes);

                        String actual_ack_string = received_ack_string
                                .substring(0, recv_pkt.getLength());
                        if (actual_ack_string.substring(48, 64).equals(
                                "1010101010101010")) {
                            String acked_seq_no = actual_ack_string.substring(
                                    0, 32);
                            long ack_seq_no = Long.parseLong(acked_seq_no, 2);
                            // Printing acked or not.
                            if (!quiet)
                                if (ack_seq_no == (old_seq_no_acked + 1)) {

                                    ack = true;
                                    old_seq_no_acked = ack_seq_no;
                                    System.out.println("  |  "
                                            + String.format("%10s", "acked"));
                                } else {
                                    ack = false;
                                    System.out
                                            .println("  |  "
                                                    + String.format("%10s",
                                                            "not acked"));
                                }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }

}
