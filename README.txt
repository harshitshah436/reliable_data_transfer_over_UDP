Reliable Data Transfer over UDP

Author: Harshit Shah

Goal: Developing a Reliable Data Transfer Protocol using UDP datagrams and TCP Tahoe like congestion control mechanism.

Application running instructions:
>	To compile and run source files
1)	Unzip hrs8207.zip then go to directory \hrs8207\src\.
2)	Run command: 
javac fcntcp.java
Note: all source files should be present in the directory. Also place the input file in the same directory.
3)	Run command: 
java fcntcp -{c, s} [options] [server address] port 

Packet Format (Client to Sever):
----------------------32 bits----------------------|-----------16 bits-----------|
                   Sequence number                    |            Checksum            |
---------------------------------------------------------------------------------------
                             Payload-Packet Data (file contents)

This is my packet format sending from client to server. All the data is store in binary format.
Packet header is made of first 32 bits (binary sequence number) and next 16 bit of checksum. Then all the data is contents of a file.
Acknowledge Packet Format (Server to Client)
----------------------32 bits----------------------|-----------16 bits-----------|-----------16 bits-----------
                         Ack number                         |            checksum            |           ack indicator        
Here, Server side checksum would be ‘0000000000000000’ for correctly received packets and ack indicator will always be ‘1010101010101010‘.

Application Messages:
Packet loss/ Corrupt Packet/ Excessive Delay: 
Same error message because catching exception in the same block.
Packet loss occurred. Timeout for packet number = #.
Resending the packet and resetting window size to 1 with slow start. New threshold = #. 
3 Duplicate Acks received:
3 duplicate acks received for packet number = #.
Resending the packet and resetting window size to 1 with slow start. New threshold = #.

Error Codes:
The application shows error messages and exit the programs with the error codes.
fcntcp:
Error Codes:
401: Usage: java fcntcp -{c, s} [options] [server address] port.
402: Usage: java fcntcp -s [options] port. 
         Please start server with port details.
403: Usage: java fcntcp -c [options] <server_address> port.
         Please start client with destination host and port details. 
404: Usage: java fcntcp -{c, s} [options] [server address] port. No other parameters are allowed.
405: Usage: java fcntcp -{c, s} [options] [server address] port. fcntcp can only be started by -c or -s.
Default message with all above: Note: case sensitive. Server: -s, Client: -c

Failure Messages:
410/420: Could not listen on port: port_number. (This error occurs while starting server on specific UDP listening port)

Description about all source files:
•	fcntcp.java: fcntcp class contains main method. This class parses command line arguments to run the application (server/client).
•	Server.java: Server class receives file in form of packets and send acknowledgement for each packets.
•	Client.java: Client class sends file in form of packets and receive acknowledgments for each packets. This class also implements TCP Tahoe like congestion control.
•	Utility.java: Utility class to perform binary operations, MD5 hash generation and file operations.
Note: Please refer all source files for detailed explanation and understanding of application.


Additional Algorithms:
1)	TCP Tahoe: All of above working is shown for TCP Tahoe congestion control. If not algorithm specified then by default the congestion control will be TCP Tahoe.
2)	TCP Reno: Only difference between TCP Tahoe and TCP Reno is when 3 duplicate acks received and RTT timeout occurs, in TCP Reno we need to perform fast transmit by decreasing congestion window to half unlike a slow start in TCP Tahoe.
Run command: java fcntcp -c -f alice.txt -a reno 3000
3)	TCP Vegas: More focused on RTT delay rather than timeout. After sending each packet it will keep track of RTT and if it’s greater than Base RTT it will decrease the congestion window by 1 and if less than expected RTT then will increase the congestion window size.
Run command: java fcntcp -c -f alice.txt -a vegas 3000
4)	BIC: Binary Increase Congestion Control (BIC) uses binary search algorithm to adjust congestion window.
Basic Algorithm:
–	If cwnd < Wmax
cwnd += (Wmax – cwnd) / 2
–	Else
cwnd += cwnd - Wmax
Run command: java fcntcp -c -f alice.txt -a bic 3000