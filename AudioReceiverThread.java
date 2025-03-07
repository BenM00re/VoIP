import java.io.IOException;
import java.net.DatagramSocket;
import uk.ac.uea.cmp.voip.DatagramSocket2;
import uk.ac.uea.cmp.voip.DatagramSocket3;
import uk.ac.uea.cmp.voip.DatagramSocket4;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.PriorityQueue;
import CMPC3M06.AudioPlayer;
import javax.sound.sampled.LineUnavailableException;
import java.util.List;
import java.util.Collections;


public class AudioReceiverThread implements Runnable {
    static DatagramSocket receivingSocket;
    private final Set<Integer> receivedSequenceNumbers = new HashSet<>(); //Keep track of received packet IDs
    private int expectedSequenceNumber = 0; //The next sequence number we expect
    private final PriorityQueue<PacketData> packetBuffer = new PriorityQueue<>(Comparator.comparingInt(p -> p.sequenceNumber)); // Buffer for out-of-order packets
    private static final int maxPacketDelay = 40; //How many packets out-of-order we tolerate
    private static final int bufferSize = 20;     //Max number of packets in the buffer
    private static final int audioBlockSize = 512;  //Size of an audio block in bytes
    private static final int InterleaveBufferSize = 10; //MATCH THE SENDER BUFFER
    private List<PacketData> DeinterleaveBuffer = new ArrayList<>(); //Buffer for deinterleaving


    //Inner class to store packet data (sequence number and audio)
    private static class PacketData {
        int sequenceNumber;
        byte[] audioData;

        public PacketData(int sequenceNumber, byte[] audioData) {
            this.sequenceNumber = sequenceNumber;
            this.audioData = audioData;
        }
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {
        int port = Address.PORT;
        AudioPlayer player = null;

        try {
            player = new AudioPlayer(); //Set up the audio player
        } catch (LineUnavailableException e) {
            System.err.println("Error initializing AudioPlayer: " + e.getMessage());
            return;  //Cant continue without audio player
        }

        try {
            receivingSocket = new DatagramSocket(port); // Use DatagramSocket3
        } catch (SocketException e) {
            System.err.println("Error creating DatagramSocket: " + e.getMessage());
            return; // Cant continue without a socket
        }

        boolean running = true;
        byte encryptionKey = 0x5A;
        //int receivedPacketCount = 0; // Not strictly needed, but kept for consistency

        try {
            while (running) {
                try {
                    byte[] packetData = new byte[518]; // Use 518 byte packets (4 for seq, 2 for key, 512 for audio)
                    DatagramPacket packet = new DatagramPacket(packetData, packetData.length);
                    receivingSocket.receive(packet); //Block until packets are received
                    //receivedPacketCount++ ;

                    ByteBuffer voipPacket = ByteBuffer.wrap(packet.getData());
                    int receivedSequenceNumber = voipPacket.getInt(); // Get sequence number
                    short receivedKey = voipPacket.getShort(); // Get key

                    if (receivedKey != 10) {
                        System.out.println("For Debugging: Rejected packet (invalid key): " + receivedSequenceNumber);
                        continue; //Skip this packet
                    }

                    if (receivedSequenceNumber < expectedSequenceNumber - maxPacketDelay) {
                        System.out.println("For Debugging: Discarded packet (too old): " + receivedSequenceNumber + " Expected: " + expectedSequenceNumber);
                        continue; //Skip this packet
                    }

                    byte[] encryptedBlock = new byte[512];
                    voipPacket.get(encryptedBlock);
                    byte[] decryptedBlock = decrypt(encryptedBlock, encryptionKey); //Decrypt

                    //Add to deinterleaveBuffer
                    DeinterleaveBuffer.add(new PacketData(receivedSequenceNumber, decryptedBlock));

                    //Deinterleave when buffer is full
                    if (DeinterleaveBuffer.size() >= InterleaveBufferSize) {
                        deinterleave(DeinterleaveBuffer);

                        //Process deinterleaved packets
                        for (PacketData p : DeinterleaveBuffer) {
                            if (!receivedSequenceNumbers.contains(p.sequenceNumber)) {
                                packetBuffer.add(p);
                                receivedSequenceNumbers.add(p.sequenceNumber);
                                System.out.println("For Debugging: Received, deinterleaved, and buffered: " + p.sequenceNumber + " Expected: " + expectedSequenceNumber + ", Buffer: " + packetBufferToString());
                            } else {
                                System.out.println("For Debugging: Duplicate packet - discarding (after deinterleave): " + p.sequenceNumber);
                            }
                        }
                        DeinterleaveBuffer.clear(); //Clear after
                    }

                    //Process packets from packetBuffer
                    while (!packetBuffer.isEmpty() && packetBuffer.peek().sequenceNumber <= expectedSequenceNumber + maxPacketDelay) {
                        if (packetBuffer.peek().sequenceNumber == expectedSequenceNumber) {
                            PacketData currentPacket = packetBuffer.poll(); //Play packet
                            System.out.println("For Debugging: Attempting to play packet: " + currentPacket.sequenceNumber);
                            player.playBlock(currentPacket.audioData);
                            System.out.println("For Debugging: Played: " + currentPacket.sequenceNumber);
                            expectedSequenceNumber++;
                        } else if (packetBuffer.peek().sequenceNumber > expectedSequenceNumber) {
                            byte[] silence = new byte[audioBlockSize]; //Insert silence
                            player.playBlock(silence);
                            System.out.println("For Debugging: Played silence (missing packet): " + expectedSequenceNumber);
                            expectedSequenceNumber++;
                        } else {
                            packetBuffer.poll(); //Packet too late
                        }
                    }
                    pruneReceivedSequenceNumbers(); //Prevent set from growing forever


                    if (packetBuffer.size() >= bufferSize) {
                        System.out.println("WARNING: Buffer approaching overflow. Discarding oldest packets.");
                        while (packetBuffer.size() > bufferSize / 2) {
                            packetBuffer.poll(); //Remove oldest
                        }
                    }


                } catch (IOException e) {
                    System.err.println("IOException in receiver thread: " + e.getMessage());
                    running = false;
                }
            }
        } finally {
            if (receivingSocket != null) {
                receivingSocket.close(); //Close socket
            }
            if (player != null) {
                player.close(); //Close player
            }
            System.out.println("Debugging: Resources closed.");
        }
    }

