/* 
 * fcntcp.java
 * 
 * CSCI 651: Foundations of Computer Networks, Project 2. To implement RDT
 * protocol using UDP datagrams and TCP tahoe like congestion control.
 */
/**
 * fcntcp class contains main method. This class takes command line arguments to
 * run the application.
 * 
 * @author Harshit
 */
public class fcntcp {

    /**
     * class variables which is updated by parsing command line arguments.
     */
    static String sc_identify = "", username = "", password = "", host = "",
            file = "", algorithm = "tahoe";
    // default timeout to 1s.
    static int timeout = 1000;
    static int port, remote_tcp_port, local_proxy_udp_port,
            local_proxy_tcp_port, query_time;
    static boolean quiet = false;

    /**
     * main method.
     * 
     * @param args
     *            command-line arguments
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            usage(401);
        }
        // Check if server or client.
        for (int i = 0; i < args.length; i++) {
            switch (i) {
            case 0:
                if (args[0].equals("-s") || args[0].equals("-c")) {
                    sc_identify = args[0];
                } else {
                    usage(404);
                }
                break;
            default:
                i = getFlags(args, i);
                break;
            }
        }
        startServerOrClientOrProxy();
    }

    /**
     * Update respective class variables from given/parsing command-line
     * arguments.
     * 
     * @param args
     *            commandline arguments
     * @param i
     *            current i
     * @return integer
     */
    private static int getFlags(String[] args, int i) {
        if (args[i].startsWith("-")) {
            if (args[i].equals("-f")) {
                file = args[i + 1];
                return ++i;
            } else if (args[i].equals("-t")) {
                timeout = Integer.parseInt(args[i + 1]);
                return ++i;
            } else if (args[i].equals("-a")) {
                algorithm = args[i + 1];
                return ++i;
            }
            else if (args[i].equals("-q")) {
                quiet = true;
            } else {
                usage(405);
            }
        } else if (!isInteger(args[i])) {
            host = args[i];
        } else {
            port = Integer.parseInt(args[i]);
        }
        return i;
    }

    /**
     * Start server or client based on parsed command line argument.
     */
    private static void startServerOrClientOrProxy() {
        if (sc_identify.equals("-s")) {
            if (port == 0) {
                usage(402);
            }
            if (port != 0) {
                System.out.println("Starting Server on port: " + port);
                // Starting server
                new Server(port, quiet).start();

            }
        }
        // Else start client
        else {
            if (port == 0) {
                usage(403);
            }
            if (port != 0) {
                System.out
                        .println("Starting Client for: " + host + ": " + port);

                // Starting client
                new Client(host.isEmpty() ? "localhost" : host, port,
                        file.isEmpty() ? "alice.txt" : file, timeout, quiet, algorithm)
                        .start();

            }
        }

    }

    /**
     * Check if given string is integer or not
     * 
     * @param s
     *            string
     * @return true if string contains only numeric value.
     */
    public static boolean isInteger(String s) {
        int radix = 10;
        if (s.isEmpty())
            return false;
        for (int i = 0; i < s.length(); i++) {
            if (i == 0 && s.charAt(i) == '-') {
                if (s.length() == 1) {
                    return false;
                } else
                    continue;
            }
            if (Character.digit(s.charAt(i), radix) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Print error messages and exit the program for invalid arguments.
     * 
     * @param i
     */
    private static void usage(int i) {
        switch (i) {
        case 401:
            System.err
                    .println("Usage: java fcntcp -{c,s} [options] [server address] port");
            break;
        case 402:
            System.err
                    .println("Usage: java fcntcp -s [options] port. \nPlease start server with port details.");
            break;
        case 403:
            System.err
                    .println("Usage: java fcntcp -c [options] <server_address> port. \nPlease start client with destination host and port details.");
            break;

        case 404:
            System.err
                    .println("Usage: java fcntcp -{c,s} [options] [server address] port. No other parameters are allowed");
            break;

        case 405:
            System.err
                    .println("Usage: java fcntcp -{c,s} [options] [server address] port. fcntcp can only be started by -c or -s.");
            break;

        default:
            break;
        }
        System.err.println("Note: case sensitive. Server: -s, Client: -c");
        System.exit(i);
    }
}