    //Remove sequence numbers that are too far in the past
    private void pruneReceivedSequenceNumbers() {
        if (expectedSequenceNumber > maxPacketDelay) {
            receivedSequenceNumbers.removeIf(seq -> seq < expectedSequenceNumber - maxPacketDelay);
        }
    }

    //Helper method to get a string representation of the buffer (for debugging)
    private String packetBufferToString() {
        StringBuilder sb = new StringBuilder("[");
        for (PacketData p : packetBuffer) {
            sb.append(p.sequenceNumber).append(", ");
        }
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 2);
        }
        sb.append("]");
        return sb.toString();
    }

    //Decrypt data using XOR
    private static byte[] decrypt(byte[] data, byte key) {
        return encrypt(data, key); //XOR is reversible
    }
    //Encrypt data using XOR
    private static byte[] encrypt(byte[] data, byte key) {
        byte[] encrypted = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encrypted[i] = (byte) (data[i] ^ key);  //XOR each byte
        }
        return encrypted;
    }

    private void deinterleave(List<PacketData> packets) {
        System.out.print("For DeBugging: packets before deinterleaving");
        for (PacketData p : packets) {
            System.out.print(p.sequenceNumber + ", ");
        }
        System.out.println("]");
        Collections.sort(packets, Comparator.comparingInt(p -> p.sequenceNumber)); //Sort by sequence
        System.out.print("For DeBugging: packets after deinterleaving");
        for (PacketData p : packets) {
            System.out.print(p.sequenceNumber + ", ");
        }
        System.out.println("]");
    }
}
//SOCKET2
class AudioReceiverThread2 implements Runnable {
    static DatagramSocket2 receivingSocket;
    private final Set<Integer> receivedSequenceNumbers = new HashSet<>(); //Keep track of received packet IDs
    private int expectedSequenceNumber = 0; //The next sequence number we expect
    private final PriorityQueue<PacketData> packetBuffer = new PriorityQueue<>(Comparator.comparingInt(p -> p.sequenceNumber)); // Buffer for out-of-order packets
    private static final int maxPacketDelay = 40; //How many packets out-of-order we tolerate
    private static final int bufferSize = 20;     //Max number of packets in the buffer
    private static final int audioBlockSize = 512;  //Size of an audio block in bytes
    private static final int InterleaveBufferSize = 10; //MATCH THE SENDER BUFFER
    private List<PacketData> DeinterleaveBuffer = new ArrayList<>(); //Buffer for deinterleaving


    //Inner class to store packet data (sequence number and audio)
    private static class PacketData {
        int sequenceNumber;
        byte[] audioData;

        public PacketData(int sequenceNumber, byte[] audioData) {
            this.sequenceNumber = sequenceNumber;
            this.audioData = audioData;
        }
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {
        int port = Address.PORT;
        AudioPlayer player = null;

        try {
            player = new AudioPlayer(); //Set up the audio player
        } catch (LineUnavailableException e) {
            System.err.println("Error initializing AudioPlayer: " + e.getMessage());
            return;  //Cant continue without audio player
        }

        try {
            receivingSocket = new DatagramSocket2(port); // Use DatagramSocket3
        } catch (SocketException e) {
            System.err.println("Error creating DatagramSocket: " + e.getMessage());
            return; // Cant continue without a socket
        }

        boolean running = true;
        byte encryptionKey = 0x5A;
        //int receivedPacketCount = 0; // Not strictly needed, but kept for consistency

        try {
            while (running) {
                try {
                    byte[] packetData = new byte[518]; // Use 518 byte packets (4 for seq, 2 for key, 512 for audio)
                    DatagramPacket packet = new DatagramPacket(packetData, packetData.length);
                    receivingSocket.receive(packet); //Block until packets are received
                    //receivedPacketCount++ ;

                    ByteBuffer voipPacket = ByteBuffer.wrap(packet.getData());
                    int receivedSequenceNumber = voipPacket.getInt(); // Get sequence number
                    short receivedKey = voipPacket.getShort(); // Get key

                    if (receivedKey != 10) {
                        System.out.println("For Debugging: Rejected packet (invalid key): " + receivedSequenceNumber);
                        continue; //Skip this packet
                    }

                    if (receivedSequenceNumber < expectedSequenceNumber - maxPacketDelay) {
                        System.out.println("For Debugging: Discarded packet (too old): " + receivedSequenceNumber + " Expected: " + expectedSequenceNumber);
                        continue; //Skip this packet
                    }

                    byte[] encryptedBlock = new byte[512];
                    voipPacket.get(encryptedBlock);
                    byte[] decryptedBlock = decrypt(encryptedBlock, encryptionKey); //Decrypt

                    //Add to deinterleaveBuffer
                    DeinterleaveBuffer.add(new PacketData(receivedSequenceNumber, decryptedBlock));

                    //Deinterleave when buffer is full
                    if (DeinterleaveBuffer.size() >= InterleaveBufferSize) {
                        deinterleave(DeinterleaveBuffer);

                        //Process deinterleaved packets
                        for (PacketData p : DeinterleaveBuffer) {
                            if (!receivedSequenceNumbers.contains(p.sequenceNumber)) {
                                packetBuffer.add(p);
                                receivedSequenceNumbers.add(p.sequenceNumber);
                                System.out.println("For Debugging: Received, deinterleaved, and buffered: " + p.sequenceNumber + " Expected: " + expectedSequenceNumber + ", Buffer: " + packetBufferToString());
                            } else {
                                System.out.println("For Debugging: Duplicate packet - discarding (after deinterleave): " + p.sequenceNumber);
                            }
                        }
                        DeinterleaveBuffer.clear(); //Clear after
                    }

                    //Process packets from packetBuffer
                    while (!packetBuffer.isEmpty() && packetBuffer.peek().sequenceNumber <= expectedSequenceNumber + maxPacketDelay) {
                        if (packetBuffer.peek().sequenceNumber == expectedSequenceNumber) {
                            PacketData currentPacket = packetBuffer.poll(); //Play packet
                            System.out.println("For Debugging: Attempting to play packet: " + currentPacket.sequenceNumber);
                            player.playBlock(currentPacket.audioData);
                            System.out.println("For Debugging: Played: " + currentPacket.sequenceNumber);
                            expectedSequenceNumber++;
                        } else if (packetBuffer.peek().sequenceNumber > expectedSequenceNumber) {
                            byte[] silence = new byte[audioBlockSize]; //Insert silence
                            player.playBlock(silence);
                            System.out.println("For Debugging: Played silence (missing packet): " + expectedSequenceNumber);
                            expectedSequenceNumber++;
                        } else {
                            packetBuffer.poll(); //Packet too late
                        }
                    }
                    pruneReceivedSequenceNumbers(); //Prevent set from growing forever


                    if (packetBuffer.size() >= bufferSize) {
                        System.out.println("WARNING: Buffer approaching overflow. Discarding oldest packets.");
                        while (packetBuffer.size() > bufferSize / 2) {
                            packetBuffer.poll(); //Remove oldest
                        }
                    }


                } catch (IOException e) {
                    System.err.println("IOException in receiver thread: " + e.getMessage());
                    running = false;
                }
            }
        } finally {
            if (receivingSocket != null) {
                receivingSocket.close(); //Close socket
            }
            if (player != null) {
                player.close(); //Close player
            }
            System.out.println("Debugging: Resources closed.");
        }
    }

    //Remove sequence numbers that are too far in the past
    private void pruneReceivedSequenceNumbers() {
        if (expectedSequenceNumber > maxPacketDelay) {
            receivedSequenceNumbers.removeIf(seq -> seq < expectedSequenceNumber - maxPacketDelay);
        }
    }

    //Helper method to get a string representation of the buffer (for debugging)
    private String packetBufferToString() {
        StringBuilder sb = new StringBuilder("[");
        for (PacketData p : packetBuffer) {
            sb.append(p.sequenceNumber).append(", ");
        }
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 2);
        }
        sb.append("]");
        return sb.toString();
    }

    //Decrypt data using XOR
    private static byte[] decrypt(byte[] data, byte key) {
        return encrypt(data, key); //XOR is reversible
    }
    //Encrypt data using XOR
    private static byte[] encrypt(byte[] data, byte key) {
        byte[] encrypted = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encrypted[i] = (byte) (data[i] ^ key);  //XOR each byte
        }
        return encrypted;
    }

    private void deinterleave(List<PacketData> packets) {
        System.out.print("For DeBugging: packets before deinterleaving");
        for (PacketData p : packets) {
            System.out.print(p.sequenceNumber + ", ");
        }
        System.out.println("]");
        Collections.sort(packets, Comparator.comparingInt(p -> p.sequenceNumber)); //Sort by sequence
        System.out.print("For DeBugging: packets after deinterleaving");
        for (PacketData p : packets) {
            System.out.print(p.sequenceNumber + ", ");
        }
        System.out.println("]");
    }
}
//SOCKET3
class AudioReceiverThread3 implements Runnable {
    static DatagramSocket3 receivingSocket;
    private final Set<Integer> receivedSequenceNumbers = new HashSet<>(); //Keep track of received packet IDs
    private int expectedSequenceNumber = 0; //The next sequence number we expect
    private final PriorityQueue<PacketData> packetBuffer = new PriorityQueue<>(Comparator.comparingInt(p -> p.sequenceNumber)); // Buffer for out-of-order packets
    private static final int maxPacketDelay = 40; //How many packets out-of-order we tolerate
    private static final int bufferSize = 20;     //Max number of packets in the buffer
    private static final int audioBlockSize = 512;  //Size of an audio block in bytes
    private static final int InterleaveBufferSize = 10; //MATCH THE SENDER BUFFER
    private List<PacketData> DeinterleaveBuffer = new ArrayList<>(); //Buffer for deinterleaving


    //Inner class to store packet data (sequence number and audio)
    private static class PacketData {
        int sequenceNumber;
        byte[] audioData;

        public PacketData(int sequenceNumber, byte[] audioData) {
            this.sequenceNumber = sequenceNumber;
            this.audioData = audioData;
        }
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {
        int port = Address.PORT;
        AudioPlayer player = null;

        try {
            player = new AudioPlayer(); //Set up the audio player
        } catch (LineUnavailableException e) {
            System.err.println("Error initializing AudioPlayer: " + e.getMessage());
            return;  //Cant continue without audio player
        }

        try {
            receivingSocket = new DatagramSocket3(port); // Use DatagramSocket3
        } catch (SocketException e) {
            System.err.println("Error creating DatagramSocket: " + e.getMessage());
            return; // Cant continue without a socket
        }

        boolean running = true;
        byte encryptionKey = 0x5A;
        //int receivedPacketCount = 0; // Not strictly needed, but kept for consistency

        try {
            while (running) {
                try {
                    byte[] packetData = new byte[518]; // Use 518 byte packets (4 for seq, 2 for key, 512 for audio)
                    DatagramPacket packet = new DatagramPacket(packetData, packetData.length);
                    receivingSocket.receive(packet); //Block until packets are received
                    //receivedPacketCount++ ;

                    ByteBuffer voipPacket = ByteBuffer.wrap(packet.getData());
                    int receivedSequenceNumber = voipPacket.getInt(); // Get sequence number
                    short receivedKey = voipPacket.getShort(); // Get key

                    if (receivedKey != 10) {
                        System.out.println("For Debugging: Rejected packet (invalid key): " + receivedSequenceNumber);
                        continue; //Skip this packet
                    }

                    if (receivedSequenceNumber < expectedSequenceNumber - maxPacketDelay) {
                        System.out.println("For Debugging: Discarded packet (too old): " + receivedSequenceNumber + " Expected: " + expectedSequenceNumber);
                        continue; //Skip this packet
                    }

                    byte[] encryptedBlock = new byte[512];
                    voipPacket.get(encryptedBlock);
                    byte[] decryptedBlock = decrypt(encryptedBlock, encryptionKey); //Decrypt

                    //Add to deinterleaveBuffer
                    DeinterleaveBuffer.add(new PacketData(receivedSequenceNumber, decryptedBlock));

                    //Deinterleave when buffer is full
                    if (DeinterleaveBuffer.size() >= InterleaveBufferSize) {
                        deinterleave(DeinterleaveBuffer);

                        //Process deinterleaved packets
                        for (PacketData p : DeinterleaveBuffer) {
                            if (!receivedSequenceNumbers.contains(p.sequenceNumber)) {
                                packetBuffer.add(p);
                                receivedSequenceNumbers.add(p.sequenceNumber);
                                System.out.println("For Debugging: Received, deinterleaved, and buffered: " + p.sequenceNumber + " Expected: " + expectedSequenceNumber + ", Buffer: " + packetBufferToString());
                            } else {
                                System.out.println("For Debugging: Duplicate packet - discarding (after deinterleave): " + p.sequenceNumber);
                            }
                        }
                        DeinterleaveBuffer.clear(); //Clear after
                    }

                    //Process packets from packetBuffer
                    while (!packetBuffer.isEmpty() && packetBuffer.peek().sequenceNumber <= expectedSequenceNumber + maxPacketDelay) {
                        if (packetBuffer.peek().sequenceNumber == expectedSequenceNumber) {
                            PacketData currentPacket = packetBuffer.poll(); //Play packet
                            System.out.println("For Debugging: Attempting to play packet: " + currentPacket.sequenceNumber);
                            player.playBlock(currentPacket.audioData);
                            System.out.println("For Debugging: Played: " + currentPacket.sequenceNumber);
                            expectedSequenceNumber++;
                        } else if (packetBuffer.peek().sequenceNumber > expectedSequenceNumber) {
                            byte[] silence = new byte[audioBlockSize]; //Insert silence
                            player.playBlock(silence);
                            System.out.println("For Debugging: Played silence (missing packet): " + expectedSequenceNumber);
                            expectedSequenceNumber++;
                        } else {
                            packetBuffer.poll(); //Packet too late
                        }
                    }
                    pruneReceivedSequenceNumbers(); //Prevent set from growing forever


                    if (packetBuffer.size() >= bufferSize) {
                        System.out.println("WARNING: Buffer approaching overflow. Discarding oldest packets.");
                        while (packetBuffer.size() > bufferSize / 2) {
                            packetBuffer.poll(); //Remove oldest
                        }
                    }


                } catch (IOException e) {
                    System.err.println("IOException in receiver thread: " + e.getMessage());
                    running = false;
                }
            }
        } finally {
            if (receivingSocket != null) {
                receivingSocket.close(); //Close socket
            }
            if (player != null) {
                player.close(); //Close player
            }
            System.out.println("Debugging: Resources closed.");
        }
    }

    //Remove sequence numbers that are too far in the past
    private void pruneReceivedSequenceNumbers() {
        if (expectedSequenceNumber > maxPacketDelay) {
            receivedSequenceNumbers.removeIf(seq -> seq < expectedSequenceNumber - maxPacketDelay);
        }
    }

    //Helper method to get a string representation of the buffer (for debugging)
    private String packetBufferToString() {
        StringBuilder sb = new StringBuilder("[");
        for (PacketData p : packetBuffer) {
            sb.append(p.sequenceNumber).append(", ");
        }
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 2);
        }
        sb.append("]");
        return sb.toString();
    }

    //Decrypt data using XOR
    private static byte[] decrypt(byte[] data, byte key) {
        return encrypt(data, key); //XOR is reversible
    }
    //Encrypt data using XOR
    private static byte[] encrypt(byte[] data, byte key) {
        byte[] encrypted = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encrypted[i] = (byte) (data[i] ^ key);  //XOR each byte
        }
        return encrypted;
    }

    private void deinterleave(List<PacketData> packets) {
        System.out.print("For DeBugging: packets before deinterleaving");
        for (PacketData p : packets) {
            System.out.print(p.sequenceNumber + ", ");
        }
        System.out.println("]");
        Collections.sort(packets, Comparator.comparingInt(p -> p.sequenceNumber)); //Sort by sequence
        System.out.print("For DeBugging: packets after deinterleaving");
        for (PacketData p : packets) {
            System.out.print(p.sequenceNumber + ", ");
        }
        System.out.println("]");
    }
}
//SOCKET4
class AudioReceiverThread4 implements Runnable {
    static DatagramSocket4 receivingSocket;
    private final Set<Integer> receivedSequenceNumbers = new HashSet<>(); //Keep track of received packet IDs
    private int expectedSequenceNumber = 0; //The next sequence number we expect
    private final PriorityQueue<PacketData> packetBuffer = new PriorityQueue<>(Comparator.comparingInt(p -> p.sequenceNumber)); // Buffer for out-of-order packets
    private static final int maxPacketDelay = 40; //How many packets out-of-order we tolerate
    private static final int bufferSize = 20;     //Max number of packets in the buffer
    private static final int audioBlockSize = 512;  //Size of an audio block in bytes
    private static final int InterleaveBufferSize = 10; //MATCH THE SENDER BUFFER
    private List<PacketData> DeinterleaveBuffer = new ArrayList<>(); //Buffer for deinterleaving


    //Inner class to store packet data (sequence number and audio)
    private static class PacketData {
        int sequenceNumber;
        byte[] audioData;

        public PacketData(int sequenceNumber, byte[] audioData) {
            this.sequenceNumber = sequenceNumber;
            this.audioData = audioData;
        }
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {
        int port = Address.PORT;
        AudioPlayer player = null;

        try {
            player = new AudioPlayer(); //Set up the audio player
        } catch (LineUnavailableException e) {
            System.err.println("Error initializing AudioPlayer: " + e.getMessage());
            return;  //Cant continue without audio player
        }

        try {
            receivingSocket = new DatagramSocket4(port); // Use DatagramSocket3
        } catch (SocketException e) {
            System.err.println("Error creating DatagramSocket: " + e.getMessage());
            return; // Cant continue without a socket
        }

        boolean running = true;
        byte encryptionKey = 0x5A;
        //int receivedPacketCount = 0; // Not strictly needed, but kept for consistency

        try {
            while (running) {
                try {
                    byte[] packetData = new byte[518]; // Use 518 byte packets (4 for seq, 2 for key, 512 for audio)
                    DatagramPacket packet = new DatagramPacket(packetData, packetData.length);
                    receivingSocket.receive(packet); //Block until packets are received
                    //receivedPacketCount++ ;

                    ByteBuffer voipPacket = ByteBuffer.wrap(packet.getData());
                    int receivedSequenceNumber = voipPacket.getInt(); // Get sequence number
                    short receivedKey = voipPacket.getShort(); // Get key

                    if (receivedKey != 10) {
                        System.out.println("For Debugging: Rejected packet (invalid key): " + receivedSequenceNumber);
                        continue; //Skip this packet
                    }

                    if (receivedSequenceNumber < expectedSequenceNumber - maxPacketDelay) {
                        System.out.println("For Debugging: Discarded packet (too old): " + receivedSequenceNumber + " Expected: " + expectedSequenceNumber);
                        continue; //Skip this packet
                    }

                    byte[] encryptedBlock = new byte[512];
                    voipPacket.get(encryptedBlock);
                    byte[] decryptedBlock = decrypt(encryptedBlock, encryptionKey); //Decrypt

                    //Add to deinterleaveBuffer
                    DeinterleaveBuffer.add(new PacketData(receivedSequenceNumber, decryptedBlock));

                    //Deinterleave when buffer is full
                    if (DeinterleaveBuffer.size() >= InterleaveBufferSize) {
                        deinterleave(DeinterleaveBuffer);

                        //Process deinterleaved packets
                        for (PacketData p : DeinterleaveBuffer) {
                            if (!receivedSequenceNumbers.contains(p.sequenceNumber)) {
                                packetBuffer.add(p);
                                receivedSequenceNumbers.add(p.sequenceNumber);
                                System.out.println("For Debugging: Received, deinterleaved, and buffered: " + p.sequenceNumber + " Expected: " + expectedSequenceNumber + ", Buffer: " + packetBufferToString());
                            } else {
                                System.out.println("For Debugging: Duplicate packet - discarding (after deinterleave): " + p.sequenceNumber);
                            }
                        }
                        DeinterleaveBuffer.clear(); //Clear after
                    }

                    //Process packets from packetBuffer
                    while (!packetBuffer.isEmpty() && packetBuffer.peek().sequenceNumber <= expectedSequenceNumber + maxPacketDelay) {
                        if (packetBuffer.peek().sequenceNumber == expectedSequenceNumber) {
                            PacketData currentPacket = packetBuffer.poll(); //Play packet
                            System.out.println("For Debugging: Attempting to play packet: " + currentPacket.sequenceNumber);
                            player.playBlock(currentPacket.audioData);
                            System.out.println("For Debugging: Played: " + currentPacket.sequenceNumber);
                            expectedSequenceNumber++;
                        } else if (packetBuffer.peek().sequenceNumber > expectedSequenceNumber) {
                            byte[] silence = new byte[audioBlockSize]; //Insert silence
                            player.playBlock(silence);
                            System.out.println("For Debugging: Played silence (missing packet): " + expectedSequenceNumber);
                            expectedSequenceNumber++;
                        } else {
                            packetBuffer.poll(); //Packet too late
                        }
                    }
                    pruneReceivedSequenceNumbers(); //Prevent set from growing forever


                    if (packetBuffer.size() >= bufferSize) {
                        System.out.println("WARNING: Buffer approaching overflow. Discarding oldest packets.");
                        while (packetBuffer.size() > bufferSize / 2) {
                            packetBuffer.poll(); //Remove oldest
                        }
                    }


                } catch (IOException e) {
                    System.err.println("IOException in receiver thread: " + e.getMessage());
                    running = false;
                }
            }
        } finally {
            if (receivingSocket != null) {
                receivingSocket.close(); //Close socket
            }
            if (player != null) {
                player.close(); //Close player
            }
            System.out.println("Debugging: Resources closed.");
        }
    }

    //Remove sequence numbers that are too far in the past
    private void pruneReceivedSequenceNumbers() {
        if (expectedSequenceNumber > maxPacketDelay) {
            receivedSequenceNumbers.removeIf(seq -> seq < expectedSequenceNumber - maxPacketDelay);
        }
    }

    //Helper method to get a string representation of the buffer (for debugging)
    private String packetBufferToString() {
        StringBuilder sb = new StringBuilder("[");
        for (PacketData p : packetBuffer) {
            sb.append(p.sequenceNumber).append(", ");
        }
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 2);
        }
        sb.append("]");
        return sb.toString();
    }

    //Decrypt data using XOR
    private static byte[] decrypt(byte[] data, byte key) {
        return encrypt(data, key); //XOR is reversible
    }
    //Encrypt data using XOR
    private static byte[] encrypt(byte[] data, byte key) {
        byte[] encrypted = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encrypted[i] = (byte) (data[i] ^ key);  //XOR each byte
        }
        return encrypted;
    }

    private void deinterleave(List<PacketData> packets) {
        System.out.print("For DeBugging: packets before deinterleaving");
        for (PacketData p : packets) {
            System.out.print(p.sequenceNumber + ", ");
        }
        System.out.println("]");
        Collections.sort(packets, Comparator.comparingInt(p -> p.sequenceNumber)); //Sort by sequence
        System.out.print("For DeBugging: packets after deinterleaving");
        for (PacketData p : packets) {
            System.out.print(p.sequenceNumber + ", ");
        }
        System.out.println("]");
    }
}
